package com.apple.iad.rhq.alert.aggregate;

import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.ResourceGroup;

class MockAlertProcessor extends AlertProcessor {

    private Alert fired;
    private List<AlertDefinition> def;

    // private final Set<Alert> alerts = new LinkedHashSet<Alert>();

    public Set<Alert> getAlerts() {
        return def.get(0).getAlerts();
    }

    MockAlertProcessor(Expr expr, ResourceGroup group, MeasurementDefinition md) {
        super(expr, group, md);
    }

    MockAlertProcessor(Expr expr, Resource r, MeasurementDefinition md) {
        super(expr, r, md);
    }

    @Override
    void fire(Alert alert) {
        this.fired = alert;
    }

    public Alert getFired() {
        return fired;

    }

    public void setDef(List<AlertDefinition> def) {
        this.def = def;
    }

    @Override
    List<AlertDefinition> getDefinitions() {
        return this.def;
    }
}