package com.apple.iad.rhq.alert.aggregate;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.alert.AlertConditionCategory;
import org.rhq.core.domain.alert.AlertConditionLog;
import org.rhq.core.domain.alert.AlertConditionOperator;
import org.rhq.core.domain.alert.AlertDampening;
import org.rhq.core.domain.alert.AlertDampening.Category;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.BooleanExpression;
import org.rhq.core.domain.alert.notification.AlertNotification;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.enterprise.server.alert.AlertManagerLocal;

import com.apple.iad.rhq.alert.aggregate.Expr.Func;

/**
 * Generates or finds the alert definition, creates and sends the alert.
 */
public class AlertProcessor {

    private static final String SELECT_AD = "select ad from AlertDefinition ad where ad.group = ?1 and ad.name = ?2";

    private final Log log = LogFactory.getLog(getClass());

    // TODO configurable
    public static final String NOTIFY = "Tagged Alerting";

    private final Expr expr;
    private final ResourceGroup group;
    private final Resource resource;
    private final MeasurementDefinition md;

    /**
     * Process an alert.
     *
     * @param expr
     *            expression
     * @param group
     *            resource group
     * @param md
     *            measurement
     * @param message
     *            containing the comparison
     */
    public AlertProcessor(Expr expr, ResourceGroup group, MeasurementDefinition md) {
        this(expr, group, null, md);
    }

    AlertProcessor(Expr expr, ResourceGroup group, Resource resource, MeasurementDefinition md) {
        this.expr = expr;
        this.group = group;
        this.resource = resource;
        this.md = md;
    }

    /**
     * Process an alert.
     *
     * @param expr
     *            expression
     * @param resource
     *            single resource
     * @param md
     *            measurement
     * @param message
     *            containing the comparison
     */
    public AlertProcessor(Expr expr, Resource resource, MeasurementDefinition md) {
        this(expr, null, resource, md);
    }

    private String desc() {
        String what = (group != null) ? "group " + group.getName() : "resource " + resource.getName();
        return "Aggregate alerting " + expr + " for " + what;
    }

    /**
     * Send an alert with a message. Locates an alert definition, if not found,
     * creates one.
     */
    public void process(String message) {
        // create an alert definition if necessary
        log.debug("process " + message);
        AlertDefinition def;
        List<AlertDefinition> add = getDefinitions();
        boolean exists = !add.isEmpty();

        AlertCondition cond;
        EntityManager em = Lookup.getEntityManager();
        if (exists) {
            def = add.get(0);
            for (Alert alert : def.getAlerts()) {
                if (alert.getAcknowledgingSubject() == null) {
                    String name = alert.getAlertDefinition().getName();
                    if (name.contains("percd") || name.contains("percw")) {
                        Calendar cal = Calendar.getInstance();
                        long ctime = alert.getCtime();
                        cal.setTimeInMillis(ctime);
                        cal.add(Calendar.HOUR_OF_DAY, 1);
                        log.debug("ctime after adding 1 hour " + cal.getTime());

                        Calendar ncal = Calendar.getInstance();
                        log.debug("now time " + ncal.getTime());
                        // ack the alert if it is an hour old
                        if (cal.before(ncal)) {
                            log.info("acking alrt older than 1 hour " + alert);
                            acknowledge("auto ackd");
                            continue;
                        } else {
                            log.debug("not firing alert, unacked alert less than hour old " + alert);
                            return;
                        }

                    } else {
                        log.debug("not firing alert, because already have an unacked alert " + alert);
                        return;
                    }
                }

            }
            // always one condition
            Set<AlertCondition> conditions = def.getConditions();
            if (conditions.isEmpty())
                throw new IllegalStateException("no conditions");
            cond = conditions.iterator().next();
            log.debug("condition " + cond);
            /*
             * RHQ will replicate this alert definition to new resource members,
             * causing alerting at the resource level, mainly for availability
             * alerts. For now, all aggregate alert definitions are disabled.
             */
        } else {
            log.debug("new alert def");
            def = alertDef(expr);

            AlertNotification an = new AlertNotification(NOTIFY);
            def.addAlertNotification(an);
            an.setConfiguration(new Configuration());
            an.setAlertDefinition(def);

            if (expr.getFunc() == Func.avail) {
                cond = new AlertCondition(def, AlertConditionCategory.AVAILABILITY);
                cond.setName(AlertConditionOperator.AVAIL_GOES_DOWN.name());
            } else {
                cond = new AlertCondition(def, AlertConditionCategory.THRESHOLD);
                switch (expr.getFunc()) {
                case min:
                    cond.setOption("MIN");
                    break;
                case max:
                    cond.setOption("MAX");
                    break;
                default:
                    cond.setOption("AVG");
                    break; // sum etc.
                }
                cond.setMeasurementDefinition(md);
                log.debug("setting md in cond " + md);
                cond.setName(md.getDisplayName());
            }
            cond.setComparator(expr.getOp().toString());
            cond.setThreshold(expr.getThreshold());
            def.addCondition(cond);

            if (group != null) {
                def.setGroup(group);
            } else {
                // Clone the original resource since Hibernate will
                // attempt to persist the definition when the resource is
                // flushed, causing an exception:
                // org.hibernate.PersistentObjectException: detached entity
                // passed to persist: org.rhq.core.domain.alert.AlertDefinition
                def.setResource(new Resource(resource.getId()));
            }
            em.persist(def);
            em.persist(an);
            em.persist(cond);
            log.debug("condition " + cond);
        }

        // persist alert -> persists also condition logs

        long ctime = System.currentTimeMillis();
        Alert alert = new Alert(def, ctime);
        def.addAlert(alert);
        // clone the alert condition (don't share between definition and alert)
        AlertCondition condCopy = new AlertCondition(cond);
        condCopy.setMeasurementDefinition(md);
        if (md != null && md.getDisplayName() != null)
            condCopy.setName(alert.getAlertDefinition().getName());
        AlertConditionLog acl = new AlertConditionLog(condCopy, ctime);
        condCopy.addConditionLog(acl);
        acl.setValue(message);
        alert.addConditionLog(acl);
        em.persist(condCopy);
        em.persist(alert);

        log.debug("firing alert " + alert);
        fire(alert);
        log.debug("done");
    }

