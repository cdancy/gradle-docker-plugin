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
package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerWorkflowFunctionalTest extends AbstractFunctionalTest {
    def "Can create Dockerfile and build an image from it"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task createDockerfile(type: Dockerfile) {
                destFile = project.file('build/mydockerfile/Dockerfile')
                from 'ubuntu:12.04'
                maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn createDockerfile
                inputDir = createDockerfile.destFile.parentFile
                tag = "${createUniqueImageId()}"
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task workflow {
                dependsOn inspectImage
            }
        """

        when:
        BuildResult result = build('workflow')

        then:
        new File(projectDir, 'build/mydockerfile/Dockerfile').exists()
        result.standardOutput.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build and verify image"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task workflow {
                dependsOn inspectImage
            }
        """

        when:
        BuildResult result = build('workflow')

        then:
        result.standardOutput.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build an image, create and start a container"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerKillContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
                containerName = "$uniqueContainerName"
                portBindings = ['8080:8080']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
            }

            task killContainer(type: DockerKillContainer) {
                dependsOn inspectContainer
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn killContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("Name        : /$uniqueContainerName")
    }

    def "Can build an image, create and link a container"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task createContainer1(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
                containerName = "${uniqueContainerName}1"
            }

            task createContainer2(type: DockerCreateContainer) {
                dependsOn createContainer1
                targetImageId { buildImage.getImageId() }
                containerName = "${uniqueContainerName}2"
                links = ["${uniqueContainerName}1:container1"]
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer2
                targetContainerId { createContainer2.getContainerId() }
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("Links       : [${uniqueContainerName}1:container1]")
    }

    def "Can build an image, create a container and link its volumes into another container"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        def dockefile = createDockerfile(imageDir)

        dockefile << 'VOLUME /data'

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task createContainer1(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
                containerName = "${uniqueContainerName}-1"
            }

            task createContainer2(type: DockerCreateContainer) {
                dependsOn createContainer1
                targetImageId { buildImage.getImageId() }
                containerName = "${uniqueContainerName}-2"
                volumesFrom = ["${uniqueContainerName}-1"]
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer2
                targetContainerId { createContainer2.getContainerId() }
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("VolumesFrom : [${uniqueContainerName}-1:rw]")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can build an image and push to private registry"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

            task createDockerfile(type: Dockerfile) {
                destFile = project.file('build/mydockerfile/Dockerfile')
                from 'ubuntu:12.04'
                maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn createDockerfile
                inputDir = createDockerfile.destFile.parentFile
                tag = '${TestConfiguration.dockerPrivateRegistryDomain}/${createUniqueImageId()}'
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                conventionMapping.imageName = { buildImage.getTag() }
            }

            task workflow {
                dependsOn pushImage
            }
        """

        when:
        build('workflow')

        then:
        new File(projectDir, 'build/mydockerfile/Dockerfile').exists()
        noExceptionThrown()
    }

    def "Can build an image, create a container, and copy file from it"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
                containerName = "$uniqueContainerName"
            }

            task copyFileFromContainer(type: DockerCopyFileFromContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/copy-file-dir/shebang.tar"
                remotePath = "/bin/sh"
                compressed = true
            }

            task copyDirFromContainer(type: DockerCopyFileFromContainer) {
                dependsOn copyFileFromContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/copy-dir"
                remotePath = "/var/log"
            }

            task workflow {
                dependsOn copyDirFromContainer
            }
        """

        when:
        BuildResult result = build('workflow')

        then:
        new File("$projectDir/copy-file-dir/shebang.tar").exists() &&
                new File("$projectDir/copy-dir").exists()
    }

    def "Can build an image only once"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueImageId = createUniqueImageId()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${uniqueImageId}"
            }

            task buildImageAgain(type: DockerBuildImage) {
                dependsOn buildImage
                inputDir = file('images/minimal')
                tag = "${uniqueImageId}"
            }

            task workflow {
                dependsOn buildImageAgain
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("UP-TO-DATE")
    }

    def "Can build an image, create a container and expose a port"() {

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                tag = "${createUniqueImageId()}"
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
                containerName = "${uniqueContainerName}"
                exposePorts("tcp", [9999])
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("ExposedPorts : [9999/tcp]")
    }

    private File createDockerfile(File imageDir) {
        File dockerFile = new File(imageDir, 'Dockerfile')

        dockerFile << """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        dockerFile
    }
}