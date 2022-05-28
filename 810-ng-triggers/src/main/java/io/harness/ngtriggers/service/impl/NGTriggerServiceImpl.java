/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.PollingResponseDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.TriggerException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails.WebhookEventProcessingDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventsKeys;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.PollingSubscriptionStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.ngtriggers.exceptions.InvalidTriggerYamlException;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.ngtriggers.utils.PollingSubscriptionHelper;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.serializer.KryoSerializer;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerServiceImpl implements NGTriggerService {
  public static final long TRIGGER_CURRENT_YML_VERSION = 3l;
  private final NGTriggerRepository ngTriggerRepository;
  private final TriggerWebhookEventRepository webhookEventQueueRepository;
  private final TriggerEventHistoryRepository triggerEventHistoryRepository;
  private final ConnectorResourceClient connectorResourceClient;
  private final NGTriggerWebhookRegistrationService ngTriggerWebhookRegistrationService;
  private final TriggerValidationHandler triggerValidationHandler;
  private final PollingSubscriptionHelper pollingSubscriptionHelper;
  private final ExecutorService executorService;
  private final KryoSerializer kryoSerializer;
  private final PipelineServiceClient pipelineServiceClient;
  private final BuildTriggerHelper buildTriggerHelper;
  private final PollingResourceClient pollingResourceClient;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final OutboxService outboxService;
  private final BuildTriggerHelper validationHelper;
  private static final String PIPELINE = "pipeline";
  private static final String TRIGGER = "trigger";
  private static final String INPUT_YAML = "inputYaml";

  private static final String DUP_KEY_EXP_FORMAT_STRING = "Trigger [%s] already exists or is soft deleted";

  @Override
  public NGTriggerEntity create(NGTriggerEntity ngTriggerEntity) {
    try {
      NGTriggerEntity savedNgTriggerEntity = ngTriggerRepository.save(ngTriggerEntity);
      performPostUpsertFlow(savedNgTriggerEntity);
      outboxService.save(new TriggerCreateEvent(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
          ngTriggerEntity.getProjectIdentifier(), savedNgTriggerEntity));
      return savedNgTriggerEntity;
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format(DUP_KEY_EXP_FORMAT_STRING, ngTriggerEntity.getIdentifier()), USER_SRE, e);
    }
  }

  private void performPostUpsertFlow(NGTriggerEntity ngTriggerEntity) {
    registerWebhookAsync(ngTriggerEntity);
    registerPollingAsync(validateTrigger(ngTriggerEntity));
  }

  private void registerPollingAsync(NGTriggerEntity ngTriggerEntity) {
    if (checkForValidationFailure(ngTriggerEntity)) {
      log.warn(
          String.format("Trigger Validation Failed for Trigger: %s, Skipping Polling Framework subscription request",
              TriggerHelper.getTriggerRef(ngTriggerEntity)));
      return;
    }

    // Polling not required for other trigger types
    if (ngTriggerEntity.getType() != ARTIFACT && ngTriggerEntity.getType() != MANIFEST) {
      return;
    }

    executorService.submit(() -> {
      PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);

      try {
        byte[] pollingItemBytes = kryoSerializer.asBytes(pollingItem);

        if (!ngTriggerEntity.getEnabled()
            && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE)) {
          updatePollingRegistrationStatus(ngTriggerEntity, null, StatusResult.SUCCESS);
        } else {
          ResponseDTO<PollingResponseDTO> responseDTO = executePollingSubscription(ngTriggerEntity, pollingItemBytes);
          PollingDocument pollingDocument =
              (PollingDocument) kryoSerializer.asObject(responseDTO.getData().getPollingResponse());
          updatePollingRegistrationStatus(ngTriggerEntity, pollingDocument, StatusResult.SUCCESS);
        }
      } catch (Exception exception) {
        log.error(String.format("Polling Subscription Request failed for Trigger: %s with error",
                      TriggerHelper.getTriggerRef(ngTriggerEntity)),
            exception);
        updatePollingRegistrationStatus(ngTriggerEntity, null, StatusResult.FAILED);
        throw new InvalidRequestException(exception.getMessage());
      }
    });
  }

  private ResponseDTO<PollingResponseDTO> executePollingSubscription(
      NGTriggerEntity ngTriggerEntity, byte[] pollingItemBytes) {
    try {
      return SafeHttpCall.executeWithExceptions(pollingResourceClient.subscribe(
          RequestBody.create(MediaType.parse("application/octet-stream"), pollingItemBytes)));

    } catch (Exception exception) {
      String msg = String.format("Polling Subscription Request failed for Trigger: %s with error ",
                       TriggerHelper.getTriggerRef(ngTriggerEntity))
          + exception;
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private Boolean executePollingUnSubscription(NGTriggerEntity ngTriggerEntity, byte[] pollingItemBytes) {
    try {
      return SafeHttpCall.executeWithExceptions(pollingResourceClient.unsubscribe(
          RequestBody.create(MediaType.parse("application/octet-stream"), pollingItemBytes)));
    } catch (Exception exception) {
      String msg = String.format("Polling Unsubscription Request failed for Trigger: %s with error ",
                       TriggerHelper.getTriggerRef(ngTriggerEntity))
          + exception;
      log.error(msg);
      throw new InvalidRequestException(msg);
    }
  }

  private boolean checkForValidationFailure(NGTriggerEntity ngTriggerEntity) {
    return null != ngTriggerEntity.getTriggerStatus()
        && ngTriggerEntity.getTriggerStatus().getValidationStatus() != null
        && ngTriggerEntity.getTriggerStatus().getValidationStatus().getStatusResult() != StatusResult.SUCCESS;
  }

  private void updatePollingRegistrationStatus(
      NGTriggerEntity ngTriggerEntity, PollingDocument pollingDocument, StatusResult statusResult) {
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);

    stampPollingStatusInfo(ngTriggerEntity, pollingDocument, statusResult);
    NGTriggerEntity updatedEntity = ngTriggerRepository.updateValidationStatusAndMetadata(criteria, ngTriggerEntity);

    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }

  private void stampPollingStatusInfo(
      NGTriggerEntity ngTriggerEntity, PollingDocument pollingDocument, StatusResult statusResult) {
    // change pollingDocId only if request was successful. Else, we dont know what happened.
    // In next trigger upsert, we will try again
    if (statusResult == StatusResult.SUCCESS) {
      String pollingDocId = null == pollingDocument ? null : pollingDocument.getPollingDocId();
      ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().setPollingDocId(pollingDocId);
    }

    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder().pollingSubscriptionStatus(PollingSubscriptionStatus.builder().build()).build());
    } else if (ngTriggerEntity.getTriggerStatus().getPollingSubscriptionStatus() == null) {
      ngTriggerEntity.getTriggerStatus().setPollingSubscriptionStatus(PollingSubscriptionStatus.builder().build());
    }
    ngTriggerEntity.getTriggerStatus().getPollingSubscriptionStatus().setStatusResult(statusResult);
  }

  private void registerWebhookAsync(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getType() == WEBHOOK && ngTriggerEntity.getMetadata().getWebhook().getGit() != null) {
      executorService.submit(() -> {
        WebhookRegistrationStatus registrationStatus =
            ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
        updateWebhookRegistrationStatus(ngTriggerEntity, registrationStatus);
      });
    }
  }

  @Override
  public Optional<NGTriggerEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, boolean deleted) {
    return ngTriggerRepository
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, targetIdentifier, identifier, !deleted);
  }

  @Override
  public NGTriggerEntity update(NGTriggerEntity ngTriggerEntity) {
    ngTriggerEntity.setYmlVersion(TRIGGER_CURRENT_YML_VERSION);
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);
    NGTriggerEntity updatedTriggerEntity = updateTriggerEntity(ngTriggerEntity, criteria);
    outboxService.save(new TriggerUpdateEvent(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), updatedTriggerEntity, ngTriggerEntity));
    return updatedTriggerEntity;
  }

  @NotNull
  private NGTriggerEntity updateTriggerEntity(NGTriggerEntity ngTriggerEntity, Criteria criteria) {
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }

    performPostUpsertFlow(updatedEntity);
    return updatedEntity;
  }

  @Override
  public boolean updateTriggerStatus(NGTriggerEntity ngTriggerEntity, boolean status) {
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);
    ngTriggerEntity.setEnabled(status);
    NGTriggerEntity updatedEntity = updateTriggerEntity(ngTriggerEntity, criteria);
    if (updatedEntity != null) {
      return updatedEntity.getEnabled();
    } else {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }

  @Override
  public Page<NGTriggerEntity> list(Criteria criteria, Pageable pageable) {
    return ngTriggerRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String targetIdentifier,
      String identifier, Long version) {
    Criteria criteria = getTriggerEqualityCriteria(
        accountId, orgIdentifier, projectIdentifier, targetIdentifier, identifier, false, version);

    Optional<NGTriggerEntity> ngTriggerEntity =
        get(accountId, orgIdentifier, projectIdentifier, targetIdentifier, identifier, false);
    UpdateResult deleteResult = ngTriggerRepository.delete(criteria);
    if (!deleteResult.wasAcknowledged() || deleteResult.getModifiedCount() != 1) {
      throw new InvalidRequestException(String.format("NGTrigger [%s] couldn't be deleted", identifier));
    }

    if (ngTriggerEntity.isPresent()) {
      NGTriggerEntity foundTriggerEntity = ngTriggerEntity.get();
      outboxService.save(new TriggerDeleteEvent(foundTriggerEntity.getAccountId(),
          foundTriggerEntity.getOrgIdentifier(), foundTriggerEntity.getProjectIdentifier(), foundTriggerEntity));
      if (foundTriggerEntity.getType() == MANIFEST || foundTriggerEntity.getType() == ARTIFACT) {
        log.info("Submitting unsubscribe request after delete for Trigger :"
            + TriggerHelper.getTriggerRef(foundTriggerEntity));
        submitUnsubscribeAsync(foundTriggerEntity);
      }
    }
    return true;
  }

  private void submitUnsubscribeAsync(NGTriggerEntity ngTriggerEntity) {
    // Fetch trigger to unsubscribe from polling
    if (ngTriggerEntity != null) {
      executorService.submit(() -> {
        try {
          PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);
          if (!executePollingUnSubscription(ngTriggerEntity, kryoSerializer.asBytes(pollingItem))) {
            log.warn(String.format("Trigger {} failed to unsubsribe from Polling", ngTriggerEntity.getIdentifier()));
          }
        } catch (Exception exception) {
          log.error(exception.getMessage());
        }
      });
    }
  }

  @Override
  public boolean deleteAllForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    String pipelineRef = new StringBuilder(128)
                             .append(accountId)
                             .append('/')
                             .append(orgIdentifier)
                             .append('/')
                             .append(projectIdentifier)
                             .append('/')
                             .append(pipelineIdentifier)
                             .toString();

    final AtomicBoolean exceptionOccured = new AtomicBoolean(false);

    try {
      Optional<List<NGTriggerEntity>> nonDeletedTriggerForPipeline =
          ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
              accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true);

      if (nonDeletedTriggerForPipeline.isPresent()) {
        log.info("Deleting triggers for pipeline-deletion-event. PipelineRef: " + pipelineRef);
        List<NGTriggerEntity> ngTriggerEntities = nonDeletedTriggerForPipeline.get();
        String triggerRef = new StringBuilder(128)
                                .append(accountId)
                                .append('/')
                                .append(orgIdentifier)
                                .append('/')
                                .append(projectIdentifier)
                                .append('/')
                                .append(pipelineIdentifier)
                                .append('/')
                                .append("{trigger}")
                                .toString();

        ngTriggerEntities.forEach(ngTriggerEntity -> {
          try {
            log.info("Deleting triggers for pipeline-deletion-event. TriggerRef: "
                + triggerRef.replace("{trigger}", ngTriggerEntity.getIdentifier()));
            delete(
                accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, ngTriggerEntity.getIdentifier(), null);
          } catch (Exception e) {
            log.error("Error while deleting trigger while processing pipeline-delete-event. TriggerRef: "
                + triggerRef.replace("{trigger}", ngTriggerEntity.getIdentifier()));
            exceptionOccured.set(true);
          }
        });
      } else {
        log.info("No non-deleted Trigger found while processing pipeline-deletion-event. PipelineRef: " + pipelineRef);
      }
    } catch (Exception e) {
      log.error("Error while deleting triggers while processing pipeline-delete-event. PipelineRef: " + pipelineRef);
      exceptionOccured.set(true);
    }

    return !exceptionOccured.get();
  }

  @Override
  public WebhookEventProcessingDetails fetchTriggerEventHistory(String accountId, String eventId) {
    TriggerEventHistory triggerEventHistory =
        triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(accountId, eventId);
    WebhookEventProcessingDetailsBuilder builder =
        WebhookEventProcessingDetails.builder().eventId(eventId).accountIdentifier(accountId);
    if (triggerEventHistory == null) {
      builder.eventFound(false);
    } else {
      builder.eventFound(true)
          .orgIdentifier(triggerEventHistory.getOrgIdentifier())
          .projectIdentifier(triggerEventHistory.getProjectIdentifier())
          .triggerIdentifier(triggerEventHistory.getTriggerIdentifier())
          .pipelineIdentifier(triggerEventHistory.getTargetIdentifier())
          .exceptionOccured(triggerEventHistory.isExceptionOccurred())
          .status(triggerEventHistory.getFinalStatus())
          .message(triggerEventHistory.getMessage())
          .payload(triggerEventHistory.getPayload())
          .eventCreatedAt(triggerEventHistory.getCreatedAt());

      if (triggerEventHistory.getTargetExecutionSummary() != null) {
        builder.pipelineExecutionId(triggerEventHistory.getTargetExecutionSummary().getPlanExecutionId())
            .runtimeInput(triggerEventHistory.getTargetExecutionSummary().getRuntimeInput());
      }
    }

    return builder.build();
  }

  @Override
  public TriggerWebhookEvent addEventToQueue(TriggerWebhookEvent webhookEventQueueRecord) {
    try {
      return webhookEventQueueRepository.save(webhookEventQueueRecord);
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be received");
    }
  }

  @Override
  public TriggerWebhookEvent updateTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord) {
    Criteria criteria = getTriggerWebhookEventEqualityCriteria(webhookEventQueueRecord);
    TriggerWebhookEvent updatedEntity = webhookEventQueueRepository.update(criteria, webhookEventQueueRecord);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          "TriggerWebhookEvent with uuid " + webhookEventQueueRecord.getUuid() + " could not be updated");
    }
    return updatedEntity;
  }

  @Override
  public void deleteTriggerWebhookEvent(TriggerWebhookEvent webhookEventQueueRecord) {
    webhookEventQueueRepository.delete(webhookEventQueueRecord);
  }

  @Override
  public List<NGTriggerEntity> findTriggersForCustomWehbook(
      TriggerWebhookEvent triggerWebhookEvent, boolean isDeleted, boolean enabled) {
    Page<NGTriggerEntity> triggersPage = list(TriggerFilterHelper.createCriteriaForCustomWebhookTriggerGetList(
                                                  triggerWebhookEvent, EMPTY, isDeleted, enabled),
        Pageable.unpaged());

    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public List<NGTriggerEntity> findTriggersForWehbookBySourceRepoType(
      TriggerWebhookEvent triggerWebhookEvent, boolean isDeleted, boolean enabled) {
    Page<NGTriggerEntity> triggersPage = list(TriggerFilterHelper.createCriteriaFormWebhookTriggerGetListByRepoType(
                                                  triggerWebhookEvent, EMPTY, isDeleted, enabled),
        Pageable.unpaged());

    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public List<NGTriggerEntity> findBuildTriggersByAccountIdAndSignature(String accountId, List<String> signatures) {
    Page<NGTriggerEntity> triggersPage =
        list(TriggerFilterHelper.createCriteriaFormBuildTriggerUsingAccIdAndSignature(accountId, signatures),
            Pageable.unpaged());
    return triggersPage.get().collect(Collectors.toList());
  }

  @Override
  public List<NGTriggerEntity> listEnabledTriggersForCurrentProject(
      String accountId, String orgIdentifier, String projectIdentifier) {
    Optional<List<NGTriggerEntity>> enabledTriggerForProject;

    // Now kept for backward compatibility, but will be changed soon to validate for non-empty project and
    // orgIdentifier.
    if (isNotEmpty(projectIdentifier) && isNotEmpty(orgIdentifier)) {
      enabledTriggerForProject =
          ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnabledAndDeletedNot(
              accountId, orgIdentifier, projectIdentifier, true, true);
    } else if (isNotEmpty(orgIdentifier)) {
      enabledTriggerForProject = ngTriggerRepository.findByAccountIdAndOrgIdentifierAndEnabledAndDeletedNot(
          accountId, orgIdentifier, true, true);
    } else {
      enabledTriggerForProject = ngTriggerRepository.findByAccountIdAndEnabledAndDeletedNot(accountId, true, true);
    }

    if (enabledTriggerForProject.isPresent()) {
      return enabledTriggerForProject.get();
    }

    return emptyList();
  }

  @Override
  public List<NGTriggerEntity> listEnabledTriggersForAccount(String accountId) {
    return listEnabledTriggersForCurrentProject(accountId, null, null);
  }

  @Override
  public List<ConnectorResponseDTO> fetchConnectorsByFQN(String accountIdentifier, List<String> fqns) {
    if (isEmpty(fqns)) {
      return emptyList();
    }
    try {
      return SafeHttpCall.executeWithExceptions(connectorResourceClient.listConnectorByFQN(accountIdentifier, fqns))
          .getData();
    } catch (Exception e) {
      log.error("Failed while retrieving connectors", e);
      throw new TriggerException("Failed while retrieving connectors" + e.getMessage(), e, USER_SRE);
    }
  }

  @Override
  public void validateTriggerConfig(TriggerDetails triggerDetails) {
    // will be returned if certain conditions are not met. Either use this as a gateway or spin off a specific class
    // for the validation.

    // trigger source validation
    if (isBlank(triggerDetails.getNgTriggerEntity().getIdentifier())) {
      throw new InvalidArgumentsException("Identifier can not be empty");
    }

    if (isBlank(triggerDetails.getNgTriggerEntity().getName())) {
      throw new InvalidArgumentsException("Name can not be empty");
    }

    if (isNotEmpty(triggerDetails.getNgTriggerConfigV2().getInputYaml())) {
      validatePipelineRef(triggerDetails);
    }

    NGTriggerSourceV2 triggerSource = triggerDetails.getNgTriggerConfigV2().getSource();
    NGTriggerSpecV2 spec = triggerSource.getSpec();
    switch (triggerSource.getType()) {
      case WEBHOOK:
        return; // TODO(adwait): define trigger source validation
      case SCHEDULED:
        ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) triggerSource.getSpec();
        CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
        CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        Cron cron = cronParser.parse(cronTriggerSpec.getExpression());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        Optional<ZonedDateTime> nextTime = executionTime.nextExecution(ZonedDateTime.now());
        if (!nextTime.isPresent()) {
          throw new InvalidArgumentsException("cannot find iteration time!");
        }
        return;
      case MANIFEST:
        validateStageIdentifierAndBuildRef((BuildAware) spec, "manifestRef");
        return;
      case ARTIFACT:
        validateStageIdentifierAndBuildRef((BuildAware) spec, "artifactRef");
        return;
      default:
        return; // not implemented
    }
  }

  @Override
  public void validateInputSets(TriggerDetails triggerDetails) {
    MergeInputSetResponseDTOPMS mergeInputSetResponseDTOPMS = validateInputSetsInternal(triggerDetails);
    if (mergeInputSetResponseDTOPMS != null
        && (mergeInputSetResponseDTOPMS.isErrorResponse()
            || mergeInputSetResponseDTOPMS.getInputSetErrorWrapper() != null)) {
      InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS = mergeInputSetResponseDTOPMS.getInputSetErrorWrapper();

      Map<String, Map<String, String>> errorMap = generateErrorMap(inputSetErrorWrapperDTOPMS);
      throw new InvalidTriggerYamlException("Invalid Yaml", errorMap, triggerDetails, null);
    }
  }

  public MergeInputSetResponseDTOPMS validateInputSetsInternal(TriggerDetails triggerDetails) {
    if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
      validatePipelineRef(triggerDetails);
      return null;
    } else {
      NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        return null;
      }
      NGTriggerConfigV2 triggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity);
      String pipelineBranch = triggerConfigV2.getPipelineBranchName();
      String branch = null;
      if (isNotEmpty(pipelineBranch) && !isBranchExpr(pipelineBranch)) {
        branch = pipelineBranch;
      }
      List<String> inputSetRefs = triggerConfigV2.getInputSetRefs();
      return NGRestUtils.getResponse(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(
          ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(),
          ngTriggerEntity.getTargetIdentifier(), branch,
          MergeInputSetRequestDTOPMS.builder().inputSetReferences(inputSetRefs).build()));
    }
  }

  private void validatePipelineRef(TriggerDetails triggerDetails) {
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    Optional<String> pipelineYmlOptional = validationHelper.fetchPipelineForTrigger(ngTriggerEntity);
    if (pipelineYmlOptional.isPresent()) {
      String pipelineYaml = pipelineYmlOptional.get();
      String templateYaml = createRuntimeInputForm(pipelineYaml);
      String triggerYaml = triggerDetails.getNgTriggerEntity().getYaml();
      String triggerPipelineYml = getPipelineComponent(triggerYaml);
      Map<FQN, String> invalidFQNs = getInvalidFQNsInTrigger(templateYaml, triggerPipelineYml);
      if (EmptyPredicate.isEmpty(invalidFQNs)) {
        return;
      }
      Map<String, Map<String, String>> errorMap = new HashMap<>();
      for (Map.Entry<FQN, String> entry : invalidFQNs.entrySet()) {
        Map<String, String> innerMap = new HashMap<>();
        innerMap.put("fieldName", entry.getKey().getFieldName());
        innerMap.put("message", entry.getValue());
        errorMap.put(entry.getKey().getExpressionFqn(), innerMap);
      }
      throw new InvalidTriggerYamlException("Invalid Yaml", errorMap, triggerDetails, null);
    }
  }

  private String getPipelineComponent(String triggerYml) {
    try {
      if (EmptyPredicate.isEmpty(triggerYml)) {
        return triggerYml;
      }
      JsonNode node = YamlUtils.readTree(triggerYml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get(TRIGGER);
      if (innerMap == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      JsonNode inputYaml = innerMap.get(INPUT_YAML);
      if (inputYaml == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      JsonNode pipelineNode = YamlUtils.readTree(inputYaml.asText()).getNode().getCurrJsonNode();
      return YamlUtils.write(pipelineNode).replace("---\n", "");
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid Trigger Yaml", e);
    }
  }

  public Map<FQN, String> getInvalidFQNsInTrigger(String templateYaml, String triggerPipelineCompYaml) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    YamlConfig triggerConfig = new YamlConfig(triggerPipelineCompYaml);
    Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
    if (EmptyPredicate.isEmpty(templateYaml)) {
      triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains any runtime input"));
      return errorMap;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);

    // Make sure everything in trigger exist in pipeline
    templateConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (triggerFQNs.contains(key)) {
        Object templateValue = templateConfig.getFqnToValueMap().get(key);
        Object value = triggerConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            errorMap.put(key,
                "The value for " + key.getExpressionFqn() + " is " + templateValue.toString()
                    + "in the pipeline yaml, but the trigger has it as " + value.toString());
          }
        } else {
          String error = validateStaticValues(templateValue, value);
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        triggerFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(triggerConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(triggerFQNs::remove);
      }
    });
    triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Field either not present in pipeline or not a runtime input"));
    return errorMap;
  }

  public String createRuntimeInputForm(String yaml) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\"", "");
      if (NGExpressionUtils.matchesInputSetPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  private void validateStageIdentifierAndBuildRef(BuildAware buildAware, String fieldName) {
    StringBuilder msg = new StringBuilder(128);
    boolean validationFailed = false;
    if (isBlank(buildAware.fetchStageRef())) {
      msg.append("stageIdentifier can not be blank/missing. ");
      validationFailed = true;
    }
    if (isBlank(buildAware.fetchbuildRef())) {
      msg.append(fieldName).append(" can not be blank/missing. ");
      validationFailed = true;
    }

    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  private void updateWebhookRegistrationStatus(
      NGTriggerEntity ngTriggerEntity, WebhookRegistrationStatus registrationStatus) {
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);
    stampWebhookRegistrationInfo(ngTriggerEntity, registrationStatus);
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }

  private void stampWebhookRegistrationInfo(
      NGTriggerEntity ngTriggerEntity, WebhookRegistrationStatus registrationStatus) {
    // TODO: Needs to be removed later, as will re replaced by new TriggerStatus
    ngTriggerEntity.getMetadata().getWebhook().setRegistrationStatus(registrationStatus);
    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder()
              .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder().build())
              .build());
    } else if (ngTriggerEntity.getTriggerStatus().getWebhookAutoRegistrationStatus() == null) {
      ngTriggerEntity.getTriggerStatus().setWebhookAutoRegistrationStatus(
          WebhookAutoRegistrationStatus.builder().build());
    }
    ngTriggerEntity.getTriggerStatus().getWebhookAutoRegistrationStatus().setRegistrationResult(registrationStatus);
  }

  private Criteria getTriggerWebhookEventEqualityCriteria(TriggerWebhookEvent webhookEventQueueRecord) {
    return Criteria.where(TriggerWebhookEventsKeys.uuid).is(webhookEventQueueRecord.getUuid());
  }

  private Criteria getTriggerEqualityCriteria(NGTriggerEntity ngTriggerEntity, boolean deleted) {
    return getTriggerEqualityCriteria(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
        deleted, ngTriggerEntity.getVersion());
  }

  private Criteria getTriggerEqualityCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, String identifier, boolean deleted, Long version) {
    Criteria criteria = Criteria.where(NGTriggerEntityKeys.accountId)
                            .is(accountId)
                            .and(NGTriggerEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NGTriggerEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NGTriggerEntityKeys.targetIdentifier)
                            .is(targetIdentifier)
                            .and(NGTriggerEntityKeys.identifier)
                            .is(identifier)
                            .and(NGTriggerEntityKeys.deleted)
                            .is(deleted);
    if (version != null) {
      criteria.and(NGTriggerEntityKeys.version).is(version);
    }
    return criteria;
  }

  public NGTriggerEntity validateTrigger(NGTriggerEntity ngTriggerEntity) {
    try {
      ValidationResult validationResult = triggerValidationHandler.applyValidations(
          ngTriggerElementMapper.toTriggerDetails(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
              ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getYaml()));
      if (!validationResult.isSuccess()) {
        ngTriggerEntity.setEnabled(false);
      }
      return updateTriggerWithValidationStatus(ngTriggerEntity, validationResult);
    } catch (Exception e) {
      log.error(String.format("Failed in trigger validation for Trigger: {}", ngTriggerEntity.getIdentifier()), e);
    }
    return ngTriggerEntity;
  }

  /*
  This function is to update ValidatioStatus. Will be used by triggerlist to display the status of trigger in case of
  failure in Polling, etc
   */
  public NGTriggerEntity updateTriggerWithValidationStatus(
      NGTriggerEntity ngTriggerEntity, ValidationResult validationResult) {
    String identifier = ngTriggerEntity.getIdentifier();
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);
    boolean needsUpdate = false;

    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder().validationStatus(ValidationStatus.builder().build()).build());
    } else if (ngTriggerEntity.getTriggerStatus().getValidationStatus() == null) {
      ngTriggerEntity.getTriggerStatus().setValidationStatus(ValidationStatus.builder().build());
    }

    if (validationResult.isSuccess() && ngTriggerEntity.getTriggerStatus().getValidationStatus() != null
        && ngTriggerEntity.getTriggerStatus().getValidationStatus().getStatusResult() != StatusResult.SUCCESS) {
      // Validation result was a failure and now it's a success
      ngTriggerEntity.getTriggerStatus().setValidationStatus(
          ValidationStatus.builder().statusResult(StatusResult.SUCCESS).build());
      needsUpdate = true;
    } else if (!validationResult.isSuccess()) {
      // Validation failed
      ngTriggerEntity.getTriggerStatus().setValidationStatus(ValidationStatus.builder()
                                                                 .statusResult(StatusResult.FAILED)
                                                                 .detailedMessage(validationResult.getMessage())
                                                                 .build());
      ngTriggerEntity.setEnabled(false);
      needsUpdate = true;
    }

    if (needsUpdate) {
      // enabled filed is part of yml as well as extracted at the entity level.
      // if we are setting it to false, we need to update yml content as well.
      // With gitsync, we need to brainstorm
      ngTriggerElementMapper.updateEntityYmlWithEnabledValue(ngTriggerEntity);
      ngTriggerEntity = ngTriggerRepository.updateValidationStatus(criteria, ngTriggerEntity);
      if (ngTriggerEntity == null) {
        throw new InvalidRequestException(
            String.format("NGTrigger [%s] couldn't be updated or doesn't exist", identifier));
      }
    }

    return ngTriggerEntity;
  }

  @Override
  public Map<String, Map<String, String>> generateErrorMap(InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS) {
    return buildTriggerHelper.generateErrorMap(inputSetErrorWrapperDTOPMS);
  }

  @Override
  public TriggerDetails fetchTriggerEntity(
      String accountId, String orgId, String projectId, String pipelineId, String triggerId, String newYaml) {
    NGTriggerConfigV2 config = ngTriggerElementMapper.toTriggerConfigV2(newYaml);
    Optional<NGTriggerEntity> existingEntity = get(accountId, orgId, projectId, pipelineId, triggerId, false);
    NGTriggerEntity entity = ngTriggerElementMapper.toTriggerEntity(accountId, orgId, projectId, triggerId, newYaml);
    if (existingEntity.isPresent()) {
      ngTriggerElementMapper.copyEntityFieldsOutsideOfYml(existingEntity.get(), entity);
    }

    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  @VisibleForTesting
  public boolean isBranchExpr(String pipelineBranch) {
    return pipelineBranch.startsWith(EngineExpressionEvaluator.EXPR_START)
        && pipelineBranch.endsWith(EngineExpressionEvaluator.EXPR_END);
  }
}
