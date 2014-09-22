package com.apple.iad.rhq.alert.cleanup;

import static org.rhq.core.domain.alert.AlertConditionCategory.AVAILABILITY;
import static org.rhq.core.domain.alert.AlertConditionCategory.AVAIL_DURATION;
import static org.rhq.core.domain.alert.AlertConditionOperator.AVAIL_GOES_DISABLED;
import static org.rhq.core.domain.alert.AlertConditionOperator.AVAIL_GOES_UNKNOWN;
import static org.rhq.core.domain.alert.AlertConditionOperator.AVAIL_GOES_UP;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.LazyInitializationException;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.alert.AlertConditionLog;
import org.rhq.core.domain.alert.AlertConditionOperator;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.criteria.AlertCriteria;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
// import org.rhq.enterprise.server.alert.AlertConditionLogManagerBean;
import org.rhq.enterprise.server.alert.AlertConditionLogManagerLocal;
import org.rhq.enterprise.server.alert.AlertManagerLocal;
import org.rhq.enterprise.server.plugin.pc.ControlFacet;
import org.rhq.enterprise.server.plugin.pc.ControlResults;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginContext;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Alert cleanup processing.
 */
public class AlertCleanup implements ServerPluginComponent, ControlFacet {

    private final Log log = LogFactory.getLog(getClass());
    private AlertManagerLocal alertManager;
    private AlertConditionLogManagerLocal acml;

    private static Set<AlertConditionOperator> ignoreCond = EnumSet.of(AVAIL_GOES_DISABLED, AVAIL_GOES_UNKNOWN, AVAIL_GOES_UP);

    public AlertCleanup() {
    }

    AlertCleanup(AlertManagerLocal alertManager, AlertConditionLogManagerLocal acml) {
        this.alertManager = alertManager;
        this.acml = acml;
    }

    @Override
    public void initialize(ServerPluginContext context) throws Exception {
        // TODO any config settings we need?
        // Configuration conf = context.getPluginConfiguration();
    }

    @Override
    public void start() {
        alertManager = LookupUtil.getAlertManager();
        try {
            InitialContext ic = new InitialContext();
            acml = (AlertConditionLogManagerLocal)
                    ic.lookup("java:module/AlertConditionLogManagerBean!" + AlertConditionLogManagerLocal.class.getName());
            ic.close();
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ControlResults invoke(String name, Configuration conf) {
        log.debug("invoke " + name);
        try {
            if (name.equals("ackup")) {
                String subject = conf.getSimpleValue("subject", "rhq");
                ackup(subject);
            } else if (name.equals("refire")) {
                refire();
            } else {
                throw new IllegalArgumentException(name);
            }
            return new ControlResults();
        } catch (Exception e) {
            log.error("failed to invoke " + name, e);
            ControlResults cr = new ControlResults();
            cr.setError(e);
            return cr;
        }
    }

    private void ackup(String subject) {
        AlertCriteria ac = new AlertCriteria();
        ac.fetchAlertDefinition(true);
        ac.fetchConditionLogs(true);
        ac.setPageControl(PageControl.getUnlimitedInstance());
        Subject overlord = LookupUtil.getSubjectManager().getOverlord();
        PageList<Alert> alerts = LookupUtil.getAlertManager().findAlertsByCriteria(overlord, ac);
        for (Alert a : alerts) {
            if (a.getAcknowledgingSubject() != null) {
                checkAckUp(a, a.getAlertDefinition(), subject);
            }
        }
    }

    /**
     * Returns true if an unacked alert already exists for this definition.
     */
    private boolean hasAlert(AlertDefinition def) {
        for (Alert alert : def.getAlerts()) {
            if (alert.getAcknowledgingSubject() == null)
                return true;
        }
        return false;
    }

    EntityManager getEntityManager() {
        return LookupUtil.getEntityManager();
    }

    List<AlertDefinition> getAlertDefs(EntityManager em) {
        Query q = em.createNamedQuery(AlertDefinition.QUERY_FIND_ALL);
        return q.getResultList();
    }

    private void refire() {
        EntityManager em = getEntityManager();
        try {
            for (AlertDefinition def : getAlertDefs(em)) {
                try {
                    checkRefire(def);
                } catch (LazyInitializationException e) {
                    // failed to lazily initialize a collection of role: org.rhq.core.domain.alert.AlertDefinition.alerts, could not initialize proxy - no Session
                    log.debug("spurious exception?", e);
                    def = em.find(AlertDefinition.class, def.getId());
                    checkRefire(def);
                }
            }
        } finally {
            em.close();
        }
    }

    void checkAckUp(Alert alert, AlertDefinition def, String subject) {
        if (log.isDebugEnabled())
            log.debug("check def " + def);
        if (!def.getEnabled() || def.getDeleted() || nonZero(def.getRecoveryId())) {
            log.debug("not enabled, deleted, or recovery");
            return;
        }
        Resource resource = def.getResource();
        if (resource == null) {
            log.debug("no resource");
            return;
        }
        AvailabilityType avail = resource.getCurrentAvailability().getAvailabilityType();
        if (avail != AvailabilityType.UP) {
            log.debug("not up; ignore");
            return;
        }
        boolean ack = false;
        for (AlertConditionLog acl : alert.getConditionLogs()) {
            log.debug("checking conditions...");
            AlertCondition ac = acl.getCondition();
            if (ac.getCategory() == AVAIL_DURATION || ac.getCategory() == AVAILABILITY) {
                AlertConditionOperator oper = AlertConditionOperator.valueOf(ac.getName());
                if (ignoreCond.contains(oper)) {
                    log.debug("not a condition for down " + oper);
                } else {
                    ack = true;
                }
            }
        }
        if (ack) {
            log.info("ack alert for currently up " + resource);
            alert.setAcknowledgingSubject(subject);
        }
    }

    void checkRefire(AlertDefinition def) {
        if (log.isDebugEnabled())
            log.debug("check def " + def);
        if (!def.getEnabled() || def.getDeleted() || nonZero(def.getRecoveryId())) {
            log.debug("not enabled, deleted, or recovery");
            return;
        }
        Resource resource = def.getResource();
        if (resource == null) {
            log.debug("no resource");
            return;
        }
        AvailabilityType avail = resource.getCurrentAvailability().getAvailabilityType();
        if (avail != AvailabilityType.DOWN) {
            log.debug("not down; ignore");
            return;
        }
        if (hasAlert(def)) {
            log.debug("already alerted on");
            return;
        }
        for (AlertCondition ac : def.getConditions()) {
            checkCondition(ac);
        }
    }

    private boolean nonZero(Integer i) {
        return i != null && i > 0;
    }

    public void checkCondition(AlertCondition ac) {
        AlertDefinition def = ac.getAlertDefinition();
        Resource resource = def.getResource();

        if (ac.getCategory() == AVAIL_DURATION || ac.getCategory() == AVAILABILITY) {
            AlertConditionOperator oper = AlertConditionOperator.valueOf(ac.getName());
            if (ignoreCond.contains(oper)) {
                log.debug("not a condition for down " + oper);
                return;
            }
            log.info("fire alert for not up " + resource);

            AvailabilityType avail = resource.getCurrentAvailability().getAvailabilityType();
            long ctime = System.currentTimeMillis();

            String msg = "refired for state " + avail;
            acml.updateUnmatchedLogByAlertConditionId(ac.getId(), ctime, msg);
            alertManager.fireAlert(def.getId());
            log.debug("done");
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

}
