package com.apple.iad.rhq.alert.aggregate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.alert.AlertPriority;
import org.rhq.core.domain.measurement.MeasurementAggregate;
import org.rhq.core.domain.measurement.MeasurementUnits;
import org.rhq.core.domain.resource.group.ResourceGroup;
import org.rhq.core.domain.tagging.Tag;

/**
 * Tag expression
 */
public class Expr {

    private static final Log log = LogFactory.getLog(Expr.class);

    private static final Pattern SYNTAX = Pattern.compile(
            //   func  ( metric        op        value )   , 10m        , Priority
            "(?i)(\\w+)\\((\\S+)?\\s*([<>])\\s*(\\S+)\\)(?:,(\\d+)m)?(?:,(Low|Medium|High))?");

    // for ex: 100.0MB or .5b
    private static final Pattern UNITS = Pattern.compile("([\\d.]+)([a-zA-Z]+)");

    /**
     * Aggregate functions, also includes day and week averages.
     */
    enum Func {
        /**
         * Resource metric sum.
         */
        sum(false),

        /**
         * Metric average.
         */
        avg,

        /**
         * Minimum value.
         */
        min,

        /**
         * Maximum value.
         */
        max,

        /**
         * Resource availability.
         */
        avail(false),

        /**
         * Metric range.
         */
        range,

        /**
         * Metric range, in percent.
         */
        rangep,

        /**
         * Calculates number of unique traits in a group of resources.
         */
        unique,

        sumd("sum", 1), sumw("sum", 7), avgd("avg", 1), avgw("sum", 7),

        /**
         * Percentage increase or change relative to an x number of days back in time.
         */
        percd("avg", 1, false), percw("avg", 7, false);

        private final boolean single; // true if supported by single resource
        private final int days; // days back
        private final String pfunc;

        private Func() {
            this(null, 0, false);
        }

        private Func(String pfunc, int days) {
            this(pfunc, days, true);
        }

        private Func(boolean single) {
            this(null, 0, single);
        }

        private Func(String pfunc, int days, boolean single) {
            this.days = days;
            this.pfunc = pfunc;
            this.single = single;
        }

        public int getDays() {
            return days;
        }

        public Func getParentFunc() {
            return pfunc == null ? null : Func.valueOf(pfunc);
        }

        /**
         * Returns true if function has parent.
         */
        public boolean hasParent() {
            return pfunc != null;
        }

        /**
         * Returns true if supported for single resources.
         */
        public boolean isSingle() {
            return single;
        }

    }

    /**
     * Supported comparison operations.
     */
    enum Op {
        GT(">"), LT("<");

        private final String s;

        private Op(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    };

    enum AvailType {
        /**
         * Total number of available resources.
         */
        count,
        /**
         * Percent of available resources.
         */
        percent
    };

    private final Func func;
    private final String metric;
    private final Op op;
    private final double threshold;
    private final int window;
    private final AvailType availType;
    private final AlertPriority priority;

    private final MeasurementUnits units;

    /**
     * Expression based of an RHQ tag.
     */
    public Expr(Tag tag) {
        this(tag.getName());
    }

    /**
     * Expression with a name.
     */
    public Expr(String n) {
        Matcher matcher = SYNTAX.matcher(n);
        if (!matcher.matches())
            throw new IllegalArgumentException("does not match " + SYNTAX.pattern());
        func = Func.valueOf(matcher.group(1));
        metric = matcher.group(2);
        if (func == Func.avail) {
            availType = AvailType.valueOf(metric);
        } else {
            availType = null;
        }
        String mop = matcher.group(3);
        if (mop.equals(">"))
            op = Op.GT;
        else if (mop.equals("<"))
            op = Op.LT;
        else
            throw new IllegalArgumentException("unknown op " + mop);
        String num = matcher.group(4);
        Matcher matcher2 = UNITS.matcher(num);
        if (matcher2.matches()) {
            num = matcher2.group(1);
            units = units(matcher2.group(2));
        } else {
            units = null;
        }
        threshold = Double.parseDouble(num);
        String swindow = matcher.group(5);
        if (swindow != null) {
            window = Integer.parseInt(swindow);
        } else {
            window = 0;
        }
        String pri = matcher.group(6);
        if (pri != null) {
            priority = AlertPriority.valueOf(pri.toUpperCase());
        } else {
            priority = AlertPriority.MEDIUM;
        }
    }

