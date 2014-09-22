package com.apple.iad.rhq.alert.aggregate;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

public class MockTransactionManager implements TransactionManager {

    @Override
    public void begin() throws NotSupportedException, SystemException {
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
    }

    @Override
    public int getStatus() throws SystemException {
        return 0;
    }

    @Override
    public Transaction getTransaction() throws SystemException {
        return new MockTransaction();
    }

    @Override
    public void resume(Transaction arg0) throws InvalidTransactionException, IllegalStateException, SystemException {
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
    }

    @Override
    public void setTransactionTimeout(int arg0) throws SystemException {
    }

    @Override
    public Transaction suspend() throws SystemException {
        return null;
    }

}