    AlertDefinition alertDef(Expr expr) {
        AlertDefinition def = new AlertDefinition();
        AlertDampening ad = new AlertDampening(Category.NONE);
        def.setAlertDampening(ad);
        def.setConditionExpression(BooleanExpression.ANY);
        def.setDescription(desc());
        def.setResourceType(null);
        def.setDeleted(false);
        def.setEnabled(false);
        def.setName(expr.toString());
        def.setPriority(expr.getPriority());
        def.setReadOnly(true);
        def.setRecoveryId(0); // mandatory but nullable in object...
        def.setWillRecover(false);
        return def;
    }

    void fire(final Alert alert) {
        // Alert manager creates a new transaction, which causes issues
        // since it expects the alert object to be persisted already,
        // and 'new' suspends this transaction.
        // In any case, this works better since now the alert processing is
        // async.
        Synchronization sync = new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    log.debug("transaction committed, fire alert...");
                    AlertManagerLocal alertManager = Lookup.getAlertManager();
                    alertManager.sendAlertNotifications(alert);
                } else {
                    log.debug("transaction status " + status);
                }
            }
        };
        try {
            Lookup.getTransactionManager().getTransaction().registerSynchronization(sync);
        } catch (RollbackException e) {
            log.debug("rollback");
        } catch (SystemException e) {
            log.debug("system");
        }
    }

    List<AlertDefinition> getDefinitions() {
        EntityManager em = Lookup.getEntityManager();
        String sexpr = expr.toString();
        if (group != null) {
            List<AlertDefinition> add = em.createQuery(SELECT_AD).setParameter(1, group).setParameter(2, sexpr).getResultList();
            return add;
        } else {
            assert resource != null;
            for (AlertDefinition ad : resource.getAlertDefinitions()) {
                log.debug(sexpr + " =? " + ad.getName());
                if (ad.getName().equals(sexpr)) {
                    log.debug("matched");
                    return Collections.singletonList(ad);
                }
            }
            log.debug("no match");
            return Collections.emptyList();
        }
    }

    void acknowledge(String subject) {
        long nw = System.currentTimeMillis();
        AlertDefinition def;
        List<AlertDefinition> add = getDefinitions();
        boolean exists = !add.isEmpty();
        if (exists) {
            def = add.get(0);
            log.debug("alert definition to ack " + def);
            for (Alert alert : def.getAlerts()) {
                if (alert.getAcknowledgingSubject() == null) {
                    alert.setAcknowledgeTime(nw);
                    alert.setAcknowledgingSubject(subject);
                    log.debug("acked by subject: " + subject + " alert: " + alert);
                }
            }
        }
    }

}
