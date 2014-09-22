package com.apple.iad.rhq.alert.aggregate;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;

import org.rhq.core.domain.alert.Alert;

public class MockEntityManager implements EntityManager {

    final List<Alert> alerts = new ArrayList<Alert>();

    @Override
    public void persist(Object entity) {
        if (entity instanceof Alert)
            alerts.add((Alert) entity);
    }

    public List<Alert> getAlerts() {
        return alerts;
    }

    @Override
    public <T> T merge(T entity) {
        return null;
    }

    @Override
    public void remove(Object entity) {
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey) {
        return null;
    }

    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        return null;
    }

    @Override
    public void flush() {
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
    }

    @Override
    public FlushModeType getFlushMode() {
        return null;
    }

    @Override
    public void lock(Object entity, LockModeType lockMode) {
    }

    @Override
    public void refresh(Object entity) {
    }

    @Override
    public void clear() {
    }

    @Override
    public boolean contains(Object entity) {
        return false;
    }

    @Override
    public Query createQuery(String qlString) {
        return new MockQuery();
    }

    @Override
    public Query createNamedQuery(String name) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, Class resultClass) {
        return null;
    }

    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        return null;
    }

    @Override
    public void joinTransaction() {
    }

    @Override
    public Object getDelegate() {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public EntityTransaction getTransaction() {
        return null;
    }

}
