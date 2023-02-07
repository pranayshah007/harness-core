/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class GARArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String region;
  String project;
  String repositoryName;
  String pkg;
  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return GoogleArtifactRegistryConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .project(ParameterField.<String>builder().value(project).build())
        .pkg(ParameterField.<String>builder().value(pkg).build())
        .region(ParameterField.<String>builder().value(region).build())
        .repositoryName(ParameterField.<String>builder().value(repositoryName).build())
        .build();
  }
}
