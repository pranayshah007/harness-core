/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;
import static io.harness.pms.contracts.triggers.Type.WEBHOOK;

import io.harness.AuthorizationServiceHeader;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecution;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult.WebhookEventProcessingResultBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.WebhookEventMapperHelper;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.TriggerPayload.Builder;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerEventExecutionHelper {
  private final WebhookEventMapperHelper webhookEventMapperHelper;
  private final TriggerExecutionHelper triggerExecutionHelper;
  private final WebhookEventPayloadParser webhookEventPayloadParser;
  private final PipelineServiceClient pipelineServiceClient;

  public WebhookEventProcessingResult handleTriggerWebhookEvent(TriggerMappingRequestData mappingRequestData) {
    WebhookEventMappingResponse webhookEventMappingResponse =
        webhookEventMapperHelper.mapWebhookEventToTriggers(mappingRequestData);

    TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();
    WebhookEventProcessingResultBuilder resultBuilder = WebhookEventProcessingResult.builder();
    List<TriggerEventResponse> eventResponses = new ArrayList<>();
    if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
      log.info("Preparing for pipeline execution request");
      resultBuilder.mappedToTriggers(true);
      if (isNotEmpty(webhookEventMappingResponse.getTriggers())) {
        for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
          if (triggerDetails.getNgTriggerEntity() == null) {
            log.error("Trigger Entity is empty, This should not happen, please check");
            continue;
          }
          long yamlVersion = triggerDetails.getNgTriggerEntity().getYmlVersion() == null
              ? 3
              : triggerDetails.getNgTriggerEntity().getYmlVersion();
          eventResponses.add(triggerPipelineExecution(triggerWebhookEvent, triggerDetails,
              getTriggerPayloadForWebhookTrigger(webhookEventMappingResponse, triggerWebhookEvent, yamlVersion),
              triggerWebhookEvent.getPayload()));
        }
      }
    } else {
      resultBuilder.mappedToTriggers(false);
      eventResponses.add(webhookEventMappingResponse.getWebhookEventResponse());
    }

    return resultBuilder.responses(eventResponses).build();
  }

  @VisibleForTesting
  TriggerPayload getTriggerPayloadForWebhookTrigger(
      WebhookEventMappingResponse webhookEventMappingResponse, TriggerWebhookEvent triggerWebhookEvent, long version) {
    Builder builder = TriggerPayload.newBuilder().setType(Type.WEBHOOK);

    if (CUSTOM.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.CUSTOM_REPO);
    } else if (GITHUB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITHUB_REPO);
    } else if (GITLAB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITLAB_REPO);
    } else if (BITBUCKET.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (AWS_CODECOMMIT.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    }

    ParseWebhookResponse parseWebhookResponse = webhookEventMappingResponse.getParseWebhookResponse();
    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    builder.setVersion(version);

    return builder.setType(WEBHOOK).build();
  }

  private TriggerEventResponse triggerPipelineExecution(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, TriggerPayload triggerPayload, String payload) {
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
          triggerDetails, triggerPayload, triggerWebhookEvent, payload);
      return generateEventHistoryForSuccess(
          triggerDetails, runtimeInputYaml, ngTriggerEntity, triggerWebhookEvent, response);
    } catch (Exception e) {
      return generateEventHistoryForError(triggerWebhookEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e);
    }
  }

  public TriggerEventResponse generateEventHistoryForError(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, String runtimeInputYaml, NGTriggerEntity ngTriggerEntity, Exception e) {
    log.error(new StringBuilder(512)
                  .append("Exception occurred while requesting pipeline execution using Trigger ")
                  .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                  .append(". Exception Message: ")
                  .append(e.getMessage())
                  .toString(),
        e);

    TargetExecutionSummary targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        (PlanExecution) null, triggerDetails, runtimeInputYaml);
    return TriggerEventResponseHelper.toResponse(
        INVALID_RUNTIME_INPUT_YAML, triggerWebhookEvent, null, ngTriggerEntity, e.getMessage(), targetExecutionSummary);
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
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    TriggerWebhookEvent pseudoEvent = TriggerWebhookEvent.builder()
                                          .accountId(ngTriggerEntity.getAccountId())
                                          .createdAt(System.currentTimeMillis())
                                          .build();
    try {
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

      Type buildType = ngTriggerEntity.getType() == NGTriggerType.ARTIFACT ? Type.ARTIFACT : Type.MANIFEST;
      Builder triggerPayloadBuilder = TriggerPayload.newBuilder().setType(buildType);

      String build = pollingResponse.getBuildInfo().getVersions(0);
      if (buildType == Type.ARTIFACT) {
        triggerPayloadBuilder.setArtifactData(ArtifactData.newBuilder().setBuild(build).build());
      } else if (buildType == Type.MANIFEST) {
        triggerPayloadBuilder.setManifestData(ManifestData.newBuilder().setVersion(build).build());
      }

      PlanExecution response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionReques(
          triggerDetails, triggerPayloadBuilder.build());
      return generateEventHistoryForSuccess(triggerDetails, runtimeInputYaml, ngTriggerEntity, pseudoEvent, response);
    } catch (Exception e) {
      return generateEventHistoryForError(pseudoEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e);
    }
  }

  private TriggerEventResponse generateEventHistoryForSuccess(TriggerDetails triggerDetails, String runtimeInputYaml,
      NGTriggerEntity ngTriggerEntity, TriggerWebhookEvent pseudoEvent, PlanExecution response) {
    TargetExecutionSummary targetExecutionSummary =
        TriggerEventResponseHelper.prepareTargetExecutionSummary(response, triggerDetails, runtimeInputYaml);

    log.info(ngTriggerEntity.getTargetType() + " execution was requested successfully for Pipeline: "
        + ngTriggerEntity.getTargetIdentifier() + ", using trigger: " + ngTriggerEntity.getIdentifier());

    return TriggerEventResponseHelper.toResponse(TARGET_EXECUTION_REQUESTED, pseudoEvent, ngTriggerEntity,
        "Pipeline execution was requested successfully", targetExecutionSummary);
  }
}
