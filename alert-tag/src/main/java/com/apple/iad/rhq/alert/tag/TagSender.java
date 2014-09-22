package com.apple.iad.rhq.alert.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.Alert;
import org.rhq.core.domain.alert.AlertDefinition;
import org.rhq.core.domain.alert.notification.SenderResult;
import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.GroupCategory;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.rhq.enterprise.server.auth.SubjectManagerLocal;
import org.rhq.enterprise.server.plugin.pc.alert.AlertSender;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * AlertSender that notifies using resource tags.
 */
public class TagSender extends AlertSender {

    private static final String SUBJECT = "subject";

    private static final String EMAIL = "email";

    private static final String ACK = "ack";

    private static final String NOTIFY = "notify";

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Sends an alert.
     * <li> namespace must be 'notify'
     * <li> semantic must be 'subject' or 'email' or 'ack'
     * <li> name must be subject or email address or acknowledging person.
     */
    @Override
    public SenderResult send(Alert alert) {
        alert = refresh(alert);
        AlertDefinition alertDefinition = alert.getAlertDefinition();
        Resource resource = alertDefinition.getResource();
        Set<Tag> found = new HashSet<Tag>();

        Resource aresource = resource;
        while (aresource != null && !findTag(aresource.getTags(), found)) {
            aresource = aresource.getParentResource();
        }

        // Special case for group alerts (not officially RHQ)
        if (alertDefinition.getGroup() != null) {
            findTag(alertDefinition.getGroup().getTags(), found);
        }
        if (resource == null) {
            resource = new Resource();
        }

        if (found.isEmpty()) {
            for (ResourceGroup group : sorted(resource.getExplicitGroups())) {
                if (findTag(group.getTags(), found)) {
                    log.debug("tag from explicit group " + group.getName());
                    break;
                }
            }
        } else {
            log.debug("tag from resource or parent");
        }

        if (found.isEmpty()) {
            for (ResourceGroup group : sorted(resource.getImplicitGroups())) {
                if (findTag(group.getTags(), found)) {
                    log.debug("tag from implicit group " + group.getName());
                    break;
                }
            }
        }

        log.debug("tags found " + found);

        if (found.isEmpty()) {
            log.debug("Could not find notify:x=y tag for resource " + resource.getAncestry());
            String s = preferences.getSimpleValue("defaultTag", NOTIFY + ":x=y");
            found.add(new Tag(s));
        }

        SenderResult result = new SenderResult();
        for (Tag tag : found) {
            try {
                SenderResult r1 = send(tag, alert);
                if (r1.getSummary() != null)
                    result.setSummary(r1.getSummary());
                result.getFailureMessages().addAll(r1.getFailureMessages());
                result.getSuccessMessages().addAll(r1.getSuccessMessages());

            } catch (Throwable t) {
                log.error("could not send " + tag, t);
                return SenderResult.getSimpleFailure("Failure " + t);
            }
        }
        return result;
    }

    /**
     * Queries the alert using the entity manager.
     */
    protected Alert refresh(Alert alert) {
        EntityManager em = LookupUtil.getEntityManager();
        return em.find(Alert.class, alert.getId());
    }

    private SenderResult send(Tag tag, Alert alert) {

        String sem = tag.getSemantic();
        if (sem == null)
            return SenderResult.getSimpleFailure("No semantic");
        String name = tag.getName();
        if (name == null)
            return SenderResult.getSimpleFailure("No name");

        if (SUBJECT.equals(sem)) {
            log.debug("send as subject");
            Subject subject = getSubject(name);
            if (subject == null)
                return SenderResult.getSimpleFailure("Unknown subject " + name);

            Configuration config = new Configuration();
            config.setSimpleValue("subjectId", "" + subject.getId());

            SubjectsSender ss = new SubjectsSender(preferences, config, extraParameters, pluginComponent, serverPluginEnvironment);
            return ss.send(alert);
        }
        if (EMAIL.equals(sem)) {
            log.debug("send as email");
            Configuration config = new Configuration();
            config.setSimpleValue("emailAddress", name);

            EmailSender ss = new EmailSender(preferences, config, extraParameters, pluginComponent, serverPluginEnvironment);
            return ss.send(alert);
        }
        if (ACK.equals(sem)) {
            log.debug("ack auto");
            alert.setAcknowledgingSubject(name);
            alert.setAcknowledgeTime(System.currentTimeMillis());
            return SenderResult.getSimpleSuccess("Ack'ed as subject " + name);
        }

        return SenderResult.getSimpleFailure("Unknown tag sematic " + sem);
    }

    protected Subject getSubject(String name) {
        return getSubjectManager().getSubjectByName(name);
    }

    private Collection<ResourceGroup> sorted(Set<ResourceGroup> groups) {
        ArrayList<ResourceGroup> l = new ArrayList<ResourceGroup>(groups);
        Collections.sort(l, new Comparator<ResourceGroup>() {

            @Override
            public int compare(ResourceGroup g1, ResourceGroup g2) {
                if (g1.getGroupCategory() == GroupCategory.MIXED)
                    return 1;
                if (g2.getGroupCategory() == GroupCategory.MIXED)
                    return -1;
                return g1.getName().compareTo(g2.getName());
            }
        });
        return l;
    }

    private SubjectManagerLocal getSubjectManager() {
        return LookupUtil.getSubjectManager();
    }

    private boolean findTag(Set<Tag> tags, Set<Tag> found) {
        if (tags == null)
            return false;
        boolean added = false;
        for (Tag tag : tags) {
            if (NOTIFY.equals(tag.getNamespace())) {
                found.add(tag);
                added = true;
            }
        }
        return added;
    }

    public void setPreferences(Configuration preferences) {
        this.preferences = preferences;
    }

}
