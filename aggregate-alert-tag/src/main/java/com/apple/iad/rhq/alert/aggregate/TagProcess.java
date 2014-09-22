package com.apple.iad.rhq.alert.aggregate;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.DataType;
import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.rhq.core.domain.measurement.MeasurementDataTrait;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.measurement.MeasurementUnits;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.rhq.enterprise.server.measurement.MeasurementDataManagerLocal;
import org.rhq.enterprise.server.plugin.pc.ControlFacet;
import org.rhq.enterprise.server.plugin.pc.ControlResults;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginContext;
import org.rhq.enterprise.server.util.LookupUtil;

import com.apple.iad.rhq.alert.aggregate.Expr.Func;

/**
 * Aggregate alert tag processing.
 */
public class TagProcess implements ServerPluginComponent, ControlFacet {

    private final SimpleDateFormat DF = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

    /**
     * Tag Namespace for alerts.
     */
    public static final String NAMESPACE = "alert";

    /**
     * Query; should be moved to EJB.
     */
    private static final String QUERY_TAG = "SELECT t FROM Tag t where t.namespace = ?1";

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Config setting for default metrics window.
     */
    private final String WINDOW = "window";

    /**
     * Config setting for default metrics day.
     */
    private final String GOBACKHOURS = "gobackhours";
    /**
     * Config setting for not-a-number alerting.
     */
    private final String NAN = "nan";

    /**
     * Minutes to look back by default.
     */
    private int window;

    /**
     * Hours to look back; by default it is set to look at current UTC hour -1
     */
    private int gobackhours;

    /**
     * The Spend Hour for which alert is fired
     */
    private int spendhour;

    /**
     * Create an alert if no metrics are being returned.
     */
    private boolean nan = true;

    private final String SUBJECT = "subject";
    private String subject;

    /***
     * We use the full transaction manager, since we use synchronization to fire
     * alerts.
     */
    private TransactionManager transactionManager;

    /**
     * End time (current time) for looking up metrics.
     */
    private long end;

    /**
     * Begin time (current hour - 2) and End time (current hour -1) for looking
     * up metrics.
     */
    private long bhour;
    private long ehour;

    /**
     * Stores recently queried metrics.
     */
    private final MetricsCache cache = new MetricsCache();

    @Override
    public void initialize(ServerPluginContext context) throws Exception {
        Configuration conf = context.getPluginConfiguration();
        init(conf);
        transactionManager = LookupUtil.getTransactionManager();
    }

    void init(Configuration conf) {
        String win = conf.getSimpleValue(WINDOW, "15");
        window = Integer.parseInt(win);

        String goback = conf.getSimpleValue(GOBACKHOURS, "1");
        gobackhours = Integer.parseInt(goback);
        nan = Boolean.parseBoolean(conf.getSimpleValue(NAN, "true"));
        subject = conf.getSimpleValue(SUBJECT, "ack auto processor");
    }

    @Override
    public void start() {
    }

    /**
     * Method invoked by the scheduler.
     */
    public void process() {
        try {
            transactionManager.begin();
            processInTx();
        } catch (Exception e) {
            try {
                transactionManager.rollback();
            } catch (Exception e1) {
                log.error("rollback", e1);
            }
            log.error("failed to process", e);
        } finally {
            try {
                transactionManager.commit();
            } catch (Exception e) {
                log.error("commit", e);
            }
        }
    }

    private void processInTx() throws Exception {
        // use same relative times for querying metrics database
        end = System.currentTimeMillis();

        bhour = getTodayBeginTime();
        ehour = getTodayEndTime();

        // must be cleared, otherwise memory leak
        cache.clear();

        EntityManager em = getEntityManager();
        // Ensure no lingering entities
        em.clear();
        List<Tag> tags = em.createQuery(QUERY_TAG).setParameter(1, NAMESPACE).getResultList();
        for (Tag tag : tags) {
            processTag(em, tag);
        }
        long elapsed = System.currentTimeMillis() - end;
        log.info("took " + elapsed + "ms to process " + tags.size() + " tags");
    }

