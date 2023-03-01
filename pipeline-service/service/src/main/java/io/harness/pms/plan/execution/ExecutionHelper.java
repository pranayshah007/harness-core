/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;

import static java.lang.String.format;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.WingsException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.Builder;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
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
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.plan.creation.PlanCreatorMergeService;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.helpers.InputSetMergeHelperV1;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.stages.StagesExpressionExtractor;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.threading.Morpheus;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  PipelineGovernanceService pipelineGovernanceService;
  TriggeredByHelper triggeredByHelper;
  PlanExecutionService planExecutionService;
  PrincipalInfoHelper principalInfoHelper;
  PmsGitSyncHelper pmsGitSyncHelper;
  PMSYamlSchemaService pmsYamlSchemaService;
  PipelineRbacService pipelineRbacServiceImpl;
  RetryExecutionHelper retryExecutionHelper;
  PlanCreatorMergeService planCreatorMergeService;
  OrchestrationService orchestrationService;
  PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
  PlanService planService;
  PlanExecutionMetadataService planExecutionMetadataService;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineEnforcementService pipelineEnforcementService;
  PmsFeatureFlagHelper featureFlagService;
  PMSExecutionService pmsExecutionService;
  AccessControlClient accessControlClient;
  PipelineStageHelper pipelineStageHelper;
  NodeExecutionService nodeExecutionService;

  public PipelineEntity fetchPipelineEntity(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier) {
    Optional<PipelineEntity> pipelineEntityOptional =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
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

    ExecutionMetadata metadata = planExecutionService.getExecutionMetadataFromPlanExecution(originalExecutionId);
    ExecutionTriggerInfo originalTriggerInfo = metadata.getTriggerInfo();
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

  boolean validForDebug(PipelineEntity pipelineEntity) {
    List<String> modules = PipelinesApiUtils.getModules(pipelineEntity);

    return (modules != null) && (modules.contains("ci"));
  }
  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, String mergedRuntimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      boolean isDebug) {
    long start = System.currentTimeMillis();
    final String executionId = generateUuid();

    if (isDebug && !validForDebug(pipelineEntity)) {
      throw new InvalidRequestException(
          String.format("Debug executions are not allowed for pipeline [%s]", pipelineEntity.getIdentifier()));
    }
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                 pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(), executionId)) {
      String version = pipelineEntity.getHarnessVersion();
      String pipelineYaml;
      String pipelineYamlWithTemplateRef;
      boolean allowedStageExecution;
      List<NotificationRules> notificationRules = new ArrayList<>();
      switch (version) {
        case PipelineVersion.V1:
          allowedStageExecution = false;
          pipelineYaml =
              InputSetMergeHelperV1.mergeInputSetIntoPipelineYaml(mergedRuntimeInputYaml, pipelineEntity.getYaml());
          pipelineYamlWithTemplateRef = pipelineYaml;
          break;
        case PipelineVersion.V0:
          TemplateMergeResponseDTO templateMergeResponseDTO =
              getPipelineYamlAndValidateStaticallyReferredEntities(mergedRuntimeInputYaml, pipelineEntity);
          pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
          pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
          BasicPipeline basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
          allowedStageExecution = basicPipeline.isAllowStageExecutions();
          notificationRules = basicPipeline.getNotificationRules();
          break;
        default:
          throw new InvalidRequestException("version not supported");
      }

      StagesExecutionInfo stagesExecutionInfo;
      if (isNotEmpty(stagesToRun)) {
        if (!allowedStageExecution) {
          throw new InvalidRequestException(
              String.format("Stage executions are not allowed for pipeline [%s]", pipelineEntity.getIdentifier()));
        }

        StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, stagesToRun);
        pipelineYaml = StagesExpressionExtractor.replaceExpressions(pipelineYaml, expressionValues);
        stagesExecutionInfo = StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, expressionValues);
        pipelineYamlWithTemplateRef =
            InputSetMergeHelper.removeNonRequiredStages(pipelineYamlWithTemplateRef, stagesToRun);
      } else {
        stagesExecutionInfo = StagesExecutionInfo.builder()
                                  .isStagesExecution(false)
                                  .pipelineYamlToRun(pipelineYaml)
                                  .allowStagesExecution(allowedStageExecution)
                                  .build();
      }

      /*
    For schema validations, we don't want input set validators to be appended. For example, if some timeout field in
    the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for this field
    in pipelineYamlConfig will be 12h.allowedValues(12h, 1d) for validation during execution. However, this value will
    give an error in schema validation. That's why we need a value that doesn't have this validator appended.
     */
      // We don't have schema validation for V1 yaml as of now.
      if (PipelineVersion.V0.equals(version)) {
        String yamlForValidatingSchema;
        try {
          yamlForValidatingSchema =
              YamlUtils.getYamlWithoutInputs(new YamlConfig(stagesExecutionInfo.getPipelineYamlToRun()));
        } catch (Exception ex) {
          log.error("Exception occurred while removing inputs from pipeline yaml", ex);
          yamlForValidatingSchema = getPipelineYamlWithUnResolvedTemplates(mergedRuntimeInputYaml, pipelineEntity);
        }
        pmsYamlSchemaService.validateYamlSchema(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
            pipelineEntity.getProjectIdentifier(), yamlForValidatingSchema);
      }

      Builder planExecutionMetadataBuilder = obtainPlanExecutionMetadata(mergedRuntimeInputYaml, executionId,
          stagesExecutionInfo, originalExecutionId, retryExecutionParameters, notifyOnlyUser, version);
      if (stagesExecutionInfo.isStagesExecution()) {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity.getAccountId(),
            YamlUtils.extractPipelineField(planExecutionMetadataBuilder.build().getProcessedYaml()));
      } else {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);
      }
      String expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity.getAccountId(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYamlWithTemplateRef, true);
      planExecutionMetadataBuilder.expandedPipelineJson(expandedJson);
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataBuilder.build();

      boolean isRetry = retryExecutionParameters.isRetry();
      // RetryExecutionInfo
      RetryExecutionInfo retryExecutionInfo = buildRetryInfo(isRetry, originalExecutionId);
      ExecutionMetadata executionMetadata = buildExecutionMetadata(pipelineEntity.getIdentifier(), moduleType,
          triggerInfo, pipelineEntity, executionId, retryExecutionInfo, notificationRules, isDebug);
      return ExecArgs.builder().metadata(executionMetadata).planExecutionMetadata(planExecutionMetadata).build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to start execution for Pipeline with identifier [%s] in Project [%s] of Org [%s]. Error Message: %s",
              pipelineEntity.getIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier(),
              e.getMessage()),
          e);
      throw new InvalidRequestException("Failed to start execution for Pipeline.", e);
    } finally {
      log.info("[PMS_EXECUTE] Pipeline build execution args took time {}ms", System.currentTimeMillis() - start);
    }
  }

  private ExecutionMetadata buildExecutionMetadata(@NotNull String pipelineIdentifier, String moduleType,
      ExecutionTriggerInfo triggerInfo, PipelineEntity pipelineEntity, String executionId,
      RetryExecutionInfo retryExecutionInfo, List<NotificationRules> notificationRules, boolean isDebug) {
    ExecutionMetadata.Builder builder =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid(executionId)
            .setTriggerInfo(triggerInfo)
            .setModuleType(EmptyPredicate.isEmpty(moduleType) ? "" : moduleType)
            .setRunSequence(pipelineMetadataService.incrementRunSequence(pipelineEntity))
            .setPipelineIdentifier(pipelineIdentifier)
            .setRetryInfo(retryExecutionInfo)
            .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
            .setIsNotificationConfigured(isNotEmpty(notificationRules))
            .setHarnessVersion(pipelineEntity.getHarnessVersion())
            .setIsDebug(isDebug);
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(
        pipelineEntity, pipelineEntity.getStoreType(), pipelineEntity.getRepo(), pipelineEntity.getConnectorRef());
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

  public String getPipelineYamlWithUnResolvedTemplates(String mergedRuntimeInputYaml, PipelineEntity pipelineEntity) {
    YamlConfig pipelineYamlConfigForSchemaValidations;
    if (isEmpty(mergedRuntimeInputYaml)) {
      pipelineYamlConfigForSchemaValidations = new YamlConfig(pipelineEntity.getYaml());
    } else {
      YamlConfig pipelineEntityYamlConfig = new YamlConfig(pipelineEntity.getYaml());
      YamlConfig runtimeInputYamlConfig = new YamlConfig(mergedRuntimeInputYaml);
      pipelineYamlConfigForSchemaValidations = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
          pipelineEntityYamlConfig, runtimeInputYamlConfig, false, true);

      /*
      For schema validations, we don't want input set validators to be appended. For example, if some timeout field in
      the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for this field
      in pipelineYamlConfig will be 12h.allowedValues(12h, 1d) for validation during execution. However, this value will
      give an error in schema validation. That's why we need a value that doesn't have this validator appended.
       */
      pipelineYamlConfigForSchemaValidations =
          MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(pipelineEntityYamlConfig, runtimeInputYamlConfig, false);
    }
    pipelineYamlConfigForSchemaValidations = InputSetSanitizer.trimValues(pipelineYamlConfigForSchemaValidations);
    return pipelineYamlConfigForSchemaValidations.getYaml();
  }

  @VisibleForTesting
  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(
      String mergedRuntimeInputYaml, PipelineEntity pipelineEntity) {
    YamlConfig pipelineYamlConfig;

    long start = System.currentTimeMillis();
    if (isEmpty(mergedRuntimeInputYaml)) {
      pipelineYamlConfig = new YamlConfig(pipelineEntity.getYaml());
    } else {
      YamlConfig pipelineEntityYamlConfig = new YamlConfig(pipelineEntity.getYaml());
      YamlConfig runtimeInputYamlConfig = new YamlConfig(mergedRuntimeInputYaml);
      pipelineYamlConfig = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
          pipelineEntityYamlConfig, runtimeInputYamlConfig, true, true);
    }
    pipelineYamlConfig = InputSetSanitizer.trimValues(pipelineYamlConfig);

    String pipelineYaml = pipelineYamlConfig.getYaml();
    log.info("[PMS_EXECUTE] Pipeline input set merge total time took {}ms", System.currentTimeMillis() - start);

    String unresolvedPipelineYaml = pipelineYaml;
    String pipelineYamlWithTemplateRef = pipelineYaml;
    if (Boolean.TRUE.equals(TemplateRefHelper.hasTemplateRef(pipelineYamlConfig))) {
      TemplateMergeResponseDTO templateMergeResponseDTO =
          pipelineTemplateHelper.resolveTemplateRefsInPipelineAndAppendInputSetValidators(pipelineEntity.getAccountId(),
              pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYaml, true,
              featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE),
              BOOLEAN_FALSE_VALUE);
      pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      pipelineYamlWithTemplateRef =
          EmptyPredicate.isEmpty(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef())
          ? pipelineYaml
          : templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
    }
    if (pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE) {
      // For REMOTE Pipelines, entity setup usage framework cannot be relied upon. That is because the setup usages can
      // be outdated wrt the YAML we find on Git during execution. This means the fail fast approach that we have for
      // RBAC checks can't be provided for remote pipelines
      pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(pipelineEntity.getAccountId(),
          pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
          unresolvedPipelineYaml);
    }
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(pipelineYaml)
        .mergedPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .build();
  }

  private PlanExecutionMetadata.Builder obtainPlanExecutionMetadata(String mergedRuntimeInputYaml, String executionId,
      StagesExecutionInfo stagesExecutionInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, String version) {
    long start = System.currentTimeMillis();
    boolean isRetry = retryExecutionParameters.isRetry();
    String pipelineYaml = stagesExecutionInfo.getPipelineYamlToRun();
    PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
        PlanExecutionMetadata.builder()
            .planExecutionId(executionId)
            .inputSetYaml(mergedRuntimeInputYaml)
            .yaml(pipelineYaml)
            .stagesExecutionMetadata(stagesExecutionInfo.toStagesExecutionMetadata())
            .allowStagesExecution(stagesExecutionInfo.isAllowStagesExecution())
            .notifyOnlyUser(notifyOnlyUser);
    String currentProcessedYaml;
    try {
      switch (version) {
        case PipelineVersion.V1:
          currentProcessedYaml = YamlUtils.injectUuidWithType(pipelineYaml, YAMLFieldNameConstants.PIPELINE);
          break;
        case PipelineVersion.V0:
          currentProcessedYaml = YamlUtils.injectUuid(pipelineYaml);
          break;
        default:
          throw new IllegalStateException("version not supported");
      }

    } catch (IOException e) {
      log.error("Unable to inject Uuids into pipeline Yaml. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Unable to inject Uuids into pipeline Yaml", e);
    }
    if (isRetry) {
      try {
        currentProcessedYaml =
            retryExecutionHelper.retryProcessedYaml(retryExecutionParameters.getPreviousProcessedYaml(),
                currentProcessedYaml, retryExecutionParameters.getRetryStagesIdentifier(),
                retryExecutionParameters.getIdentifierOfSkipStages(), version);
      } catch (IOException e) {
        log.error("Unable to get processed yaml. Previous Processed yaml:\n"
                + retryExecutionParameters.getPreviousProcessedYaml(),
            e);
        throw new InvalidYamlException("Unable to get processed yaml for retry.", e);
      }
    }
    planExecutionMetadataBuilder.processedYaml(currentProcessedYaml);

    if (isNotEmpty(originalExecutionId)) {
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
      List<String> identifierOfSkipStages, String previousExecutionId, List<String> retryStagesIdentifier) {
    long startTs = System.currentTimeMillis();
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(executionMetadata, accountId, orgIdentifier, projectIdentifier)) {
      PlanCreationBlobResponse resp;
      try {
        String version = executionMetadata.getHarnessVersion();
        resp = planCreatorMergeService.createPlanVersioned(
            accountId, orgIdentifier, projectIdentifier, version, executionMetadata, planExecutionMetadata);
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
        Plan newPlan = retryExecutionHelper.transformPlan(
            plan, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier);
        return orchestrationService.startExecution(newPlan, abstractions, executionMetadata, planExecutionMetadata);
      }
      return orchestrationService.startExecution(plan, abstractions, executionMetadata, planExecutionMetadata);
    }
  }

  public PlanExecution startExecutionV2(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadata planExecutionMetadata, boolean isRetry,
      List<String> identifierOfSkipStages, String previousExecutionId, List<String> retryStagesIdentifier) {
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
      Plan newPlan =
          retryExecutionHelper.transformPlan(plan, identifierOfSkipStages, previousExecutionId, retryStagesIdentifier);
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

  public PipelineExecutionDetailDTO getResponseDTO(String stageNodeId, String stageNodeExecutionId,
      String childStageNodeId, Boolean renderFullBottomGraph, PipelineExecutionSummaryEntity executionSummaryEntity,
      EntityGitDetails entityGitDetails) {
    String accountId = executionSummaryEntity.getAccountId();
    String orgId = executionSummaryEntity.getOrgIdentifier();
    String projectId = executionSummaryEntity.getProjectIdentifier();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    // Checking if the stage is of type Pipeline Stage, then return the child graph along with top graph of parent
    // pipeline
    if (pipelineStageHelper.validateChildGraphToGenerate(executionSummaryEntity.getLayoutNodeMap(), stageNodeId)) {
      NodeExecution nodeExecution = getNodeExecution(stageNodeId, planExecutionId);
      if (nodeExecution != null && isNotEmpty(nodeExecution.getExecutableResponses())) {
        // TODO: check with @sahilHindwani whether this update is required or not.
        pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
        return pipelineStageHelper.getResponseDTOWithChildGraph(
            accountId, childStageNodeId, executionSummaryEntity, entityGitDetails, nodeExecution, stageNodeExecutionId);
      }
    }

    if (EmptyPredicate.isEmpty(stageNodeId) && (renderFullBottomGraph == null || !renderFullBottomGraph)) {
      pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
      return PipelineExecutionDetailDTO.builder()
          .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
          .build();
    }

    return PipelineExecutionDetailDTO.builder()
        .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails))
        .executionGraph(ExecutionGraphMapper.toExecutionGraph(
            pmsExecutionService.getOrchestrationGraph(stageNodeId, planExecutionId, stageNodeExecutionId),
            executionSummaryEntity))
        .build();
  }

  private NodeExecution getNodeExecution(String stageNodeId, String planExecutionId) {
    try {
      return nodeExecutionService.getByPlanNodeUuid(stageNodeId, planExecutionId);
    } catch (InvalidRequestException ex) {
      log.info("NodeExecution is null for plan node: {} ", stageNodeId);
    }
    return null;
  }
}
