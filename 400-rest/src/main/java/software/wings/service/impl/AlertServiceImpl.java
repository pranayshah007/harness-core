/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.SPG_ENABLE_NOTIFICATION_RULES;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.Misc.getDurationString;

import static software.wings.alerts.AlertStatus.Closed;
import static software.wings.alerts.AlertStatus.Open;
import static software.wings.alerts.AlertStatus.Pending;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.alert.AlertType.ARTIFACT_COLLECTION_FAILED;
import static software.wings.beans.alert.AlertType.ApprovalNeeded;
import static software.wings.beans.alert.AlertType.CONTINUOUS_VERIFICATION_ALERT;
import static software.wings.beans.alert.AlertType.DEPLOYMENT_RATE_APPROACHING_LIMIT;
import static software.wings.beans.alert.AlertType.DelegatesDown;
import static software.wings.beans.alert.AlertType.GitConnectionError;
import static software.wings.beans.alert.AlertType.GitSyncError;
import static software.wings.beans.alert.AlertType.INSTANCE_USAGE_APPROACHING_LIMIT;
import static software.wings.beans.alert.AlertType.InvalidKMS;
import static software.wings.beans.alert.AlertType.ManualInterventionNeeded;
import static software.wings.beans.alert.AlertType.RESOURCE_USAGE_APPROACHING_LIMIT;
import static software.wings.beans.alert.AlertType.USAGE_LIMIT_EXCEEDED;
import static software.wings.beans.alert.AlertType.USERGROUP_SYNC_FAILED;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.eraro.ErrorCode;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.event.publisher.EventPublisher;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HIterator;

import software.wings.alerts.AlertStatus;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertBuilder;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ApprovalNeededAlert;
import software.wings.beans.alert.ArtifactCollectionFailedAlert;
import software.wings.beans.alert.ManualInterventionNeededAlert;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.dl.WingsPersistence;
import software.wings.logcontext.AlertLogContext;
import software.wings.service.impl.event.AlertEvent;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.alert.NotificationRulesStatusService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._955_ALERT_BEANS)
public class AlertServiceImpl implements AlertService {
  // TODO: check if ARTIFACT_COLLECTION_FAILED alert type needs to be added here
  private static final List<AlertType> ALERT_TYPES_TO_NOTIFY_ON = ImmutableList.of(DelegatesDown,
      DEPLOYMENT_RATE_APPROACHING_LIMIT, INSTANCE_USAGE_APPROACHING_LIMIT, USAGE_LIMIT_EXCEEDED, USERGROUP_SYNC_FAILED,
      RESOURCE_USAGE_APPROACHING_LIMIT, GitSyncError, GitConnectionError, InvalidKMS, CONTINUOUS_VERIFICATION_ALERT);
  private static final List<AlertType> CLOSED_ALERT_TYPES_TO_NOTIFY_ON =
      ImmutableList.of(CONTINUOUS_VERIFICATION_ALERT, InvalidKMS);
  private static final Iterable<AlertStatus> STATUS_ACTIVE = ImmutableSet.of(Open, Pending);

  @Inject Map<AlertType, Class<? extends AlertData>> alertTypeClassMap;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutorService executorService;
  @Inject private Injector injector;
  @Inject private EventPublisher eventPublisher;
  @Inject private NotificationRulesStatusService notificationStatusService;
  @Inject private AppService appService;
  @Inject private SettingsService settingsService;
  @Inject private ArtifactStreamService artifactStreamService;

  @Inject private FeatureFlagService featureFlagService;

  @Override
  public PageResponse<Alert> list(PageRequest<Alert> pageRequest) {
    return wingsPersistence.querySecondary(Alert.class, pageRequest);
  }

  @Override
  public List<AlertType> listCategoriesAndTypes(String accountId) {
    List<AlertType> listOfAlertsType = new ArrayList<>(ALERT_TYPES_TO_NOTIFY_ON);

    if (featureFlagService.isEnabled(SPG_ENABLE_NOTIFICATION_RULES, accountId)) {
      listOfAlertsType.add(ApprovalNeeded);
      listOfAlertsType.add(ManualInterventionNeeded);
      listOfAlertsType.add(ARTIFACT_COLLECTION_FAILED);
    }

    return Arrays.stream(software.wings.beans.alert.AlertType.values())
        .filter(listOfAlertsType::contains)
        .collect(toList());
  }

