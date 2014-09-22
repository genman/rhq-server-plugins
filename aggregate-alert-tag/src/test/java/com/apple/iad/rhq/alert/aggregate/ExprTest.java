package com.apple.iad.rhq.alert.aggregate;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import org.rhq.core.domain.alert.AlertPriority;
import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.rhq.core.domain.measurement.MeasurementUnits;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.testng.annotations.Test;

import com.apple.iad.rhq.alert.aggregate.Expr.AvailType;
import com.apple.iad.rhq.alert.aggregate.Expr.Func;

@Test
public class ExprTest {

    public void test() {
        String s = "alert:avg(foo>100.0),50m";
        Expr e = new Expr(new Tag(s));
        assertEquals(Func.avg, e.getFunc());
        assertEquals("foo", e.getMetric());
        assertEquals(100.0, e.getThreshold());
        assertEquals(50, e.getWindow());
        assertEquals(s, "alert:" + e);
        MeasurementUnits bytes = MeasurementUnits.BYTES;
        MeasurementUnits mb = MeasurementUnits.MEGABYTES;
        assertEquals(1024 * 1024, MeasurementUnits.calculateOffset(mb, bytes).intValue());

        MeasurementAggregate agg = new MeasurementAggregate(50.0, 110.0, 150.0);
        ResourceGroup group = new ResourceGroup("foo");
        assertEquals(true, e.eval(agg, group, bytes));

        s = "alert:min(foo < 30)";
        e = new Expr(new Tag(s));
        assertEquals(30.0, e.getThreshold());
        assertEquals(0, e.getWindow());
        assertEquals(false, e.eval(agg, group, bytes));
        assertEquals(false, e.eval(agg));

        s = "alert:avg(foo < 30MB)";
        e = new Expr(new Tag(s));
        assertEquals(true, e.eval(agg, group, bytes));
        bytes = MeasurementUnits.GIGABYTES;
        assertEquals(false, e.eval(agg, group, bytes));

        s = "alert:sum(foo > 30)";
        e = new Expr(new Tag(s));
        try {
            assertEquals(true, e.eval(agg, group, bytes));
            assert false : "empty group";
        } catch (IllegalStateException ex) {
        }
        group.getExplicitResources().add(new Resource());
        assertEquals(true, e.eval(agg, group, null));

        s = "alert:sumd(foo > 1.1)"; // value over same period last day
        e = new Expr(new Tag(s));
        MeasurementAggregate agg2 = new MeasurementAggregate(50.0, 110.0, 150.0);
        assertEquals(false, e.eval(agg, agg2, group));
        agg2 = new MeasurementAggregate(50.0, 60.0, 150.0);
        assertEquals(true, e.eval(agg, agg2, group));
        MeasurementAggregate nan = new MeasurementAggregate(Double.NaN, 0.0, Double.NaN);
        try {
            e.eval(agg, nan, group);
            fail("no data");
        } catch (NoDataException ex) {}
        try {
            e.eval(nan, agg, group);
            fail("no data");
        } catch (NoDataException ex) {}

        s = "alert:avgd(foo > 1.1)"; // value over same period last day
        e = new Expr(new Tag(s));
        assertEquals(true, e.eval(agg, agg2));

        s = "alert:range(foo > 99)";
        e = new Expr(new Tag(s));
        assertEquals(true, e.eval(agg));

        s = "alert:range(foo > 100)";
        e = new Expr(new Tag(s));
        assertEquals(false, e.eval(agg));

        s = "alert:rangep(foo > .5)";
        e = new Expr(new Tag(s));
        assertEquals(true, e.eval(agg));

        s = "alert:avail(count < 10)"; // less than 10 resources
        e = new Expr(new Tag(s));
        assertEquals(AvailType.count, e.getAvailType());
        assertEquals(false, e.eval(10, 20));
        assertEquals(true, e.eval(5, 20));

        s = "alert:avail(percent < 0.5)";
        e = new Expr(new Tag(s));
        assertEquals(AvailType.percent, e.getAvailType());
        assertEquals(false, e.eval(11, 20));
        assertEquals(true, e.eval(9, 20));
        assertEquals(AlertPriority.MEDIUM, e.getPriority());

        s = "alert:avail(percent<0.5),High";
        e = new Expr(new Tag(s));
        assertEquals(AlertPriority.HIGH, e.getPriority());
        assertEquals(s, "alert:" + e);

        s = "alert:max(total_malloced>30.0MB),10m,Low";
        e = new Expr(new Tag(s));
        assertEquals(AlertPriority.LOW, e.getPriority());
        assertEquals(MeasurementUnits.MEGABYTES, e.getUnits());
        assertEquals(s, "alert:" + e);
        assertEquals(AlertPriority.LOW, e.getPriority());

        s = "alert:avg(responseTime>15.0ms),10m,High";
        e = new Expr(new Tag(s));
        assertEquals(AlertPriority.HIGH, e.getPriority());
        assertEquals(MeasurementUnits.MILLISECONDS, e.getUnits());
        assertEquals(s, "alert:" + e);
        MeasurementAggregate agg3 = new MeasurementAggregate(10.0, 13.0, 30.0);
        assertEquals(false, e.eval(agg3, null, MeasurementUnits.MILLISECONDS));
        assertEquals(true, e.eval(agg3, null, MeasurementUnits.SECONDS));

        s = "alert:unique(trait>1)";
        e = new Expr(new Tag(s));
        assertEquals(true, e.eval(2));
        assertEquals(false, e.eval(1));

        s = "alert:percd(spend>50)";
        e = new Expr(new Tag(s));
        MeasurementAggregate mag = new MeasurementAggregate(75.0, 75.0, 75.0);
        MeasurementAggregate mag1 = new MeasurementAggregate(60.0, 60.0, 60.0);

        assertEquals(false, e.evalRelative(mag, mag1, null));

        s = "alert:percw(spend>10)";
        e = new Expr(new Tag(s));

        assertEquals(true, e.evalRelative(mag, mag1, null));
    }

}
