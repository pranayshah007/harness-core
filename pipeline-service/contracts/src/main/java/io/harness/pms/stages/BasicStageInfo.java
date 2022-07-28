/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlNode;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;

@OwnedBy(PIPELINE)
@Value
@Builder
public class BasicStageInfo {
  String identifier;
  String name;
  String type;
  String yaml;
  YamlNode stageYamlNode;
  @Wither List<String> stagesRequired;
  @Wither boolean isToBeBlocked;
}