  @Override
  public Future openAlert(String accountId, String appId, AlertType alertType, AlertData alertData) {
    return executorService.submit(() -> openInternal(accountId, appId, alertType, alertData));
  }

  @Override
  public Future openAlertWithTTL(
      String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil) {
    return executorService.submit(() -> openInternal(accountId, appId, alertType, alertData, validUntil));
  }

  @Override
  public Future<?> closeExistingAlertsAndOpenNew(
      String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil) {
    return executorService.submit(() -> {
      findAllExistingAlerts(accountId, appId, alertType, alertData).forEach(this::close);
      openInternal(accountId, appId, alertType, alertData, validUntil);
    });
  }

  @Override
  public void closeAlert(String accountId, String appId, AlertType alertType, AlertData alertData) {
    executorService.submit(() -> findExistingAlert(accountId, appId, alertType, alertData).ifPresent(this::close));
  }

  @Override
  public void closeAllAlerts(String accountId, String appId, AlertType alertType, AlertData alertData) {
    executorService.submit(() -> findAllExistingAlerts(accountId, appId, alertType, alertData).forEach(this::close));
  }

  @Override
  public void closeAlertsOfType(String accountId, String appId, AlertType alertType) {
    executorService.submit(() -> {
      try (HIterator<Alert> iterator = findExistingAlertsOfType(accountId, appId, alertType)) {
        iterator.forEach(this::close);
      }
    });
  }

  @Override
  public void deploymentCompleted(String appId, String executionId) {
    executorService.submit(() -> deploymentCompletedInternal(appId, executionId));
  }

  private Alert createAlertObject(
      String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil) {
    AlertBuilder alertBuilder = Alert.builder()
                                    .appId(appId)
                                    .accountId(accountId)
                                    .type(alertType)
                                    .status(Pending)
                                    .alertData(alertData)
                                    .title(alertData.buildTitle())
                                    .resolutionTitle(alertData.buildResolutionTitle())
                                    .category(alertType.getCategory())
                                    .severity(alertType.getSeverity())
                                    .triggerCount(0)
                                    .lastTriggeredAt(Instant.now().toEpochMilli())
                                    .alertReconciliation(alertType.getAlertReconciliation());
    if (validUntil != null) {
      alertBuilder.validUntil(validUntil);
    }
    return alertBuilder.build();
  }

  private void openInternal(String accountId, String appId, AlertType alertType, AlertData alertData, Date validUntil) {
    try (AutoLogContext ignore = new AlertLogContext(accountId, alertType, appId, OVERRIDE_ERROR)) {
      Alert alert = findExistingAlert(accountId, appId, alertType, alertData).orElse(null);
      if (alert == null) {
        injector.injectMembers(alertData);
        alert = createAlertObject(accountId, appId, alertType, alertData, validUntil);
        wingsPersistence.save(alert);
        log.info("Alert created: {}", alert.getUuid());
      }
      postProcessAlertAfterCreating(accountId, alert, alertType);
    }
  }

  private void postProcessAlertAfterCreating(String accountId, Alert alert, AlertType alertType) {
    AlertStatus status = alert.getTriggerCount() >= alertType.getPendingCount() ? Open : Pending;
    // Since alert jobs are delayed. Removing the barrier check.
    if (featureFlagService.isEnabled(FeatureName.INSTANT_DELEGATE_DOWN_ALERT, accountId)
        && alertType.equals(DelegatesDown)) {
      status = Open;
    }

    UpdateOperations<Alert> updateOperations = wingsPersistence.createUpdateOperations(Alert.class);
    updateOperations.inc(AlertKeys.triggerCount);
    updateOperations.set(AlertKeys.lastTriggeredAt, Instant.now().toEpochMilli());

    boolean alertOpened = false;
    if (status == Open && alert.getStatus() == Pending) {
      updateOperations.set(AlertKeys.status, Open);
      alertOpened = true;
    }
    wingsPersistence.update(alert, updateOperations);

    alert.setTriggerCount(alert.getTriggerCount() + 1);
    alert.setStatus(status);
    if (alertOpened) {
      log.info("Alert opened: {}", alert.getUuid());

      if (notificationStatusService.get(accountId).isEnabled()) {
        publishEvent(alert);
      } else {
        log.info("No alert event will be published.");
      }
    } else if (status == Pending) {
      log.info("Alert pending: {}", alert.getUuid());
    }
  }

