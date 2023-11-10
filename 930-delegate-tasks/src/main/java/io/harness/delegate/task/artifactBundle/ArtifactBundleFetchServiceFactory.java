/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifactBundle.tas.ArtifactBundleFetchService;

import io.harness.delegate.task.artifactBundle.tas.TasArtifactBundleFetchService import software.wings.api.DeploymentType;

import com.google.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@OwnedBy(CDP)
public class ArtifactBundleFetchServiceFactory {
  private final TasArtifactBundleFetchService tasArtifactBundleFetchService;
  private final ConcurrentHashMap<String, ArtifactBundleFetchService> holder;

  @Inject
  public ArtifactBundleFetchServiceFactory(TasArtifactBundleFetchService instanceDetailsFetcher) {
    this.holder = new ConcurrentHashMap<>();
    this.tasArtifactBundleFetchService = instanceDetailsFetcher;

    initFetchers();
  }

  private void initFetchers() {
    this.holder.put(DeploymentType.PCF.toString(), tasArtifactBundleFetchService);
  }

  public ArtifactBundleFetchService getArtifactBundleFetchService(ArtifactBundleConfig artifactBundleConfig) {
    return this.holder.getOrDefault(artifactBundleConfig.getType().toString(), null);
  }
}
