package com.apple.iad.rhq.alert.aggregate;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.testng.annotations.Test;

import com.apple.iad.rhq.alert.aggregate.MetricsCache.Key;

@Test
public class MetricsCacheTest {

    public void test() {
        MetricsCache cache = new MetricsCache();
        Key key = new Key(0, 1, 55, 66);
        Key key2 = new Key(0, 2, 55, 66);
        MeasurementAggregate value = new MeasurementAggregate(2.0, 3.0, 4.0);
        assertFalse(cache.containsKey(key));
        cache.put(key, value);
        assertTrue(cache.containsKey(key));
        assertFalse(cache.containsKey(key2));
        cache.toString();
    }

}
