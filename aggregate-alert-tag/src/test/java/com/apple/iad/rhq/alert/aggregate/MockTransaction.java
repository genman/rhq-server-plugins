package com.apple.iad.rhq.alert.aggregate;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

public class MockTransaction implements Transaction {

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
    }

    @Override
    public boolean delistResource(XAResource arg0, int arg1) throws IllegalStateException, SystemException {
        return false;
    }

    @Override
    public boolean enlistResource(XAResource arg0) throws RollbackException, IllegalStateException, SystemException {
        return false;
    }

    @Override
    public int getStatus() throws SystemException {
        return 0;
    }

    @Override
    public void registerSynchronization(Synchronization arg0) throws RollbackException, IllegalStateException, SystemException {
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
    }

}
