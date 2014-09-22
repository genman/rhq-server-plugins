package com.apple.iad.rhq.alert.cleanup;

import org.rhq.core.domain.alert.Alert;
import org.rhq.enterprise.server.alert.AlertManagerBean;

public class AlertManagerTest extends AlertManagerBean {

    int alertDefinitionId;

    @Override
    public Alert fireAlert(int alertDefinitionId) {
        this.alertDefinitionId = alertDefinitionId;
        return null;
    }

}
