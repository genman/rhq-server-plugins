package com.apple.iad.rhq.agent.cleanup;

import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.criteria.AgentCriteria;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.ResourceAvailability;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.core.domain.util.PageOrdering;
import org.rhq.enterprise.server.agentclient.AgentClient;
import org.rhq.enterprise.server.core.AgentManagerLocal;
import org.rhq.enterprise.server.plugin.pc.ControlFacet;
import org.rhq.enterprise.server.plugin.pc.ControlResults;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginContext;
import org.rhq.enterprise.server.resource.ResourceManagerLocal;
import org.rhq.enterprise.server.storage.StorageClientManager;
import org.rhq.enterprise.server.util.LookupUtil;
import org.rhq.server.metrics.MetricsServer;

/**
 * Aggregate alert tag processing.
 */
public class AgentCleanup implements ServerPluginComponent, ControlFacet {

    private static final int REMOVE_LIMIT = 100;

    private static final String PING_TIMEOUT = "pingTimeout";

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Expiration setting.
     */
    private final static String EXPIRATION = "expiration";

    /**
     * Older than setting.
     */
    private final static String OLDER_THAN = "olderThan";

    /**
     * Config setting for agents with no ping or availability report.
     */
    private final static String NONE = "none";

    /**
     * Days to look back by default.
     */
    private int expiration = 30;

    /**
     * Looks at creation time. Agents older than this time can be removed. In hours.
     */
    private int olderThan = 1;

    /**
     * Hours to look back by default.
     */
    private boolean none = true;

    AgentManagerLocal am;

    ResourceManagerLocal rm;

    TransactionManager tm;

    private long pingTimeout = 5000;

    @Override
    public void initialize(ServerPluginContext context) throws Exception {
        Configuration conf = context.getPluginConfiguration();
        init(conf);
    }

    void init(Configuration conf) {
        expiration = Integer.parseInt(conf.getSimpleValue(EXPIRATION, "15"));
        olderThan = Integer.parseInt(conf.getSimpleValue(OLDER_THAN, "1"));
        none = Boolean.parseBoolean(conf.getSimpleValue(NONE, "true"));
        am = LookupUtil.getAgentManager();
        rm = LookupUtil.getResourceManager();
        tm = LookupUtil.getTransactionManager();
        pingTimeout = Integer.parseInt(conf.getSimpleValue(PING_TIMEOUT, "5000"));
    }

    @Override
    public void start() {
    }

    /**
     * Method invoked by the scheduler.
     */
    public void cleanup() throws Exception {
        log.info("cleanup() called");
        if (expiration <= 0) {
            throw new IllegalStateException("invalid expiration");
        }
        if (olderThan <= 0) {
            throw new IllegalStateException("invalid older than");
        }
        Subject subject = getOverlord();
        AgentCriteria criteria = new AgentCriteria();
        criteria.addSortName(PageOrdering.ASC);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        PageList<Agent> agents = am.findAgentsByCriteria(subject, criteria);
        Calendar olderThan = Calendar.getInstance();
        olderThan.add(Calendar.HOUR, -this.olderThan);
        Calendar expiration = Calendar.getInstance();
        expiration.add(Calendar.DATE, -this.expiration);
        for (Agent agent : agents) {
            log.debug("check " + agent);
            long created = agent.getCreatedTime();
            if (new Date(created).after(olderThan.getTime())) {
                log.debug("agent created after olderThan, skip: " + agent);
                continue;
            }
            Long ping = agent.getLastAvailabilityPing();
            Long report = agent.getLastAvailabilityReport();
            if (ping == null || report == null) {
                if (none) {
                    log.info("agent expired due to no ping or report: " + agent);
                    deleteAgent(agent);
                } else {
                    log.debug("agent has no info, ignoring " + agent);
                }
                continue;
            }
            if (new Date(ping).before(expiration.getTime())) {
                log.info("agent expired due to old ping: " + agent);
                deleteAgent(agent);
            } else {
                log.debug("agent is active");
            }
        }
    }

    /**
     * Deletes the agent, suspending the current transaction if active to
     * force a new transaction to be created.
     */
    private void deleteAgent(Agent agent) throws Exception {
        log.info("deleteAgent " + agent);
        Transaction suspend = tm.suspend();
        try {
            // deleting an agent can cause this:
            // integrity constraint (RHQ.SYS_C0012677) violated - child record found
            // needs to be fixed
            rm.uninventoryAllResourcesByAgent(getOverlord(), agent);
            am.deleteAgent(agent);
        } catch (Exception e) {
            log.warn("failed to delete or uninventory " + agent.getName() + ": " + e);
            log.debug("cause", e);
        } finally {
            if (suspend != null) {
                tm.resume(suspend);
            }
        }
    }

