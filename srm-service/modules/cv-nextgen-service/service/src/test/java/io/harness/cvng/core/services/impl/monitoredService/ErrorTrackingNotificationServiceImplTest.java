/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.FeatureFlagNames.SRM_CODE_ERROR_NOTIFICATIONS;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ErrorTrackingNotificationServiceImplTest extends CvNextGenTestBase {
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
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testSendCodeErrorNotification() throws IllegalAccessException {
    when(featureFlagService.isFeatureFlagEnabled(
             eq(builderFactory.getContext().getAccountId()), eq(SRM_CODE_ERROR_NOTIFICATIONS)))
        .thenReturn(true);

    Scorecard scorecard = Scorecard.builder().build();
    List<Scorecard> scorecards = new ArrayList<>();
    scorecards.add(scorecard);
    ErrorTrackingNotificationData errorTrackingNotificationData = ErrorTrackingNotificationData.builder()
                                                                      .from(Timestamp.from(clock.instant()))
                                                                      .to(Timestamp.from(clock.instant()))
                                                                      .scorecards(scorecards)
                                                                      .build();
    when(errorTrackingService.getNotificationData(anyString(), anyString(), anyString(), anyString(), anyString(),
             anyList(), anyList(), anyString(), anyInt()))
        .thenReturn(errorTrackingNotificationData);

    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();

    notificationRuleDTO.setName("rule1");
    notificationRuleDTO.setIdentifier("rule1");
    notificationRuleDTO.setConditions(
        List.of(NotificationRuleCondition.builder()
                    .type(NotificationRuleConditionType.CODE_ERRORS)
                    .spec(ErrorTrackingConditionSpec.builder()
                              .errorTrackingEventTypes(List.of(ErrorTrackingEventType.EXCEPTION))
                              .errorTrackingEventStatus(List.of(ErrorTrackingEventStatus.NEW_EVENTS))
                              .build())
                    .build()));
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);

    MonitoredServiceDTO monitoredServiceDTO = createMonitoredServiceDTOBuilder()
                                                  .identifier("testService_testEnvironment")
                                                  .serviceRef("testService")
                                                  .environmentRef("testEnvironment")
                                                  .build();

    monitoredServiceDTO.setNotificationRuleRefs(
        List.of(NotificationRuleRefDTO.builder()
                    .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                    .enabled(true)
                    .build()));

    saveMonitoredServiceEntity(monitoredServiceDTO);

    // Set the clock to 1 second later to ensure cool off duration does not take effect for Code Errors
    clock = Clock.fixed(UNIX_EPOCH.plus(1, ChronoUnit.SECONDS), ZoneOffset.UTC);

    FieldUtils.writeField(errorTrackingNotificationService, "clock", clock, true);

    when(notificationClient.sendNotificationAsync(any()))
        .thenReturn(NotificationResultWithoutStatus.builder().notificationId("notificationId").build());

    errorTrackingNotificationService.handleNotification(getMonitoredService(monitoredServiceDTO.getIdentifier()));
    verify(notificationClient, times(1)).sendNotificationAsync(any());
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
}