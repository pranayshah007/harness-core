/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.ManifestType.HELM_MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ACR;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ARTIFACTORY_REGISTRY;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.DOCKER_REGISTRY;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.ECR;
import static io.harness.ngtriggers.beans.source.artifact.ArtifactType.GCR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.artifact.BuildStoreType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class GeneratorFactory {
  private final BuildTriggerHelper buildTriggerHelper;
  private final HttpHelmPollingItemGenerator httpHelmPollingItemGenerator;
  private final S3HelmPollingItemGenerator s3HelmPollingItemGenerator;
  private final GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator;
  private final GcrPollingItemGenerator gcrPollingItemGenerator;
  private final EcrPollingItemGenerator ecrPollingItemGenerator;
  private final DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;
  private final ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator;
  private final AcrPollingItemGenerator acrPollingItemGenerator;

  public PollingItemGenerator retrievePollingItemGenerator(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    if (ngTriggerEntity.getType() == MANIFEST) {
      return retrievePollingItemGeneratorForManifest(buildTriggerOpsData);
    } else if (ngTriggerEntity.getType() == ARTIFACT) {
      return retrievePollingItemGeneratorForArtifact(buildTriggerOpsData);
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForArtifact(BuildTriggerOpsData buildTriggerOpsData) {
    String buildType = buildTriggerHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());
    if (GCR.getValue().equals(buildType)) {
      return gcrPollingItemGenerator;
    } else if (ECR.getValue().equals(buildType)) {
      return ecrPollingItemGenerator;
    } else if (DOCKER_REGISTRY.getValue().equals(buildType)) {
      return dockerRegistryPollingItemGenerator;
    } else if (ARTIFACTORY_REGISTRY.getValue().equals(buildType)) {
      return artifactoryRegistryPollingItemGenerator;
    } else if (ACR.getValue().equals(buildType)) {
      return acrPollingItemGenerator;
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForManifest(BuildTriggerOpsData buildTriggerOpsData) {
    String buildType = buildTriggerHelper.fetchBuildType(buildTriggerOpsData.getTriggerSpecMap());
    if (HELM_MANIFEST.getValue().equals(buildType)) {
      return retrievePollingItemGeneratorForHelmChart(buildTriggerOpsData);
    }

    return null;
  }

  private PollingItemGenerator retrievePollingItemGeneratorForHelmChart(BuildTriggerOpsData buildTriggerOpsData) {
    String storeTypeFromTrigger = buildTriggerHelper.fetchStoreTypeForHelm(buildTriggerOpsData);
    if (BuildStoreType.HTTP.getValue().equals(storeTypeFromTrigger)) {
      return httpHelmPollingItemGenerator;
    } else if (BuildStoreType.S3.getValue().equals(storeTypeFromTrigger)) {
      return s3HelmPollingItemGenerator;
    } else if (BuildStoreType.GCS.getValue().equals(storeTypeFromTrigger)) {
      return gcsHelmPollingItemGenerator;
    }

    return null;
  }
}
