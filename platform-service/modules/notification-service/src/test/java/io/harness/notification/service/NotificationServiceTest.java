/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.EmailChannel;
import io.harness.notification.entities.MicrosoftTeamsChannel;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationCondition;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.entities.NotificationEventConfig;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.PagerDutyChannel;
import io.harness.notification.entities.SlackChannel;
import io.harness.notification.entities.WebhookChannel;
import io.harness.notification.entities.eventmetadata.DelegateNotificationEventParameters;
import io.harness.notification.remote.mappers.NotificationMapper;
import io.harness.notification.repositories.NotificationRepository;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationRuleManagementService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NotificationServiceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "AccountId";
  private static final String ORG_IDENTIFIER = "OrgId";
  private static final String PROJECT_IDENTIFIER = "ProjectId";
  private static final String EMAIL_ID = "test@harness.com";

  NotificationServiceImpl notificationService;
  @Mock NotificationRepository notificationRepository;
  @Mock NotificationRuleManagementService notificationRuleManagementService;
  @Mock ChannelService channelService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    notificationService =
        new NotificationServiceImpl(channelService, notificationRepository, notificationRuleManagementService);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationEmail() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "email_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRuleEmail());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(2)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationSlack() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "slack_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRuleSlack());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(2)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationMSTeam() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "msteam_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRuleMSTeam());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(2)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationPagerDuty() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "pager_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRulePagerDuty());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(2)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationWebHook() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "webhook_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRuleWebHook());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(2)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testProcessNotificationWithRuleMoreThanOneChannels() {
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "email_test");
    NotificationTriggerRequest notificationTriggerRequest = NotificationTriggerRequest.newBuilder()
                                                                .setOrgId(ORG_IDENTIFIER)
                                                                .setProjectId(PROJECT_IDENTIFIER)
                                                                .setAccountId(ACCOUNT_IDENTIFIER)
                                                                .setEvent(NotificationEvent.DELEGATE_DOWN.name())
                                                                .setEventEntity(NotificationEntity.DELEGATE.name())
                                                                .putAllTemplateData(templateData)
                                                                .build();
    Mockito
        .when(notificationRuleManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            NotificationEntity.DELEGATE, NotificationEvent.DELEGATE_DOWN))
        .thenReturn(notificationRuleWithMoreThanOneChannel());
    Mockito.when(channelService.send(any()))
        .thenReturn(NotificationProcessingResponse.builder().result(List.of(true)).build());
    assertThat(notificationService.processNewMessage(notificationTriggerRequest)).isTrue();
    verify(notificationRepository, times(4)).save(any(Notification.class));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testVerifyNotificationWithNotificationRule() {
    NotificationRule notificationRule = notificationRuleEmail();
    NotificationChannel notificationChannel =
        notificationRule.getNotificationChannelForEvent(NotificationEvent.DELEGATE_DOWN).get(0);
    Notification notification = NotificationMapper.toNotification(notificationRule, notificationChannel);
    assertThat(notification).isNotNull();
    assertThat(notification.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(notification.getChannel().getChannelType()).isEqualTo(NotificationChannelType.EMAIL);
    assertThat(notification.getChannel()).isNotNull();
  }

  private NotificationRule notificationRuleEmail() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(Collections.singletonList(
                            NotificationChannel.builder()
                                .identifier("nc1")
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .notificationChannelType(NotificationChannelType.EMAIL)
                                .channel(EmailChannel.builder().emailIds(List.of(EMAIL_ID)).build())
                                .build()))
                        .build()))
                .build()))
        .build();
  }

  private NotificationRule notificationRuleMSTeam() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(Collections.singletonList(
                            NotificationChannel.builder()
                                .identifier("nc1")
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .notificationChannelType(NotificationChannelType.MSTEAMS)
                                .channel(MicrosoftTeamsChannel.builder().msTeamKeys(Collections.EMPTY_LIST).build())
                                .build()))
                        .build()))
                .build()))
        .build();
  }

  private NotificationRule notificationRuleSlack() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(Collections.singletonList(
                            NotificationChannel.builder()
                                .identifier("nc1")
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .notificationChannelType(NotificationChannelType.SLACK)
                                .channel(SlackChannel.builder().slackWebHookUrls(Collections.EMPTY_LIST).build())
                                .build()))
                        .build()))
                .build()))
        .build();
  }

  private NotificationRule notificationRulePagerDuty() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(Collections.singletonList(
                            NotificationChannel.builder()
                                .identifier("nc1")
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .notificationChannelType(NotificationChannelType.PAGERDUTY)
                                .channel(
                                    PagerDutyChannel.builder().pagerDutyIntegrationKeys(Collections.EMPTY_LIST).build())
                                .build()))
                        .build()))
                .build()))
        .build();
  }

  private NotificationRule notificationRuleWebHook() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(
                            Collections.singletonList(NotificationChannel.builder()
                                                          .identifier("nc1")
                                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .notificationChannelType(NotificationChannelType.WEBHOOK)
                                                          .channel(WebhookChannel.builder()
                                                                       .webHookUrls(Collections.EMPTY_LIST)
                                                                       .headers(Collections.EMPTY_MAP)
                                                                       .build())
                                                          .build()))
                        .build()))
                .build()))
        .build();
  }

  private NotificationRule notificationRuleWithMoreThanOneChannel() {
    return NotificationRule.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier("")
        .status(NotificationRule.Status.ENABLED)
        .notificationConditions(Collections.singletonList(
            NotificationCondition.builder()
                .conditionName("conditionName")
                .notificationEventConfigs(Collections.singletonList(
                    NotificationEventConfig.builder()
                        .notificationEvent(NotificationEvent.DELEGATE_DOWN)
                        .notificationEntity(NotificationEntity.DELEGATE)
                        .notificationEventParameters(DelegateNotificationEventParameters.builder()
                                                         .delegateGroupIdentifiers(Collections.singletonList("del1"))
                                                         .build())
                        .notificationChannels(
                            List.of(NotificationChannel.builder()
                                        .identifier("nc1")
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .notificationChannelType(NotificationChannelType.EMAIL)
                                        .channel(EmailChannel.builder().emailIds(List.of(EMAIL_ID)).build())
                                        .build(),
                                NotificationChannel.builder()
                                    .identifier("nc1")
                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .notificationChannelType(NotificationChannelType.SLACK)
                                    .channel(SlackChannel.builder().slackWebHookUrls(Collections.EMPTY_LIST).build())
                                    .build()))
                        .build()))
                .build()))
        .build();
  }
}
