package com.apple.iad.rhq.agent.cleanup;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.ResourceAvailability;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.resource.Resource;
import org.rhq.enterprise.server.resource.ResourceManagerBean;

public class MockResourceManager extends ResourceManagerBean {
    
    Resource r = new Resource();
    {
        r.setCurrentAvailability(new ResourceAvailability(r, AvailabilityType.DOWN));
    }
    
    Agent agent;
    
    @Override
    public Resource getPlatform(Agent agent) {
        return r;
    }
    
    @Override
    public void uninventoryAllResourcesByAgent(Subject user, Agent doomedAgent) {
        agent = doomedAgent;
    }


}