package com.apple.iad.rhq.alert.ack;

import static org.testng.AssertJUnit.assertEquals;

import java.lang.reflect.Field;

import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.configuration.Configuration;
import org.testng.annotations.Test;

@Test
public class AlertProcessorTest {

    AlertDefinition recovered = null;

    public void test() throws Exception {
        AckSender sender = new AckSender() {

            @Override
            AlertDefinition getRecovered(AlertDefinition def) {
                return recovered;
            }

        };
        Configuration config = new Configuration();

        sender.setAlertParameters(config);
        AlertDefinition alertDefinition = new AlertDefinition();
        alertDefinition.setName("new");
        alertDefinition.setRecoveryId(42);
        Alert alert = new Alert(alertDefinition, 0);
        assert alert.getAcknowledgingSubject() == null;
        sender.send(alert);
        assertEquals("unknown", alert.getAcknowledgingSubject());

        recovered = new AlertDefinition();
        recovered.setName("recovered");
        Alert old = new Alert(recovered, 0);
        recovered.addAlert(old);

        alert.setAcknowledgingSubject(null);
        alert.setAcknowledgingSubject(null);
        config.setSimpleValue(AckSender.SUBJECT, "bob");
        sender.send(alert);
        assertEquals("bob", alert.getAcknowledgingSubject());
        assertEquals("bob", old.getAcknowledgingSubject());
    }

}
