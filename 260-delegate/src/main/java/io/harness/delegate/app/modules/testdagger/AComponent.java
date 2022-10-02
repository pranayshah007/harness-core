package io.harness.delegate.app.modules.testdagger;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.nexus.service.NexusRegistryService;

import dagger.Component;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
@Component(modules = {NGDelegateDaggerModule.class})
// scope if needed
public interface AComponent {
  DockerRegistryService regService();

  NexusRegistryService getNexusRegSvc();

  ArtifactoryRegistryService getArtifactoryRegistryService();

  Map<Class<? extends Exception>, ExceptionHandler> getExceptionHandlerMap();

  void inject(ThatCoolThing thatCoolThing);
}