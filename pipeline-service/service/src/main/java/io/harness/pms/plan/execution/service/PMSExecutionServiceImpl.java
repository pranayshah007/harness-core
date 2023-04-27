/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.merger.helpers.InputSetMergeHelper.mergeInputSetIntoPipelineForGivenStages;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipeline;
import static io.harness.pms.merger.helpers.InputSetTemplateHelper.createTemplateFromPipelineForGivenStages;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
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
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.ModuleInfoOperators;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.security.PrincipalHelper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.ProtoUtils;
import io.harness.service.GraphGenerationService;

import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import javax.validation.constraints.NotNull;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSExecutionServiceImpl implements PMSExecutionService {
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  @Inject private GraphGenerationService graphGenerationService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private FilterService filterService;
  @Inject private TriggeredByHelper triggeredByHelper;
  @Inject private YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Inject private ValidateAndMergeHelper validateAndMergeHelper;
  @Inject private PmsGitSyncHelper pmsGitSyncHelper;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private GitSyncSdkService gitSyncSdkService;

  private static final int MAX_LIST_SIZE = 1000;

  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";

  private static final String BRANCH_LIST_SIZE_EXCEPTION = "The size of unique branches list is greater than [%d]";

  private static final String PARENT_PATH_MODULE_INFO = "moduleInfo";

  @Override
  public Criteria formCriteria(String accountId, String orgId, String projectId, String pipelineIdentifier,
      String filterIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String moduleName,
      String searchTerm, List<ExecutionStatus> statusList, boolean myDeployments, boolean pipelineDeleted,
      boolean isLatest) {
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
    // To show non-child execution. First or condition is added for older execution which do not have parentStageInfo
    if (EmptyPredicate.isEmpty(pipelineIdentifier)) {
      criteria.and(PlanExecutionSummaryKeys.isChildPipeline).in(null, false);
    }
    if (EmptyPredicate.isNotEmpty(statusList)) {
      criteria.and(PlanExecutionSummaryKeys.status).in(statusList);
    }

    criteria.and(PlanExecutionSummaryKeys.isLatestExecution).ne(!isLatest);

    Criteria filterCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populatePipelineFilterUsingIdentifierANDOperator(filterCriteria, accountId, orgId, projectId, filterIdentifier);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      populatePipelineFilterANDOperator(filterCriteria, filterProperties);
    }

    if (myDeployments) {
      criteria.and(PlanExecutionSummaryKeys.triggerType)
          .is(MANUAL)
          .and(PlanExecutionSummaryKeys.triggeredBy)
          .is(triggeredByHelper.getFromSecurityContext());
    }

    Criteria moduleCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(moduleName)) {
      // Pipelines having only pipeline stages like custom and approval
      moduleCriteria.orOperator(Criteria.where(PlanExecutionSummaryKeys.modules)
                                    .is(Collections.singletonList(ModuleType.PMS.name().toLowerCase())),
          // Pipelines for checking in actual module
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
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (gitEntityInfo != null) {
      //      Adding the branch filter if the branch is not null or default
      if (EmptyPredicate.isNotEmpty(gitEntityInfo.getBranch())
          && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getBranch())) {
        gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsBranch).is(gitEntityInfo.getBranch());
      }
      if (gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId)) {
        //     Adding the repoIdentifier for the old git sync flow
        if (EmptyPredicate.isNotEmpty(gitEntityInfo.getYamlGitConfigId())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getYamlGitConfigId())) {
          gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoIdentifier)
              .is(gitEntityInfo.getYamlGitConfigId());
        }
      } else {
        //     Adding the repoName for the new git experience flow
        if (EmptyPredicate.isNotEmpty(gitEntityInfo.getRepoName())
            && !GitAwareEntityHelper.DEFAULT.equals(gitEntityInfo.getRepoName())) {
          gitCriteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is(gitEntityInfo.getRepoName());
        }
      }
    }

    List<Criteria> criteriaList = new LinkedList<>();
    if (!gitCriteria.equals(new Criteria())) {
      criteriaList.add(gitCriteria);
    }
    if (!filterCriteria.equals(new Criteria())) {
      criteriaList.add(filterCriteria);
    }
    if (!moduleCriteria.equals(new Criteria())) {
      criteriaList.add(moduleCriteria);
    }
    if (!searchCriteria.equals(new Criteria())) {
      criteriaList.add(searchCriteria);
    }

    if (!criteriaList.isEmpty()) {
      criteria.andOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
    }
    return criteria;
  }

  @Override
  public Criteria formCriteriaForRepoAndBranchListing(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repoName) {
    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId).is(accountIdentifier);
    criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(projectIdentifier);

    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      criteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).is(pipelineIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(repoName)) {
      criteria.and(PlanExecutionSummaryKeys.entityGitDetailsRepoName).is(repoName);
    }
    return criteria;
  }

  @Override
  public PMSPipelineListRepoResponse getListOfRepo(Criteria criteria) {
    List<String> uniqueRepos = pmsExecutionSummaryRespository.findListOfUniqueRepositories(criteria);
    CollectionUtils.filter(uniqueRepos, PredicateUtils.notNullPredicate());
    if (uniqueRepos.size() > MAX_LIST_SIZE) {
      log.error(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return PMSPipelineListRepoResponse.builder().repositories(uniqueRepos).build();
  }

  @Override
  public PMSPipelineListBranchesResponse getListOfBranches(Criteria criteria) {
    List<String> uniqueBranches = pmsExecutionSummaryRespository.findListOfUniqueBranches(criteria);
    CollectionUtils.filter(uniqueBranches, PredicateUtils.notNullPredicate());
    if (uniqueBranches.size() > MAX_LIST_SIZE) {
      log.error(String.format(BRANCH_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(BRANCH_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return PMSPipelineListBranchesResponse.builder().branches(uniqueBranches).build();
  }

  @Override
  public Criteria formCriteriaOROperatorOnModules(String accountId, String orgId, String projectId,
      List<String> pipelineIdentifier, PipelineExecutionFilterPropertiesDTO filterProperties, String filterIdentifier) {
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
    Criteria pipelineCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      pipelineCriteria.and(PlanExecutionSummaryKeys.pipelineIdentifier).in(pipelineIdentifier);
    }

    Criteria filterCriteria = new Criteria();
    List<Criteria> filterCriteriaList = new LinkedList<>();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populatePipelineFilterUsingIdentifierOROperator(
          filterCriteria, accountId, orgId, projectId, filterIdentifier, filterCriteriaList);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      populatePipelineFilterOROperator(filterCriteria, filterProperties, filterCriteriaList);
    }

    List<Criteria> criteriaList = new LinkedList<>();
    if (!pipelineCriteria.equals(new Criteria())) {
      criteriaList.add(pipelineCriteria);
    }

    if (!filterCriteria.equals(new Criteria())) {
      criteria.andOperator(filterCriteria);
    }

    if (!filterCriteriaList.isEmpty()) {
      criteriaList.addAll(filterCriteriaList);
    }

    if (criteriaList.isEmpty()) {
      return criteria;
    }

    return criteria.orOperator(criteriaList.toArray(new Criteria[criteriaList.size()]));
  }

  private void populatePipelineFilterUsingIdentifierANDOperator(Criteria criteria, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier) {
    populatePipelineFilterUsingIdentifierParametrisedOperatorOnModules(
        criteria, accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, ModuleInfoOperators.AND, null);
  }

  private void populatePipelineFilterUsingIdentifierOROperator(Criteria criteria, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier, List<Criteria> criteriaList) {
    populatePipelineFilterUsingIdentifierParametrisedOperatorOnModules(criteria, accountIdentifier, orgIdentifier,
        projectIdentifier, filterIdentifier, ModuleInfoOperators.OR, criteriaList);
  }

  private void populatePipelineFilterUsingIdentifierParametrisedOperatorOnModules(Criteria criteria,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String filterIdentifier,
      ModuleInfoOperators operatorOnModules, List<Criteria> criteriaList) {
    FilterDTO pipelineFilterDTO = this.filterService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINEEXECUTION);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    }
    if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
      this.populatePipelineFilterOROperator(
          criteria, (PipelineExecutionFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties(), criteriaList);
    } else {
      this.populatePipelineFilterANDOperator(
          criteria, (PipelineExecutionFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties());
    }
  }

  // This is the function created and parametrized on operator to apply on modules in filterProperties to obtain the
  // criteria.
  private void populatePipelineFilterParametrisedOperatorOnModules(Criteria criteria,
      @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, ModuleInfoOperators operatorOnModules,
      List<Criteria> criteriaList) {
    if (pipelineFilter.getTimeRange() != null) {
      TimeRange timeRange = pipelineFilter.getTimeRange();
      // Apply filter to criteria if StartTime and EndTime both are not null.
      if (timeRange.getStartTime() != null && timeRange.getEndTime() != null) {
        criteria.and(PlanExecutionSummaryKeys.startTs).gte(timeRange.getStartTime()).lte(timeRange.getEndTime());

      } else if ((timeRange.getStartTime() != null && timeRange.getEndTime() == null)
          || (timeRange.getStartTime() == null && timeRange.getEndTime() != null)) {
        // If any one of StartTime and EndTime is null. Throw exception.
        throw new InvalidRequestException(
            "startTime or endTime is not provided in TimeRange filter. Either add the missing field or remove the timeRange filter.");
      }
      // Ignore TimeRange filter if StartTime and EndTime both are null.
    }

    if (EmptyPredicate.isNotEmpty(pipelineFilter.getStatus())) {
      criteria.and(PlanExecutionSummaryKeys.status).in(pipelineFilter.getStatus());
    }

    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineName())) {
      criteria.orOperator(
          where(PlanExecutionSummaryKeys.pipelineIdentifier)
              .regex(pipelineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PlanExecutionSummaryKeys.name)
              .regex(pipelineFilter.getPipelineName(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineTags())) {
      addPipelineTagsCriteria(criteria, pipelineFilter.getPipelineTags());
    }

    if (pipelineFilter.getModuleProperties() != null) {
      if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
        ModuleInfoFilterUtils.processNodeOROperator(
            JsonUtils.readTree(pipelineFilter.getModuleProperties().toJson()), PARENT_PATH_MODULE_INFO, criteriaList);
      } else {
        ModuleInfoFilterUtils.processNode(
            JsonUtils.readTree(pipelineFilter.getModuleProperties().toJson()), PARENT_PATH_MODULE_INFO, criteria);
      }
    }
  }

  private void populatePipelineFilterANDOperator(
      Criteria criteria, @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter) {
    populatePipelineFilterParametrisedOperatorOnModules(criteria, pipelineFilter, ModuleInfoOperators.AND, null);
  }

  private void populatePipelineFilterOROperator(
      Criteria criteria, @NotNull PipelineExecutionFilterPropertiesDTO pipelineFilter, List<Criteria> criteriaList) {
    populatePipelineFilterParametrisedOperatorOnModules(criteria, pipelineFilter, ModuleInfoOperators.OR, criteriaList);
  }

  private void addPipelineTagsCriteria(Criteria criteria, List<NGTag> pipelineTags) {
    List<String> tags = new ArrayList<>();
    pipelineTags.forEach(o -> {
      tags.add(o.getKey());
      tags.add(o.getValue());
    });
    Criteria tagsCriteria = new Criteria();
    tagsCriteria.orOperator(
        where(PlanExecutionSummaryKeys.tagsKey).in(tags), where(PlanExecutionSummaryKeys.tagsValue).in(tags));
    criteria.andOperator(tagsCriteria);
  }

  @Override
  public InputSetYamlWithTemplateDTO getInputSetYamlWithTemplate(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, boolean resolveExpressions,
      ResolveInputYamlType resolveExpressionsType) {
    // ToDo: Use Mongo Projections
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
        pmsExecutionSummaryRespository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
                accountId, orgId, projectId, planExecutionId, !pipelineDeleted);
    if (pipelineExecutionSummaryEntityOptional.isPresent()) {
      PipelineExecutionSummaryEntity executionSummaryEntity = pipelineExecutionSummaryEntityOptional.get();

      // InputSet yaml used during execution
      String yaml = executionSummaryEntity.getInputSetYaml();
      if (resolveExpressions && EmptyPredicate.isNotEmpty(yaml)) {
        yaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(
            yaml, planExecutionId, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
      }

      if (!resolveExpressions && EmptyPredicate.isNotEmpty(yaml)) {
        if (resolveExpressionsType.equals(ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS)) {
          yaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(
              yaml, planExecutionId, ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS);
        } else if (resolveExpressionsType.equals(ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS)) {
          yaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(
              yaml, planExecutionId, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
        }
      }
      StagesExecutionMetadata stagesExecutionMetadata = executionSummaryEntity.getStagesExecutionMetadata();
      return InputSetYamlWithTemplateDTO
          .builder()
          // template for pipelineYaml at the time of execution.
          .inputSetTemplateYaml(executionSummaryEntity.getPipelineTemplate())
          .inputSetYaml(yaml)
          .expressionValues(stagesExecutionMetadata != null ? stagesExecutionMetadata.getExpressionValues() : null)
          .build();
    }
    throw new InvalidRequestException(
        "Invalid request : Input Set did not exist or pipeline execution has been deleted");
  }

  @Override
  public String getInputSetYamlForRerun(
      String accountId, String orgId, String projectId, String planExecutionId, boolean pipelineDeleted) {
    // ToDo: Use Mongo Projections
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityOptional =
        pmsExecutionSummaryRespository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
                accountId, orgId, projectId, planExecutionId, !pipelineDeleted);
    if (pipelineExecutionSummaryEntityOptional.isPresent()) {
      PipelineExecutionSummaryEntity executionSummaryEntity = pipelineExecutionSummaryEntityOptional.get();

      // InputSet yaml used during execution
      return executionSummaryEntity.getInputSetYaml();
    }
    throw new InvalidRequestException(
        "Invalid request : pipeline execution with planExecutionId " + planExecutionId + " has been deleted");
  }

  private String getLatestTemplate(
      String accountId, String orgId, String projectId, PipelineExecutionSummaryEntity executionSummaryEntity) {
    EntityGitDetails entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    // latestTemplate is templateYaml for the pipeline in the current branch with the latest changes
    String latestTemplate;
    if (entityGitDetails != null) {
      // will come here if the pipeline was remote
      GitSyncBranchContext gitSyncBranchContext = GitSyncBranchContext.builder()
                                                      .gitBranchInfo(GitEntityInfo.builder()
                                                                         .branch(entityGitDetails.getBranch())
                                                                         .repoName(entityGitDetails.getRepoName())
                                                                         .build())
                                                      .build();
      try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
        latestTemplate = validateAndMergeHelper.getPipelineTemplate(accountId, orgId, projectId,
            executionSummaryEntity.getPipelineIdentifier(), entityGitDetails.getBranch(),
            entityGitDetails.getRepoIdentifier(), null);
      }
    } else {
      // will come here if the pipeline was INLINE
      latestTemplate = validateAndMergeHelper.getPipelineTemplate(
          accountId, orgId, projectId, executionSummaryEntity.getPipelineIdentifier(), null);
    }
    return latestTemplate;
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

  @Override
  public void sendGraphUpdateEvent(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    graphGenerationService.sendUpdateEventIfAny(pipelineExecutionSummaryEntity);
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
                                                  .setEmailId(PrincipalHelper.getEmail(principal))
                                                  .setUserId(PrincipalHelper.getUsername(principal))
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
    return pmsExecutionSummaryRespository.getCountOfExecutionSummary(criteria);
  }

  @Override
  public ExecutionDataResponseDTO getExecutionData(String planExecutionId) {
    Optional<PlanExecutionMetadata> planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(planExecutionId);

    if (!planExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(
          String.format("Execution with id [%s] is not present or deleted", planExecutionId));
    }
    String executionYaml = planExecutionMetadata.get().getYaml();

    return ExecutionDataResponseDTO.builder().executionYaml(executionYaml).executionId(planExecutionId).build();
  }

  public ExecutionMetaDataResponseDetailsDTO getExecutionDataDetails(String planExecutionId) {
    Optional<PlanExecutionMetadata> planExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(planExecutionId);

    if (!planExecutionMetadata.isPresent()) {
      throw new ResourceNotFoundException(
          String.format("Execution with id [%s] is not present or deleted", planExecutionId));
    }
    PlanExecutionMetadata metadata = planExecutionMetadata.get();
    return ExecutionMetaDataResponseDetailsDTO.builder()
        .executionYaml(metadata.getYaml())
        .planExecutionId(planExecutionId)
        .inputYaml(metadata.getInputSetYaml())
        .triggerPayload(metadata.getTriggerPayload())
        .build();
  }

  @Override
  public String mergeRuntimeInputIntoPipelineForRerun(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String pipelineBranch, String pipelineRepoID,
      List<String> stageIdentifiers) {
    String pipelineYaml = validateAndMergeHelper
                              .getPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
                                  pipelineBranch, pipelineRepoID, false, false)
                              .getYaml();
    String pipelineTemplate = EmptyPredicate.isEmpty(stageIdentifiers)
        ? createTemplateFromPipeline(pipelineYaml)
        : createTemplateFromPipelineForGivenStages(pipelineYaml, stageIdentifiers);
    if (EmptyPredicate.isEmpty(pipelineTemplate)) {
      return "";
    }
    String mergedRuntimeInputYaml =
        getInputSetYamlForRerun(accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    if (EmptyPredicate.isEmpty(stageIdentifiers)) {
      return InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineTemplate, mergedRuntimeInputYaml, false);
    }
    return mergeInputSetIntoPipelineForGivenStages(pipelineTemplate, mergedRuntimeInputYaml, false, stageIdentifiers);
  }
}
