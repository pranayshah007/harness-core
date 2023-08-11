/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_HUB_SIGNATURE_256;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ngtriggers.Constants.MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE;
import static io.harness.ngtriggers.Constants.TRIGGERS_MANDATE_GITHUB_AUTHENTICATION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_AUTHENTICATION_FAILED;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AZURE;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.HARNESS;
import static io.harness.pms.contracts.triggers.Type.WEBHOOK;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskParams;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.execution.PlanExecution;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult.WebhookEventProcessingResultBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.WebhookEventMapperHelper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.TriggerPayload.Builder;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagService;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerEventExecutionHelper {
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  private SecretManagerClientService ngSecretService;
  private TaskExecutionUtils taskExecutionUtils;
  private final NGSettingsClient settingsClient;
  private final NGTriggerRepository ngTriggerRepository;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final TriggerExecutionHelper triggerExecutionHelper;
  private final WebhookEventMapperHelper webhookEventMapperHelper;
  private final TriggerWebhookEventPublisher triggerWebhookEventPublisher;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Inject @Named("TriggerAuthenticationExecutorService") ExecutorService triggerAuthenticationExecutor;

  public WebhookEventProcessingResult handleTriggerWebhookEvent(TriggerMappingRequestData mappingRequestData) {
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId",
             mappingRequestData.getWebhookDTO() == null ? null : mappingRequestData.getWebhookDTO().getEventId(),
             mappingRequestData.getTriggerWebhookEvent().getTriggerIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getPipelineIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getProjectIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getOrgIdentifier(),
             mappingRequestData.getTriggerWebhookEvent().getAccountId(),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      WebhookEventMappingResponse webhookEventMappingResponse =
          webhookEventMapperHelper.mapWebhookEventToTriggers(mappingRequestData);

      TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();
      WebhookEventProcessingResultBuilder resultBuilder = WebhookEventProcessingResult.builder();
      List<TriggerEventResponse> eventResponses = new ArrayList<>();
      if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
        authenticateTriggers(triggerWebhookEvent, webhookEventMappingResponse);
        log.info("Preparing for pipeline execution request");
        resultBuilder.mappedToTriggers(true);
        if (isNotEmpty(webhookEventMappingResponse.getTriggers())) {
          for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
            if (triggerDetails.getNgTriggerEntity() == null) {
              log.error("Trigger Entity is empty, This should not happen, please check");
              continue;
            }
            if (triggerDetails.getAuthenticated() != null && !triggerDetails.getAuthenticated()) {
              eventResponses.add(generateEventHistoryForAuthenticationError(
                  triggerWebhookEvent, triggerDetails, triggerDetails.getNgTriggerEntity()));
              continue;
            }
            if (pmsFeatureFlagService.isEnabled(triggerDetails.getNgTriggerEntity().getAccountId(),
                    FeatureName.SPG_SEND_TRIGGER_PIPELINE_FOR_WEBHOOKS_ASYNC)
                && mappingRequestData.getWebhookDTO() != null) {
              // Added condition for webhookDTO to be not null as the flow should not go to redis if it comes via V1
              // flow.
              WebhookDTO webhookDTO = mappingRequestData.getWebhookDTO();
              TriggerExecutionDTO triggerExecutionDTO =
                  TriggerExecutionDTO.newBuilder()
                      .setWebhookDto(webhookDTO)
                      .setAccountId(triggerDetails.getNgTriggerEntity().getAccountId())
                      .setOrgIdentifier(triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                      .setProjectIdentifier(triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                      .setTargetIdentifier(triggerDetails.getNgTriggerEntity().getTargetIdentifier())
                      .setTriggerIdentifier(triggerDetails.getNgTriggerEntity().getIdentifier())
                      .setAuthenticated(
                          triggerDetails.getAuthenticated() != null ? triggerDetails.getAuthenticated() : Boolean.TRUE)
                      .build();
              triggerWebhookEventPublisher.publishTriggerWebhookEvent(triggerExecutionDTO);
            } else {
              updateWebhookRegistrationStatusAndTriggerPipelineExecution(
                  webhookEventMappingResponse.getParseWebhookResponse(), triggerWebhookEvent, eventResponses,
                  triggerDetails);
            }
          }
        }
      } else {
        resultBuilder.mappedToTriggers(false);
        eventResponses.add(webhookEventMappingResponse.getWebhookEventResponse());
      }

      return resultBuilder.responses(eventResponses).build();
    }
  }

  public void updateWebhookRegistrationStatusAndTriggerPipelineExecution(ParseWebhookResponse parseWebhookResponse,
      TriggerWebhookEvent triggerWebhookEvent, List<TriggerEventResponse> eventResponses,
      TriggerDetails triggerDetails) {
    long yamlVersion = triggerDetails.getNgTriggerEntity().getYmlVersion() == null
        ? 3
        : triggerDetails.getNgTriggerEntity().getYmlVersion();
    NGTriggerEntity triggerEntity = triggerDetails.getNgTriggerEntity();
    Criteria criteria = Criteria.where(NGTriggerEntityKeys.accountId)
                            .is(triggerEntity.getAccountId())
                            .and(NGTriggerEntityKeys.orgIdentifier)
                            .is(triggerEntity.getOrgIdentifier())
                            .and(NGTriggerEntityKeys.projectIdentifier)
                            .is(triggerEntity.getProjectIdentifier())
                            .and(NGTriggerEntityKeys.targetIdentifier)
                            .is(triggerEntity.getTargetIdentifier())
                            .and(NGTriggerEntityKeys.identifier)
                            .is(triggerEntity.getIdentifier())
                            .and(NGTriggerEntityKeys.deleted)
                            .is(false);
    if (triggerEntity.getVersion() != null) {
      criteria.and(NGTriggerEntityKeys.version).is(triggerEntity.getVersion());
    }
    try {
      TriggerHelper.stampWebhookRegistrationInfo(triggerEntity,
          WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
    } catch (Exception ex) {
      log.error("Webhook registration status update failed", ex);
    }
    ngTriggerRepository.updateValidationStatus(criteria, triggerEntity);
    List<HeaderConfig> headerConfigList = triggerWebhookEvent.getHeaders();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = WebhookTriggerConfigV2.builder().build();

    if (null != triggerDetails.getNgTriggerConfigV2() && null != triggerDetails.getNgTriggerConfigV2().getSource()
        && null != triggerDetails.getNgTriggerConfigV2().getSource().getSpec()) {
      webhookTriggerConfigV2 = (WebhookTriggerConfigV2) triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
    }

    String connectorRef = null;
    if (webhookTriggerConfigV2.getSpec() != null && webhookTriggerConfigV2.getSpec().fetchGitAware() != null
        && webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef() != null) {
      connectorRef = webhookTriggerConfigV2.getSpec().fetchGitAware().fetchConnectorRef();
    }
    eventResponses.add(triggerPipelineExecution(triggerWebhookEvent, triggerDetails,
        getTriggerPayloadForWebhookTrigger(parseWebhookResponse, triggerWebhookEvent, yamlVersion, connectorRef),
        triggerWebhookEvent.getPayload(), headerConfigList));
  }

  @VisibleForTesting
  TriggerPayload getTriggerPayloadForWebhookTrigger(ParseWebhookResponse parseWebhookResponse,
      TriggerWebhookEvent triggerWebhookEvent, long version, String connectorRef) {
    Builder builder = TriggerPayload.newBuilder().setType(Type.WEBHOOK);

    if (CUSTOM.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.CUSTOM_REPO);
    } else if (GITHUB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITHUB_REPO);
    } else if (AZURE.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.AZURE_REPO);
    } else if (GITLAB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITLAB_REPO);
    } else if (BITBUCKET.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (AWS_CODECOMMIT.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (HARNESS.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.HARNESS_REPO);
    }

    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasRelease()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setRelease(parseWebhookResponse.getRelease()).build())
            .build();
      } else if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    builder.setVersion(version);
    if (isNotEmpty(connectorRef)) {
      builder.setConnectorRef(connectorRef);
    }
    return builder.setType(WEBHOOK).build();
  }

  private TriggerEventResponse triggerPipelineExecution(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, TriggerPayload triggerPayload, String payload, List<HeaderConfig> header) {
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    try {
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
      } else {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, triggerWebhookEvent);
      }
      PlanExecution response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(
          triggerDetails, triggerPayload, triggerWebhookEvent, payload, header, runtimeInputYaml);
      return generateEventHistoryForSuccess(
          triggerDetails, runtimeInputYaml, ngTriggerEntity, triggerWebhookEvent, response, null, null);
    } catch (Exception e) {
      return generateEventHistoryForError(
          triggerWebhookEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e, null, null);
    }
  }

  public TriggerEventResponse generateEventHistoryForError(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, String runtimeInputYaml, NGTriggerEntity ngTriggerEntity, Exception e,
      String pollingDocId, String build) {
    log.error(new StringBuilder(512)
                  .append("Exception occurred while requesting pipeline execution using Trigger ")
                  .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                  .append(". Exception Message: ")
                  .append(e.getMessage())
                  .toString(),
        e);

    TargetExecutionSummary targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        (PlanExecution) null, triggerDetails, runtimeInputYaml);

    return TriggerEventResponseHelper.toResponseWithPollingInfo(INVALID_RUNTIME_INPUT_YAML, triggerWebhookEvent, null,
        ngTriggerEntity, triggerDetails.getNgTriggerConfigV2(), e.getMessage(), targetExecutionSummary, pollingDocId,
        build);
  }

  public TriggerEventResponse generateEventHistoryForAuthenticationError(
      TriggerWebhookEvent triggerWebhookEvent, TriggerDetails triggerDetails, NGTriggerEntity ngTriggerEntity) {
    log.error("Trigger Authentication Failed {}", TriggerHelper.getTriggerRef(ngTriggerEntity));
    TargetExecutionSummary targetExecutionSummary =
        TriggerEventResponseHelper.prepareTargetExecutionSummary((PlanExecution) null, triggerDetails, null);
    return TriggerEventResponseHelper.toResponse(TRIGGER_AUTHENTICATION_FAILED, triggerWebhookEvent, null,
        ngTriggerEntity, "Please check if the secret provided for webhook is correct.", targetExecutionSummary);
  }

  public List<TriggerEventResponse> processTriggersForActivation(
      List<TriggerDetails> mappedTriggers, PollingResponse pollingResponse) {
    List<TriggerEventResponse> responses = new ArrayList<>();
    for (TriggerDetails triggerDetails : mappedTriggers) {
      try {
        responses.add(triggerEventPipelineExecution(triggerDetails, pollingResponse));
      } catch (Exception e) {
        log.error("Error while requesting pipeline execution for Build Trigger: "
            + TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity()));
      }
    }

    return responses;
  }

  public TriggerEventResponse triggerEventPipelineExecution(
      TriggerDetails triggerDetails, PollingResponse pollingResponse) {
    String pollingDocId = null;
    String build = null;
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    TriggerWebhookEvent pseudoEvent = TriggerWebhookEvent.builder()
                                          .accountId(ngTriggerEntity.getAccountId())
                                          .createdAt(System.currentTimeMillis())
                                          .build();
    try (AutoLogContext ignore1 = new NgTriggerAutoLogContext("pollingDocumentId", pollingResponse.getPollingDocId(),
             ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
             ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getAccountId(),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      pollingDocId = pollingResponse.getPollingDocId();
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
      } else {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, pseudoEvent);
      }

      Type buildType = getBuildType(ngTriggerEntity);
      Builder triggerPayloadBuilder = TriggerPayload.newBuilder().setType(buildType);

      build = pollingResponse.getBuildInfo().getVersions(0);
      log.info(
          "Triggering pipeline execution for pollingDocumentId {}, build {}", pollingResponse.getPollingDocId(), build);
      if (buildType == Type.ARTIFACT) {
        Map<String, String> metadata = new HashMap<>();
        if (pollingResponse.getBuildInfo().getMetadataCount() != 0) {
          metadata = pollingResponse.getBuildInfo().getMetadata(0).getMetadataMap();
        }
        triggerPayloadBuilder.setArtifactData(
            ArtifactData.newBuilder().setBuild(build).putAllMetadata(metadata).build());

      } else if (buildType == Type.MANIFEST) {
        triggerPayloadBuilder.setManifestData(ManifestData.newBuilder().setVersion(build).build());
      }
      TriggerPayload triggerPayload = triggerPayloadBuilder.build();
      PlanExecution response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionReques(
          triggerDetails, triggerPayload, runtimeInputYaml);
      if (triggerPayload != null) {
        pseudoEvent.setPayload(triggerPayload.toString());
      }
      return generateEventHistoryForSuccess(
          triggerDetails, runtimeInputYaml, ngTriggerEntity, pseudoEvent, response, pollingDocId, build);
    } catch (Exception e) {
      return generateEventHistoryForError(
          pseudoEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e, pollingDocId, build);
    }
  }

  private TriggerEventResponse generateEventHistoryForSuccess(TriggerDetails triggerDetails, String runtimeInputYaml,
      NGTriggerEntity ngTriggerEntity, TriggerWebhookEvent pseudoEvent, PlanExecution response, String pollingDocId,
      String build) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(ngTriggerEntity.getProjectIdentifier(),
             ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AutoLogContext(ImmutableMap.of("planExecutionId", response.getPlanId()),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      TargetExecutionSummary targetExecutionSummary =
          TriggerEventResponseHelper.prepareTargetExecutionSummary(response, triggerDetails, runtimeInputYaml);

      log.info(ngTriggerEntity.getTargetType() + " execution was requested successfully for Pipeline: "
          + ngTriggerEntity.getTargetIdentifier() + ", using trigger: " + ngTriggerEntity.getIdentifier());

      return TriggerEventResponseHelper.toResponseWithPollingInfo(TARGET_EXECUTION_REQUESTED, pseudoEvent,
          ngTriggerEntity, triggerDetails.getNgTriggerConfigV2(), "Pipeline execution was requested successfully",
          targetExecutionSummary, pollingDocId, build);
    }
  }

  public void authenticateTriggers(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    List<TriggerDetails> triggersToAuthenticate =
        getTriggersToAuthenticate(triggerWebhookEvent, webhookEventMappingResponse);
    if (isEmpty(triggersToAuthenticate)) {
      return;
    }
    String hashedPayload = getHashedPayload(triggerWebhookEvent);
    if (hashedPayload == null) {
      for (TriggerDetails triggerDetails : triggersToAuthenticate) {
        triggerDetails.setAuthenticated(false);
      }
      return;
    }
    CompletableFutures<ResponseData> completableFutures = new CompletableFutures<>(triggerAuthenticationExecutor);
    Map<Integer, TriggerDetails> triggerDetailsMap = new HashMap<>();
    int counter = 0;
    for (TriggerDetails triggerDetails : triggersToAuthenticate) {
      try {
        NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
        NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                           .accountIdentifier(triggerWebhookEvent.getAccountId())
                                           .orgIdentifier(ngTriggerConfigV2.getOrgIdentifier())
                                           .projectIdentifier(ngTriggerConfigV2.getProjectIdentifier())
                                           .build();
        SecretRefData secretRefData =
            SecretRefHelper.createSecretRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
        WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
            WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
        List<EncryptedDataDetail> encryptedDataDetail =
            ngSecretService.getEncryptionDetails(basicNGAccessObject, webhookEncryptedSecretDTO);
        List<WebhookSecretData> webhookSecretData =
            Collections.singletonList(WebhookSecretData.builder()
                                          .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                                          .encryptedDataDetails(encryptedDataDetail)
                                          .build());
        Set<String> taskSelectors =
            getAuthenticationTaskSelectors(basicNGAccessObject, secretRefData, ngTriggerConfigV2.getIdentifier());
        log.info("Authenticating trigger [" + ngTriggerConfigV2.getIdentifier()
            + "] with delegate selectors: " + taskSelectors);
        completableFutures.supplyAsync(
            ()
                -> taskExecutionUtils.executeSyncTask(
                    DelegateTaskRequest.builder()
                        .accountId(triggerWebhookEvent.getAccountId())
                        .executionTimeout(
                            Duration.ofSeconds(60)) // todo: Gather suggestions regarding this timeout value.
                        .taskType(TaskType.TRIGGER_AUTHENTICATION_TASK.toString())
                        .taskSelectors(taskSelectors)
                        .taskSetupAbstractions(buildAbstractions(triggerWebhookEvent.getAccountId(),
                            ngTriggerConfigV2.getOrgIdentifier(), ngTriggerConfigV2.getProjectIdentifier()))
                        .taskParameters(TriggerAuthenticationTaskParams.builder()
                                            .eventPayload(triggerWebhookEvent.getPayload())
                                            .gitRepoType(GitRepoType.GITHUB)
                                            .hashedPayload(hashedPayload)
                                            .webhookSecretData(webhookSecretData)
                                            .build())
                        .build()));
        triggerDetailsMap.put(counter, triggerDetails);
        counter++;
      } catch (Exception e) {
        triggerDetails.setAuthenticated(false);
        log.error("Exception while authenticating trigger with id {}",
            triggerDetails.getNgTriggerEntity().getIdentifier(), e);
      }
    }
    try {
      List<ResponseData> authenticationTaskResponses = completableFutures.allOf().get(2, TimeUnit.MINUTES);
      int index = 0;
      for (ResponseData responseData : authenticationTaskResponses) {
        TriggerDetails triggerDetails = triggerDetailsMap.get(index);
        if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
          BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
          Object object = binaryResponseData.isUsingKryoWithoutReference()
              ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
              : kryoSerializer.asInflatedObject(binaryResponseData.getData());
          if (object instanceof TriggerAuthenticationTaskResponse) {
            triggerDetails.setAuthenticated(
                ((TriggerAuthenticationTaskResponse) object).getTriggersAuthenticationStatus().get(0));
          } else if (object instanceof ErrorResponseData) {
            ErrorResponseData errorResponseData = (ErrorResponseData) object;
            log.error("Failed to authenticate trigger {}. Reason: {}",
                triggerDetails.getNgTriggerEntity().getIdentifier(), errorResponseData.getErrorMessage());
            triggerDetails.setAuthenticated(false);
          }
        }
        index++;
      }
    } catch (Exception e) {
      log.error("Exception while authenticating triggers: ", e);
      for (TriggerDetails triggerDetails : triggersToAuthenticate) {
        triggerDetails.setAuthenticated(false);
      }
    }
  }

  public List<TriggerDetails> getTriggersToAuthenticate(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    // Only GitHub events authentication is supported for now
    List<TriggerDetails> triggersToAuthenticate = new ArrayList<>();
    if (GITHUB.name().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
        NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
        if (ngTriggerConfigV2 != null && shouldAuthenticateTrigger(triggerWebhookEvent, ngTriggerConfigV2)) {
          triggersToAuthenticate.add(triggerDetails);
        }
      }
    }
    return triggersToAuthenticate;
  }

  private String getHashedPayload(TriggerWebhookEvent triggerWebhookEvent) {
    String hashedPayload = null;
    for (HeaderConfig headerConfig : triggerWebhookEvent.getHeaders()) {
      if (headerConfig.getKey().equalsIgnoreCase(X_HUB_SIGNATURE_256)) {
        List<String> values = headerConfig.getValues();
        if (isNotEmpty(values) && values.size() == 1) {
          hashedPayload = values.get(0);
        }
        break;
      }
    }
    return hashedPayload;
  }

  private Boolean shouldAuthenticateTrigger(
      TriggerWebhookEvent triggerWebhookEvent, NGTriggerConfigV2 ngTriggerConfigV2) {
    String mandatoryAuth = NGRestUtils
                               .getResponse(settingsClient.getSetting(TRIGGERS_MANDATE_GITHUB_AUTHENTICATION,
                                   triggerWebhookEvent.getAccountId(), ngTriggerConfigV2.getOrgIdentifier(),
                                   ngTriggerConfigV2.getProjectIdentifier()))
                               .getValue();
    if (mandatoryAuth.equals(MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE)) {
      return true;
    }

    return isNotEmpty(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
  }

  public Set<String> getAuthenticationTaskSelectors(
      NGAccess ngAccess, SecretRefData secretRefData, String triggerIdentifier) {
    NGAccess secretNGAccess = SecretRefHelper.getScopeIdentifierForSecretRef(
        secretRefData, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    SecretResponseWrapper secret = ngSecretService.getSecret(secretNGAccess.getAccountIdentifier(),
        secretNGAccess.getOrgIdentifier(), secretNGAccess.getProjectIdentifier(), secretNGAccess.getIdentifier());
    if (secret == null || secret.getSecret() == null || !(secret.getSecret().getSpec() instanceof SecretTextSpecDTO)) {
      log.warn("Secret with identifier [" + secretRefData.getIdentifier()
          + "] either does not exist or is not of Text type. Attempting to authenticate trigger [" + triggerIdentifier
          + "] with no delegate selectors.");
      return Collections.emptySet();
    }
    String secretManagerIdentifier = ((SecretTextSpecDTO) secret.getSecret().getSpec()).getSecretManagerIdentifier();
    SecretManagerConfigDTO secretManagerDTO = ngSecretService.getSecretManager(secretNGAccess.getAccountIdentifier(),
        secretNGAccess.getOrgIdentifier(), secretNGAccess.getProjectIdentifier(), secretManagerIdentifier, false);
    return SecretManagerConfigMapper.getDelegateSelectors(secretManagerDTO);
  }

  private Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }

  private Type getBuildType(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getType() == NGTriggerType.ARTIFACT
        || ngTriggerEntity.getType() == NGTriggerType.MULTI_REGION_ARTIFACT) {
      return Type.ARTIFACT;
    }
    return Type.MANIFEST;
  }
}
