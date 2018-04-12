/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.narayana.tomcat.jta.integration.app;

import org.jboss.tm.XAResourceRecovery;

import javax.transaction.xa.XAResource;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class is used solely for simulating system crash.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 * @author <a href="mailto:zfeng@redhat.com">Amos Feng</a>
 */
public class TestXAResourceRecovery implements XAResourceRecovery {

    private static final Logger LOGGER = Logger.getLogger(TestXAResourceRecovery.class.getSimpleName());

    @Override
    public XAResource[] getXAResources() throws RuntimeException {
        List<TestXAResource> resources;
        try {
            resources = getXAResourcesFromDirectory(TestXAResource.LOG_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info(TestXAResourceRecovery.class.getSimpleName() + " returning list of resources: " + resources);

        return resources.toArray(new XAResource[]{});
    }

    private List<TestXAResource> getXAResourcesFromDirectory(String directory) throws IOException {
        List<TestXAResource> resources = new ArrayList<>();

        Files.newDirectoryStream(FileSystems.getDefault().getPath(directory), "*_").forEach(path -> {
            try {
                resources.add(new TestXAResource(path.toFile()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return resources;
    }
}