    EntityManager getEntityManager() {
        return LookupUtil.getEntityManager();
    }

    void processTag(EntityManager em, Tag tag) {
        log.debug("processTag " + tag);
        Set<Resource> resources = tag.getResources();
        Set<ResourceGroup> groups = tag.getResourceGroups();
        try {
            Expr expr = new Expr(tag);
            if (resources.isEmpty() && groups.isEmpty()) {
                log.debug("remove unused tag");
                em.remove(tag);
                return;
            }
            for (Resource resource : resources) {
                log.info("checking metrics for " + tag + " resource " + resource.getName());
                eval(resource, expr);
            }
            for (ResourceGroup group : groups) {
                log.info("checking metrics for " + tag + " group " + group.getName());
                eval(group, expr);
            }
        } catch (IllegalArgumentException e) {
            log.warn("invalid tag " + tag + ", removing", e);
            em.remove(tag);
        } catch (Exception e) {
            log.error("failed to process " + tag, e);
        }
    }

    /**
     * Process a resource and expression.
     *
     * @return true if an alert was fired
     */
    boolean eval(Resource resource, Expr expr) {
        log.debug("eval resource " + resource);
        Func f = expr.getFunc();
        if (f == Func.sum || f.getParentFunc() == Func.sum) {
            log.warn("cannot be a sum function " + expr);
            return false;
        }
        if (expr.getFunc().isSingle()) {
            log.warn("cannot be the function " + expr);
            return false;
        }

        MeasurementSchedule mschedule = null;
        MeasurementDefinition md = null;
        for (MeasurementSchedule schedule : resource.getSchedules()) {
            md = schedule.getDefinition();
            String dn = md.getName();
            if (dn.contains(expr.getMetric())) {
                // note always use 'perMinute' or default on
                if (mschedule == null || md.isPerMinute() || md.isDefaultOn()) {
                    mschedule = schedule;
                }
            }
        }
        if (mschedule == null) {
            log.warn("metric name not found: '" + expr.getMetric() + "' in " + resource.getSchedules());
            return false;
        }
        if (log.isDebugEnabled())
            log.debug("schedule" + mschedule);
        if (!mschedule.isEnabled()) {
            String msg = "metric gathering not enabled for " + expr + " for " + resource;
            log.warn(msg);
            if (nan) {
                AlertProcessor alertProcessor = new AlertProcessor(expr, resource, md);
                alertProcessor.process(msg);
            }
            return true;
        }

        if (expr.getFunc() == Func.percd || expr.getFunc() == Func.percw) {
            return perc(resource, expr, md, mschedule);
        }

        long mwindow = window(expr);
        if (mschedule.getInterval() > mwindow && nan) {
            String msg = "metric schedule too infrequent; " + mschedule.getInterval() + " > than " + mwindow;
            log.warn(msg);
            if (nan) {
                AlertProcessor alertProcessor = new AlertProcessor(expr, resource, md);
                alertProcessor.process(msg);
            }
            return true;
        }

        final long begin = end - mwindow;

        boolean alert;

        MeasurementAggregate aggregate = getAggregate(resource, mschedule, begin, end);
        log.debug("eval aggregate=" + aggregate);

        String message;
        Double val1 = expr.value(aggregate);
        assert md != null;
        MeasurementUnits units = md.getUnits();
        if (expr.getFunc().hasParent()) {
            int days = expr.getFunc().getDays();
            long ago = MILLISECONDS.convert(days, DAYS);
            MeasurementAggregate aggregate2 = getAggregate(resource, mschedule, begin - ago, end - ago);
            Double val2 = expr.value(aggregate2);

            message = "resource aggregate value:" + f(val1, units) + "; was " + days + " day ago:" + f(val2, units);
            try {
                alert = expr.eval(aggregate, aggregate2);
            } catch (NoDataException e) {
                return nodata(expr, resource, md);
            }
        } else {
            message = "resource aggregate value:" + f(val1, units);
            try {
                alert = expr.eval(aggregate);
            } catch (NoDataException e) {
                return nodata(expr, resource, md);
            }
        }
        sendAlert(alert, expr, resource, null, md, message);
        return alert;

    }

