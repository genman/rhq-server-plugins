package com.apple.iad.rhq.alert.cleanup;

import static org.testng.AssertJUnit.assertEquals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.alert.AlertConditionCategory;
import org.rhq.core.domain.alert.AlertConditionLog;
import org.rhq.core.domain.alert.AlertConditionOperator;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.ResourceAvailability;
import org.rhq.core.domain.resource.Resource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test
public class AlertCleanupTest {

    private final Log log = LogFactory.getLog(getClass());

    private AlertManagerTest alertManager = new AlertManagerTest();
    private AlertConditionLogManagerTest acml = new AlertConditionLogManagerTest();
    private AlertCleanup cleanup;
    private Configuration conf = new Configuration();
    private AlertDefinition def;
    private ResourceAvailability avail;

    @BeforeMethod
    public void before() {
        cleanup = new AlertCleanup(alertManager, acml);

        Resource r = new Resource();
        def = new AlertDefinition();
        def.setId(555);
        def.setEnabled(true);
        def.setDeleted(false);
        def.setResource(r);
        avail = new ResourceAvailability(r, AvailabilityType.DOWN);
        r.setCurrentAvailability(avail);
        AlertCondition condition = new AlertCondition(def, AlertConditionCategory.AVAIL_DURATION);
        condition.setName(AlertConditionOperator.AVAIL_GOES_DOWN.name());
        def.addCondition(condition);
    }

    public void testRefire() {
        log.debug("refire");
        // won't work (yet)
        cleanup.invoke("refire", conf);

        cleanup.checkRefire(def);
        assert acml.ctime > 0;
        assert alertManager.alertDefinitionId == def.getId();
        assertEquals("refired for state DOWN", acml.value);

        acml.ctime = 0;
        avail.setAvailabilityType(AvailabilityType.UP);
        cleanup.checkRefire(def);
        assert acml.ctime == 0;
    }

    public void testAckUp() {
        log.debug("ackup");
        // won't work (yet)
        cleanup.invoke("ackup", conf);

        avail.setAvailabilityType(AvailabilityType.UP);
        Alert alert = new Alert();
        alert.setAlertDefinition(def);
        def.addAlert(alert);
        AlertCondition cond = def.getConditions().iterator().next();
        AlertConditionLog acl = new AlertConditionLog(cond, 0);
        alert.getConditionLogs().add(acl);

        String subject = "bob";
        log.debug("go ackup");
        cleanup.checkAckUp(alert, def, subject);
        assert alert.getAcknowledgingSubject() == subject;
    }


}
