package com.apple.iad.rhq.agent.cleanup;

import org.rhq.core.clientapi.agent.bundle.BundleAgentService;
import org.rhq.core.clientapi.agent.configuration.ConfigurationAgentService;
import org.rhq.core.clientapi.agent.content.ContentAgentService;
import org.rhq.core.clientapi.agent.discovery.DiscoveryAgentService;
import org.rhq.core.clientapi.agent.drift.DriftAgentService;
import org.rhq.core.clientapi.agent.inventory.ResourceFactoryAgentService;
import org.rhq.core.clientapi.agent.measurement.MeasurementAgentService;
import org.rhq.core.clientapi.agent.operation.OperationAgentService;
import org.rhq.core.clientapi.agent.support.SupportAgentService;
import org.rhq.core.domain.resource.Agent;
import org.rhq.enterprise.server.agentclient.AgentClient;

public class MockAgentClient implements AgentClient {

    Agent agent;
    boolean ping;
    MockDiscoveryAgentService discovery = new MockDiscoveryAgentService();

    public MockAgentClient(Agent agent) {
        this.agent = agent;
    }

    @Override
    public Agent getAgent() {
        return agent;
    }

    @Override
    public void startSending() {
    }

    @Override
    public void stopSending() {
    }

    @Override
    public boolean ping(long timeoutMillis) {
        return ping;
    }

    @Override
    public BundleAgentService getBundleAgentService() {
        return null;
    }

    @Override
    public BundleAgentService getBundleAgentService(Long timeout) {
        return null;
    }

    @Override
    public ContentAgentService getContentAgentService() {
        return null;
    }

    @Override
    public ContentAgentService getContentAgentService(Long timeout) {
        return null;
    }

    @Override
    public ResourceFactoryAgentService getResourceFactoryAgentService() {
        return null;
    }

    @Override
    public ResourceFactoryAgentService getResourceFactoryAgentService(Long timeout) {
        return null;
    }

    @Override
    public DiscoveryAgentService getDiscoveryAgentService() {
        return discovery;
    }

    @Override
    public DiscoveryAgentService getDiscoveryAgentService(Long timeout) {
        return getDiscoveryAgentService();
    }

    @Override
    public MeasurementAgentService getMeasurementAgentService() {
        return null;
    }

    @Override
    public MeasurementAgentService getMeasurementAgentService(Long timeout) {
        return getMeasurementAgentService();
    }

    @Override
    public OperationAgentService getOperationAgentService() {
        return null;
    }

    @Override
    public OperationAgentService getOperationAgentService(Long timeout) {
        return getOperationAgentService();
    }

    @Override
    public ConfigurationAgentService getConfigurationAgentService() {
        return null;
    }

    @Override
    public ConfigurationAgentService getConfigurationAgentService(Long timeout) {
        return null;
    }

    @Override
    public SupportAgentService getSupportAgentService() {
        return null;
    }

    @Override
    public SupportAgentService getSupportAgentService(Long timeout) {
        return null;
    }

    @Override
    public DriftAgentService getDriftAgentService() {
        return null;
    }

    @Override
    public DriftAgentService getDriftAgentService(Long timeout) {
        return null;
    }

    @Override
    public void updatePlugins() {
    }

    @Override
    public boolean pingService(long timeoutMillis) {
        return false;
    }

}
