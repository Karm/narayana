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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:karm@redhat.com">Michal Karm Babacek</a>
 *         <p>
 *         Start databases according to the profile. The goal is to be able to work both with platform
 *         independent simple H2 example, locally started databases in containers, e.g. Postgres, and
 *         with remote databases configured via REST API, e.g. MS SQL, Oracle, Maria and more.
 *         </p>
 */
public abstract class Allocator {
    private static final Logger LOGGER = Logger.getLogger(Allocator.class.getName());

    private static Allocator dbAllocator;

    Allocator() {
        // Use getInstance
    }

    public static Allocator getInstance() throws IOException {
        if (dbAllocator != null) {
            return dbAllocator;
        }

        final String mode = System.getProperty("h2.or.container.or.dballocator");
        if (StringUtils.isEmpty(mode)) {
            throw new IllegalArgumentException("h2.or.container.or.dballocator must not be empty");
        }
        if ("h2".equals(mode)) {
            dbAllocator = new H2Allocator();
        } else if ("container".equals(mode)) {
            dbAllocator = new PostgresContainerAllocator();
        } else if ("dballocator".equals(mode)) {
            dbAllocator = new DBAllocator();
        } else {
            throw new IllegalArgumentException("Unknown operation mode, expected h2 or container or dballocator but was: " + mode);
        }

        return dbAllocator;
    }

    public abstract DB allocateDB(final int expiryMinutes);

    public abstract DB allocateDB();

    public abstract boolean deallocateDB(final DB db);

    public abstract boolean reallocateDB(final int expiryMinutes, final DB db);

    public abstract boolean reallocateDB(final DB db);

    public abstract boolean cleanDB(final DB db);

    static boolean fileOK(final int minSize, final File file) {
        return file != null && file.exists() && FileUtils.sizeOf(file) >= minSize;
    }

    static boolean waitForTcp(final String host, final int port, final int connTimeoutMs, final long overallTimeoutMs) {
        final long timestamp = System.currentTimeMillis();
        final SocketAddress sa = new java.net.InetSocketAddress(host, port);
        while (System.currentTimeMillis() - timestamp < overallTimeoutMs) {
            try (final Socket socket = new Socket()) {
                socket.connect(sa, connTimeoutMs);
                socket.shutdownInput();
                socket.shutdownOutput();
                return true;
            } catch (IOException e) {
                LOGGER.fine(String.format("waitForTcp: %s:%d", host, port));
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.fine(String.format("waitForTcp: %s", e.getMessage()));
            }
        }
        // still port not ready, failing
        return false;
    }
}