    private MeasurementUnits units(String u) {
        for (MeasurementUnits unit : MeasurementUnits.values()) {
            if (unit.toString().equals(u)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("unknown units " + u);
    }

    public MeasurementUnits getUnits() {
        return units;
    }

    /**
     * Aggregate function.
     */
    public Func getFunc() {
        return func;
    }

    /**
     * Returns the metric name. May be null if this is an availability test.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Returns the metric operation.
     */
    public Op getOp() {
        return op;
    }

    /**
     * Returns the metric threshold.
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * Returns the window in minutes for aggregation.
     */
    public int getWindow() {
        return window;
    }

    /**
     * Returns the alert priority.
     */
    public AlertPriority getPriority() {
        return priority;
    }

    /**
     * Returns the availability count type.
     */
    public AvailType getAvailType() {
        return availType;
    }

    /**
     * Alert definition name. Do not change this rendering without
     * consideration.
     */
    @Override
    public String toString() {
        String u = this.units == null ? "" : this.units.toString();
        String s = func + "(" + metric + "" + op + "" + threshold + u + ")";
        if (window > 0)
            s += "," + window + "m";
        if (priority != AlertPriority.MEDIUM)
            s += "," + priority;
        return s;
    }

    public boolean eval(MeasurementAggregate aggregate, ResourceGroup group, MeasurementUnits units) {
        Double val = value(aggregate, group);
        if (val == null || val.isNaN())
            throw new NoDataException("aggregate contains no data " + this);
        double d = val;
        if (this.units != null) {
            d *= MeasurementUnits.calculateOffset(units, this.units);
        }
        return test(d, threshold);
    }

    public boolean eval(MeasurementAggregate aggregate) {
        return eval(aggregate, (ResourceGroup) null, null);
    }

    public Double value(MeasurementAggregate aggregate, ResourceGroup group) {
        Func f = func;
        if (func.hasParent())
            f = func.getParentFunc();
        assert aggregate != null;
        switch (f) {
        case sum:
            assert group != null;
            int size = group.getExplicitResources().size();
            if (size == 0)
                throw new IllegalStateException("empty group");
            return aggregate.getAvg() * size;
        case avg: return aggregate.getAvg();
        case min: return aggregate.getMin();
        case max: return aggregate.getMax();
        case range: return aggregate.getMax() - aggregate.getMin();
        case rangep: return (aggregate.getMax() - aggregate.getMin()) / aggregate.getAvg();
        default: throw new IllegalStateException("opr not supported");
        }
    }

    public boolean eval(MeasurementAggregate aggregate, MeasurementAggregate aggregate2, ResourceGroup group) {
        Double val = value(aggregate, group);
        // average can be 0 even if min/max is NaN
        if (val == null || aggregate.getMax().isNaN())
            throw new NoDataException("current aggregate contains no data " + this);
        Double val2 = value(aggregate2, group);
        if (val2 == null || aggregate2.getMax().isNaN())
            throw new NoDataException("past aggregate contains no data " + this);

        double expected = val2.doubleValue() * threshold;
        if (log.isDebugEnabled())
            log.debug("val " + val + " " + op + " expect " + expected);
        return test(val, expected);
    }

    public boolean evalRelative(MeasurementAggregate aggregate, MeasurementAggregate aggregate2, ResourceGroup group) {
        Double a = value(aggregate, group);
        // average can be 0 even if min/max is NaN
        if (a == null || aggregate.getMax().isNaN())
            throw new NoDataException("current aggregate contains no data " + this);
        Double b = value(aggregate2, group);
        if (b == null || aggregate2.getMax().isNaN())
            throw new NoDataException("past aggregate contains no data " + this);
        if (b.doubleValue() == 0 || a.doubleValue() == 0)
            log.warn("Found values : today " + a.doubleValue() + " yesterday " + b.doubleValue() );

        double c =  ((a.doubleValue() - b.doubleValue())/b.doubleValue()) * 100;
        if (log.isDebugEnabled())
            log.debug("percent diff evaluated for a =" + a + " b= " + b + "is " + c + " threshold is " + threshold);
        return test(c, threshold);
    }

    private boolean test(double val, double expected) {
        switch (op) {
        case GT:
            if (val > expected)
                return true;
            break;
        case LT:
            if (val < expected)
                return true;
            break;
        }
        return false;
    }

    /**
     * For availability testing, tests if the number of down resources is beyond
     * threshold.
     *
     * @param up
     *            number of up resources
     * @param size
     *            number of total resources
     */
    public boolean eval(int up, int size) {
        assert availType != null;
        assert up <= size;
        double threshold;
        switch (availType) {
        case count:
            threshold = up;
            break;
        case percent:
            threshold = (size == 0) ? 0 : (double) up / (double) size;
            break;
        default:
            throw new IllegalStateException();
        }
        return test(threshold, this.threshold);
    }

    /**
     * For trait uniqueness, returns if the number of unique traits
     * meets the specified threshold.
     * @param size
     * @return true if the threshold was met.
     */
    public boolean eval(int size) {
        return test(size, this.threshold);
    }

    public Double value(MeasurementAggregate aggregate) {
        return value(aggregate, null);
    }

    public boolean eval(MeasurementAggregate aggregate, MeasurementAggregate aggregate2) {
        return eval(aggregate, aggregate2, null);
    }

}
