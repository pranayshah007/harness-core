package io.harness.delegate.app.modules.testdagger;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.nexus.service.NexusRegistryService;

import dagger.Component;

@Component(modules = {NGDelegateDaggerModule.class})
// scope if needed
public interface AComponent {
  DockerRegistryService regService();

  NexusRegistryService getNexusRegSvc();

  ArtifactoryRegistryService getArtifactoryRegistryService();

  void inject(ThatCoolThing thatCoolThing);
}