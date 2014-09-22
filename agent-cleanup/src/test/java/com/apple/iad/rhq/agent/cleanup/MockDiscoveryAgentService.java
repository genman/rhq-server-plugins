package com.apple.iad.rhq.agent.cleanup;

import java.util.Collection;

import org.rhq.core.clientapi.agent.PluginContainerException;
import org.rhq.core.clientapi.agent.discovery.DiscoveryAgentService;
import org.rhq.core.clientapi.agent.discovery.InvalidPluginConfigurationClientException;
import org.rhq.core.clientapi.server.discovery.InventoryReport;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.discovery.AvailabilityReport;
import org.rhq.core.domain.discovery.MergeResourceResponse;
import org.rhq.core.domain.discovery.PlatformSyncInfo;
import org.rhq.core.domain.discovery.ResourceSyncInfo;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;

public class MockDiscoveryAgentService implements DiscoveryAgentService {

    boolean requested;

    @Override
    public void updatePluginConfiguration(int resourceId,
            Configuration newPluginConfiguration)
            throws InvalidPluginConfigurationClientException,
            PluginContainerException {
    }

    @Override
    public Resource getPlatform() {
        return null;
    }

    @Override
    public InventoryReport executeServerScanImmediately()
            throws PluginContainerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InventoryReport executeServiceScanImmediately()
            throws PluginContainerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AvailabilityReport executeAvailabilityScanImmediately(
            boolean changedOnlyReport) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AvailabilityReport getCurrentAvailability(Resource resource,
            boolean changesOnly) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void requestFullAvailabilityReport() {
        requested = true;
    }

    @Override
    public MergeResourceResponse manuallyAddResource(ResourceType resourceType,
            int parentResourceId, Configuration pluginConfiguration,
            int creatorSubjectId)
            throws InvalidPluginConfigurationClientException,
            PluginContainerException {
        return null;
    }

    @Override
    public void uninventoryResource(int resourceId) {
    }

    @Override
    public void enableServiceScans(int serverResourceId, Configuration config) {
    }

    @Override
    public void disableServiceScans(int serverResourceId) {
    }

    @Override
    public void synchronizePlatform(PlatformSyncInfo syncInfo) {
    }

    @Override
    public void synchronizeServer(int resourceId, Collection<ResourceSyncInfo> toplevelServerSyncInfo) {
    }

    @Override
    public void executeServiceScanDeferred(int resourceId) {
    }

    @Override
    public boolean executeServiceScanDeferred() {
        return false;
    }

}
