package com.apple.iad.rhq.alert.tag;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.enterprise.server.plugin.pc.ServerPluginComponent;
import org.rhq.enterprise.server.plugin.pc.ServerPluginEnvironment;

/**
 * Delegates to the superclass.
 */
public class EmailSender extends
        org.rhq.enterprise.server.plugins.alertEmail.EmailSender {

    public EmailSender(Configuration preferences,
            Configuration alertParameters, Configuration extraParameters,
            ServerPluginComponent pluginComponent,
            ServerPluginEnvironment serverPluginEnvironment) {
        this.preferences = preferences;
        this.alertParameters = alertParameters;
        this.extraParameters = extraParameters;
        this.pluginComponent = pluginComponent;
        this.serverPluginEnvironment = serverPluginEnvironment;
    }

}
