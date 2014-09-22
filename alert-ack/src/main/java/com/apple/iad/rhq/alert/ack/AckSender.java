package com.apple.iad.rhq.alert.ack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.SenderResult;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.enterprise.server.plugin.pc.alert.AlertSender;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Acknowledges an alert automatically using a fixed subject, meaning a username.
 */
public class AckSender extends AlertSender {

    public static final String RECOVERY = "recovery";
    public static final String SUBJECT = "subject";

    private final Log log = LogFactory.getLog(getClass());

    @SuppressWarnings("unchecked")
    void setAlertParameters(Configuration alertParameters) {
        this.alertParameters = alertParameters;
    }

    @Override
    public SenderResult send(Alert alert) {
        log.debug("ack by sender");
        ack(alert);
        if (recovery()) {
            log.debug("ack recovery");
            AlertDefinition def = alert.getAlertDefinition();
            AlertDefinition rdef = getRecovered(def);
            if (rdef != null && def != null) {
                log.debug("find existing alerts for " + rdef);
                for (Alert old : rdef.getAlerts()) {
                    ack(old);
                }
            }
        }
        return SenderResult.getSimpleSuccess("Ack'ed as subject " + alert.getAcknowledgingSubject());
    }

    AlertDefinition getRecovered(AlertDefinition def) {
        if (def.getRecoveryId() == null) {
            return null;
        }
        Subject subject = LookupUtil.getSubjectManager().getOverlord();
        return LookupUtil.getAlertDefinitionManager().getAlertDefinitionById(subject, def.getRecoveryId());
    }

    private void ack(Alert alert) {
        if (alert.getAcknowledgingSubject() == null) {
            alert.setAcknowledgeTime(System.currentTimeMillis());
            String name = getSubject();
            alert.setAcknowledgingSubject(name);
            log.debug("ack " + alert);
        }
    }

    @Override
    public String previewConfiguration() {
        return super.previewConfiguration();
    }

    private String getSubject() {
        return alertParameters.getSimpleValue(SUBJECT, "unknown");
    }

    private boolean recovery() {
        return Boolean.parseBoolean(alertParameters.getSimpleValue(RECOVERY, "true"));
    }

}