    private void sendAlert(boolean alert, Expr expr, Resource resource, ResourceGroup group, MeasurementDefinition md, String message) {
        AlertProcessor alertProcessor;

        if (group == null)
            alertProcessor = new AlertProcessor(expr, resource, md);
        else
            alertProcessor = new AlertProcessor(expr, group, md);

        if (alert) {
            log.debug("eval true; triggering for " + expr);
            alertProcessor.process(message);
        } else {
            log.debug("eval false; not triggered, checking previous alerts");
            alertProcessor.acknowledge(subject);
        }

    }

    /**
     * Milliseconds window.
     */
    private long window(Expr expr) {
        int window = expr.getWindow() > 0 ? expr.getWindow() : this.window;
        return MILLISECONDS.convert(window, MINUTES);
    }

    private long getBegin(Expr expr, long end) {
        return end - window(expr);
    }

    /**
     * Evaluates the aggregate for this group, using this expression. Returns
     * true if triggering occurred.
     */
    boolean eval(ResourceGroup group, Expr expr) {
        log.debug("eval group " + group);
        ResourceType rt = group.getResourceType();
        if (rt == null) {
            // can happen if group is empty
            log.warn("must be a compatible group " + group + "; " + expr);
            return false;
        }

        if (expr.getFunc() == Func.avail) {
            return avail(group, expr);
        }

        MeasurementDefinition md = getDefinition(expr, rt);
        if (md == null) {
            log.warn("metric name not found: '" + expr.getMetric() + "' in " + rt.getMetricDefinitions());
            return false;
        }
        log.debug("expr " + expr);
        if (expr.getFunc() == Func.unique) {
            return unique(group, expr, md);
        }

        final long begin = getBegin(expr, end);

        MeasurementAggregate aggregate = getAggregate(group, md, begin, end);
        log.debug("eval aggregate=" + aggregate);

        boolean alert;

        String message;
        Double val1 = expr.value(aggregate, group);
        MeasurementUnits units = md.getUnits();
        if (expr.getFunc().hasParent()) {
            int days = expr.getFunc().getDays();
            long ago = MILLISECONDS.convert(days, DAYS);
            MeasurementAggregate aggregate2 = getAggregate(group, md, begin - ago, end - ago);
            Double val2 = expr.value(aggregate2, group);
            message = "group aggregate value: " + f(val1, units) + "; was " + days + " day ago: " + f(val2, units);
            try {
                alert = expr.eval(aggregate, aggregate2, group);
            } catch (NoDataException e) {
                return nodata(expr, group, md);
            }
        } else {
            if (units != null && expr.getUnits() != null) {
                log.debug("val before calculating offset:" + val1 + "" + units);
                val1 *= MeasurementUnits.calculateOffset(units, expr.getUnits());
                log.debug("val after calculating offset:" + val1);
            }
            message = "group aggregate value: " + f(val1, expr.getUnits());
            log.debug("message is " + message);
            try {
                alert = expr.eval(aggregate, group, units);
            } catch (NoDataException e) {
                return nodata(expr, group, md);
            }
        }

        sendAlert(alert, expr, null, group, md, message);

        return alert;
    }

    private boolean nodata(Expr expr, ResourceGroup group, MeasurementDefinition md) {
        if (nan) {
            AlertProcessor alertProcessor = new AlertProcessor(expr, group, md);
            alertProcessor.process("no metrics found");
            return true;
        } else {
            log.warn("no metrics found for group " + group);
            return false;
        }
    }

    private boolean nodata(Expr expr, Resource resource, MeasurementDefinition md) {
        if (nan) {
            AlertProcessor alertProcessor = new AlertProcessor(expr, resource, md);
            alertProcessor.process("no metrics found");
            return true;
        } else {
            log.warn("no metrics found for resource " + resource);
            return false;
        }
    }

