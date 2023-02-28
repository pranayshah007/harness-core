/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.ngtriggers.Constants.EVENT_CORRELATION_ID;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.PR;
import static io.harness.ngtriggers.Constants.PUSH;
import static io.harness.ngtriggers.Constants.TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD_BRANCH;
import static io.harness.ngtriggers.Constants.TRIGGER_REF;
import static io.harness.ngtriggers.Constants.TRIGGER_REF_DELIMITER;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK;
import static io.harness.pms.contracts.plan.TriggerType.WEBHOOK_CUSTOM;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORTALL;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.TriggerException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionConstants;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.notification.bean.NotificationRules;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.TriggerIssuer;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.PipelineEnforcementService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.ExecutionHelper;
import io.harness.pms.plan.execution.StoreTypeMapper;
import io.harness.pms.plan.execution.helpers.InputSetMergeHelperV1;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.User;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.serializer.ProtoUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class TriggerExecutionHelper {
  private final PMSPipelineService pmsPipelineService;
  private final PipelineMetadataService pipelineMetadataService;
  private final PMSPipelineServiceHelper pmsPipelineServiceHelper;

  private final PipelineGovernanceService pipelineGovernanceService;
  private final PlanExecutionService planExecutionService;
  private final PMSExecutionService pmsExecutionService;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final PMSYamlSchemaService pmsYamlSchemaService;
  private final ExecutionHelper executionHelper;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PipelineEnforcementService pipelineEnforcementService;
  private final WebhookEventPayloadParser webhookEventPayloadParser;
  private final PipelineServiceClient pipelineServiceClient;
  private final PmsFeatureFlagHelper featureFlagService;

  public PlanExecution resolveRuntimeInputAndSubmitExecutionReques(
      TriggerDetails triggerDetails, TriggerPayload triggerPayload) {
    String executionTag = generateExecutionTagForEvent(triggerDetails, triggerPayload);
    TriggeredBy embeddedUser =
        generateTriggerdBy(executionTag, triggerDetails.getNgTriggerEntity(), triggerPayload, null);

    TriggerType triggerType = findTriggerType(triggerPayload);
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(triggerType).setTriggeredBy(embeddedUser).build();
    return createPlanExecution(triggerDetails, triggerPayload, null, executionTag, triggerInfo, null);
  }

  public PlanExecution resolveRuntimeInputAndSubmitExecutionRequest(TriggerDetails triggerDetails,
      TriggerPayload triggerPayload, TriggerWebhookEvent triggerWebhookEvent, String payload) {
    String executionTagForGitEvent = generateExecutionTagForEvent(triggerDetails, triggerPayload);
    TriggeredBy embeddedUser = generateTriggerdBy(
        executionTagForGitEvent, triggerDetails.getNgTriggerEntity(), triggerPayload, triggerWebhookEvent.getUuid());

    TriggerType triggerType = findTriggerType(triggerPayload);
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(triggerType).setTriggeredBy(embeddedUser).build();
    return createPlanExecution(
        triggerDetails, triggerPayload, payload, executionTagForGitEvent, triggerInfo, triggerWebhookEvent);
  }

  // Todo: Check if we can merge some logic with ExecutionHelper
  private PlanExecution createPlanExecution(TriggerDetails triggerDetails, TriggerPayload triggerPayload,
      String payload, String executionTagForGitEvent, ExecutionTriggerInfo triggerInfo,
      TriggerWebhookEvent triggerWebhookEvent) {
    try {
      NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
      Optional<PipelineEntity> pipelineEntityToExecute;
      String targetIdentifier = ngTriggerEntity.getTargetIdentifier();

      ByteString gitSyncBranchContextByteString;
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        pipelineEntityToExecute = pmsPipelineService.getPipeline(ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), targetIdentifier, false, false);
        if (!pipelineEntityToExecute.isPresent()) {
          throw new TriggerException("Unable to continue trigger execution. Pipeline with identifier: "
                  + ngTriggerEntity.getTargetIdentifier() + ", with org: " + ngTriggerEntity.getOrgIdentifier()
                  + ", with ProjectId: " + ngTriggerEntity.getProjectIdentifier()
                  + ", For Trigger: " + ngTriggerEntity.getIdentifier() + " does not exist.",
              USER);
        }
        final GitEntityInfo branchInfo = GitEntityInfo.builder()
                                             .branch(pipelineEntityToExecute.get().getBranch())
                                             .yamlGitConfigId(pipelineEntityToExecute.get().getYamlGitConfigRef())
                                             .build();

        GitSyncBranchContext gitSyncBranchContext = GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build();
        gitSyncBranchContextByteString = pmsGitSyncHelper.serializeGitSyncBranchContext(gitSyncBranchContext);
      } else {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        String branch = null;
        if (isNotEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
          if (isBranchExpr(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
            branch = resolveBranchExpression(
                triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), triggerWebhookEvent);
          } else {
            branch = triggerDetails.getNgTriggerConfigV2().getPipelineBranchName();
          }
        }

        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder().gitBranchInfo(GitEntityInfo.builder().branch(branch).build()).build();
        gitSyncBranchContextByteString = pmsGitSyncHelper.serializeGitSyncBranchContext(gitSyncBranchContext);

        try (PmsGitSyncBranchContextGuard ignore =
                 pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(gitSyncBranchContextByteString, false)) {
          pipelineEntityToExecute =
              pmsPipelineService.getPipeline(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
                  ngTriggerEntity.getProjectIdentifier(), targetIdentifier, false, false);
        }

        if (pipelineEntityToExecute.isEmpty()) {
          throw new TriggerException("Unable to continue trigger execution. Pipeline with identifier: "
                  + ngTriggerEntity.getTargetIdentifier() + ", with org: " + ngTriggerEntity.getOrgIdentifier()
                  + ", with ProjectId: " + ngTriggerEntity.getProjectIdentifier()
                  + ", For Trigger: " + ngTriggerEntity.getIdentifier() + " does not exist in branch: " + branch
                  + " configured in trigger.",
              USER);
        }

        GitSyncBranchContext gitSyncContextWithRepoAndFilePath =
            getGitSyncContextWithRepoAndFilePath(pipelineEntityToExecute.get(), branch);
        gitSyncBranchContextByteString =
            pmsGitSyncHelper.serializeGitSyncBranchContext(gitSyncContextWithRepoAndFilePath);
        log.info(
            "Triggering execution for pipeline with identifier:  {} , in org: {} , ProjectId: {} , accountIdentifier: {} , For Trigger: {},  in branch {}, repo {} , filePath {}",
            ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getOrgIdentifier(),
            ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getAccountId(), ngTriggerEntity.getIdentifier(),
            branch, pipelineEntityToExecute.get().getRepo(), pipelineEntityToExecute.get().getFilePath());
      }
      PipelineEntity pipelineEntity = pipelineEntityToExecute.get();

      String runtimeInputYaml = null;
      if (PipelineVersion.V0.equals(pipelineEntity.getHarnessVersion())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
      } else {
        runtimeInputYaml = fetchInputSetYAML(triggerDetails, triggerWebhookEvent);
      }

      final String executionId = generateUuid();
      ExecutionMetadata.Builder executionMetaDataBuilder =
          ExecutionMetadata.newBuilder()
              .setExecutionUuid(executionId)
              .setTriggerInfo(triggerInfo)
              .setRunSequence(pipelineMetadataService.incrementRunSequence(pipelineEntity))
              .setPipelineIdentifier(pipelineEntity.getIdentifier())
              .setHarnessVersion(pipelineEntity.getHarnessVersion());

      if (isNotEmpty(pipelineEntity.getConnectorRef())) {
        executionMetaDataBuilder.setPipelineConnectorRef(pipelineEntity.getConnectorRef());
      }
      if (pipelineEntity.getStoreType() != null) {
        executionMetaDataBuilder.setPipelineStoreType(getPipelineStoreType(pipelineEntity.getStoreType()));
      } else {
        log.warn("The storeType is null for the pipeline: " + pipelineEntity.getIdentifier());
      }
      if (gitSyncBranchContextByteString != null) {
        executionMetaDataBuilder.setGitSyncBranchContext(gitSyncBranchContextByteString);
      }

      PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
          PlanExecutionMetadata.builder().planExecutionId(executionId).triggerJsonPayload(payload);

      String pipelineYaml;
      if (isBlank(runtimeInputYaml)) {
        pipelineYaml = pipelineEntity.getYaml();
      } else {
        String pipelineYamlBeforeMerge = pipelineEntity.getYaml();
        switch (pipelineEntity.getHarnessVersion()) {
          case PipelineVersion.V1:
            planExecutionMetadataBuilder.inputSetYaml(runtimeInputYaml);
            pipelineYaml =
                InputSetMergeHelperV1.mergeInputSetIntoPipelineYaml(runtimeInputYaml, pipelineYamlBeforeMerge);
            break;
          default:
            String sanitizedRuntimeInputYaml =
                InputSetSanitizer.sanitizeRuntimeInput(pipelineYamlBeforeMerge, runtimeInputYaml);
            if (isBlank(sanitizedRuntimeInputYaml)) {
              pipelineYaml = pipelineYamlBeforeMerge;
            } else {
              planExecutionMetadataBuilder.inputSetYaml(sanitizedRuntimeInputYaml);
              pipelineYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(
                  pipelineYamlBeforeMerge, sanitizedRuntimeInputYaml, true);
            }
        }
      }

      if (pipelineEntity.getHarnessVersion().equals(PipelineVersion.V0)) {
        pipelineYaml = InputSetSanitizer.trimValues(pipelineYaml);
      }

      try (PmsGitSyncBranchContextGuard ignore =
               pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(gitSyncBranchContextByteString, false)) {
        String pipelineYamlWithTemplateRef = pipelineYaml;
        if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
          log.info("Principal is {}", SourcePrincipalContextBuilder.getSourcePrincipal());
          TemplateMergeResponseDTO templateMergeResponseDTO =
              pipelineTemplateHelper.resolveTemplateRefsInPipelineAndAppendInputSetValidators(
                  pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                  pipelineEntity.getProjectIdentifier(), pipelineYaml, false,
                  featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE),
                  BOOLEAN_FALSE_VALUE);
          pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
          pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef() == null
              ? pipelineYaml
              : templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
        }

        List<NotificationRules> notificationRules = Collections.emptyList();
        String processedYaml;

        switch (pipelineEntity.getHarnessVersion()) {
          case PipelineVersion.V1:
            processedYaml = YamlUtils.injectUuidWithType(pipelineYaml, YAMLFieldNameConstants.PIPELINE);
            PipelineStoreType pipelineStoreType = StoreTypeMapper.fromStoreType(pipelineEntity.getStoreType());
            if (pipelineStoreType != null) {
              executionMetaDataBuilder.setPipelineStoreType(pipelineStoreType);
            }
            if (pipelineEntity.getConnectorRef() != null) {
              executionMetaDataBuilder.setPipelineConnectorRef(pipelineEntity.getConnectorRef());
            }
            break;
          case PipelineVersion.V0:
            BasicPipeline basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
            notificationRules = basicPipeline.getNotificationRules();
            processedYaml = YamlUtils.injectUuid(pipelineYaml);
            break;
          default:
            throw new InvalidYamlException("Invalid version");
        }

        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity);

        String expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity.getAccountId(),
            pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineYamlWithTemplateRef,
            true);

        planExecutionMetadataBuilder.yaml(pipelineYaml);
        planExecutionMetadataBuilder.processedYaml(processedYaml);
        planExecutionMetadataBuilder.triggerPayload(triggerPayload);
        planExecutionMetadataBuilder.expandedPipelineJson(expandedJson);

        executionMetaDataBuilder.setIsNotificationConfigured(EmptyPredicate.isNotEmpty(notificationRules));
        // Set Principle user as pipeline service.
        SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
        switch (pipelineEntity.getHarnessVersion()) {
          case PipelineVersion.V0:
            String yamlForValidatingSchema;
            try {
              yamlForValidatingSchema = YamlUtils.getYamlWithoutInputs(new YamlConfig(pipelineYaml));
            } catch (Exception ex) {
              log.error("Exception occurred while removing inputs from pipeline yaml", ex);
              yamlForValidatingSchema =
                  executionHelper.getPipelineYamlWithUnResolvedTemplates(runtimeInputYaml, pipelineEntity);
            }
            pmsYamlSchemaService.validateYamlSchema(pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(),
                pipelineEntity.getProjectIdentifier(), yamlForValidatingSchema);
            break;
          default:
        }

        executionMetaDataBuilder.setPrincipalInfo(
            ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build());

        PlanExecution planExecution = executionHelper.startExecution(ngTriggerEntity.getAccountId(),
            ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
            executionMetaDataBuilder.build(), planExecutionMetadataBuilder.build(), false, null, null, null);
        // check if abort prev execution needed.
        requestPipelineExecutionAbortForSameExecTagIfNeeded(triggerDetails, planExecution, executionTagForGitEvent);
        return planExecution;
      }
    } catch (Exception e) {
      throw new TriggerException(
          "Failed while requesting Pipeline Execution through Trigger: " + e.getMessage(), e, USER);
    }
  }

  @VisibleForTesting
  TriggeredBy generateTriggerdBy(
      String executionTagForGitEvent, NGTriggerEntity ngTriggerEntity, TriggerPayload triggerPayload, String eventId) {
    TriggeredBy.Builder builder =
        TriggeredBy.newBuilder().setIdentifier(ngTriggerEntity.getIdentifier()).setUuid("systemUser");
    if (isNotBlank(executionTagForGitEvent)) {
      builder.putExtraInfo(PlanExecution.EXEC_TAG_SET_BY_TRIGGER, executionTagForGitEvent);
      builder.putExtraInfo(TRIGGER_REF, generateTriggerRef(ngTriggerEntity));

      if (isNotBlank(eventId)) {
        builder.putExtraInfo(EVENT_CORRELATION_ID, eventId);
      }

      if (triggerPayload.hasParsedPayload()) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        User sender = null;
        if (parsedPayload.hasPush()) {
          sender = parsedPayload.getPush().getSender();
        } else if (parsedPayload.hasPr()) {
          sender = parsedPayload.getPr().getSender();
        }

        if (sender != null) {
          builder.putExtraInfo(GIT_USER, sender.getLogin());
          if (isNotEmpty(sender.getEmail())) {
            builder.putExtraInfo("email", sender.getEmail());
          }
          if (isNotEmpty(sender.getLogin())) {
            builder.setIdentifier(sender.getLogin());
          }
          if (isNotEmpty(sender.getName())) {
            builder.setUuid(sender.getName());
          }
        }
      }
    }
    return builder.build();
  }

  @VisibleForTesting
  String generateTriggerRef(NGTriggerEntity ngTriggerEntity) {
    return new StringBuilder(256)
        .append(ngTriggerEntity.getAccountId())
        .append(TRIGGER_REF_DELIMITER)
        .append(ngTriggerEntity.getOrgIdentifier())
        .append(TRIGGER_REF_DELIMITER)
        .append(ngTriggerEntity.getProjectIdentifier())
        .append(TRIGGER_REF_DELIMITER)
        .append(ngTriggerEntity.getIdentifier())
        .toString();
  }

  @VisibleForTesting
  TriggerType findTriggerType(TriggerPayload triggerPayload) {
    TriggerType triggerType = WEBHOOK;
    if (triggerPayload.getType() == Type.SCHEDULED) {
      triggerType = TriggerType.SCHEDULER_CRON;
    } else if (triggerPayload.getType() == Type.ARTIFACT) {
      triggerType = TriggerType.ARTIFACT;
    } else if (triggerPayload.getType() == Type.MANIFEST) {
      triggerType = TriggerType.MANIFEST;
    } else if (triggerPayload.getSourceType() == SourceType.CUSTOM_REPO) {
      triggerType = WEBHOOK_CUSTOM;
    }

    return triggerType;
  }

  /**
   * Generate execution tag to identify pipeline executions caused by similar trigger git events.
   * PR: {accId:orgId:projectId:pipelineIdentifier:PR:RepoUrl:PrNum:SourceBranch:TargetBranch}
   * PUSH: {accId:orgId:projectId:pipelineIdentifier:PUSH:RepoUrl:Ref}
   *
   * @param triggerDetails
   * @param triggerPayload
   * @return
   */
  public String generateExecutionTagForEvent(TriggerDetails triggerDetails, TriggerPayload triggerPayload) {
    String triggerRef = new StringBuilder(256)
                            .append(triggerDetails.getNgTriggerEntity().getAccountId())
                            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
                            .append(triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
                            .append(triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
                            .append(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
                            .toString();

    try {
      if (!triggerPayload.hasParsedPayload()) {
        return triggerRef;
      }

      ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
      StringBuilder executionTag = new StringBuilder(512).append(triggerRef);

      if (parsedPayload.hasPr()) {
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER).append(PR);
        PullRequestHook pullRequestHook = parsedPayload.getPr();
        PullRequest pr = pullRequestHook.getPr();
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pullRequestHook.getRepo().getLink())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getNumber())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getSource())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pr.getTarget());
      } else if (parsedPayload.hasPush()) {
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER).append(PUSH);
        PushHook pushHook = parsedPayload.getPush();
        executionTag.append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pushHook.getRepo().getLink())
            .append(TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER)
            .append(pushHook.getRef());
      }
      return executionTag.toString();
    } catch (Exception e) {
      log.error("failed to generate complete Execution Tag for Trigger: " + triggerRef, e);
    }

    return triggerRef;
  }

  @VisibleForTesting
  void requestPipelineExecutionAbortForSameExecTagIfNeeded(
      TriggerDetails triggerDetails, PlanExecution planExecution, String executionTag) {
    try {
      if (!isAutoAbortSelected(triggerDetails.getNgTriggerConfigV2())) {
        return;
      }

      List<PlanExecution> executionsToAbort =
          planExecutionService.findPrevUnTerminatedPlanExecutionsByExecutionTag(planExecution, executionTag);
      if (isEmpty(executionsToAbort)) {
        return;
      }

      for (PlanExecution execution : executionsToAbort) {
        registerPipelineExecutionAbortInterrupt(execution, executionTag, triggerDetails.getNgTriggerEntity());
      }
    } catch (Exception e) {
      log.error("Failed while requesting abort for pipeline executions using executionTag: " + executionTag, e);
    }
  }

  @VisibleForTesting
  boolean isAutoAbortSelected(NGTriggerConfigV2 ngTriggerConfigV2) {
    boolean autoAbortPreviousExecutions = false;
    if (WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerConfigV2.getSource().getSpec().getClass())) {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerConfigV2.getSource().getSpec();
      GitAware gitAware = webhookTriggerConfigV2.getSpec().fetchGitAware();
      if (gitAware != null && gitAware.fetchAutoAbortPreviousExecutions()) {
        autoAbortPreviousExecutions = gitAware.fetchAutoAbortPreviousExecutions();
      }
    }

    return autoAbortPreviousExecutions;
  }

  public String fetchInputSetYAML(TriggerDetails triggerDetails, TriggerWebhookEvent triggerWebhookEvent) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    NGTriggerConfigV2 triggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
    String pipelineBranch = triggerConfigV2.getPipelineBranchName();
    if (isEmpty(triggerConfigV2.getInputSetRefs())) {
      return triggerConfigV2.getInputYaml();
    }

    String branch = null;
    if (isNotEmpty(pipelineBranch)) {
      if (isBranchExpr(pipelineBranch)) {
        branch = resolveBranchExpression(pipelineBranch, triggerWebhookEvent);
      } else {
        branch = pipelineBranch;
      }
    }

    List<String> inputSetRefs = triggerConfigV2.getInputSetRefs();
    MergeInputSetResponseDTOPMS mergeInputSetResponseDTOPMS =
        NGRestUtils.getResponse(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
            ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
            ngTriggerEntity.getTargetIdentifier(), branch,
            MergeInputSetRequestDTOPMS.builder().inputSetReferences(inputSetRefs).build()));

    return mergeInputSetResponseDTOPMS.getPipelineYaml();
  }

  private void registerPipelineExecutionAbortInterrupt(
      PlanExecution execution, String executionTag, NGTriggerEntity ngTriggerEntity) {
    try {
      log.info(new StringBuilder(128)
                   .append("Requesting Pipeline Execution Abort for planExecutionId")
                   .append(execution.getUuid())
                   .append(", with Tag: ")
                   .append(executionTag)
                   .toString());

      InterruptConfig interruptConfig =
          InterruptConfig.newBuilder()
              .setIssuedBy(IssuedBy.newBuilder()
                               .setTriggerIssuer(TriggerIssuer.newBuilder()
                                                     .setTriggerRef(generateTriggerRef(ngTriggerEntity))
                                                     .setAbortPrevConcurrentExecution(true)
                                                     .build())
                               .setIssueTime(ProtoUtils.unixMillisToTimestamp(System.currentTimeMillis()))
                               .build())
              .build();
      pmsExecutionService.registerInterrupt(ABORTALL, execution.getUuid(), null, interruptConfig);
    } catch (Exception e) {
      log.error("Exception white requesting Pipeline Execution Abort: " + executionTag, e);
    }
  }

  public String resolveBranchExpression(String expression, TriggerWebhookEvent triggerWebhookEvent) {
    if (triggerWebhookEvent == null || triggerWebhookEvent.getPayload() == null) {
      throw new InvalidRequestException("Branch can not be expression for non webhook executions");
    }

    TriggerExpressionEvaluator triggerExpressionEvaluator;
    if (!triggerWebhookEvent.getSourceRepoType().equals("CUSTOM")) {
      WebhookPayloadData webhookPayloadData = webhookEventPayloadParser.parseEvent(triggerWebhookEvent);
      triggerExpressionEvaluator = WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(webhookPayloadData);
      return (String) triggerExpressionEvaluator.evaluateExpression(expression);
    } else {
      triggerExpressionEvaluator = WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(
          null, triggerWebhookEvent.getHeaders(), triggerWebhookEvent.getPayload());
      return (String) triggerExpressionEvaluator.evaluateExpression(TRIGGER_PAYLOAD_BRANCH);
    }
  }

  public boolean isBranchExpr(String pipelineBranch) {
    return pipelineBranch.startsWith(ExpressionConstants.EXPR_START)
        && pipelineBranch.endsWith(ExpressionConstants.EXPR_END);
  }

  private PipelineStoreType getPipelineStoreType(StoreType storeType) {
    if (StoreType.REMOTE.equals(storeType)) {
      return PipelineStoreType.REMOTE;
    } else if (StoreType.INLINE.equals(storeType)) {
      return PipelineStoreType.INLINE;
    } else {
      return PipelineStoreType.UNDEFINED;
    }
  }

  @VisibleForTesting
  GitSyncBranchContext getGitSyncContextWithRepoAndFilePath(PipelineEntity pipelineEntityToExecute, String branch) {
    return GitSyncBranchContext.builder()
        .gitBranchInfo(GitEntityInfo.builder()
                           .repoName(pipelineEntityToExecute.getRepo())
                           .filePath(pipelineEntityToExecute.getFilePath())
                           .branch(branch)
                           .yamlGitConfigId(pipelineEntityToExecute.getRepo())
                           .connectorRef(pipelineEntityToExecute.getConnectorRef())
                           .build())
        .build();
  }
}
