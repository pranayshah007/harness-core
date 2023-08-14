/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.ArtifactInfo;

import lombok.Builder;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class EcrArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String region;
  String imagePath;
  String registryId;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.ECR;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return EcrArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .region(ParameterField.<String>builder().value(region).build())
        .imagePath(ParameterField.<String>builder().value(imagePath).build())
        .registryId(ParameterField.<String>builder().value(registryId).build())
        .build();
  }
}
