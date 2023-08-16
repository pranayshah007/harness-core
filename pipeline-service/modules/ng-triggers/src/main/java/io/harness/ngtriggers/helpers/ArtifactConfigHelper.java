/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.DOCKER_REGISTRY;
import static io.harness.ngtriggers.Constants.ECR;
import static io.harness.ngtriggers.Constants.NEXUS3_REGISTRY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.NexusRegistrySpec;
import io.harness.pms.contracts.triggers.TriggerPayload;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class ArtifactConfigHelper {
  public void setConnectorAndImage(TriggerPayload.Builder triggerPayloadBuilder, ArtifactTriggerConfig artifactConfig) {
    if (artifactConfig.getSpec() != null && artifactConfig.getSpec().fetchConnectorRef() != null) {
      triggerPayloadBuilder.setConnectorRef(artifactConfig.getSpec().fetchConnectorRef());
    }

    if (fetchImagePath(artifactConfig) != null) {
      triggerPayloadBuilder.setImagePath(fetchImagePath(artifactConfig));
    }
  }

  public String fetchImagePath(ArtifactTriggerConfig config) {
    ArtifactTypeSpec spec = config.getSpec();

    switch (spec.fetchBuildType()) {
      case ECR:
        return ((EcrSpec) spec).getImagePath();
      case DOCKER_REGISTRY:
        return ((DockerRegistrySpec) spec).getImagePath();
      case NEXUS3_REGISTRY:
        return ((NexusRegistrySpec) spec).getImagePath();
      default:
        return null;
    }
  }
}
