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

package org.jboss.narayana.tomcat.jta.integration.utils;

import org.apache.commons.lang.StringUtils;

/**
 * @author <a href="mailto:karm@redhat.com">Michal Karm Babacek</a>
 */
public class H2Allocator extends Allocator {

    H2Allocator() {
        // Use getInstance
    }

    public DB allocateDB(final int expiryMinutes) {
        final String versionComH2database = System.getProperty("version.com.h2database");
        if (StringUtils.isEmpty(versionComH2database)) {
            throw new IllegalArgumentException("version.com.h2database must not be empty");
        }
        return new DB.Builder()
                .dsType("org.h2.jdbcx.JdbcDataSource")
                .dsUsername("sa")
                .dsUser("sa")
                .dsPassword("sa")
                .dsDbName("testdb")
                .dsUrl("jdbc:h2:mem:testdb;TRACE_LEVEL_FILE=3;TRACE_LEVEL_SYSTEM_OUT=3")
                .dsLoginTimeout("0")
                .dsFactory("org.h2.jdbcx.JdbcDataSourceFactory")
                .dsDriverClassName("org.h2.Driver")
                .tdsType("javax.sql.DataSource")
                .tdsUrl("jdbc:arjuna:java:comp/env")
                .tdsDriverClassName("com.arjuna.ats.jdbc.TransactionalDriver")
                .dbDriverArtifact("com.h2database:h2:" + versionComH2database)
                .build();
    }

    public DB allocateDB() {
        return allocateDB(0);
    }

    public boolean deallocateDB(final DB db) {
        // Intentionally nothing
        return true;
    }

    public boolean reallocateDB(final int expiryMinutes, final DB db) {
        // Intentionally nothing
        return true;
    }

    public boolean reallocateDB(final DB db) {
        // Intentionally nothing
        return true;
    }

    public boolean cleanDB(final DB db) {
        // Intentionally nothing
        return true;
    }
}
