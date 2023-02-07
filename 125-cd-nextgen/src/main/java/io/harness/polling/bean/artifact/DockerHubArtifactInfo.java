/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class DockerHubArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String imagePath;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.DOCKER_REGISTRY;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return DockerHubArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .imagePath(ParameterField.<String>builder().value(imagePath).build())
        .build();
  }
}
