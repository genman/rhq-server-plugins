package com.apple.iad.rhq.alert.aggregate;

import java.util.HashMap;

import org.rhq.core.domain.measurement.MeasurementAggregate;

/**
 * Help avoid making the same queries again for group metrics.
 * This is used because different thresholds are used and priorities with the same time ranges.
 * Should be cleared before processing tags.
 */
@SuppressWarnings("serial")
public class MetricsCache extends HashMap<MetricsCache.Key, MeasurementAggregate> {

    public MetricsCache() {
    }

    /**
     * Cache key, using begin end, group ID, and definition ID.
     */
    public static class Key {
        private final long begin;
        private final long end;
        private final int group;
        private final int md;

        public Key(long begin, long end, int group, int md) {
            this.begin = begin;
            this.end = end;
            this.group = group;
            this.md = md;
        }

        @Override
        public int hashCode() {
            return (int)begin ^ (int)end ^ group ^ md;
        }

        @Override
        public boolean equals(Object obj) {
            Key other = (Key) obj;
            if (begin != other.begin)
                return false;
            if (end != other.end)
                return false;
            if (group != other.group)
                return false;
            if (md != other.md)
                return false;
            return true;
        }

    }


}
