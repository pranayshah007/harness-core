/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.FeatureFlagNames.CET_SAVED_SEARCH_NOTIFICATION;
import static io.harness.cvng.core.utils.FeatureFlagNames.CET_SINGLE_NOTIFICATION;
import static io.harness.cvng.core.utils.FeatureFlagNames.SRM_CODE_ERROR_NOTIFICATIONS;
import static io.harness.cvng.notification.utils.errortracking.AggregatedEventTest.buildSavedFilter;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.OveropsEventType;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.client.ErrorTrackingService;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.MonitoredServiceDTOBuilder;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.WebhookConfigService;
import io.harness.cvng.core.services.api.monitoredService.ErrorTrackingNotificationService;
import io.harness.cvng.notification.beans.ErrorTrackingConditionSpec;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ErrorTrackingNotificationServiceImplTest extends CvNextGenTestBase {
  private static final String MONITORED_SERVICE_ID = "testService_testEnvironment";
  private static final Instant UNIX_EPOCH = Instant.parse("1970-01-01T00:00:00Z");

  @Inject private MetricPackService metricPackService;
  @Inject private HPersistence hPersistence;
  @Inject private NotificationRuleService notificationRuleService;
  @Inject private ErrorTrackingNotificationService errorTrackingNotificationService;

  @Mock private FeatureFlagService featureFlagService;
  @Mock private ErrorTrackingService errorTrackingService;
  @Mock private WebhookConfigService webhookConfigService;
  @Mock private FakeNotificationClient notificationClient;

  private BuilderFactory builderFactory;
  private Clock clock;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private String environmentIdentifier;
  private String serviceIdentifier;
  private String monitoredServiceName;
  private String monitoredServiceIdentifier;
  private ProjectParams projectParams;
  private Map<String, String> tags;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setProjectIdentifier("project");
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentIdentifier = builderFactory.getContext().getEnvIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceName = "monitoredServiceName";
    monitoredServiceIdentifier =
        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier();
    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    tags = new HashMap<>() {
      {
        put("tag1", "value1");
        put("tag2", "");
      }
    };

    projectParams = ProjectParams.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .build();

    when(webhookConfigService.getWebhookApiBaseUrl()).thenReturn("http://localhost:6457/cv/api/");

    FieldUtils.writeField(errorTrackingNotificationService, "notificationClient", notificationClient, true);
    FieldUtils.writeField(errorTrackingNotificationService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(errorTrackingNotificationService, "errorTrackingService", errorTrackingService, true);

    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendBackwardsCompatibleNotification() {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    Integer thresholdCount = null;
    mockGetBasicNotification(thresholdCount);

    final ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION))
            .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
            .build();

    saveMonitoredService(errorTrackingConditionSpec);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendDefinedFilterNotification() throws IllegalAccessException {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    Integer thresholdCount = null;
    mockGetBasicNotification(thresholdCount);

    final ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION))
            .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
            .aggregated(true)
            .build();

    saveMonitoredService(errorTrackingConditionSpec);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendDefinedFilterThresholdNotMetNotification() throws IllegalAccessException {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);

    Integer thresholdCount = 10;
    mockGetBasicNotification(thresholdCount);

    final ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION))
            .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
            .aggregated(true)
            .volumeThresholdCount(thresholdCount)
            .volumeThresholdMinutes(20)
            .build();

    saveMonitoredService(errorTrackingConditionSpec);

    // Set the clock to 1 second later to ensure we don't meet the threshold minutes
    clock = Clock.fixed(UNIX_EPOCH.plus(1, ChronoUnit.SECONDS), ZoneOffset.UTC);
    FieldUtils.writeField(errorTrackingNotificationService, "clock", clock, true);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(0)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendDefinedFilterThresholdMetNotification() throws IllegalAccessException {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);

    int thresholdMinutes = 20;
    int thresholdCount = 10;

    mockGetBasicNotification(thresholdCount);

    final ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION))
            .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
            .aggregated(true)
            .volumeThresholdCount(thresholdCount)
            .volumeThresholdMinutes(thresholdMinutes)
            .build();

    saveMonitoredService(errorTrackingConditionSpec);

    // Set the clock to threshold minutes later to ensure the threshold is met
    clock = Clock.fixed(UNIX_EPOCH.plus(thresholdMinutes, ChronoUnit.MINUTES), ZoneOffset.UTC);

    FieldUtils.writeField(errorTrackingNotificationService, "clock", clock, true);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendTwoImmediateEmailNotifications() {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    enableFeatureFlag(CET_SINGLE_NOTIFICATION);

    mockGetTwoNewNotifications();

    final ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder()
            .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION, ErrorTrackingEventType.CUSTOM,
                ErrorTrackingEventType.LOG, ErrorTrackingEventType.HTTP))
            .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
            .aggregated(false)
            .build();
    saveMonitoredService(errorTrackingConditionSpec);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendSavedFilterNotification() {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    enableFeatureFlag(CET_SAVED_SEARCH_NOTIFICATION);

    Long savedFilterId = 990L;
    Integer thresholdCount = null;
    mockGetBasicSavedFilterNotification(savedFilterId, thresholdCount);
    ErrorTrackingConditionSpec errorTrackingConditionSpec =
        ErrorTrackingConditionSpec.builder().aggregated(true).savedFilterId(savedFilterId).build();
    saveMonitoredService(errorTrackingConditionSpec);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendSavedFilterThresholdNotMetNotification() throws IllegalAccessException {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    enableFeatureFlag(CET_SAVED_SEARCH_NOTIFICATION);

    Long savedFilterId = 990L;
    Integer thresholdCount = 10;
    mockGetBasicSavedFilterNotification(savedFilterId, thresholdCount);
    ErrorTrackingConditionSpec errorTrackingConditionSpec = ErrorTrackingConditionSpec.builder()
                                                                .aggregated(true)
                                                                .savedFilterId(savedFilterId)
                                                                .volumeThresholdCount(thresholdCount)
                                                                .volumeThresholdMinutes(20)
                                                                .build();
    saveMonitoredService(errorTrackingConditionSpec);

    // Set the clock to 1 second later to ensure we don't meet the threshold minutes
    clock = Clock.fixed(UNIX_EPOCH.plus(1, ChronoUnit.SECONDS), ZoneOffset.UTC);
    FieldUtils.writeField(errorTrackingNotificationService, "clock", clock, true);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(0)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendSavedFilterThresholdMetNotification() throws IllegalAccessException {
    enableFeatureFlag(SRM_CODE_ERROR_NOTIFICATIONS);
    enableFeatureFlag(CET_SAVED_SEARCH_NOTIFICATION);

    int thresholdMinutes = 20;
    int thresholdCount = 10;

    Long savedFilterId = 990L;
    mockGetBasicSavedFilterNotification(savedFilterId, thresholdCount);
    ErrorTrackingConditionSpec errorTrackingConditionSpec = ErrorTrackingConditionSpec.builder()
                                                                .aggregated(true)
                                                                .savedFilterId(savedFilterId)
                                                                .volumeThresholdCount(thresholdCount)
                                                                .volumeThresholdMinutes(thresholdMinutes)
                                                                .build();
    saveMonitoredService(errorTrackingConditionSpec);

    // Set the clock to threshold minutes later to ensure the threshold is met
    clock = Clock.fixed(UNIX_EPOCH.plus(thresholdMinutes, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(errorTrackingNotificationService, "clock", clock, true);

    errorTrackingNotificationService.handleNotification(getMonitoredService(MONITORED_SERVICE_ID));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  private void enableFeatureFlag(String flagName) {
    when(featureFlagService.isFeatureFlagEnabled(eq(builderFactory.getContext().getAccountId()), eq(flagName)))
        .thenReturn(true);
  }

  private void mockGetBasicNotification(Integer thresholdCount) {
    Scorecard scorecard = Scorecard.builder().newHitCount(1).versionIdentifier("v1").build();
    List<Scorecard> scorecards = new ArrayList<>();
    scorecards.add(scorecard);
    ErrorTrackingNotificationData errorTrackingNotificationData = ErrorTrackingNotificationData.builder()
                                                                      .from(Timestamp.from(clock.instant()))
                                                                      .to(Timestamp.from(clock.instant()))
                                                                      .scorecards(scorecards)
                                                                      .build();
    when(errorTrackingService.getNotificationData(anyString(), anyString(), anyString(), anyString(), anyString(),
             anyList(), anyList(), anyString(), eq(thresholdCount)))
        .thenReturn(errorTrackingNotificationData);
  }

  private void mockGetBasicSavedFilterNotification(Long savedFilterId, Integer thresholdCount) {
    Scorecard scorecard = Scorecard.builder().newHitCount(1).versionIdentifier("v1").build();
    List<Scorecard> scorecards = new ArrayList<>();
    scorecards.add(scorecard);

    final SavedFilter savedFilter = buildSavedFilter(savedFilterId);

    ErrorTrackingNotificationData errorTrackingNotificationData = ErrorTrackingNotificationData.builder()
                                                                      .from(Timestamp.from(clock.instant()))
                                                                      .to(Timestamp.from(clock.instant()))
                                                                      .scorecards(scorecards)
                                                                      .filter(savedFilter)
                                                                      .build();
    when(errorTrackingService.getNotificationSavedFilterData(anyString(), anyString(), anyString(), anyString(),
             anyString(), nullable(Long.class), eq(thresholdCount), anyString()))
        .thenReturn(errorTrackingNotificationData);
  }

  private void mockGetTwoNewNotifications() {
    Scorecard scorecard = Scorecard.builder().newHitCount(1).versionIdentifier("v1").build();
    List<Scorecard> scorecards = new ArrayList<>();
    scorecards.add(scorecard);

    ErrorTrackingHitSummary errorTrackingHitSummary1 = createHitSummary("testVersion1");
    ErrorTrackingHitSummary errorTrackingHitSummary2 = createHitSummary("testVersion2");

    when(errorTrackingService.getNotificationNewData(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of(errorTrackingHitSummary1, errorTrackingHitSummary2));
  }

  private void saveMonitoredService(ErrorTrackingConditionSpec errorTrackingConditionSpec) {
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();

    notificationRuleDTO.setName("rule1");
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleDTO.setConditions(List.of(NotificationRuleCondition.builder()
                                                  .type(NotificationRuleConditionType.CODE_ERRORS)
                                                  .spec(errorTrackingConditionSpec)
                                                  .build()));
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder()
                                                  .identifier(MONITORED_SERVICE_ID)
                                                  .serviceRef("testService")
                                                  .environmentRef("testEnvironment")
                                                  .build();

    monitoredServiceDTO.setNotificationRuleRefs(
        List.of(NotificationRuleRefDTO.builder()
                    .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                    .enabled(true)
                    .build()));

    saveMonitoredServiceEntity(monitoredServiceDTO);
  }

  private void saveMonitoredServiceEntity(MonitoredServiceDTO monitoredServiceDTO) {
    long currentTime = System.currentTimeMillis();
    MonitoredService monitoredServiceEntity =
        MonitoredService.builder()
            .name(monitoredServiceDTO.getName())
            .desc(monitoredServiceDTO.getDescription())
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .environmentIdentifierList(monitoredServiceDTO.getEnvironmentRefList())
            .serviceIdentifier(monitoredServiceDTO.getServiceRef())
            .identifier(monitoredServiceDTO.getIdentifier())
            .type(monitoredServiceDTO.getType())
            .enabled(monitoredServiceDTO.isEnabled())
            .lastDisabledAt(clock.millis())
            .tags(TagMapper.convertToList(monitoredServiceDTO.getTags()))
            .notificationRuleRefs(notificationRuleService.getNotificationRuleRefs(projectParams,
                monitoredServiceDTO.getNotificationRuleRefs(), NotificationRuleType.MONITORED_SERVICE,
                Instant.ofEpochSecond(0)))
            .createdAt(currentTime)
            .lastUpdatedAt(currentTime)
            .build();

    hPersistence.save(monitoredServiceEntity);
  }

  private MonitoredService getMonitoredService(String identifier) {
    return hPersistence.createQuery(MonitoredService.class)
        .filter(MonitoredServiceKeys.accountId, accountId)
        .filter(MonitoredServiceKeys.orgIdentifier, orgIdentifier)
        .filter(MonitoredServiceKeys.projectIdentifier, projectIdentifier)
        .filter(MonitoredServiceKeys.identifier, identifier)
        .get();
  }

  private MonitoredServiceDTOBuilder createMonitoredServiceDTOBuilder() {
    return builderFactory.monitoredServiceDTOBuilder()
        .identifier(monitoredServiceIdentifier)
        .serviceRef(serviceIdentifier)
        .environmentRef(environmentIdentifier)
        .name(monitoredServiceName)
        .tags(tags);
  }

  public static ErrorTrackingHitSummary createHitSummary(String versionId) {
    Long currentTime = System.currentTimeMillis();

    List<String> stackTrace = new ArrayList<>();
    stackTrace.add("stacktrace line 1");
    stackTrace.add("stacktrace line 2");
    stackTrace.add("stacktrace line 3");

    return ErrorTrackingHitSummary.builder()
        .stackTrace(stackTrace)
        .eventType(OveropsEventType.ET_EXCEPTION)
        .firstSeen(new Date(currentTime))
        .requestId(27)
        .versionId(versionId)
        .build();
  }
}