    MeasurementDefinition getDefinition(Expr expr, ResourceType rt) {
        MeasurementDefinition md = null;
        Func func = expr.getFunc();
        for (MeasurementDefinition amd : rt.getMetricDefinitions()) {
            if (func == Func.unique) {
                if (amd.getDataType() != DataType.TRAIT)
                    continue;
            } else {
                if (amd.getDataType() != DataType.MEASUREMENT)
                    continue;
            }
            // would be nice if DisplayName was used ... hmm
            if (amd.getName().contains(expr.getMetric())) {
                // note always use 'perMinute' or default on
                if (md == null || amd.isPerMinute() || amd.isDefaultOn())
                    md = amd;
            }
        }
        if (log.isDebugEnabled())
            log.debug("eval def " + md);
        return md;
    }

    boolean avail(ResourceGroup group, Expr expr) {
        int up = 0;
        int size = group.getExplicitResources().size();
        for (Resource r : group.getExplicitResources()) {
            if (r.getCurrentAvailability().getAvailabilityType() == AvailabilityType.UP) {
                up++;
            }
        }
        boolean alert = expr.eval(up, size);
        if (log.isDebugEnabled())
            log.debug("trigger? " + alert + ": " + up + "/" + size);
        if (alert) {
            String message = "only " + up + " of " + size + " resources up";
            AlertProcessor alertProcessor = new AlertProcessor(expr, group, null);
            alertProcessor.process(message);
        } else {
            log.debug("avail: check previous alerts to ack : " + group);
            AlertProcessor alertProcessor = new AlertProcessor(expr, group, null);
            alertProcessor.acknowledge(subject);
        }

        return alert;
    }

    boolean unique(ResourceGroup group, Expr expr, MeasurementDefinition md) {
        log.debug("unique group " + group + " display name of md " + md.getDisplayName());

        Set<Resource> resources = group.getExplicitResources();
        Set<String> unique = new HashSet<String>();
        for (Resource resource : resources) {

            List<MeasurementDataTrait> traits = resourceTraits(resource.getId());
            for (MeasurementDataTrait t : traits) {
                if (t.getName().equals(md.getDisplayName())) {
                    MeasurementSchedule schedule = t.getSchedule();
                    if (schedule != null && !schedule.isEnabled()) {
                        log.warn("not enabled " + schedule.getResource());
                    }
                    unique.add(t.getValue());
                }
            }
        }
        boolean alert = expr.eval(unique.size());
        if (log.isDebugEnabled())
            log.debug("trigger? " + alert + ": " + unique);
        if (alert) {
            String message = "trait set not expected: " + unique;
            AlertProcessor alertProcessor = new AlertProcessor(expr, group, md);
            alertProcessor.process(message);
        } else {
            log.debug("unique: check previous alerts to ack : " + group);
            AlertProcessor alertProcessor = new AlertProcessor(expr, group, md);
            alertProcessor.acknowledge(subject);
        }
        return alert;
    }

    boolean perc(Resource resource, Expr expr, MeasurementDefinition md, MeasurementSchedule mschedule) {

        boolean alert = false;

        MeasurementAggregate aggregate = getAggregate(resource, mschedule, bhour, ehour);
        log.debug("bhour=" + format(bhour) + " ehour=" + format(ehour) + "eval aggregate=" + aggregate);
        Double val1 = expr.value(aggregate);
        assert md != null;
        MeasurementUnits units = md.getUnits();
        int days = expr.getFunc().getDays();
        long ago = MILLISECONDS.convert(days, DAYS);
        log.debug("Days back:" + days);
        MeasurementAggregate aggregate2 = getAggregate(resource, mschedule, bhour - ago, ehour - ago);
        log.debug("past begin=" + format(bhour - ago) + " past end hour=" + format(ehour - ago) + " eval aggregate=" + aggregate2);
        Double val2 = expr.value(aggregate2);

        String message = "value for hour " + spendhour + " is: " + f(val1, units) + "; was " + days + " day ago: " + f(val2, units);
        try {
            alert = expr.evalRelative(aggregate, aggregate2, null);
            sendAlert(alert, expr, resource, null, md, message);

        } catch (NoDataException e) {
            return nodata(expr, resource, md);
        }

        return alert;
    }