    public void unbackfill() {
        AgentCriteria criteria = new AgentCriteria();
        criteria.addSortName(PageOrdering.ASC);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        Subject subject = getOverlord();
        PageList<Agent> agents = am.findAgentsByCriteria(subject, criteria);
        List<Agent> eligible = new ArrayList<Agent>();
        for (Agent agent : agents) {
            if (down(agent)) {
                eligible.add(agent);
            }
        }
        for (Agent agent : eligible) {
            try {
                unbackfill(agent);
            } catch (Exception e) {
                log.warn("could not unbackfill " + agent, e);
            }
        }
        log.info("done");
    }

    private boolean down(Agent agent) {
        if (log.isDebugEnabled()) {
            log.debug("check " + agent);
        }
        Resource platform = rm.getPlatform(agent);
        if (platform == null) {
            return false;
        }
        ResourceAvailability ra = platform.getCurrentAvailability();
        if (ra == null || ra.getAvailabilityType() == AvailabilityType.UP) {
            // null happens if the resource was never registered
            return false;
        }
        return true;
    }

    public void unbackfill(Agent agent) {
        AgentClient client = am.getAgentClient(agent);
        if (client.ping(pingTimeout)) {
            log.info("pinged agent; requesting full availability report " + agent.getName());
            try {
                client.getDiscoveryAgentService(pingTimeout).requestFullAvailabilityReport();
            } catch (UndeclaredThrowableException e) {
                if (e.getCause() instanceof TimeoutException) {
                    log.warn("timeout requesting; is the agent running but not registered?");
                } else {
                    log.warn("failed to request", e);
                }
            } catch (Exception e) {
                log.warn("failed to request", e);
            }
        } else {
            log.info("ping timeout " + agent.getName());
        }
    }

    Subject getOverlord() {
        return LookupUtil.getSubjectManager().getOverlord();
    }

    @Override
    public ControlResults invoke(String name, Configuration parameters) {
        try {
            if (name.equals("remove")) {
                remove(parameters.getSimpleValue("name"));
            } else {
                Method method = getClass().getMethod(name);
                // no parameters
                method.invoke(this);
            }
            return new ControlResults();
        } catch (Exception e) {
            ControlResults cr = new ControlResults();
            cr.setError(e);
            return cr;
        }
    }

    private void remove(String name) throws Exception {
        AgentCriteria criteria = new AgentCriteria();
        criteria.addSortName(PageOrdering.ASC);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        criteria.addFilterName(name);
        Subject subject = getOverlord();
        PageList<Agent> agents = am.findAgentsByCriteria(subject, criteria);
        if (agents.isEmpty()) {
            throw new Exception("no agents found matching " + name);
        }
        if (agents.size() > REMOVE_LIMIT) {
            throw new Exception("too many agents matching " + name);
        }
        for (Agent agent : agents) {
            log.debug("check " + agent);
            Long ping = agent.getLastAvailabilityPing();
            if (ping != null) {
                long since = System.currentTimeMillis() - ping;
                if (since < TimeUnit.HOURS.toMillis(1)) {
                    log.warn("agent pinged recently " + agent);
                    continue;
                }
            }
            deleteAgent(agent);
        }
    }

    public void compress() {
        log.info("compressing data");
        StorageClientManager storageClientManager = LookupUtil.getStorageClientManager();
        MetricsServer metricsServer = storageClientManager.getMetricsServer();
        metricsServer.calculateAggregates();
        log.info("done");
    }

    public void update() {
        log.info("update plugins");
        AgentCriteria criteria = new AgentCriteria();
        criteria.addSortName(PageOrdering.ASC);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        Subject subject = getOverlord();
        PageList<Agent> agents = am.findAgentsByCriteria(subject, criteria);
        for (Agent agent : agents) {
            try {
                AgentClient client = am.getAgentClient(agent);
                String name = agent.getName();
                if (client.pingService(pingTimeout)) {
                    log.info("update plugins on " + name);
                    client.updatePlugins();
                } else {
                    log.warn("could not ping " + name);
                }
            } catch (Exception e) {
                log.warn("could not unbackfill " + agent, e);
            }
        }
        log.info("done");
    }

    @Override
    public void stop() {
    }

    @Override
    public void shutdown() {
    }

}
