/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.group;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YamlField;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("groupStepParametersV1")
@RecasterAlias("io.harness.steps.group.GroupStepParametersV1")
public class GroupStepParametersV1 implements StepParameters {
  String identifier;
  String name;
  String childNodeID;

  public static GroupStepParametersV1 getStepParameters(YamlField config, String childNodeID) {
    if (config == null) {
      return GroupStepParametersV1.builder().childNodeID(childNodeID).build();
    }
    return GroupStepParametersV1.builder()
        .identifier(config.getId())
        .name(config.getNodeName())
        .childNodeID(childNodeID)
        .build();
  }
}
