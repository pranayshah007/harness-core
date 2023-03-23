/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.ArtifactInfo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class BambooArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String planKey;
  List<String> artifactPaths;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.BAMBOO;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return BambooArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .planKey(ParameterField.<String>builder().value(planKey).build())
        .artifactPaths(ParameterField.<List<String>>builder().value(artifactPaths).build())
        .build();
  }
}
