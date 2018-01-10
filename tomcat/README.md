# Tomcat Narayana testing

The objective is to run basic integration with multiple versions of Tomcat across several databases.
The test suite offers three modes of operation:

## 1. H2, embedded database

This is the default. If there are no parameters specified, the test suite runs with embedded H2 database and its driver fetched from Maven central.

## 2. DBAllocator

When specified, the test suite could take advantage of DBAllocator service and allocate an arbitrary database system.
It was tested to work well with Postgres family of database systems. The DBAllocator service is not public and thus it is not used in the upstream testing.

## 3. Container - Postgres

The third and the most versatile mode is to control a Docker daemon and to pull database images and to start 
containers. The test suite expects a Docker daemon listening to its REST commands and nothing else. 
No special or pre-fetched images or local dependencies are expected. Postgres family of databases is currently implemented.

# Examples

All undermentioned examples expect a Tomcat installation pointed to by ```CATALINA_HOME``` env var. Certain users (mandatory) and logging (convenient) settings are also listed:

```bash
wget http://www-us.apache.org/dist/tomcat/tomcat-9/v9.0.5/bin/apache-tomcat-9.0.5.zip
unzip apache-tomcat-9.0.5.zip
export CATALINA_HOME=`pwd`/apache-tomcat-9.0.5/

cat <<EOT >> ${CATALINA_HOME}/conf/logging.properties
org.apache.tomcat.tomcat-jdbc.level = ALL
org.h2.level = ALL
org.postgresql.level = ALL
javax.sql.level = ALL
org.apache.tomcat.tomcat-dbcp.level = ALL
com.arjuna.level = ALL
EOT

sed -i 's/<\/tomcat-users>/<user username="arquillian" password="arquillian" roles="manager-script"\/>\n<\/tomcat-users>/' ${CATALINA_HOME}/conf/tomcat-users.xml
```

That is all. Nothing else is to be done for the Tomcat installation. One does not start Tomcat, the TS does it automatically.

```bash
pushd narayana/tomcat
```

## 1. H2, embedded database
Run the TS:
```bash
mvn integration-test -Parq-tomcat -Dtomcat.user=arquillian -Dtomcat.pass=arquillian
```
See logs, including Tomcat ones:
```
vim tomcat-jta/target/failsafe-reports/org.jboss.narayana.tomcat.jta.integration.BaseITCase-output.txt
```
It is noteworthy that one can see the database trace log too, e.g.
```bash
...
406 /**/Statement stat2 = conn0.createStatement();
407 2018-02-13 16:55:05 jdbc[3]:
408 /**/stat2.execute("PREPARE COMMIT XID_131077_00000000000000000000ffff7f000001000097c35a830a590000000c0000000000000000_00000000000000000000ffff7f000001000097c35a830a590000000831");
409 2018-02-13 16:55:05 jdbc[3]:
410 /*SQL */PREPARE COMMIT XID_131077_00000000000000000000ffff7f000001000097c35a830a590000000c0000000000000000_00000000000000000000ffff7f000001000097c35a830a590000000831;
411 2018-02-13 16:55:05 jdbc[3]:
...
```

The overall runtime with the simple base integration 

## 2. DBAllocator

DBAllocator is a reservation tool paired with a driver repository, one simply adjusts these parameters:

```bash
mvn integration-test -Parq-tomcat -Dtomcat.user=arquillian -Dtomcat.pass=arquillian \
    -Ddballocator.host.port=http://your.db.allocator.instance.example.com:8080 \
    -Ddballocator.driver.url=http://path.to.your.drivers.stash.example.com/postgresql94/jdbc4/postgresql-42.1.1.jar \
    -Dh2.or.container.or.dballocator=dballocator
```

To see logs:
```bash
vim tomcat-jta/target/failsafe-reports/org.jboss.narayana.tomcat.jta.integration.BaseITCase-output.txt
```

With DBAllocated database system, the TS does not retrieve database trace log though.

## 3. Container - Postgres

Verify you have a Docker daemon running. One can either use secure socket and a special user group or a plain REST control. Note that such exposure enables anyone to take control of the host system with a malicious container. The default Test suite setting is insecure and can be used only on trusted hosts withing trusted network with trusted images.
Check the daemon replies:
```bash
[jenkins@karm-centos7-x86-64 tomcat]$ docker -H=tcp://127.0.0.1:2375 ps -a;
CONTAINER ID        IMAGE               COMMAND             CREATED             STATUS              PORTS               NAMES
[jenkins@karm-centos7-x86-64 tomcat]$ 
```
Let's run the TS with a database from a container:

```bash
mvn integration-test -Parq-tomcat -Dtomcat.user=arquillian -Dtomcat.pass=arquillian \
    -Dh2.or.container.or.dballocator=container \
    -Dcontainer.database.driver.artifact=org.postgresql:postgresql:42.2.1 \
    -Dcontainer.database.image=postgres:10
```

The aforementioned example uses ```postgres:10``` from [DockerHub Postgres](https://hub.docker.com/_/postgres/), i.e. a vanilla image without
any additional layers or modifications. All configuration is done at runtime, see class ```PostgresContainerAllocator``` 
around ```final CreateContainerResponse narayanaDB ...``` assignment. To implement e.g. a MariaDB allocator one would simply refactor ```PostgresContainerAllocator``` class into a hierarchy.


To see the results:
```bash
vim tomcat-jta/target/failsafe-reports/org.jboss.narayana.tomcat.jta.integration.BaseITCase-output.txt
```
It is noteworthy that the test suite retrieves trace log from the database container, see:
```bash
2018-02-13 16:12:57.982 UTC transaction_id:0 LOG:  execute <unnamed>: BEGIN
2018-02-13 16:12:57.983 UTC transaction_id:0 LOG:  execute <unnamed>: INSERT INTO test VALUES ('test-entry-16:12:57.966')
2018-02-13 16:12:57.985 UTC transaction_id:558 LOG:  execute <unnamed>: PREPARE TRANSACTION '131077_AAAAAAAAAAAAAP//fwAAAQAArnVagw6JAAAACDE=_AAAAAAAAAAAAAP//fwAAAQAArnVagw6JAAAADAAAAAAAAAAA'
2018-02-13 16:13:01.396 UTC transaction_id:0 LOG:  execute <unnamed>: SET extra_float_digits = 3
2018-02-13 16:13:01.396 UTC transaction_id:0 LOG:  execute <unnamed>: SET application_name = 'PostgreSQL JDBC Driver'
2018-02-13 16:13:01.398 UTC transaction_id:0 LOG:  execute <unnamed>: COMMIT PREPARED '131077_AAAAAAAAAAAAAP//fwAAAQAArnVagw6JAAAACDE=_AAAAAAAAAAAAAP//fwAAAQAArnVagw6JAAAADAAAAAAAAAAA'
2018-02-13 16:13:01.417 UTC transaction_id:0 LOG:  execute <unnamed>: SELECT COUNT(*) FROM test WHERE value='test-entry-16:12:57.966'
```

The container based testing is used upstream.

# Jenkins job

See Jenkins job: [https://ci.modcluster.io/job/narayana-tomcat/](https://ci.modcluster.io/job/narayana-tomcat/). There are three axis:
 * Tomcat version, 9.0.5, 8.5.28 and 8.0.49
 * Database, h2, postgres:9.4 and postgres:10
 * JDK, openjdk-8 and oraclejdk-9
 
Narayana neither builds nor runs tests on JDK 9 at the moment, see: 
 * https://issues.jboss.org/browse/JBTM-2986
 * https://issues.jboss.org/browse/JBTM-2992
