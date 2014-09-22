package com.apple.iad.rhq.alert.tag;

import static org.testng.AssertJUnit.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.ResultState;
import org.rhq.core.domain.alert.notification.SenderResult;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.testng.annotations.Test;

@Test
public class TagSenderTest {

    private final Log log = LogFactory.getLog(getClass());

    public void test() {
        Resource r = new Resource();
        Alert alert = new Alert();
        AlertDefinition ad = new AlertDefinition();
        alert.setAlertDefinition(ad);
        Set<ResourceGroup> egroup = new HashSet<ResourceGroup>();
        Set<ResourceGroup> igroup = new HashSet<ResourceGroup>();
        r.setExplicitGroups(egroup);
        r.setImplicitGroups(igroup);
        ad.setResource(r);

        TagSender ts = new TagSender() {
            protected Subject getSubject(String name) {
                return new Subject(name, true, true);
            }
            protected Alert refresh(Alert alert) {
                return alert;
            }
        };
        ts.setPreferences(new Configuration());
        assert alert.getAlertDefinition().getResource() != null;
        assert alert.getAlertDefinition().getResource().getImplicitGroups() != null;
        assert alert.getAlertDefinition().getResource().getExplicitGroups() != null;
        SenderResult send = ts.send(alert);
        assert send.getState() == ResultState.FAILURE;

        Set<Tag> tags = new HashSet<Tag>();
        r.setTags(tags);
        send = ts.send(alert);
        assert send.getState() == ResultState.FAILURE;

        Tag tag = new Tag("notify:ack=bob");
        tags.add(tag);
        send = ts.send(alert);
        assert send.getState() == ResultState.SUCCESS;
        assertEquals("bob", alert.getAcknowledgingSubject());

        log.debug("special case: notify using a group alert");
        ad.setResource(null);
        alert.setAcknowledgingSubject(null);
        ResourceGroup group = new ResourceGroup("group");
        group.setTags(tags);
        ad.setGroup(group);
        send = ts.send(alert);
        assert send.getState() == ResultState.SUCCESS;
        assertEquals("bob", alert.getAcknowledgingSubject());

        tag.setSemantic("subject");
        send = ts.send(alert);
        // won't get to send
        assert send.getState() == ResultState.FAILURE;

        tag.setSemantic("email");
        send = ts.send(alert);
        log.info(send);

        tags.add(new Tag("notify:ack=foo"));
        tags.add(new Tag("notify:email=bob@example.com"));
        send = ts.send(alert);
        assertEquals(ResultState.PARTIAL, send.getState());
        assertEquals("foo", alert.getAcknowledgingSubject());

    }

}
