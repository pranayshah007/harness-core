/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PMSExecutionService {
  InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions,
      ResolveInputYamlType resolveExpressionsType);

  String getInputSetYamlForRerun(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted);

  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(Criteria criteria, Pageable pageable);

  Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntityWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId);

  void sendGraphUpdateEvent(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity);

  OrchestrationGraphDTO getOrchestrationGraph(String stageNodeId, String planExecutionId, String stageNodeExecutionId);

  InterruptDTO registerInterrupt(
      PlanExecutionInterruptType executionInterruptType, String planExecutionId, String nodeExecutionId);

  InterruptDTO registerInterrupt(PlanExecutionInterruptType executionInterruptType, String planExecutionId,
      String nodeExecutionId, InterruptConfig interruptConfig);

  Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      boolean isLatest);
  Criteria formCriteriaForRepoAndBranchListing(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoName);

  PMSPipelineListRepoResponse getListOfRepo(Criteria criteria);
  PMSPipelineListBranchesResponse getListOfBranches(Criteria criteria);

  // This is created only for internal purpose to support IDP plugin. It creates criteria using account ID, project ID,
  // pipeline IDs(As List to support multiple pipeline Identifiers) and filterProperties Operator(AND or OR) is
  // parameterized for modules in filterProperties.
  Criteria formCriteriaOROperatorOnModules(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String filterIdentifier);

  void deleteExecutionsOnPipelineDeletion(PipelineEntity pipelineEntity);

  long getCountOfExecutions(Criteria criteria);

  ExecutionDataResponseDTO getExecutionData(String planExecutionId);

  ExecutionMetaDataResponseDetailsDTO getExecutionDataDetails(String planExecutionId);

  String mergeRuntimeInputIntoPipelineForRerun(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers);
}
