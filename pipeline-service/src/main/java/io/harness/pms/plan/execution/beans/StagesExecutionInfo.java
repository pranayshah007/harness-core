/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.StagesExecutionMetadata;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class StagesExecutionInfo {
  boolean isStagesExecution;
  String pipelineYamlToRun;
  String fullPipelineYaml;
  List<String> stageIdentifiers;
  Map<String, String> expressionValues;
  boolean allowStagesExecution;

  public StagesExecutionMetadata toStagesExecutionMetadata() {
    return StagesExecutionMetadata.builder()
        .isStagesExecution(isStagesExecution)
        .fullPipelineYaml(fullPipelineYaml)
        .stageIdentifiers(stageIdentifiers)
        .expressionValues(expressionValues)
        .build();
  }
}
