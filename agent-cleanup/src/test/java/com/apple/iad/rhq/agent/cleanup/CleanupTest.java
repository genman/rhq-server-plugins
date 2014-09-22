package com.apple.iad.rhq.agent.cleanup;

import static org.testng.AssertJUnit.assertEquals;

import java.lang.reflect.Field;
import java.util.Calendar;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.resource.Agent;
import org.testng.annotations.Test;

@Test
public class CleanupTest {

    private final Log log = LogFactory.getLog(getClass());

    public void testThis() throws Exception {
        AgentCleanup ac = new AgentCleanup() {

            @Override
            Subject getOverlord() {
                return new Subject();
            }

        };
        AgentManager am = new AgentManager();
        ac.am = am;
        ac.tm = new MockTransactionManager();
        ac.rm = new MockResourceManager();
        Agent agent1 = new Agent("a1", "", 0, "", "");
        Agent agent2 = new Agent("a2", "", 0, "", "");
        Agent agent3 = new Agent("a3", "", 0, "", "");
        am.createAgent(agent1);
        am.createAgent(agent2);
        am.createAgent(agent3);
        ac.cleanup();
        assertEquals(3, am.getAgents().size());

        log.debug("set ctime to something old");
        Field field = agent1.getClass().getDeclaredField("ctime");
        field.setAccessible(true);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);

        // agent1 has no ping
        field.set(agent1, c.getTimeInMillis());

        // agent2 has recent ping and report
        field.set(agent2, c.getTimeInMillis());
        Calendar ping = Calendar.getInstance();
        ping.add(Calendar.HOUR, -1);
        agent2.setLastAvailabilityPing(ping.getTimeInMillis());
        agent2.setLastAvailabilityReport(ping.getTimeInMillis());

        // agent3 has old ping
        ping.add(Calendar.MONTH, -1);
        field.set(agent3, c.getTimeInMillis());
        agent3.setLastAvailabilityPing(ping.getTimeInMillis());
        agent3.setLastAvailabilityReport(ping.getTimeInMillis());

        ac.cleanup();
        assertEquals(1, am.getAgents().size());
        assertEquals(true, am.getAgents().contains(agent2));

        log.debug("test discovery scan");
        agent2.setBackFilled(false);
        ac.unbackfill();
        assertEquals(false, am.getClient().discovery.requested);

        log.debug("set backfill, but no ping");
        agent2.setBackFilled(true);
        ac.unbackfill();
        assertEquals(false, am.getClient().discovery.requested);

        log.debug("set ping");
        am.getClient().ping = true;
        ac.unbackfill();
        assertEquals(true, am.getClient().discovery.requested);
    }

}
