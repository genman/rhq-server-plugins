package com.apple.iad.rhq.alert.aggregate;

import static org.testng.AssertJUnit.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.DataType;
import org.rhq.core.domain.measurement.DisplayType;
import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.rhq.core.domain.measurement.MeasurementCategory;
import org.rhq.core.domain.measurement.MeasurementDataPK;
import org.rhq.core.domain.measurement.MeasurementDataTrait;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.measurement.MeasurementUnits;
import org.rhq.core.domain.measurement.NumericType;
import org.rhq.core.domain.measurement.ResourceAvailability;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;
import org.testng.annotations.Test;

@Test
public class TagProcessTest {

    private final Log log = LogFactory.getLog(getClass());

    MeasurementAggregate agg;

    private long fixedEnd;
    static MeasurementDefinition def = new MeasurementDefinition("malloc", MeasurementCategory.PERFORMANCE, MeasurementUnits.BYTES, DataType.MEASUREMENT, NumericType.DYNAMIC, false, 0,
            DisplayType.DETAIL);
    static MeasurementDefinition def1 = new MeasurementDefinition("spend", MeasurementCategory.PERFORMANCE, MeasurementUnits.NONE, DataType.MEASUREMENT, NumericType.DYNAMIC, false, 0,
            DisplayType.DETAIL);

    public void test() {
        TagProcess tp = new TagProcess() {

            EntityManager getEntityManager() {
                return new MockEntityManager();
            }

            @Override
            EntityManager getEntityManager() {
                return new MockEntityManager();
            }

            @Override
            MeasurementAggregate getAggregate(Resource resource, MeasurementSchedule ms, long begin, long end) {
                return agg;
            }

            @Override
            MeasurementAggregate getAggregate(ResourceGroup group, MeasurementDefinition mmd, long begin, long end) {
                return agg;
            }

            @Override
            List<MeasurementDataTrait> resourceTraits(int rid) {
                List<MeasurementDataTrait> list = new ArrayList<MeasurementDataTrait>();
                MeasurementDataPK pk = new MeasurementDataPK(0);
                MeasurementDataTrait mdt = new MeasurementDataTrait(pk, "2014-01-02");
                mdt.setName("malloc");
                if (rid == 4) {
                    mdt.setValue("2014-01-04");
                }
                list.add(mdt);
                return list;
            }

        };
        tp.init(new Configuration());
        tp.setTransactionManager(new MockTransactionManager());
        Lookup.tm = new MockTransactionManager();
        MockEntityManager mem = new MockEntityManager();
        Lookup.em = mem;

        tp.process();

        ResourceGroup group = new ResourceGroup("foo");

        String s = "alert:avg(malloc<30)";
        Expr expr = new Expr(new Tag(s));

        // mixed
        assertEquals(false, tp.eval(group, expr));
        fixedEnd = tp.getEnd();

        log.debug("single type, but no metrics defs");
        ResourceType resourceType = new ResourceType();
        group.setResourceType(resourceType);
        assertEquals(false, tp.eval(group, expr));

        resourceType.getMetricDefinitions().add(def);

        log.debug("group not triggering based on values");
        agg = new MeasurementAggregate(10.0, 50.0, 80.0);
        assertEquals(false, tp.eval(group, expr));

        log.debug("resource not triggering based on values");
        Resource r = new Resource();
        r.setName("test resource");
        MeasurementSchedule sched = new MeasurementSchedule();
        sched.setEnabled(true);
        sched.setDefinition(def);
        r.getSchedules().add(sched);
        assertEquals(false, tp.eval(r, expr));

        log.debug("trigger based on disabled");
        int c = 1;
        sched.setEnabled(false);
        assertEquals(true, tp.eval(r, expr));
        assertEquals(c++, mem.getAlerts().size());

        log.debug("trigger based on too infrequent");
        sched.setEnabled(true);
        sched.setInterval(Integer.MAX_VALUE);
        assertEquals(true, tp.eval(r, expr));
        assertEquals(c++, mem.getAlerts().size());
        sched.setInterval(0);

        log.debug("trigger based on NaN");
        agg = new MeasurementAggregate(Double.NaN, Double.NaN, Double.NaN);
        assertEquals(true, tp.eval(group, expr));
        assertEquals(c++, mem.getAlerts().size());
        def.setUnits(MeasurementUnits.MEGABYTES);
        agg = new MeasurementAggregate(10.0, 10.0, 30.0);
        log.debug("megabytes test; not trigger");
        String s1 = "alert:avg(malloc>0.01GB)";
        Expr expr1 = new Expr(new Tag(s1));
        tp.eval(group, expr1);
        log.debug("megabytes test; trigger");
        agg = new MeasurementAggregate(10.0, 11.0, 30.0);
        log.debug("eval resource group for " + s1);
        tp.eval(group, expr1);
        assertEquals(c++, mem.getAlerts().size());

        log.debug("eval resource");
        tp.eval(r, expr);
        assertEquals(c++, mem.getAlerts().size());

        agg = new MeasurementAggregate(Double.NaN, Double.NaN, Double.NaN);
        log.debug("eval resource nan");
        tp.eval(r, expr);
        assertEquals(c++, mem.getAlerts().size());

        log.debug("trigger based on trait uniqueness");
        s = "alert:unique(malloc > 1)";
        expr = new Expr(new Tag(s));
        r = new Resource();
        r.setUuid("1");
        r.setId(1);
        group.addExplicitResource(r);
        r = new Resource();
        r.setUuid("2");
        r.setId(2);
        group.addExplicitResource(r);
        r = new Resource();
        r.setUuid("3");
        r.setId(3);
        group.addExplicitResource(r);
        r = new Resource();
        r.setUuid("4");
        r.setId(4);
        group.addExplicitResource(r);
        tp.eval(group, expr);
        def.setDataType(DataType.TRAIT);
        def.setDisplayName("malloc");
        log.debug("trigger, two unique metrics");
        tp.eval(group, expr);
        assertEquals(c++, mem.getAlerts().size());

        s = "alert:unique(malloc > 2)";
        expr = new Expr(new Tag(s));
        tp.eval(group, expr);
        log.debug("no trigger, only two unique metrics");

        log.debug("trigger based on availability");
        group = new ResourceGroup("foo");
        group.setResourceType(resourceType);
        resourceType.getMetricDefinitions().add(def);
        s = "alert:avail(percent < .6)";
        expr = new Expr(new Tag(s));
        Resource resource = new Resource();
        resource.setUuid("1");
        resource.setCurrentAvailability(new ResourceAvailability(resource, AvailabilityType.UP));
        group.addExplicitResource(resource);
        assertEquals(false, tp.eval(group, expr));

        Resource resource2 = new Resource();
        resource2.setUuid("2");
        resource2.setCurrentAvailability(new ResourceAvailability(resource, AvailabilityType.DOWN));
        group.addExplicitResource(resource2);
        tp.eval(group, expr);
        assertEquals(c++, mem.getAlerts().size());

        s = "alert:percd(spend>25)";
        expr = new Expr(new Tag(s));
        Resource resource3 = new Resource();
        resource3.setUuid("3");
        resourceType = new ResourceType();
        resourceType.getMetricDefinitions().add(def1);
        agg = new MeasurementAggregate(65.0, 65.0, 65.0);
        resource3.setResourceType(resourceType);
        sched = new MeasurementSchedule();
        sched.setEnabled(true);
        sched.setDefinition(def1);
        resource3.getSchedules().add(sched);
        assertEquals(false, tp.eval(resource3, expr));

        long fixedEnd2 = tp.getEnd();
        assertEquals("same end time always", fixedEnd, fixedEnd2);


    }

}
