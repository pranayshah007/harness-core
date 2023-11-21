/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.ssca.beans.SLSAVerificationSummary;
import io.harness.ssca.entities.ArtifactEntity;

import com.fasterxml.jackson.databind.JsonNode;

public interface PipelineService {
  JsonNode getPmsExecutionSummary(ArtifactEntity artifact);

  JsonNode getPmsExecutionSummary(Instance instance);

  String getPipelineName(JsonNode pmsExecutionSummaryJsonNode);

  String getPipelineExecutionSequenceId(JsonNode pmsExecutionSummaryJsonNode);

  String getPipelineExecutionTriggerType(JsonNode pmsExecutionSummaryJsonNode);
  SLSAVerificationSummary getSlsaVerificationSummary(JsonNode pmsExecutionSummaryJsonNode, ArtifactEntity artifact);
}
