/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION;
import static io.harness.ngtriggers.Constants.MANDATE_CUSTOM_WEBHOOK_TRUE_VALUE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.YamlFields.PIPELINE_BRANCH_NAME;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.common.NGExpressionUtils;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.dto.PollingResponseDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.TriggerException;
import io.harness.exception.ngexception.NGPipelineNotFoundException;
import io.harness.expression.common.ExpressionConstants;
import io.harness.gitsync.beans.StoreType;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails.WebhookEventProcessingDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventsKeys;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.status.PollingSubscriptionStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.StatusResult;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.ValidationStatus;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.artifact.MultiArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.ngtriggers.helpers.TriggerCatalogHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.TriggerSetupUsageHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.ngtriggers.utils.PollingSubscriptionHelper;
import io.harness.ngtriggers.utils.TriggerReferenceHelper;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.ngtriggers.validations.ValidationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.service.PollingDocument;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.repositories.spring.TriggerWebhookEventRepository;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagService;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.CollectionUtils;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerServiceImpl implements NGTriggerService {
  public static final long TRIGGER_CURRENT_YML_VERSION = 3l;
  public static final int WEBHOOK_POLLING_UNSUBSCRIBE = 0;
  public static final int WEBHOOOk_POLLING_MIN_INTERVAL = 2;
  public static final int WEBHOOOk_POLLING_MAX_INTERVAL = 60;
  private static final long MIN_INTERVAL_MINUTES = 5;

  private final AccessControlClient accessControlClient;
  private final NGSettingsClient settingsClient;
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
  private final TriggerCatalogHelper triggerCatalogHelper;
  private final PollingResourceClient pollingResourceClient;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final OutboxService outboxService;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final BuildTriggerHelper validationHelper;
  private final NGTriggerYamlSchemaService ngTriggerYamlSchemaService;
  private final TriggerReferenceHelper triggerReferenceHelper;
  private final TriggerSetupUsageHelper triggerSetupUsageHelper;
  private static final String TRIGGER = "trigger";
  private static final String INPUT_YAML = "inputYaml";

  private static final String DUP_KEY_EXP_FORMAT_STRING = "Trigger [%s] already exists or is soft deleted";

  @Override
  public NGTriggerEntity create(NGTriggerEntity ngTriggerEntity) {
    if (pmsFeatureFlagService.isEnabled(
            ngTriggerEntity.getAccountId(), FeatureName.CDS_ENABLE_TRIGGER_YAML_VALIDATION)) {
      ngTriggerYamlSchemaService.validateTriggerYaml(ngTriggerEntity.getYaml(), ngTriggerEntity.getProjectIdentifier(),
          ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getIdentifier());
    }
    try {
      populateCustomWebhookTokenForCustomWebhookTriggers(ngTriggerEntity);
      NGTriggerEntity savedNgTriggerEntity = ngTriggerRepository.save(ngTriggerEntity);
      performPostUpsertFlow(savedNgTriggerEntity, false);
      outboxService.save(new TriggerCreateEvent(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
          ngTriggerEntity.getProjectIdentifier(), savedNgTriggerEntity));
      try {
        List<EntityDetailProtoDTO> referredEntities = triggerReferenceHelper.getReferences(
            ngTriggerEntity.getAccountId(), ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity));
        triggerSetupUsageHelper.publishSetupUsageEvent(ngTriggerEntity, referredEntities);
      } catch (Exception ex) {
        log.error("Error publishing the setup usages for the trigger with the identifier {}, in project {} in org {}",
            ngTriggerEntity.getAccountId(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier(),
            ex);
      }
      return savedNgTriggerEntity;
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format(DUP_KEY_EXP_FORMAT_STRING, ngTriggerEntity.getIdentifier()), USER_SRE, e);
    }
  }

  private void populateCustomWebhookTokenForCustomWebhookTriggers(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getMetadata().getWebhook() != null
        && ngTriggerEntity.getMetadata().getWebhook().getCustom() != null) {
      ngTriggerEntity.setCustomWebhookToken(generateUuid());
    }
  }

  private void performPostUpsertFlow(NGTriggerEntity ngTriggerEntity, boolean isUpdate) {
    NGTriggerEntity validatedTrigger = validateTrigger(ngTriggerEntity);
    registerWebhookAsync(validatedTrigger);
    registerPollingAsync(validatedTrigger, isUpdate);
  }

  private void registerPollingAsync(NGTriggerEntity ngTriggerEntity, boolean isUpdate) {
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

    executorService.submit(() -> { subscribePolling(ngTriggerEntity, isUpdate); });
  }

  private void subscribePolling(NGTriggerEntity ngTriggerEntity, boolean isUpdate) {
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);

    try {
      byte[] pollingItemBytes = kryoSerializer.asBytes(pollingItem);

      if (!ngTriggerEntity.getEnabled()
          && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE)) {
        updatePollingRegistrationStatus(ngTriggerEntity, null, StatusResult.SUCCESS);
      } else if (isWebhookGitPollingEnabled(ngTriggerEntity)
          && NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(ngTriggerEntity.getPollInterval())
              == WEBHOOK_POLLING_UNSUBSCRIBE
          && executePollingUnSubscription(ngTriggerEntity, pollingItemBytes).equals(Boolean.TRUE)) {
        updatePollingRegistrationStatus(ngTriggerEntity, null, StatusResult.SUCCESS);
      } else {
        if (isUpdate) {
          executePollingUnSubscription(ngTriggerEntity, pollingItemBytes);
        }

        ResponseDTO<PollingResponseDTO> responseDTO = executePollingSubscription(ngTriggerEntity, pollingItemBytes);
        PollingDocument pollingDocument =
            (PollingDocument) kryoSerializer.asObject(responseDTO.getData().getPollingResponse());
        try (
            AutoLogContext ignore0 = new NgTriggerAutoLogContext("pollingDocumentId", pollingDocument.getPollingDocId(),
                ngTriggerEntity.getIdentifier(), ngTriggerEntity.getTargetIdentifier(),
                ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier(),
                ngTriggerEntity.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          log.info("Polling Subscription successful for Trigger {} with pollingDocumentId {}",
              ngTriggerEntity.getIdentifier(), pollingDocument.getPollingDocId());
          updatePollingRegistrationStatus(ngTriggerEntity, pollingDocument, StatusResult.SUCCESS);
        }
      }
    } catch (Exception exception) {
      log.error(String.format("Polling Subscription Request failed for Trigger: %s with error",
                    TriggerHelper.getTriggerRef(ngTriggerEntity)),
          exception);
      updatePollingRegistrationStatus(ngTriggerEntity, null, StatusResult.FAILED);
      throw new InvalidRequestException(exception.getMessage());
    }
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
    Criteria criteria = getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, false);

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
        WebhookRegistrationStatusData registrationStatus =
            ngTriggerWebhookRegistrationService.registerWebhook(ngTriggerEntity);
        updateWebhookRegistrationStatus(ngTriggerEntity, registrationStatus);
        checkAndEnableWebhookPolling(ngTriggerEntity);
      });
    }
  }
  private void checkAndEnableWebhookPolling(NGTriggerEntity ngTriggerEntity) {
    if (pmsFeatureFlagService.isEnabled(ngTriggerEntity.getAccountId(), FeatureName.CD_GIT_WEBHOOK_POLLING)
        && GITHUB.getEntityMetadataName().equalsIgnoreCase(ngTriggerEntity.getMetadata().getWebhook().getType())) {
      String webhookId = ngTriggerEntity.getTriggerStatus().getWebhookInfo().getWebhookId();
      String pollInterval = ngTriggerEntity.getPollInterval();

      if (StringUtils.isEmpty(webhookId) || StringUtils.isEmpty(pollInterval)) {
        log.error(String.format("Either Webhook Id or Poll Interval null. Polling cannot be enabled for the trigger %s"
                + ", webhookId %s, pollInterval %s}",
            ngTriggerEntity.getIdentifier(), webhookId, pollInterval));
        return;
      }
      subscribePolling(ngTriggerEntity, false);
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
    if (pmsFeatureFlagService.isEnabled(
            ngTriggerEntity.getAccountId(), FeatureName.CDS_ENABLE_TRIGGER_YAML_VALIDATION)) {
      ngTriggerYamlSchemaService.validateTriggerYaml(ngTriggerEntity.getYaml(), ngTriggerEntity.getProjectIdentifier(),
          ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getIdentifier());
    }
    Criteria criteria = getTriggerEqualityCriteria(ngTriggerEntity, false);
    NGTriggerEntity updatedTriggerEntity = updateTriggerEntity(ngTriggerEntity, criteria);
    outboxService.save(new TriggerUpdateEvent(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), updatedTriggerEntity, ngTriggerEntity));
    try {
      List<EntityDetailProtoDTO> referredEntities = triggerReferenceHelper.getReferences(
          updatedTriggerEntity.getAccountId(), ngTriggerElementMapper.toTriggerConfigV2(updatedTriggerEntity));
      triggerSetupUsageHelper.publishSetupUsageEvent(updatedTriggerEntity, referredEntities);
    } catch (Exception ex) {
      log.error("Error publishing the setup usages for the trigger with the identifier {} in project {} in org {}",
          updatedTriggerEntity.getIdentifier(), updatedTriggerEntity.getProjectIdentifier(),
          updatedTriggerEntity.getOrgIdentifier(), ex);
    }
    return updatedTriggerEntity;
  }

  @NotNull
  private NGTriggerEntity updateTriggerEntity(NGTriggerEntity ngTriggerEntity, Criteria criteria) {
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }

    performPostUpsertFlow(updatedEntity, true);
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

    DeleteResult hardDeleteResult = ngTriggerRepository.hardDelete(criteria);
    if (!hardDeleteResult.wasAcknowledged()) {
      throw new InvalidRequestException(String.format("NGTrigger [%s] couldn't hard delete", identifier));
    }
    log.info("NGTrigger {} hard delete successful", identifier);

    if (ngTriggerEntity.isPresent()) {
      NGTriggerEntity foundTriggerEntity = ngTriggerEntity.get();
      outboxService.save(new TriggerDeleteEvent(foundTriggerEntity.getAccountId(),
          foundTriggerEntity.getOrgIdentifier(), foundTriggerEntity.getProjectIdentifier(), foundTriggerEntity));

      boolean isWebhookGitPollingEnabled = isWebhookGitPollingEnabled(foundTriggerEntity);
      if (foundTriggerEntity.getType() == MANIFEST || foundTriggerEntity.getType() == ARTIFACT
          || isWebhookGitPollingEnabled) {
        log.info("Submitting unsubscribe request after delete for Trigger :"
            + TriggerHelper.getTriggerRef(foundTriggerEntity));
        submitUnsubscribeAsync(foundTriggerEntity);
        try {
          triggerSetupUsageHelper.deleteExistingSetupUsages(foundTriggerEntity);
        } catch (Exception ex) {
          log.error(
              "Error while deleting the setup usages for the trigger with the identifier {} in project {} in org {}",
              foundTriggerEntity.getIdentifier(), foundTriggerEntity.getProjectIdentifier(),
              foundTriggerEntity.getOrgIdentifier(), ex);
        }
      }
    }
    return true;
  }

  private boolean isWebhookGitPollingEnabled(NGTriggerEntity foundTriggerEntity) {
    if (foundTriggerEntity.getType() == WEBHOOK
        && GITHUB.getEntityMetadataName().equalsIgnoreCase(foundTriggerEntity.getMetadata().getWebhook().getType())
        && pmsFeatureFlagService.isEnabled(foundTriggerEntity.getAccountId(), FeatureName.CD_GIT_WEBHOOK_POLLING)) {
      if (foundTriggerEntity.getTriggerStatus().getWebhookInfo() != null) {
        String webhookId = foundTriggerEntity.getTriggerStatus().getWebhookInfo().getWebhookId();
        String pollInterval = foundTriggerEntity.getPollInterval();
        return !StringUtils.isEmpty(webhookId) && !StringUtils.isEmpty(pollInterval);
      }
    }
    return false;
  }

  private void submitUnsubscribeAsync(NGTriggerEntity ngTriggerEntity) {
    // Fetch trigger to unsubscribe from polling
    if (ngTriggerEntity != null) {
      executorService.submit(() -> {
        try {
          PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);
          if (!executePollingUnSubscription(ngTriggerEntity, kryoSerializer.asBytes(pollingItem))) {
            log.warn(String.format("Trigger %s failed to unsubsribe from Polling", ngTriggerEntity.getIdentifier()));
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
    List<TriggerEventHistory> triggerEventHistoryList =
        triggerEventHistoryRepository.findByAccountIdAndEventCorrelationId(accountId, eventId);
    if (triggerEventHistoryList.size() == 0) {
      throw new InvalidRequestException(String.format("Trigger event history %s does not exist", eventId));
    }
    TriggerEventHistory triggerEventHistory = triggerEventHistoryList.get(0);
    String warningMsg = null;
    if (triggerEventHistoryList.size() > 1) {
      warningMsg =
          "There are multiple trigger events generated from this eventId. This response contains only one of them.";
    }
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
          .eventCreatedAt(triggerEventHistory.getCreatedAt())
          .warningMsg(warningMsg);

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
  public Optional<NGTriggerEntity> findTriggersForCustomWebhookViaCustomWebhookToken(String webhookToken) {
    return ngTriggerRepository.findByCustomWebhookToken(webhookToken);
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

    NGTriggerSourceV2 triggerSource = triggerDetails.getNgTriggerConfigV2().getSource();
    NGTriggerSpecV2 spec = triggerSource.getSpec();
    switch (triggerSource.getType()) {
      case WEBHOOK:
        // Validate webhook polling trigger
        WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) triggerSource.getSpec();
        if (webhookTriggerConfig.getType() != GITHUB) {
          return;
        }

        if (pmsFeatureFlagService.isEnabled(
                triggerDetails.getNgTriggerEntity().getAccountId(), FeatureName.CD_GIT_WEBHOOK_POLLING)) {
          String pollInterval = triggerDetails.getNgTriggerEntity().getPollInterval();
          if (StringUtils.isEmpty(pollInterval)) {
            log.info("Polling not enabled for trigger {}, pollInterval {} ",
                triggerDetails.getNgTriggerEntity().getIdentifier(), pollInterval);
            return;
          }
          int pollInt = NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
              triggerDetails.getNgTriggerEntity().getPollInterval());
          if (pollInt != WEBHOOK_POLLING_UNSUBSCRIBE
              && (pollInt < WEBHOOOk_POLLING_MIN_INTERVAL || pollInt > WEBHOOOk_POLLING_MAX_INTERVAL)) {
            throw new InvalidArgumentsException("Poll Interval should be between " + WEBHOOOk_POLLING_MIN_INTERVAL
                + " and " + WEBHOOOk_POLLING_MAX_INTERVAL + " minutes");
          }
        }
        return; // TODO define other trigger source validation
      case SCHEDULED:
        ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) triggerSource.getSpec();
        CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
        CronParser cronParser;
        if (cronTriggerSpec.getType() != null && cronTriggerSpec.getType().equalsIgnoreCase("QUARTZ")) {
          cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        } else {
          cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        }
        Cron cron = cronParser.parse(cronTriggerSpec.getExpression());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        Optional<ZonedDateTime> firstExecutionTimeOptional = executionTime.nextExecution(ZonedDateTime.now());
        if (!firstExecutionTimeOptional.isPresent()) {
          throw new InvalidArgumentsException("cannot find iteration time!");
        }
        ZonedDateTime firstExecutionTime = firstExecutionTimeOptional.get();
        Optional<ZonedDateTime> secondExecutionTimeOptional = executionTime.nextExecution(firstExecutionTime);
        if (secondExecutionTimeOptional.isPresent()) {
          ZonedDateTime secondExecutionTime = secondExecutionTimeOptional.get();
          if (Duration.between(firstExecutionTime, secondExecutionTime).getSeconds() < MIN_INTERVAL_MINUTES * 60) {
            throw new InvalidArgumentsException("Cron interval must be greater than or equal to " + MIN_INTERVAL_MINUTES
                + " minutes. The next two execution times when this trigger is suppose to fire are "
                + firstExecutionTime.toLocalTime().toString() + " and " + secondExecutionTime.toLocalTime().toString()
                + " which do not have a difference of " + MIN_INTERVAL_MINUTES + " minutes between them.");
          }
        }
        return;
      case MANIFEST:
        validateStageIdentifierAndBuildRef(
            (BuildAware) spec, "manifestRef", triggerDetails.getNgTriggerEntity().getWithServiceV2());
        return;
      case ARTIFACT:
        validateStageIdentifierAndBuildRef(
            (BuildAware) spec, "artifactRef", triggerDetails.getNgTriggerEntity().getWithServiceV2());
        return;
      case MULTI_ARTIFACT:
        if (!pmsFeatureFlagService.isEnabled(
                triggerDetails.getNgTriggerEntity().getAccountId(), FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)) {
          throw new InvalidRequestException(
              "Feature Flag CDS_NG_TRIGGER_MULTI_ARTIFACTS must be enabled for creation of multi-artifact triggers.");
        }
        validateMultiArtifactTriggerConfig(
            (MultiArtifactTriggerConfig) spec, triggerDetails.getNgTriggerEntity().getWithServiceV2());
        return;
      default:
        return; // not implemented
    }
  }

  @Override
  public void validatePipelineRef(TriggerDetails triggerDetails) {
    if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
      PMSPipelineResponseDTO pipelineResponse = validationHelper.fetchPipelineForTrigger(triggerDetails);
      if (pipelineResponse != null && pipelineResponse.getStoreType() == StoreType.REMOTE
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())) {
        throw new InvalidRequestException("pipelineBranchName is missing or is empty.");
      }
    }
  }

  private String getPipelineComponent(String triggerYml) {
    try {
      if (isEmpty(triggerYml)) {
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

  private String setPipelineComponent(String triggerYml, String pipelineComponent) {
    try {
      if (isEmpty(triggerYml)) {
        return triggerYml;
      }
      JsonNode node = YamlUtils.readTree(triggerYml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get(TRIGGER);
      if (innerMap == null) {
        throw new InvalidRequestException("Invalid Trigger Yaml.");
      }
      ObjectMapper objectMapper = ngTriggerElementMapper.getObjectMapper();
      innerMap.set(INPUT_YAML, new TextNode(pipelineComponent));
      return objectMapper.writeValueAsString(node);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid Trigger Yaml", e);
    }
  }

  public Map<FQN, String> getInvalidFQNsInTrigger(
      String templateYaml, String triggerPipelineCompYaml, String accountIdentifier) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    YamlConfig triggerConfig = new YamlConfig(triggerPipelineCompYaml);
    Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
    if (isEmpty(templateYaml)) {
      triggerFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains runtime input"));
      return errorMap;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);

    if (CollectionUtils.isEmpty(triggerFQNs)) {
      templateConfig.getFqnToValueMap().keySet().forEach(
          fqn -> errorMap.put(fqn, "Trigger does not contain pipeline runtime input"));
      return errorMap;
    }

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
          String error = validateStaticValues(templateValue, value, key.getExpressionFqn());
          if (isNotEmpty(error)) {
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

  private void validateStageIdentifierAndBuildRef(BuildAware buildAware, String fieldName, boolean serviceV2) {
    StringBuilder msg = new StringBuilder(128);
    boolean validationFailed = false;
    if (serviceV2 == false && isBlank(buildAware.fetchStageRef())) {
      msg.append("stageIdentifier can not be blank/missing. ");
      validationFailed = true;
    }
    if (serviceV2 == false && isBlank(buildAware.fetchbuildRef())) {
      msg.append(fieldName).append(" can not be blank/missing. ");
      validationFailed = true;
    }

    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  private void validateMultiArtifactTriggerConfig(MultiArtifactTriggerConfig triggerConfig, boolean serviceV2) {
    StringBuilder msg = new StringBuilder(128);
    boolean validationFailed = false;
    if (!serviceV2) {
      msg.append("Multi-Artifact triggers are only supported with Service V2.\n");
      validationFailed = true;
    }
    if (triggerConfig.getType() == null) {
      msg.append("Multi-Artifact trigger source type must have a valid artifact source type value.\n");
    }
    if (isEmpty(triggerConfig.getSources())) {
      msg.append("Multi-Artifact trigger sources list must have at least one element.\n");
      validationFailed = true;
    }
    if (isNotEmpty(triggerConfig.getSources())) {
      String artifactBuildType = triggerConfig.fetchBuildType();
      for (ArtifactTypeSpec artifactSource : triggerConfig.getSources()) {
        if (!artifactBuildType.equals(artifactSource.fetchBuildType())) {
          msg.append("Multi-Artifact sources must all be of type ").append(artifactBuildType).append(".\n");
          validationFailed = true;
          break;
        }
      }
    }
    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  private void updateWebhookRegistrationStatus(
      NGTriggerEntity ngTriggerEntity, WebhookRegistrationStatusData registrationStatus) {
    Criteria criteria = getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, false);
    TriggerHelper.stampWebhookRegistrationInfo(ngTriggerEntity, registrationStatus.getWebhookAutoRegistrationStatus());
    TriggerHelper.stampWebhookIdInfo(ngTriggerEntity, registrationStatus.getWebhookId());
    NGTriggerEntity updatedEntity = ngTriggerRepository.update(criteria, ngTriggerEntity);
    if (updatedEntity == null) {
      throw new InvalidRequestException(
          String.format("NGTrigger [%s] couldn't be updated or doesn't exist", ngTriggerEntity.getIdentifier()));
    }
  }
  private Criteria getTriggerWebhookEventEqualityCriteria(TriggerWebhookEvent webhookEventQueueRecord) {
    return Criteria.where(TriggerWebhookEventsKeys.uuid).is(webhookEventQueueRecord.getUuid());
  }

  private Criteria getTriggerEqualityCriteria(NGTriggerEntity ngTriggerEntity, boolean deleted) {
    return getTriggerEqualityCriteria(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
        deleted, ngTriggerEntity.getVersion());
  }

  private Criteria getTriggerEqualityCriteriaWithoutDbVersion(NGTriggerEntity ngTriggerEntity, boolean deleted) {
    return getTriggerEqualityCriteria(ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
        deleted, null);
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
              ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getYaml(), ngTriggerEntity.getWithServiceV2()));
      if (!validationResult.isSuccess()) {
        ngTriggerEntity.setEnabled(false);
      }
      return updateTriggerWithValidationStatus(ngTriggerEntity, validationResult, false);
    } catch (Exception e) {
      log.error(String.format("Failed in trigger validation for Trigger: %s", ngTriggerEntity.getIdentifier()), e);
    }
    return ngTriggerEntity;
  }

  /*
  This function is to update ValidatioStatus. Will be used by triggerlist to display the status of trigger in case of
  failure in Polling, etc
   */
  public NGTriggerEntity updateTriggerWithValidationStatus(
      NGTriggerEntity ngTriggerEntity, ValidationResult validationResult, boolean whileExecution) {
    String identifier = ngTriggerEntity.getIdentifier();
    Criteria criteria = getTriggerEqualityCriteriaWithoutDbVersion(ngTriggerEntity, false);
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
      if (!whileExecution) {
        ngTriggerEntity.setEnabled(false);
      }
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
  public TriggerDetails fetchTriggerEntity(String accountId, String orgId, String projectId, String pipelineId,
      String triggerId, String newYaml, boolean withServiceV2) {
    NGTriggerConfigV2 config = ngTriggerElementMapper.toTriggerConfigV2(newYaml);
    Optional<NGTriggerEntity> existingEntity = get(accountId, orgId, projectId, pipelineId, triggerId, false);
    NGTriggerEntity entity =
        ngTriggerElementMapper.toTriggerEntity(accountId, orgId, projectId, triggerId, newYaml, withServiceV2);
    if (existingEntity.isPresent()) {
      ngTriggerElementMapper.copyEntityFieldsOutsideOfYml(existingEntity.get(), entity);
    }

    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  @VisibleForTesting
  public boolean isBranchExpr(String pipelineBranch) {
    return pipelineBranch.startsWith(ExpressionConstants.EXPR_START)
        && pipelineBranch.endsWith(ExpressionConstants.EXPR_END);
  }

  public Object fetchExecutionSummaryV2(String planExecutionId, String accountId, String orgId, String projectId) {
    return NGRestUtils.getResponse(
        pipelineServiceClient.getExecutionDetailV2(planExecutionId, accountId, orgId, projectId));
  }
  @Override
  public List<TriggerCatalogItem> getTriggerCatalog(String accountIdentifier) {
    return triggerCatalogHelper.getTriggerTypeToCategoryMapping(accountIdentifier);
  }

  private String getUpdatedTriggerPipelineComponent(String triggerPipelineYaml, String templateYaml) {
    // This method updates the input specs in trigger's yaml according to the current pipeline's input specs
    // In case pipeline's input specs have changed since trigger's creation, this will fix the trigger's input yaml
    String newTriggerPipelineYaml = "pipeline: {}\n";
    if (isNotEmpty(templateYaml)) {
      YamlConfig templateConfig = new YamlConfig(templateYaml);
      YamlConfig triggerConfig = new YamlConfig(triggerPipelineYaml);
      Map<FQN, Object> toUpdateTriggerPipelineFQNToValueMap = new HashMap<>();
      Set<FQN> triggerFQNs = new LinkedHashSet<>(triggerConfig.getFqnToValueMap().keySet());
      templateConfig.getFqnToValueMap().forEach((key, templateValue) -> {
        // Iterate through pipeline's input keys
        // If trigger contains input spec which no longer exists in pipeline, it will not be added
        if (triggerFQNs.contains(key)) {
          // Case where trigger input yaml contains a key from pipeline's input yaml
          Object triggerValue = triggerConfig.getFqnToValueMap().get(key);
          if (key.isType() || key.isIdentifierOrVariableName()) {
            // If key is variable identifier or name, keep it
            toUpdateTriggerPipelineFQNToValueMap.put(key, templateValue);
          } else {
            // If key is variable value, validate its value type
            String error = validateStaticValues(templateValue, triggerValue, key.getExpressionFqn());
            if (isNotEmpty(error)) {
              // Replace by empty variable value if validation fails (user will need to provide the value)
              toUpdateTriggerPipelineFQNToValueMap.put(key, "");
            } else {
              // Keep variable value if validation succeeds
              toUpdateTriggerPipelineFQNToValueMap.put(key, triggerValue);
            }
          }
        } else {
          // Case where trigger input yaml does not contain a key from pipeline's input yaml
          if (key.isType() || key.isIdentifierOrVariableName()) {
            // If key is variable identifier or name, add it
            toUpdateTriggerPipelineFQNToValueMap.put(key, templateValue);
          } else {
            // If key is variable value, add it with empty value (user will need to provide the value)
            toUpdateTriggerPipelineFQNToValueMap.put(key, "");
          }
        }
      });
      newTriggerPipelineYaml =
          new YamlConfig(toUpdateTriggerPipelineFQNToValueMap, templateConfig.getYamlMap(), true).getYaml();
    }
    return newTriggerPipelineYaml;
  }

  @Override
  public void checkAuthorization(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<HeaderConfig> headerConfigs) {
    boolean hasApiKey = false;
    for (HeaderConfig headerConfig : headerConfigs) {
      if (headerConfig.getKey().equalsIgnoreCase(X_API_KEY)) {
        hasApiKey = true;
        break;
      }
    }
    if (!hasApiKey && pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.NG_SETTINGS)) {
      String mandatoryAuth = NGRestUtils
                                 .getResponse(settingsClient.getSetting(MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION,
                                     accountIdentifier, orgIdentifier, projectIdentifier))
                                 .getValue();
      if (mandatoryAuth.equals(MANDATE_CUSTOM_WEBHOOK_TRUE_VALUE)) {
        throw new InvalidRequestException(String.format(
            "Authorization is mandatory for custom triggers in %s:%s:%s. Please add %s header in the request",
            accountIdentifier, orgIdentifier, projectIdentifier, X_API_KEY));
      }
    }
    if (hasApiKey) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    }
  }

  public TriggerYamlDiffDTO getTriggerYamlDiff(TriggerDetails triggerDetails) {
    String triggerYaml = triggerDetails.getNgTriggerEntity().getYaml();
    String newTriggerYaml;
    if (triggerDetails.getNgTriggerConfigV2() == null
        || isNotEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
        || isNotEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
      // No reconciliation can be done at trigger level if it is for remote pipeline or if it is using input sets
      newTriggerYaml = triggerYaml;
    } else {
      Optional<String> pipelineYmlOptional = validationHelper.fetchPipelineYamlForTrigger(triggerDetails);
      if (pipelineYmlOptional.isPresent()) {
        String pipelineYaml = pipelineYmlOptional.get();
        String templateYaml = createRuntimeInputForm(pipelineYaml);
        String triggerPipelineYaml = getPipelineComponent(triggerYaml);
        String newTriggerPipelineYaml = getUpdatedTriggerPipelineComponent(triggerPipelineYaml, templateYaml);
        newTriggerYaml = setPipelineComponent(triggerYaml, newTriggerPipelineYaml);
      } else {
        throw new NGPipelineNotFoundException(
            "No pipeline found for trigger " + triggerDetails.getNgTriggerConfigV2().getIdentifier());
      }
    }
    return TriggerYamlDiffDTO.builder().oldYAML(triggerYaml).newYAML(newTriggerYaml).build();
  }

  public TriggerUpdateCount updateBranchName(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String targetIdentifier, GitMoveOperationType operationType, String pipelineBranchName) {
    Optional<List<NGTriggerEntity>> listOfTriggers =
        ngTriggerRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndDeletedNot(
            accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier, true);
    List<NGTriggerEntity> listOfUpdatedTriggers = new ArrayList<>();
    long failedYamlUpdateCount = 0;
    if (listOfTriggers.isPresent()) {
      for (NGTriggerEntity triggerEntity : listOfTriggers.get()) {
        try {
          YamlField yamlField = YamlUtils.readTree(triggerEntity.getYaml());
          YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
          if (Objects.equals(operationType, GitMoveOperationType.INLINE_TO_REMOTE)) {
            ((ObjectNode) triggerNode.getCurrJsonNode()).put(PIPELINE_BRANCH_NAME, pipelineBranchName);
          } else if (Objects.equals(operationType, GitMoveOperationType.REMOTE_TO_INLINE)) {
            ((ObjectNode) triggerNode.getCurrJsonNode()).remove(PIPELINE_BRANCH_NAME);
          }
          String updateYml = YamlUtils.writeYamlString(yamlField);
          triggerEntity.setYaml(updateYml);
          listOfUpdatedTriggers.add(triggerEntity);
        } catch (Exception e) {
          failedYamlUpdateCount++;
          log.error(
              "Error performing updateBranchName operation on trigger: " + TriggerHelper.getTriggerRef(triggerEntity),
              e);
        }
      }

      TriggerUpdateCount updateTriggerYamlResult = ngTriggerRepository.updateTriggerYaml(listOfUpdatedTriggers);
      return TriggerUpdateCount.builder()
          .failureCount(updateTriggerYamlResult.getFailureCount() + failedYamlUpdateCount)
          .successCount(updateTriggerYamlResult.getSuccessCount())
          .build();
    } else {
      log.info("No non-deleted Trigger found to update pipelineBranchName");
      return TriggerUpdateCount.builder().successCount(0).failureCount(0).build();
    }
  }
}
