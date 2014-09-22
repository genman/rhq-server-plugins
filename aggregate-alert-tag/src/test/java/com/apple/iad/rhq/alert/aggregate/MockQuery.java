package com.apple.iad.rhq.alert.aggregate;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.persistence.FlushModeType;
import javax.persistence.Query;
import javax.persistence.TemporalType;

public class MockQuery implements Query {

    @Override
    public List getResultList() {
        return Collections.emptyList();
    }

    @Override
    public Object getSingleResult() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int executeUpdate() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Query setMaxResults(int maxResult) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Query setFirstResult(int startPosition) {
        return this;
    }

    @Override
    public Query setHint(String hintName, Object value) {
        return this;
    }

    @Override
    public Query setParameter(String name, Object value) {
        return this;
    }

    @Override
    public Query setParameter(String name, Date value, TemporalType temporalType) {
        return this;
    }

    @Override
    public Query setParameter(String name, Calendar value, TemporalType temporalType) {
        return this;
    }

    @Override
    public Query setParameter(int position, Object value) {
        return this;
    }

    @Override
    public Query setParameter(int position, Date value,
            TemporalType temporalType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Query setParameter(int position, Calendar value,
            TemporalType temporalType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Query setFlushMode(FlushModeType flushMode) {
        // TODO Auto-generated method stub
        return null;
    }

}
