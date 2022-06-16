/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.OrchestrationService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.interrupts.Interrupt;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.filter.utils.ModuleInfoFilterUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.ProtoUtils;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSExecutionServiceImpl implements PMSExecutionService {
  @Inject private PmsExecutionSummaryRespository pmsExecutionSummaryRespository;
  @Inject private GraphGenerationService graphGenerationService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private FilterService filterService;
  @Inject private TriggeredByHelper triggeredByHelper;
  @Inject private YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Inject private ValidateAndMergeHelper validateAndMergeHelper;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;

  @Override
  public Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      ByteString gitSyncBranchContext, boolean isLatest) {
    Criteria criteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(accountId)) {
      criteria.and(PlanExecutionSummaryKeys.accountId).is(accountId);
    }
    if (EmptyPredicate.isNotEmpty(orgId)) {
      criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(projectId);
    }
    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      criteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).is(pipelineIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(statusList)) {
      criteria.and(PlanExecutionSummaryKeys.status).in(statusList);
    }

    criteria.and(PlanExecutionSummaryKeys.isLatestExecution).ne(!isLatest);

    Criteria filterCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populatePipelineFilterUsingIdentifier(filterCriteria, accountId, orgId, projectId, filterIdentifier);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      populatePipelineFilter(filterCriteria, filterProperties);
    }

    if (myDeployments) {
      criteria.and(PlanExecutionSummaryKeys.triggerType)
          .is(MANUAL)
          .and(PlanExecutionSummaryKeys.triggeredBy)
          .is(triggeredByHelper.getFromSecurityContext());
    }

    Criteria moduleCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(moduleName)) {
      // Check for pipeline with no filters also - empty pipeline or pipelines with only approval stage
      moduleCriteria.orOperator(Criteria.where(PlanExecutionSummaryKeys.modules).is(Collections.emptyList()),
          // This is here just for backward compatibility should be removed
          Criteria.where(PlanExecutionSummaryKeys.modules)
              .is(Collections.singletonList(ModuleType.PMS.name().toLowerCase())),
          Criteria.where(PlanExecutionSummaryKeys.modules).in(moduleName));
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      try {
        searchCriteria.orOperator(where(PlanExecutionSummaryKeys.pipelineIdentifier)
                                      .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.name)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.tags + "." + NGTagKeys.key)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(PlanExecutionSummaryKeys.tags + "." + NGTagKeys.value)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }

    Criteria gitCriteria = new Criteria();
    if (gitSyncBranchContext != null) {
      Criteria gitCriteriaDeprecated =
          Criteria.where(PlanExecutionSummaryKeys.gitSyncBranchContext).is(gitSyncBranchContext);

      EntityGitDetails entityGitDetails = pmsGitSyncHelper.getEntityGitDetailsFromBytes(gitSyncBranchContext);
      Criteria gitCriteriaNew = Criteria
                                    .where(PlanExecutionSummaryKeys.entityGitDetails + "."
                                        + "branch")
                                    .is(entityGitDetails.getBranch());
      if (entityGitDetails.getRepoIdentifier() != null
          && !entityGitDetails.getRepoIdentifier().equals(GitAwareEntityHelper.DEFAULT)) {
        gitCriteriaNew
            .and(PlanExecutionSummaryKeys.entityGitDetails + "."
                + "repoIdentifier")
            .is(entityGitDetails.getRepoIdentifier());
      }
      gitCriteria.orOperator(gitCriteriaDeprecated, gitCriteriaNew);
    }

    criteria.andOperator(filterCriteria, moduleCriteria, searchCriteria, gitCriteria);

    return criteria;
  }

  private void populatePipelineFilterUsingIdentifier(Criteria criteria, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String filterIdentifier) {
    FilterDTO pipelineFilterDTO = this.filterService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINEEXECUTION);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    }
    this.populatePipelineFilter(
        criteria, (PipelineExecutionFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties());
  }

  private void populatePipelineFilter(Criteria criteria, @NotNull PipelineExecutionFilterPropertiesDTO piplineFilter) {
    if (EmptyPredicate.isNotEmpty(piplineFilter.getPipelineName())) {
      criteria.orOperator(
          where(PlanExecutionSummaryKeys.name)
              .regex(piplineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PlanExecutionSummaryKeys.pipelineIdentifier)
              .regex(piplineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    if (piplineFilter.getTimeRange() != null) {
      TimeRange timeRange = piplineFilter.getTimeRange();
      // Apply filter to criteria if StartTime and EndTime both are not null.
      if (timeRange.getStartTime() != null && timeRange.getEndTime() != null) {
        criteria.and(PlanExecutionSummaryKeys.createdAt).gte(timeRange.getStartTime()).lte(timeRange.getEndTime());

      } else if ((timeRange.getStartTime() != null && timeRange.getEndTime() == null)
          || (timeRange.getStartTime() == null && timeRange.getEndTime() != null)) {
        // If any one of StartTime and EndTime is null. Throw exception.
        throw new InvalidRequestException(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");
      }
      // Ignore TimeRange filter if StartTime and EndTime both are null.
    }

    if (EmptyPredicate.isNotEmpty(piplineFilter.getPipelineTags())) {
      addPipelineTagsCriteria(criteria, piplineFilter.getPipelineTags());
    }
    if (EmptyPredicate.isNotEmpty(piplineFilter.getStatus())) {
      criteria.and(PlanExecutionSummaryKeys.status).in(piplineFilter.getStatus());
    }
    if (piplineFilter.getModuleProperties() != null) {
      ModuleInfoFilterUtils.processNode(
          JsonUtils.readTree(piplineFilter.getModuleProperties().toJson()), "moduleInfo", criteria);
    }
  }

  private void addPipelineTagsCriteria(Criteria criteria, List<NGTag> pipelineTags) {
    List<String> tags = new ArrayList<>();
    pipelineTags.forEach(o -> {
      tags.add(o.getKey());
      tags.add(o.getValue());
    });
    Criteria tagsCriteria = new Criteria();
    tagsCriteria.orOperator(where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.tags + "."
                                + "key")
                                .in(tags),
        where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.tags + "."
            + "value")
            .in(tags));
    criteria.andOperator(tagsCriteria);
  }

  @Override
  public InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions) {
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
        pmsExecutionSummaryRespository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
                accountId, orgId, projectId, planExecutionId, !pipelineDeleted);
    if (pipelineExecutionSummaryEntityOptional.isPresent()) {
      PipelineExecutionSummaryEntity executionSummaryEntity = pipelineExecutionSummaryEntityOptional.get();
      String latestTemplate = validateAndMergeHelper.getPipelineTemplate(
          accountId, orgId, projectId, executionSummaryEntity.getPipelineIdentifier(), null);
      String yaml = executionSummaryEntity.getInputSetYaml();
      String template = executionSummaryEntity.getPipelineTemplate();
      if (resolveExpressions && EmptyPredicate.isNotEmpty(yaml)) {
        yaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(yaml, planExecutionId);
      }
      if (EmptyPredicate.isEmpty(template) && EmptyPredicate.isNotEmpty(yaml)) {
        EntityGitDetails entityGitDetails =
            pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
        if (entityGitDetails != null) {
          template = validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId,
              executionSummaryEntity.getPipelineIdentifier(), entityGitDetails.getBranch(),
              entityGitDetails.getRepoIdentifier(), null);
        } else {
          template = latestTemplate;
        }
      }
      StagesExecutionMetadata stagesExecutionMetadata = executionSummaryEntity.getStagesExecutionMetadata();
      return InputSetYamlWithTemplateDTO.builder()
          .inputSetTemplateYaml(template)
          .inputSetYaml(yaml)
          .latestTemplateYaml(latestTemplate)
          .expressionValues(stagesExecutionMetadata != null ? stagesExecutionMetadata.getExpressionValues() : null)
          .build();
    }
    throw new InvalidRequestException(
        "Invalid request : Input Set did not exist or pipeline execution has been deleted");
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted) {
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
        pmsExecutionSummaryRespository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
                accountId, orgId, projectId, planExecutionId, !pipelineDeleted);
    if (pipelineExecutionSummaryEntityOptional.isPresent()) {
      return pipelineExecutionSummaryEntityOptional.get();
    }
    throw new EntityNotFoundException(
        "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntity(
      String accountId, String orgId, String projectId, String planExecutionId) {
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
        pmsExecutionSummaryRespository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
            accountId, orgId, projectId, planExecutionId);
    if (pipelineExecutionSummaryEntityOptional.isPresent()) {
      return pipelineExecutionSummaryEntityOptional.get();
    }
    throw new EntityNotFoundException(
        "Plan Execution Summary does not exist or has been deleted for planExecutionId: " + planExecutionId);
  }

  @Override
  public Page<PipelineExecutionSummaryEntity> getPipelineExecutionSummaryEntity(Criteria criteria, Pageable pageable) {
    return pmsExecutionSummaryRespository.findAll(criteria, pageable);
  }

  public PipelineExecutionSummaryEntity findFirst(Criteria criteria) {
    return pmsExecutionSummaryRespository.findFirst(criteria);
  }

  @Override
  public OrchestrationGraphDTO getOrchestrationGraph(
      String stageNodeId, String planExecutionId, String stageNodeExecutionId) {
    if (EmptyPredicate.isEmpty(stageNodeId)) {
      return graphGenerationService.generateOrchestrationGraphV2(planExecutionId);
    }
    return graphGenerationService.generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
        stageNodeId, planExecutionId, stageNodeExecutionId);
  }
  @Override
  public InterruptDTO registerInterrupt(
      PlanExecutionInterruptType executionInterruptType, String planExecutionId, String nodeExecutionId) {
    final Principal principal = SecurityContextBuilder.getPrincipal();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder()
                                                  .setType(principal.getType().toString())
                                                  .setIdentifier(principal.getName())
                                                  .build())
                             .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                             .build())
            .build();
    return registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId, interruptConfig);
  }

  @Override
  public InterruptDTO registerInterrupt(PlanExecutionInterruptType executionInterruptType, String planExecutionId,
      String nodeExecutionId, InterruptConfig interruptConfig) {
    InterruptPackage interruptPackage = InterruptPackage.builder()
                                            .interruptType(executionInterruptType.getExecutionInterruptType())
                                            .planExecutionId(planExecutionId)
                                            .nodeExecutionId(nodeExecutionId)
                                            .interruptConfig(interruptConfig)
                                            .metadata(getMetadata(executionInterruptType))
                                            .build();
    Interrupt interrupt = orchestrationService.registerInterrupt(interruptPackage);
    return InterruptDTO.builder()
        .id(interrupt.getUuid())
        .planExecutionId(interrupt.getPlanExecutionId())
        .type(executionInterruptType)
        .build();
  }

  private Map<String, String> getMetadata(PlanExecutionInterruptType planExecutionInterruptType) {
    if (planExecutionInterruptType == PlanExecutionInterruptType.STAGEROLLBACK
        || planExecutionInterruptType == PlanExecutionInterruptType.STEPGROUPROLLBACK) {
      return Collections.singletonMap("ROLLBACK", planExecutionInterruptType.getDisplayName());
    }
    return Collections.emptyMap();
  }

  @Override
  public void deleteExecutionsOnPipelineDeletion(PipelineEntity pipelineEntity) {
    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId)
        .is(pipelineEntity.getAccountId())
        .and(PlanExecutionSummaryKeys.orgIdentifier)
        .is(pipelineEntity.getOrgIdentifier())
        .and(PlanExecutionSummaryKeys.projectIdentifier)
        .is(pipelineEntity.getProjectIdentifier())
        .and(PlanExecutionSummaryKeys.pipelineIdentifier)
        .is(pipelineEntity.getIdentifier());
    Query query = new Query(criteria);

    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.pipelineDeleted, Boolean.TRUE);

    UpdateResult updateResult = pmsExecutionSummaryRespository.deleteAllExecutionsWhenPipelineDeleted(query, update);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Executions for Pipeline [%s] under Project[%s], Organization [%s] couldn't be deleted.",
          pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier()));
    }
  }

  @Override
  public long getCountOfExecutions(Criteria criteria) {
    Pageable pageRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, PlanExecutionSummaryKeys.startTs));
    return pmsExecutionSummaryRespository.findAll(criteria, pageRequest).getTotalElements();
  }
}
