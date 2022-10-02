package io.harness.delegate.app.modules.testdagger;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.nexus.service.NexusRegistryService;

import javax.inject.Inject;
import java.util.Map;

public class ThatCoolThing {
  @Inject DockerRegistryService svc;
  @Inject NexusRegistryService nrs;
  @Inject ArtifactoryRegistryService artifactoryRegistryService;
  @Inject Map<Class<? extends Exception>, ExceptionHandler> mapOfExceptionHandlers;
}
