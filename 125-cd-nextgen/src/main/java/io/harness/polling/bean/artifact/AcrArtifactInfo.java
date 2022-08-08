/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class AcrArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String registry;
  String repository;
  String subscriptionId;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.ACR;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return AcrArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .registry(ParameterField.<String>builder().value(registry).build())
        .repository(ParameterField.<String>builder().value(repository).build())
        .subscriptionId(ParameterField.<String>builder().value(subscriptionId).build())
        .build();
  }
}