  private void openInternal(String accountId, String appId, AlertType alertType, AlertData alertData) {
    try (AutoLogContext ignore = new AlertLogContext(accountId, alertType, appId, OVERRIDE_ERROR)) {
      Alert alert = findExistingAlert(accountId, appId, alertType, alertData).orElse(null);
      if (alert == null) {
        injector.injectMembers(alertData);
        alert = createAlertObject(accountId, appId, alertType, alertData, null);
        wingsPersistence.save(alert);
        log.info("Alert created: {}", alert.getUuid());
      }
      postProcessAlertAfterCreating(accountId, alert, alertType);
    }
  }

  private void publishEvent(Alert alert) {
    List<AlertType> listOfAlertsType = new ArrayList<>(ALERT_TYPES_TO_NOTIFY_ON);

    if (featureFlagService.isEnabled(SPG_ENABLE_NOTIFICATION_RULES, alert.getAccountId())) {
      listOfAlertsType.add(ApprovalNeeded);
      listOfAlertsType.add(ManualInterventionNeeded);
      listOfAlertsType.add(ARTIFACT_COLLECTION_FAILED);
    }

    try {
      if (listOfAlertsType.contains(alert.getType())) {
        eventPublisher.publishEvent(
            Event.builder().eventData(alertEventData(alert)).eventType(EventType.OPEN_ALERT).build());
      } else {
        log.info("No alert event will be published in event queue. Type: {}", alert.getType());
      }
    } catch (Exception e) {
      log.error("Could not publish alert event. Alert: {}", alert);
    }
  }

  private void publishCloseEvent(Alert alert) {
    try {
      if (CLOSED_ALERT_TYPES_TO_NOTIFY_ON.contains(alert.getType())) {
        eventPublisher.publishEvent(
            Event.builder().eventData(alertEventData(alert)).eventType(EventType.CLOSE_ALERT).build());
      } else {
        log.info("No close alert event will be published in event queue. Type: {}", alert.getType());
      }
    } catch (Exception e) {
      log.error("Could not publish alert event. Alert: {}", alert);
    }
  }

  private static EventData alertEventData(Alert alert) {
    return EventData.builder().eventInfo(new AlertEvent(alert)).build();
  }

  private void deploymentCompletedInternal(String appId, String executionId) {
    Query<Alert> query = wingsPersistence.createQuery(Alert.class)
                             .filter(AlertKeys.appId, appId)
                             .field(AlertKeys.type)
                             .in(asList(ApprovalNeeded, ManualInterventionNeeded))
                             .field(AlertKeys.status)
                             .in(STATUS_ACTIVE);
    try (HIterator<Alert> alerts = new HIterator<>(query.fetch())) {
      for (Alert alert : alerts) {
        String alertExecutionId = alert.getType() == ApprovalNeeded
            ? ((ApprovalNeededAlert) alert.getAlertData()).getExecutionId()
            : ((ManualInterventionNeededAlert) alert.getAlertData()).getExecutionId();
        if (executionId.equals(alertExecutionId)) {
          close(alert);
        }
      }
    }
  }

  @Override
  public Optional<Alert> findExistingAlert(String accountId, String appId, AlertType alertType, AlertData alertData) {
    try (AutoLogContext ignore = new AlertLogContext(accountId, alertType, appId, OVERRIDE_ERROR)) {
      log.debug("Finding existing alerts for accountId {}", accountId);
      Query<Alert> query = getAlertsQuery(accountId, appId, alertType, alertData);
      try (HIterator<Alert> iterator = new HIterator<>(query.fetch())) {
        for (Alert alert : iterator) {
          injector.injectMembers(alert.getAlertData());
          if (alertData.matches(alert.getAlertData())) {
            return Optional.of(alert);
          }
        }
      }
    }
    return Optional.empty();
  }

  private List<Alert> findAllExistingAlerts(String accountId, String appId, AlertType alertType, AlertData alertData) {
    try (AutoLogContext ignore = new AlertLogContext(accountId, alertType, appId, OVERRIDE_ERROR)) {
      log.info("Finding all existing alerts");
      Query<Alert> query = getAlertsQuery(accountId, appId, alertType, alertData);
      List<Alert> alerts = new ArrayList<>();
      try (HIterator<Alert> iterator = new HIterator<>(query.fetch())) {
        for (Alert alert : iterator) {
          injector.injectMembers(alert.getAlertData());
          if (alertData.matches(alert.getAlertData())) {
            alerts.add(alert);
          }
        }
      }
      return alerts;
    }
  }

