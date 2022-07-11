/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.ManifestType;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(PIPELINE)
public class ManifestTriggerConfig implements NGTriggerSpecV2, BuildAware {
  String stageIdentifier;
  String manifestRef;

  ManifestType type;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = EXTERNAL_PROPERTY, property = "type", visible = true)
  ManifestTypeSpec spec;

  @Builder
  public ManifestTriggerConfig(ManifestType type, ManifestTypeSpec spec) {
    this.type = type;
    this.spec = spec;
  }

  @Override
  public String fetchStageRef() {
    return stageIdentifier;
  }

  @Override
  public String fetchbuildRef() {
    return manifestRef;
  }

  @Override
  public String fetchBuildType() {
    return spec.fetchBuildType();
  }
}
