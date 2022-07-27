/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;

import com.google.protobuf.ByteString;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public interface PMSExecutionService {
  InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions);

  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(Criteria criteria, Pageable pageable);

  PipelineExecutionSummaryEntity findFirst(Criteria criteria);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId);

  OrchestrationGraphDTO getOrchestrationGraph(String stageNodeId, String planExecutionId, String stageNodeExecutionId);

  InterruptDTO registerInterrupt(
      PlanExecutionInterruptType executionInterruptType, String planExecutionId, String nodeExecutionId);

  InterruptDTO registerInterrupt(PlanExecutionInterruptType executionInterruptType, String planExecutionId,
      String nodeExecutionId, InterruptConfig interruptConfig);

  Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      ByteString gitEntityBasicInfo, boolean isLatest);

  void deleteExecutionsOnPipelineDeletion(PipelineEntity pipelineEntity);

  long getCountOfExecutions(Criteria criteria);

  ExecutionDataResponseDTO getExecutionData(String planExecutionId);
}