  private Query<Alert> getAlertsQuery(String accountId, String appId, AlertType alertType, AlertData alertData) {
    Class<? extends AlertData> aClass = alertTypeClassMap.get(alertType);
    if (!aClass.isAssignableFrom(alertData.getClass())) {
      String errorMsg = format("Alert type %s requires alert data of class %s but was %s", alertType.name(),
          aClass.getName(), alertData.getClass().getName());
      log.error(errorMsg);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT).addParam("args", errorMsg);
    }
    Query<Alert> alertQuery = wingsPersistence.createQuery(Alert.class)
                                  .filter(AlertKeys.accountId, accountId)
                                  .filter(AlertKeys.type, alertType)
                                  .field(AlertKeys.status)
                                  .in(STATUS_ACTIVE);
    if (appId != null) {
      alertQuery.filter(AlertKeys.appId, appId);
    }
    injector.injectMembers(alertData);
    return alertQuery;
  }

  private HIterator<Alert> findExistingAlertsOfType(String accountId, String appId, AlertType alertType) {
    Query<Alert> alertQuery = wingsPersistence.createQuery(Alert.class)
                                  .filter(AlertKeys.accountId, accountId)
                                  .filter(AlertKeys.type, alertType)
                                  .field(AlertKeys.status)
                                  .in(STATUS_ACTIVE);
    if (appId != null) {
      alertQuery.filter(AlertKeys.appId, appId);
    }
    return new HIterator<>(alertQuery.fetch());
  }

  @Override
  public void close(Alert alert) {
    long now = currentTimeMillis();
    Date expiration = Date.from(OffsetDateTime.now().plusDays(5).toInstant());

    final UpdateResults updateResults = wingsPersistence.update(wingsPersistence.createQuery(Alert.class)
                                                                    .filter(AlertKeys.uuid, alert.getUuid())
                                                                    .filter(AlertKeys.accountId, alert.getAccountId()),
        wingsPersistence.createUpdateOperations(Alert.class)
            .set(AlertKeys.status, Closed)
            .set(AlertKeys.closedAt, now)
            .set(AlertKeys.validUntil, expiration));

    if (updateResults.getUpdatedCount() > 0) {
      log.info("Alert closed after {} : {}", getDurationString(alert.getCreatedAt(), now), alert.getUuid());
      alert.setStatus(Closed);
      alert.setClosedAt(now);
      alert.setValidUntil(expiration);
      publishCloseEvent(alert);
    }
  }

  @Override
  public void deleteByAccountId(String accountId) {
    try (HIterator<Alert> alerts = new HIterator<>(
             wingsPersistence.createQuery(Alert.class).filter(AlertKeys.accountId, accountId).fetch())) {
      alerts.forEach(alert -> wingsPersistence.delete(alert));
    }
  }

  @Override
  public void pruneByApplication(String appId) {
    try (HIterator<Alert> alerts =
             new HIterator<>(wingsPersistence.createQuery(Alert.class).filter(AlertKeys.appId, appId).fetch())) {
      alerts.forEach(alert -> wingsPersistence.delete(alert));
    }
  }

  @Override
  public void deleteByArtifactStream(String appId, String artifactStreamId) {
    // NOTE: this pruning is done only for ArtifactCollectionFailedAlert
    String accountId;
    if (GLOBAL_APP_ID.equals(appId)) {
      ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
      if (artifactStream == null || artifactStream.getSettingId() == null) {
        return;
      }
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        return;
      }

      // NOTE: appId not known at this point, hence set to null - appId will be removed from this alert type later
      accountId = settingAttribute.getAccountId();
    } else {
      accountId = appService.getAccountIdByAppId(appId);
    }

    try (HIterator<Alert> alerts = findExistingAlertsOfType(accountId, null, ARTIFACT_COLLECTION_FAILED)) {
      for (Alert alert : alerts) {
        ArtifactCollectionFailedAlert data = (ArtifactCollectionFailedAlert) alert.getAlertData();
        if (data.getArtifactStreamId().equals(artifactStreamId)) {
          wingsPersistence.delete(alert);
        }
      }
    }
  }

  @Override
  public void deleteArtifactStreamAlertForAccount(String accountId) {
    try (HIterator<Alert> alerts = findExistingAlertsOfType(accountId, null, ARTIFACT_COLLECTION_FAILED)) {
      for (Alert alert : alerts) {
        ArtifactCollectionFailedAlert data = (ArtifactCollectionFailedAlert) alert.getAlertData();
        wingsPersistence.delete(alert);
      }
    }
  }
}
