package io.harness.delegate.app.modules.testdagger;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.nexus.service.NexusRegistryService;

import javax.inject.Inject;

public class ThatCoolThing {
    @Inject
    DockerRegistryService svc;

    @Inject
    NexusRegistryService nrs;

    @Inject
    ArtifactoryRegistryService artifactoryRegistryService;
}
