package com.apple.iad.rhq.agent.cleanup;

import java.util.HashSet;
import java.util.Set;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.criteria.AgentCriteria;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.server.agentclient.AgentClient;
import org.rhq.enterprise.server.core.AgentManagerBean;

/**
 * Mock class for testing.
 */
public class AgentManager extends AgentManagerBean {

    private Set<Agent> agents = new HashSet<Agent>();
    private MockAgentClient client = new MockAgentClient(null);

    @Override
    public void deleteAgent(Agent agent) {
        boolean remove = agents.remove(agent);
        assert remove : "not in set";
    }

    public Set<Agent> getAgents() {
        return agents;
    }

    @Override
    public AgentClient getAgentClient(Agent agent) {
        return client;
    }

    public MockAgentClient getClient() {
        return client;
    }

    @Override
    public PageList<Agent> findAgentsByCriteria(Subject subject, AgentCriteria criteria) {
        return new PageList<Agent>(agents, criteria.getPageControlOverrides());
    }

    @Override
    public void createAgent(Agent agent) {
        agents.add(agent);
    }

}
