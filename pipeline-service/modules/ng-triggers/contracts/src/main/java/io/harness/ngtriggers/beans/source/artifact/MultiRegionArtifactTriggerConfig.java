/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(PIPELINE)
public class MultiRegionArtifactTriggerConfig implements NGTriggerSpecV2, BuildAware {
  String stageIdentifier;
  String artifactRef;
  ArtifactType type;
  List<ArtifactTypeSpecWrapper> sources;
  List<TriggerEventDataCondition> eventConditions;
  List<TriggerEventDataCondition> metaDataConditions;
  String jexlCondition;

  @Builder
  public MultiRegionArtifactTriggerConfig(ArtifactType type, List<ArtifactTypeSpecWrapper> sources) {
    this.type = type;
    this.sources = sources;
  }

  @Override
  public String fetchStageRef() {
    return stageIdentifier;
  }

  @Override
  public String fetchbuildRef() {
    return artifactRef;
  }

  @Override
  public String fetchBuildType() {
    if (isEmpty(this.sources)) {
      return null;
    }
    return this.sources.get(0).getSpec().fetchBuildType();
  }
}
