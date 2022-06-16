/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetErrorsHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.creation.PlanCreatorMergeService;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.stages.StagesExpressionExtractor;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;
import io.harness.threading.Morpheus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ExecutionHelper {
  PMSPipelineService pmsPipelineService;
  PipelineMetadataService pipelineMetadataService;
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  TriggeredByHelper triggeredByHelper;
  PlanExecutionService planExecutionService;
  PrincipalInfoHelper principalInfoHelper;
  PmsGitSyncHelper pmsGitSyncHelper;
  PMSYamlSchemaService pmsYamlSchemaService;
  PipelineRbacService pipelineRbacServiceImpl;
  RetryExecutionHelper retryExecutionHelper;
  PlanCreatorMergeService planCreatorMergeService;
  OrchestrationService orchestrationService;
  PmsExecutionSummaryRespository pmsExecutionSummaryRespository;
  PlanService planService;
  PlanExecutionMetadataService planExecutionMetadataService;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineEnforcementService pipelineEnforcementService;
  PmsFeatureFlagHelper featureFlagService;

  public PipelineEntity fetchPipelineEntity(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntityOptional =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);
    if (!pipelineEntityOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return pipelineEntityOptional.get();
  }

  public ExecutionTriggerInfo buildTriggerInfo(String originalExecutionId) {
    ExecutionTriggerInfo.Builder triggerInfoBuilder =
        ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(
            triggeredByHelper.getFromSecurityContext());

    if (originalExecutionId == null) {
      return triggerInfoBuilder.setIsRerun(false).build();
    }

    PlanExecution originalPlanExecution = planExecutionService.get(originalExecutionId);
    ExecutionTriggerInfo originalTriggerInfo = originalPlanExecution.getMetadata().getTriggerInfo();
    RerunInfo.Builder rerunInfoBuilder = RerunInfo.newBuilder()
                                             .setPrevExecutionId(originalExecutionId)
                                             .setPrevTriggerType(originalTriggerInfo.getTriggerType());
    if (originalTriggerInfo.getIsRerun()) {
      return triggerInfoBuilder.setIsRerun(true)
          .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalTriggerInfo.getRerunInfo().getRootExecutionId())
                            .setRootTriggerType(originalTriggerInfo.getRerunInfo().getRootTriggerType())
                            .build())
          .build();
    }

    return triggerInfoBuilder.setIsRerun(true)
        .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalExecutionId)
                          .setRootTriggerType(originalTriggerInfo.getTriggerType())
                          .build())
        .build();
  }

  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, String mergedRuntimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters) {
    long start = System.currentTimeMillis();
    final String executionId = generateUuid();
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                 pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), executionId)) {
      boolean isRetry = retryExecutionParameters.isRetry();
      // RetryExecutionInfo
      RetryExecutionInfo retryExecutionInfo = buildRetryInfo(isRetry, originalExecutionId);
      TemplateMergeResponseDTO templateMergeResponseDTO =
          getPipelineYamlAndValidate(mergedRuntimeInputYaml, pipelineEntity);
      String pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      String pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();

      BasicPipeline basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
      StagesExecutionInfo stagesExecutionInfo = StagesExecutionInfo.builder()
                                                    .isStagesExecution(false)
                                                    .pipelineYamlToRun(pipelineYaml)
                                                    .allowStagesExecution(basicPipeline.isAllowStageExecutions())
                                                    .build();
      if (EmptyPredicate.isNotEmpty(stagesToRun)) {
        if (!basicPipeline.isAllowStageExecutions()) {
          throw new InvalidRequestException(
              String.format("Stage executions are not allowed for pipeline [%s]", basicPipeline.getIdentifier()));
        }

        StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, stagesToRun);
        pipelineYaml = StagesExpressionExtractor.replaceExpressions(pipelineYaml, expressionValues);
        stagesExecutionInfo = StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, expressionValues);
        pipelineYamlWithTemplateRef =
            InputSetMergeHelper.removeNonRequiredStages(pipelineYamlWithTemplateRef, stagesToRun);
      }

      PlanExecutionMetadata.Builder planExecutionMetadataBuilder = obtainPlanExecutionMetadata(
          mergedRuntimeInputYaml, executionId, stagesExecutionInfo, originalExecutionId, retryExecutionParameters);
      if (stagesExecutionInfo.isStagesExecution()) {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity.getAccountId(),
            YamlUtils.extractPipelineField(planExecutionMetadataBuilder.build().getProcessedYaml()));
      } else {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);
      }
      String expandedJson = pmsPipelineServiceHelper.fetchExpandedPipelineJSONFromYaml(pipelineEntity.getAccountId(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYamlWithTemplateRef);
      planExecutionMetadataBuilder.expandedPipelineJson(expandedJson);
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataBuilder.build();
      basicPipeline = YamlUtils.read(planExecutionMetadata.getYaml(), BasicPipeline.class);
      ExecutionMetadata executionMetadata = buildExecutionMetadata(pipelineEntity.getIdentifier(), moduleType,
          triggerInfo, pipelineEntity, executionId, retryExecutionInfo, basicPipeline.getNotificationRules());
      return ExecArgs.builder().metadata(executionMetadata).planExecutionMetadata(planExecutionMetadata).build();
    } finally {
      log.info("[PMS_EXECUTE] Pipeline build execution args took time {}ms", System.currentTimeMillis() - start);
    }
  }

  private ExecutionMetadata buildExecutionMetadata(@NotNull String pipelineIdentifier, String moduleType,
      ExecutionTriggerInfo triggerInfo, PipelineEntity pipelineEntity, String executionId,
      RetryExecutionInfo retryExecutionInfo, List<NotificationRules> notificationRules) {
    ExecutionMetadata.Builder builder =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid(executionId)
            .setTriggerInfo(triggerInfo)
            .setModuleType(moduleType)
            .setRunSequence(pipelineMetadataService.incrementRunSequence(pipelineEntity))
            .setPipelineIdentifier(pipelineIdentifier)
            .setRetryInfo(retryExecutionInfo)
            .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
            .setIsNotificationConfigured(EmptyPredicate.isNotEmpty(notificationRules));
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(
        pipelineEntity, pipelineEntity.getStoreType(), pipelineEntity.getRepo());
    if (gitSyncBranchContext != null) {
      builder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    PipelineStoreType pipelineStoreType = StoreTypeMapper.fromStoreType(pipelineEntity.getStoreType());
    if (pipelineStoreType != null) {
      builder.setPipelineStoreType(pipelineStoreType);
    }
    if (pipelineEntity.getConnectorRef() != null) {
      builder.setPipelineConnectorRef(pipelineEntity.getConnectorRef());
    }
    return builder.build();
  }

  @VisibleForTesting
  TemplateMergeResponseDTO getPipelineYamlAndValidate(String mergedRuntimeInputYaml, PipelineEntity pipelineEntity) {
    String pipelineYaml;
    long start = System.currentTimeMillis();
    if (isEmpty(mergedRuntimeInputYaml)) {
      pipelineYaml = pipelineEntity.getYaml();
    } else {
      Map<FQN, String> invalidFQNsInInputSet = InputSetErrorsHelper.getInvalidFQNsInInputSet(
          InputSetTemplateHelper.createTemplateFromPipeline(pipelineEntity.getYaml()), mergedRuntimeInputYaml);
      if (EmptyPredicate.isNotEmpty(invalidFQNsInInputSet)) {
        throw new InvalidRequestException("Some fields are not valid: "
            + invalidFQNsInInputSet.entrySet()
                  .stream()
                  .map(o -> o.getKey().getExpressionFqn() + ": " + o.getValue())
                  .collect(Collectors.toList())
                  .toString());
      }
      pipelineYaml =
          InputSetMergeHelper.mergeInputSetIntoPipeline(pipelineEntity.getYaml(), mergedRuntimeInputYaml, true);
    }
    log.info("[PMS_EXECUTE] Pipeline input set merge total time took {}ms", System.currentTimeMillis() - start);
    String pipelineYamlWithTemplateRef = pipelineYaml;
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      TemplateMergeResponseDTO templateMergeResponseDTO =
          pipelineTemplateHelper.resolveTemplateRefsInPipeline(pipelineEntity.getAccountId(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYaml, true,
              featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE));
      pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      pipelineYamlWithTemplateRef =
          EmptyPredicate.isEmpty(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef())
          ? pipelineYaml
          : templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
    }
    pipelineYaml = InputSetSanitizer.trimValues(pipelineYaml);
    pipelineYamlWithTemplateRef = InputSetSanitizer.trimValues(pipelineYamlWithTemplateRef);
    pmsYamlSchemaService.validateYamlSchema(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier(), pipelineYaml);
    if (pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE) {
      // For REMOTE Pipelines, entity setup usage framework cannot be relied upon. That is because the setup usages can
      // be outdated wrt the YAML we find on Git during execution. This means the fail fast approach that we have for
      // RBAC checks can't be provided for remote pipelines
      pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(pipelineEntity.getAccountId(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
          pipelineYaml);
    }
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(pipelineYaml)
        .mergedPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .build();
  }

  private PlanExecutionMetadata.Builder obtainPlanExecutionMetadata(String mergedRuntimeInputYaml, String executionId,
      StagesExecutionInfo stagesExecutionInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters) {
    long start = System.currentTimeMillis();
    boolean isRetry = retryExecutionParameters.isRetry();
    String pipelineYaml = stagesExecutionInfo.getPipelineYamlToRun();
    PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
        PlanExecutionMetadata.builder()
            .planExecutionId(executionId)
            .inputSetYaml(mergedRuntimeInputYaml)
            .yaml(pipelineYaml)
            .stagesExecutionMetadata(stagesExecutionInfo.toStagesExecutionMetadata())
            .allowStagesExecution(stagesExecutionInfo.isAllowStagesExecution());
    String currentProcessedYaml;
    try {
      currentProcessedYaml = YamlUtils.injectUuid(pipelineYaml);
    } catch (IOException e) {
      log.error("Unable to inject Uuids into pipeline Yaml. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Unable to inject Uuids into pipeline Yaml", e);
    }
    if (isRetry) {
      try {
        currentProcessedYaml = retryExecutionHelper.retryProcessedYaml(
            retryExecutionParameters.getPreviousProcessedYaml(), currentProcessedYaml,
            retryExecutionParameters.getRetryStagesIdentifier(), retryExecutionParameters.getIdentifierOfSkipStages());
      } catch (IOException e) {
        log.error("Unable to get processed yaml. Previous Processed yaml:\n"
                + retryExecutionParameters.getPreviousProcessedYaml(),
            e);
        throw new InvalidYamlException("Unable to get processed yaml for retry.", e);
      }
    }
    planExecutionMetadataBuilder.processedYaml(currentProcessedYaml);

    if (EmptyPredicate.isNotEmpty(originalExecutionId)) {
      planExecutionMetadataBuilder = populateTriggerDataForRerun(originalExecutionId, planExecutionMetadataBuilder);
    }
    log.info("[PMS_EXECUTE] PlanExecution Metadata creation took total time {}ms", System.currentTimeMillis() - start);
    return planExecutionMetadataBuilder;
  }

  private PlanExecutionMetadata.Builder populateTriggerDataForRerun(
      String originalExecutionId, PlanExecutionMetadata.Builder planExecutionMetadataBuilder) {
    Optional<PlanExecutionMetadata> prevMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(originalExecutionId);

    if (prevMetadataOptional.isPresent()) {
      PlanExecutionMetadata prevMetadata = prevMetadataOptional.get();
      return planExecutionMetadataBuilder.triggerPayload(prevMetadata.getTriggerPayload())
          .triggerJsonPayload(prevMetadata.getTriggerJsonPayload());
    } else {
      log.warn("No prev plan execution metadata found for plan execution id [" + originalExecutionId + "]");
    }
    return planExecutionMetadataBuilder;
  }

  public PlanExecution startExecution(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadata planExecutionMetadata, boolean isRetry,
      List<String> identifierOfSkipStages, String previousExecutionId) {
    long startTs = System.currentTimeMillis();
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(executionMetadata, accountId, orgIdentifier, projectIdentifier)) {
      PlanCreationBlobResponse resp;
      try {
        resp = planCreatorMergeService.createPlan(
            accountId, orgIdentifier, projectIdentifier, executionMetadata, planExecutionMetadata);
      } catch (IOException e) {
        log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
        throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
      }
      Plan plan = PlanExecutionUtils.extractPlan(resp);
      ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                      .put(SetupAbstractionKeys.accountId, accountId)
                                                      .put(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                                                      .put(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                                                      .build();
      long endTs = System.currentTimeMillis();
      log.info("[PMS_PLAN] Time taken to complete plan: {}ms ", endTs - startTs);
      if (isRetry) {
        Plan newPlan = retryExecutionHelper.transformPlan(plan, identifierOfSkipStages, previousExecutionId);
        return orchestrationService.startExecution(newPlan, abstractions, executionMetadata, planExecutionMetadata);
      }
      return orchestrationService.startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    }
  }

  public PlanExecution startExecutionV2(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadata planExecutionMetadata, boolean isRetry,
      List<String> identifierOfSkipStages, String previousExecutionId) {
    long startTs = System.currentTimeMillis();
    String planCreationId = generateUuid();
    try {
      planCreatorMergeService.createPlanV2(
          accountId, orgIdentifier, projectIdentifier, planCreationId, executionMetadata, planExecutionMetadata);
    } catch (IOException e) {
      log.error(
          "Could not extract Pipeline Field from the processed yaml:\n" + planExecutionMetadata.getProcessedYaml(), e);
      throw new InvalidYamlException("Could not extract Pipeline Field from the yaml.", e);
    }

    ImmutableMap<String, String> abstractions = ImmutableMap.<String, String>builder()
                                                    .put(SetupAbstractionKeys.accountId, accountId)
                                                    .put(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
                                                    .put(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
                                                    .build();
    while (!planService.fetchPlanOptional(planCreationId).isPresent()) {
      Morpheus.sleep(Duration.ofMillis(100));
    }
    long endTs = System.currentTimeMillis();
    log.info("Time taken to complete plan: {}", endTs - startTs);
    Plan plan = planService.fetchPlan(planCreationId);
    if (!plan.isValid()) {
      PmsExceptionUtils.checkAndThrowPlanCreatorException(ImmutableList.of(plan.getErrorResponse()));
      return PlanExecution.builder().build();
    }
    if (isRetry) {
      Plan newPlan = retryExecutionHelper.transformPlan(plan, identifierOfSkipStages, previousExecutionId);
      return orchestrationService.startExecutionV2(
          planCreationId, abstractions, executionMetadata, planExecutionMetadata);
    }
    return orchestrationService.startExecutionV2(
        planCreationId, abstractions, executionMetadata, planExecutionMetadata);
  }

  public RetryExecutionInfo buildRetryInfo(boolean isRetry, String originalExecutionId) {
    if (!isRetry || isEmpty(originalExecutionId)) {
      return RetryExecutionInfo.newBuilder().setIsRetry(false).build();
    }
    String rootRetryExecutionId = pmsExecutionSummaryRespository.fetchRootRetryExecutionId(originalExecutionId);
    return RetryExecutionInfo.newBuilder()
        .setIsRetry(true)
        .setParentRetryId(originalExecutionId)
        .setRootExecutionId(rootRetryExecutionId)
        .build();
  }
}
