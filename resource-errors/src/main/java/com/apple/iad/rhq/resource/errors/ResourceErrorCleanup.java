package com.apple.iad.rhq.resource.errors;

import static java.lang.Integer.parseInt;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceError;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.enterprise.server.plugin.pc.ControlFacet;
import org.rhq.enterprise.server.plugin.pc.ControlResults;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginContext;
import org.rhq.enterprise.server.resource.ResourceTypeNotFoundException;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Performs the cleanup of old ResourceErrors.
 */
public class ResourceErrorCleanup implements ServerPluginComponent, ControlFacet {

    private static final String QUERY_RESOURCE_ERROR = "SELECT re FROM ResourceError re";

    private final Log log = LogFactory.getLog(getClass());

    private int days;

    @Override
    public void initialize(ServerPluginContext context) throws Exception {
        Configuration conf = context.getPluginConfiguration();
        this.days = parseInt(conf.getSimpleValue("age", "30"));
        if (days < 0)
            days = 0;
    }

    @Override
    public void start() {
        log.info("Cleanup listener: Scheduled to clean up resource errors older than " + days + " days");
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

    /**
     * Performs cleanup, returning the number of aged-out errors.
     */
    public int cleanup() {
        long before = before();
        int removed = 0;
        boolean debug = log.isDebugEnabled();

        EntityManager em = LookupUtil.getEntityManager();
        List<ResourceError> rerrors = em.createQuery(QUERY_RESOURCE_ERROR).getResultList();
        for (ResourceError re : rerrors) {
            if (re.getTimeOccurred() < before) {
                if (debug)
                    log.debug("remove error " + re.getId() + ": " + re.getErrorType() + " " + re.getSummary());
                em.remove(re);
                removed++;
            }
        }
        log.info("Removed " + removed + " old resource errors (out of " + rerrors.size() + " errors)");
        return removed;
    }

    private long before() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -days);
        return cal.getTimeInMillis();
    }

    @Override
    public ControlResults invoke(String name, Configuration parameters) {
        log.info(name + " called");
        ControlResults cr = new ControlResults();
        if (name.equals("cleanup")) {
            int count = cleanup();
            cr.getComplexResults().setSimpleValue("count", "" + count);
        }
        if (name.equals("schedule")) {
            emptySchedule();
        }
        return cr;
    }

    private void emptySchedule() {
        String qs = "SELECT r FROM Resource r WHERE r.schedules IS EMPTY";
        EntityManager em = LookupUtil.getEntityManager();
        log.info("query " + qs);
        Query q = em.createQuery(qs);
        log.info("done query");

        int count = 0;
        for (Resource r : (List<Resource>)q.getResultList()) {
            log.info("adding missing schedules for " + r);
            Set<MeasurementDefinition> missing = r.getResourceType().getMetricDefinitions();
            for (MeasurementDefinition d : missing) {
                MeasurementSchedule s = new MeasurementSchedule(d, r);
                s.setEnabled(d.isDefaultOn());
                s.setInterval(d.getDefaultInterval());
                count++;
                r.setAgentSynchronizationNeeded();
                em.persist(s);
                log.info("added missing schedule " + s);
            }
        }
        log.info("added missing schedule count " + count);
    }
}
