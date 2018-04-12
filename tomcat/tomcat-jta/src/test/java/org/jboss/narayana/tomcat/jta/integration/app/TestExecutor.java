/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.narayana.tomcat.jta.integration.app;

import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAResource;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@Path(TestExecutor.BASE_PATH)
public class TestExecutor {

    public static final String BASE_PATH = "executor";

    public static final String JNDI_TEST = "jndi";

    public static final String RECOVERY_T = "recovery";

    public static final String CRASH_T = "crash";

    private static final Logger LOGGER = Logger.getLogger(TestExecutor.class.getSimpleName());

    private final StringDao stringDao;

    private final TransactionManager transactionManager;

    public TestExecutor() throws NamingException, SQLException {
        stringDao = new StringDao();
        transactionManager = getTransactionManager();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getStrings() throws SQLException {
        LOGGER.info(" GET");
        return stringDao.getAll();
    }

    @POST
    public void saveString(String string) throws Exception {
        LOGGER.info(" POST");
        LOGGER.info(" begin transaction");
        transactionManager.begin();
        LOGGER.info(" save string");
        try {
            stringDao.save(string);
            LOGGER.info(" commit transaction");
            transactionManager.commit();
            LOGGER.info(" transaction committed successfully");
        } catch (SQLException e) {
            LOGGER.info(" rollback transaction");
            transactionManager.rollback();
            LOGGER.info(" transaction rolled back");
            throw e;
        } finally {
            stringDao.close();
        }
    }

    @DELETE
    public void removeAll() throws Exception {
        LOGGER.info(" DELETE");
        stringDao.removeAll();
    }

    @POST
    @Path(CRASH_T)
    public void crash(String string) throws Exception {
        transactionManager.begin();
        try {
            transactionManager.getTransaction().enlistResource(new TestXAResource());
            stringDao.save(string);
            transactionManager.commit();
        } catch (SQLException e) {
            transactionManager.rollback();
            throw e;
        } finally {
            stringDao.close();
        }
    }

    @GET
    @Path(RECOVERY_T)
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> recovery() throws Exception {
        List<String> stringsBefore = stringDao.getAll();
        LOGGER.info("Strings at the start: " + stringsBefore);
        getXARecoveryModule().addXAResourceRecoveryHelper(new XAResourceRecoveryHelper() {
            @Override
            public boolean initialise(String s) throws Exception {
                return true;
            }

            @Override
            public XAResource[] getXAResources() throws Exception {
                return new TestXAResourceRecovery().getXAResources();
            }
        });
        waitForRecovery(stringsBefore);
        LOGGER.info("Strings at the end: " + stringDao.getAll());
        return stringDao.getAll();
    }

    private void waitForRecovery(List<String> stringsBefore) throws Exception {
        boolean isComplete = false;

        for (int i = 0; i < 3 && !isComplete; i++) {
            sleep(5000);
            isComplete = stringsBefore.size() < stringDao.getAll().size();
        }

        if (isComplete) {
            LOGGER.info("Recovery completed successfully");
        } else {
            throw new Exception("Something wrong happened and recovery didn't complete");
        }
    }

    private XARecoveryModule getXARecoveryModule() {
        XARecoveryModule xaRecoveryModule = XARecoveryModule.getRegisteredXARecoveryModule();
        if (xaRecoveryModule != null) {
            return xaRecoveryModule;
        }
        throw new IllegalStateException("XARecoveryModule is not registered with recovery manager");
    }

    @GET
    @Path(JNDI_TEST)
    public Response verifyJndi() throws NamingException {
        LOGGER.info("Verifying JNDI");

        if (getUserTransaction() == null) {
            return Response.serverError().entity("UserTransaction not found in JNDI").build();
        }

        if (getTransactionManager() == null) {
            return Response.serverError().entity("TransactionManager not found in JNDI").build();
        }

        if (getTransactionSynchronizationRegistry() == null) {
            return Response.serverError().entity("TransactionSynchronizationRegistry not found in JNDI").build();
        }

        if (getTransactionalDataSource() == null) {
            return Response.serverError().entity("DataSource not found in JNDI").build();
        }

        return Response.noContent().build();
    }

    private UserTransaction getUserTransaction() throws NamingException {
        return InitialContext.doLookup("java:comp/UserTransaction");
    }

    private TransactionManager getTransactionManager() throws NamingException {
        return InitialContext.doLookup("java:comp/env/TransactionManager");
    }

    private TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() throws NamingException {
        return InitialContext.doLookup("java:comp/env/TransactionSynchronizationRegistry");
    }

    private DataSource getTransactionalDataSource() throws NamingException {
        return InitialContext.doLookup("java:comp/env/transactionalDataSource");
    }
}
