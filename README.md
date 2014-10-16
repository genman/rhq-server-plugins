rhq-server-plugins
==================

RHQ server plugins developed by iAd.

These are designed to work with the RHQ project: https://github.com/rhq-project/rhq

Note that some of these plugins are potentially obsoleted by fixes within the RHQ project itself.

Detailed documentation is within the plugin descriptor, but here is a summary of the plugins

## alert-tag

Delegates alerting to other alert senders based on tagging of a
resource or a resource's group tag. If a resource, or its group, or its
parent or parent's group contains a tag, it will indicate who to alert.

For example, if a resource group is labeled:
```
notify:email=bob_smith@example.com
```

Any resource in that group configured to use the alert-tag sender, will send emails to that user.

## alert-cleanup

Contains controls for refiring alerts that may have been ack'd by
mistake, or clears alerts for resources that are now currently up.

## agent-cleanup

Contains controls for cleaning up agents that may have connected
(and failed to register) or agents that have not connected very
recently, or agents that were backfilled (due to transaction timeouts, etc.)
but are really alive.

## resource-errors

Plugin for cleaning up resource errors in the system. These
accumulate over time when resources have periodic timeouts or fail
to start. By default, it cleans up errors older than a week every Sunday.

Note that this may be used as an example to write similar cleanup plugins.

## alert-ack

Use this alert sender to acknowledge an alert. This is useful for automatically
ignoring recovery alerts that may be generated, or for ignoring or
acknowledging alerts from alert definitions that are now recovered.

## aggregate-alert-tag

Alerts on resource or group metrics, traits, and availability. This provides
support for an alerting feature RHQ does not currently have.

Example: `alert:max(totalSize>10.0MB),5m, High`
means for this metric, if the max value is over 10MB for the past
5 minutes, alert with High priority

*Note that this functionality does not work well with RHQ notification's system without a patch*
