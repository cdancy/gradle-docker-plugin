/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks.container

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCopyFileToContainer extends DockerExistingContainer {
    /**
     * Path inside container
     */
    @Input
    @Optional
    String remotePath

    /**
     * Path on host
     */
    @Input
    @Optional
    String hostPath

    /**
     * Tar based InputStream on host
     */
    @Input
    @Optional
    InputStream hostStream

    @Override
    void runRemoteCommand(dockerClient) {
        try {
            def containerCommand = dockerClient.copyArchiveToContainerCmd(getContainerId())
            setContainerCommandConfig(containerCommand)
            logger.quiet "Copying '${getHostPath()}' to container with ID '${getContainerId()}' at '${getRemotePath()}'."
            containerCommand.exec()
        } finally {
            getHostStream()?.close()
        }
    }

    private void setContainerCommandConfig(containerCommand) {
        if (getRemotePath()) {
            containerCommand.withRemotePath(getRemotePath())
        }

        if (getHostPath()) {
            containerCommand.withHostResource(getHostPath())
        }

        if (getHostStream()) {
            containerCommand.withTarInputStream(getHostStream())
        }
    }
}
