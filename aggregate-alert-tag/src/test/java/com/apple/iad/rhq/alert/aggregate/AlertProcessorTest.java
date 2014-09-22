package com.apple.iad.rhq.alert.aggregate;

import static java.util.Collections.singletonList;
import static org.testng.AssertJUnit.assertEquals;

import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.testng.annotations.Test;


@Test
public class AlertProcessorTest {

    private final Log log = LogFactory.getLog(getClass());
    List<AlertDefinition> def;

    public void test() {
        String s = "alert:avg(foo>100.0MB),5m";
        Expr expr = new Expr(new Tag(s));

        // group alert

        log.info("1. testing for group");
        ResourceGroup group = new ResourceGroup("foo");
        MeasurementDefinition md = TagProcessTest.def;
        AlertDefinition ad = new AlertDefinition();
        ad.setName(expr.toString());
        ad.addCondition(new AlertCondition());
        ad.setEnabled(true);
        def = singletonList(ad);
        MockAlertProcessor ap = new MockAlertProcessor(expr, group, md);
        Lookup.em = new MockEntityManager();
        ap.setDef(def);
        ap.process("processing group");
        assert ap.getFired() != null;

        // alert on a resource, not acknowledged

        log.info("2. testing for resource, not acknowledged");
        Resource r = new Resource();
        ap = new MockAlertProcessor(expr, r, md);
        ap.setDef(def);
        ap.process("processing resource");
        assert ap.getFired() == null;

        log.info("3. testing for group, not acked");

        group = new ResourceGroup("foo");
        md = TagProcessTest.def;
        ad = new AlertDefinition();
        ad.setName(expr.toString());
        ad.addCondition(new AlertCondition());
        ad.setEnabled(true);
        ap = new MockAlertProcessor(expr, group, md);
        ap.setDef(def);
        ap.process("processing group");
        assert ap.getFired() == null;

        // acknowledge the alert

        log.info("4. testing for resource, after acknowledging");

        /*  if (ap.getAlerts() != null) {
            Alert a = ap.getAlerts().iterator().next();
            a.setAcknowledgingSubject("ack");
        } */
        ap.acknowledge("subj");
        Set<Alert> alerts = def.get(0).getAlerts();
        assertEquals("subj", alerts.iterator().next().getAcknowledgingSubject());
        r = new Resource();
        ap = new MockAlertProcessor(expr, r, md);
        ap.setDef(def);
        ap.process("process resource");
        assert ap.getFired() != null;

        log.info("5. testing for group, after acknowledging");

        group = new ResourceGroup("foo");
        md = TagProcessTest.def;
        ad = new AlertDefinition();
        ad.setName(expr.toString());
        ad.addCondition(new AlertCondition());
        ad.setEnabled(true);
        def = singletonList(ad);
        ap = new MockAlertProcessor(expr, group, md);
        ap.setDef(def);
        ap.process("processing group");
        assert ap.getFired() != null;

        log.info("6. testing for group, after clearing the alert");
        ap.getAlerts().clear();
        group = new ResourceGroup("foo");
        md = TagProcessTest.def;
        ad = new AlertDefinition();
        ad.setName(expr.toString());
        ad.addCondition(new AlertCondition());
        ad.setEnabled(true);
        def = singletonList(ad);
        ap = new MockAlertProcessor(expr, group, md);
        ap.setDef(def);
        ap.process("processing group");
        assert ap.getFired() != null;

        log.info("7. testing for group, not acked");

        group = new ResourceGroup("foo");
        md = TagProcessTest.def;
        ad = new AlertDefinition();
        ad.setName(expr.toString());
        ad.addCondition(new AlertCondition());
        ad.setEnabled(true);
        ap = new MockAlertProcessor(expr, group, md);
        ap.setDef(def);
        ap.process("processing group");
        assert ap.getFired() == null;

    }

}
