/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.testdagger;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifactory.service.ArtifactoryRegistryServiceImpl;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryServiceImpl;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.artifacts.gcr.service.GcrApiServiceImpl;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactoryImpl;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryServiceImpl;
import io.harness.http.HttpService;
import io.harness.http.HttpServiceImpl;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.nexus.service.NexusRegistryServiceImpl;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@TargetModule(HarnessModule._420_DELEGATE_AGENT_RDM)
@Module
public abstract class NGDelegateDaggerModule {
  @Provides
  // scope if needed
  public static DockerRegistryService provideDockerRegistryService() {
    return new DockerRegistryServiceImpl();
  }

  @Provides
  public static NexusRegistryService provideNexusRegistryService() {
    return new NexusRegistryServiceImpl();
  }

  @Binds
  abstract ArtifactoryRegistryService bindArtifactoryRegistryService(
      ArtifactoryRegistryServiceImpl artifactoryRegistryService);

  @Binds abstract GcrApiService bindGcrApiService(GcrApiServiceImpl gcrApiService);

  @Binds
  abstract GithubPackagesRestClientFactory bindGithubPackagesRestClientFactory(
      GithubPackagesRestClientFactoryImpl githubPackagesRestClientFactory);

  @Binds
  abstract GithubPackagesRegistryService bindGithubPackagesRegistryService(
      GithubPackagesRegistryServiceImpl githubPackagesRegistryService);

  @Binds abstract GarApiService bindGarApiService(GARApiServiceImpl garApiService);

  @Binds abstract HttpService bindHttpService(HttpServiceImpl httpService);
}
