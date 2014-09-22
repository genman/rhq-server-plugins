package com.apple.iad.rhq.alert.aggregate;

import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;

import org.rhq.enterprise.server.alert.AlertManagerLocal;
import org.rhq.enterprise.server.util.LookupUtil;

class Lookup {

    static EntityManager em;

    static AlertManagerLocal alertManagerLocal;

    static TransactionManager tm;

    static EntityManager getEntityManager() {
        if (em == null)
            return LookupUtil.getEntityManager();
        return em;
    }

    public static AlertManagerLocal getAlertManager() {
        if (alertManagerLocal == null)
            return LookupUtil.getAlertManager();
        return alertManagerLocal;
    }

    public static TransactionManager getTransactionManager() {
        if (tm == null)
            return LookupUtil.getTransactionManager();
        return tm;
    }


}
