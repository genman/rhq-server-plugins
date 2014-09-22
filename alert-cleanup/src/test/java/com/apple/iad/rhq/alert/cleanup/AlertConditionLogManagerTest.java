package com.apple.iad.rhq.alert.cleanup;

import org.rhq.enterprise.server.alert.AlertConditionLogManagerBean;

public class AlertConditionLogManagerTest extends AlertConditionLogManagerBean {

    int alertConditionId;
    long ctime;
    String value;

    @Override
    public void updateUnmatchedLogByAlertConditionId(int alertConditionId, long ctime, String value) {
        this.alertConditionId = alertConditionId;
        this.ctime = ctime;
        this.value = value;
    }

}