    List<MeasurementDataTrait> resourceTraits(int rid) {
        MeasurementDataManagerLocal mdm = LookupUtil.getMeasurementDataManager();
        Subject subj = LookupUtil.getSubjectManager().getOverlord();
        return mdm.findCurrentTraitsForResource(subj, rid, null /*all*/);
    }

    private String u(MeasurementUnits units) {
        return (units == null) ? "" : units.toString();
    }

    private String f(double d, MeasurementUnits units) {
        if (units == MeasurementUnits.PERCENTAGE) {
            d *= 100;
        }
        return String.format("%1.2f%s", d, u(units));
    }

    MeasurementAggregate getAggregate(Resource resource, MeasurementSchedule ms, long begn, long nd) {

        MeasurementDataManagerLocal mdm = LookupUtil.getMeasurementDataManager();
        // 'Subject' cannot be cached
        Subject subject = LookupUtil.getSubjectManager().getOverlord();
        String msg = "rid=" + resource.getId() + " msched=" + ms.getId() + " b=" + begn + " e=" + nd;
        log.debug(msg);
        MeasurementAggregate aggregate = mdm.getAggregate(subject, ms.getId(), begn, nd);
        return aggregate;
    }

    MeasurementAggregate getAggregate(ResourceGroup group, MeasurementDefinition md, long begin, long end) {
        MetricsCache.Key key = new MetricsCache.Key(begin, end, group.getId(), md.getId());
        MeasurementAggregate aggregate = cache.get(key);
        if (aggregate != null) {
            log.debug("cache hit");
            return aggregate;
        }
        MeasurementDataManagerLocal mdm = LookupUtil.getMeasurementDataManager();
        // 'Subject' cannot be cached
        String msg = "gid=" + group.getId() + " mdef=" + md.getId() + " b=" + format(begin) + " e=" + format(end);
        log.debug(msg);
        Subject subject = LookupUtil.getSubjectManager().getOverlord();
        aggregate = mdm.getAggregate(subject, group.getId(), md.getId(), begin, end);
        log.debug("aggregate=" + aggregate);
        if (aggregate.getMax().isNaN()) {
            log.warn("NAN returned: " + msg);
        }
        cache.put(key, aggregate);
        return aggregate;
    }

    private String format(long l) {
        return DF.format(new Date(l));
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public ControlResults invoke(String name, Configuration parameters) {
        log.info(name + " called");
        ControlResults cr = new ControlResults();
        try {
            processInTx();
        } catch (Exception e) {
            cr.setError(e);
        }
        return cr;
    }

    void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public String toString() {
        return "TagProcess [window=" + window + ", nan=" + nan + "]";
    }

    /*
     * sets the hour to current hour - gobackhours, for begin of the
     * hourly window ;
     */
    private long getTodayBeginTime() {
        Calendar calen = Calendar.getInstance();
        spendhour = calen.get(Calendar.HOUR_OF_DAY);
        log.debug("UTC Hour going on is " + spendhour);

        spendhour = spendhour - gobackhours;
        if (spendhour < 0) {
            spendhour = 23;
            calen.add(Calendar.DATE, -1);
            calen.set(Calendar.HOUR_OF_DAY, spendhour);
            calen.set(Calendar.MINUTE, 1);
            log.debug("taking prev day hour 23: " + calen.getTime());
        } else {
            log.debug("today hour " + spendhour);
            calen.set(Calendar.HOUR_OF_DAY, spendhour);
            calen.set(Calendar.MINUTE, 1);
            log.debug("taking today hour " + spendhour + calen.getTime());
        }

        return (calen.getTimeInMillis());
    }

    /*
     * sets the minute to 59 for today's begin time , to get a window of 1 hour
     */
    private long getTodayEndTime() {

        long beg = getTodayBeginTime();
        Calendar calen = Calendar.getInstance();
        calen.setTimeInMillis(beg);
        calen.set(Calendar.MINUTE, 59);
        log.debug("setting endtime  " + calen.getTime());

        return (calen.getTimeInMillis());
    }

    long getEnd() {
        return end;
    }

}
