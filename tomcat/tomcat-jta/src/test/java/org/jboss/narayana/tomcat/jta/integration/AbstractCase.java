package org.jboss.narayana.tomcat.jta.integration;

import org.jboss.narayana.tomcat.jta.integration.utils.Allocator;
import org.jboss.narayana.tomcat.jta.integration.utils.DB;
import org.jboss.narayana.tomcat.jta.integration.utils.DBAllocator;
import org.jboss.narayana.tomcat.jta.integration.utils.H2Allocator;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Shared functionality for test cases, mainly DB allocation.
 *
 * @author <a href="mailto:karm@redhat.com">Michal Karm Babacek</a>
 */
public abstract class AbstractCase {

    private static final Logger LOGGER = Logger.getLogger(AbstractCase.class.getName());

    /**
     * The deployment is the same for each test, but context.xml and DB driver jar
     * changes according to the database used.
     */
    static WebArchive webArchive;
    static Allocator dba;
    static DB db;

    /**
     * Acquire resources and prepare configuration
     */
    @BeforeClass
    public static void init() {
        try {
            dba = Allocator.getInstance();
            LOGGER.info("Allocating a new database might take many minutes, depending on the mode the test suite operates in.");
            db = dba.allocateDB();
            assertNotNull("Failed to allocate DB. Check logs for the root cause.", db);
            // Configuration of deployment, driver, data sources XML, ...
            prepareContextXML();
            final File[] dbDriver = (dba instanceof DBAllocator) ? new File[]{new File(db.dbDriverArtifact)} :
                    Maven.resolver().resolve(db.dbDriverArtifact).withTransitivity().asFile();
            assertNotNull("WebArchive was not created by @Deployment before @BeforeClass. Arquillian lifecycle config error?", webArchive);
            webArchive.addAsLibraries(dbDriver);
            webArchive.addAsManifestResource("context.xml", "context.xml");
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            fail(e.getMessage());
            if (dba != null && db != null) {
                dba.deallocateDB(db);
            }
        }
    }

    /**
     * Release resources
     */
    @AfterClass
    public static void clean() {
        if (dba != null && db != null) {
            dba.deallocateDB(db);
        }
    }

    /**
     * Edits context.xml so as it reflects the current database being used.
     */
    private static void prepareContextXML() {
        try {
            final File contextXML = new File(URLDecoder.decode(AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            }).getResource("context.xml").getFile(), "UTF-8"));

            final Document context = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contextXML);

            final Element dbSource = context.createElement("Resource");
            dbSource.setAttribute("name", "myDataSource");
            dbSource.setAttribute("uniqueName", "myDataSource");
            dbSource.setAttribute("auth", "Container");
            dbSource.setAttribute("type", db.dsType);
            dbSource.setAttribute("username", db.dsUsername);
            dbSource.setAttribute("user", db.dsUser);
            dbSource.setAttribute("password", db.dsPassword);
            dbSource.setAttribute("url", db.dsUrl);
            dbSource.setAttribute("description", "Data Source");
            dbSource.setAttribute("loginTimeout", db.dsLoginTimeout);
            dbSource.setAttribute("factory", db.dsFactory);
            if (!(dba instanceof H2Allocator)) {
                dbSource.setAttribute("databaseName", db.dsDbName);
                dbSource.setAttribute("portNumber", db.dsDbPort);
                dbSource.setAttribute("serverName", db.dsDbHostname);
            }
            context.getDocumentElement().appendChild(dbSource);

            final Element tsDbSource = context.createElement("Resource");
            tsDbSource.setAttribute("name", "transactionalDataSource");
            tsDbSource.setAttribute("uniqueName", "transactionalDataSource");
            tsDbSource.setAttribute("auth", "Container");
            tsDbSource.setAttribute("type", db.tdsType);
            tsDbSource.setAttribute("username", db.dsUser);
            tsDbSource.setAttribute("password", db.dsPassword);
            tsDbSource.setAttribute("driverClassName", db.tdsDriverClassName);
            tsDbSource.setAttribute("url", db.tdsUrl + "/myDataSource");
            tsDbSource.setAttribute("description", "Transactional Driver Data Source");
            // Connection pool settings
            tsDbSource.setAttribute("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
            // We don'r want Arjuna to do the pooling, we want Tomcat JDBC to do the deed.
            tsDbSource.setAttribute("connectionProperties", "POOL_CONNECTIONS=false");
            tsDbSource.setAttribute("testWhileIdle", "true");
            tsDbSource.setAttribute("testOnBorrow", "true");
            tsDbSource.setAttribute("testOnReturn", "true");
            tsDbSource.setAttribute("validationQuery", "SELECT 1");
            tsDbSource.setAttribute("validationInterval", "10000");
            tsDbSource.setAttribute("timeBetweenEvictionRunsMillis", "20000");
            tsDbSource.setAttribute("maxActive", "100");
            tsDbSource.setAttribute("minIdle", "10");
            tsDbSource.setAttribute("maxWait", "10000");
            tsDbSource.setAttribute("initialSize", "10");
            tsDbSource.setAttribute("removeAbandonedTimeout", "60");
            tsDbSource.setAttribute("removeAbandoned", "true");
            tsDbSource.setAttribute("logAbandoned", "true");
            tsDbSource.setAttribute("minEvictableIdleTimeMillis", "30000");
            tsDbSource.setAttribute("jmxEnabled", "true");
            tsDbSource.setAttribute("defaultAutoCommit", "false");
            tsDbSource.setAttribute("jdbcInterceptors", "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer");
            context.getDocumentElement().appendChild(tsDbSource);

            final Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(context), new StreamResult(contextXML));
        } catch (ParserConfigurationException | TransformerException | SAXException e) {
            fail("Failed to parse, update and serialize web app's context.xml for data source configuration.");
        } catch (IOException e) {
            fail("Failed to locate context.xml to process.");
        }
    }
}
