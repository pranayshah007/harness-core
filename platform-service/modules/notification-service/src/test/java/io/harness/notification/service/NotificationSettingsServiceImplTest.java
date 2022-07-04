/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.repositories.NotificationSettingRepository;
import io.harness.remote.client.RestClientUtils;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(PL)
@RunWith(PowerMockRunner.class)
@PrepareForTest(RestClientUtils.class)
public class NotificationSettingsServiceImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private UserClient userClient;
  @Mock private NotificationSettingRepository notificationSettingRepository;
  @Mock private SmtpConfigClient smtpConfigClient;
  private NotificationSettingsServiceImpl notificationSettingsService;
  private static final String SLACK_WEBHOOK_URL = "https://hooks.slack.com/services/TL81600E8/B027JT97D5X/";
  private static final String SLACK_SECRET_1 = "<+secrets.getValue('SlackWebhookUrlSecret1')>";
  private static final String SLACK_SECRET_2 = "<+secrets.getValue('SlackWebhookUrlSecret2')>";
  private static final String SLACK_SECRET_3 = "<+secrets.getValue(\"SlackWebhookUrlSecret3\")>";
  private static final String SLACK_ORG_SECRET = "<+secrets.getValue('org.SlackWebhookUrlSecret')>";
  private static final String SLACK_ACCOUNT_SECRET = "<+secrets.getValue('account.SlackWebhookUrlSecret')>";
  private static final String PAGERDUTY_SECRET = "<+secrets.getValue('PagerDutyWebhookUrlSecret')>";
  private static final long EXPRESSION_FUNCTOR_TOKEN_1 = HashGenerator.generateIntegerHash();
  private static final String RESOLVED_SLACK_SECRET_1 =
      String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret1\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_SLACK_SECRET_2 =
      String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret2\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_SLACK_SECRET_3 =
      String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret3\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_PAGER_DUTY_SECRET =
      String.format("${ngSecretManager.obtain(\"PagerDutyWebhookUrlSecret\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_SLACK_ORG_SECRET =
      String.format("${ngSecretManager.obtain(\"org.SlackWebhookUrlSecret\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_SLACK_ACCOUNT_SECRET =
      String.format("${ngSecretManager.obtain(\"account.SlackWebhookUrlSecret\", %d)}", EXPRESSION_FUNCTOR_TOKEN_1);
  private static final String RESOLVED_SLACK_SECRET_WITH_FUNCTOR_ZERO =
      "${ngSecretManager.obtain(\"SlackWebhookUrlSecret1\", 0)}";
  private static final String EMAIL_ID_1 = "user1@gmail.com";
  private static final String EMAIL_ID_2 = "user2@gmail.com";
  private static final String EMAIL_ID_3 = "user3@gmail.com";
  private static final String ACCOUNT_ID = "vpCkHKsDSxK9_KYfjCT";
  private static final String USER_ID_1 = "kIbAmAVeQIaUPntB2jDBKA";
  private static final String USER_ID_2 = "imsuYBJ1TKG4j1ycwZEqOA";
  private static final String USER_ID_3 = "EiTE4Ij2RXqazZlvlvHpMQ";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    PowerMockito.mockStatic(RestClientUtils.class);
    notificationSettingsService = new NotificationSettingsServiceImpl(
        userGroupClient, userClient, notificationSettingRepository, smtpConfigClient);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForSecretExpressionSlackUserGroups() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_SECRET_1);
    notificationSettings.add(SLACK_SECRET_2);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(RESOLVED_SLACK_SECRET_1, resolvedUserGroups.get(0));
    assertEquals(RESOLVED_SLACK_SECRET_2, resolvedUserGroups.get(1));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForOrgSecretAndAccountSecretExpressionSlackUserGroups() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_ORG_SECRET);
    notificationSettings.add(SLACK_ACCOUNT_SECRET);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(RESOLVED_SLACK_ORG_SECRET, resolvedUserGroups.get(0));
    assertEquals(RESOLVED_SLACK_ACCOUNT_SECRET, resolvedUserGroups.get(1));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForInvalidExpression() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add("<+adbc>");
    assertThatThrownBy(()
                           -> notificationSettingsService.resolveUserGroups(
                               NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Expression provided is not valid");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForPlainTextSlackUserGroups() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_WEBHOOK_URL);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(SLACK_WEBHOOK_URL, resolvedUserGroups.get(0));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForSecretExpressionPagerDutyUserGroups() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(PAGERDUTY_SECRET);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(RESOLVED_PAGER_DUTY_SECRET, resolvedUserGroups.get(0));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForEmptyUserGroups() {
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, new ArrayList<>(), EXPRESSION_FUNCTOR_TOKEN_1);
    assertTrue(resolvedUserGroups.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRegex() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_SECRET_3);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(RESOLVED_SLACK_SECRET_3, resolvedUserGroups.get(0));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookSecret() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(RESOLVED_SLACK_SECRET_1);
    notificationSettings.add(RESOLVED_SLACK_SECRET_2);
    notificationSettings.add(RESOLVED_SLACK_SECRET_WITH_FUNCTOR_ZERO);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookNonSecret() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_WEBHOOK_URL);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertFalse(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookSecretAndNonSecret() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_WEBHOOK_URL);
    notificationSettings.add(RESOLVED_SLACK_SECRET_1);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetEmailsForUserIds() {
    List<String> userIds = new ArrayList<>();
    userIds.add(USER_ID_1);
    userIds.add(USER_ID_2);
    userIds.add(USER_ID_3);
    List<UserInfo> userInfoList = new ArrayList<>();
    userInfoList.add(UserInfo.builder().email(EMAIL_ID_1).build());
    userInfoList.add(UserInfo.builder().email(EMAIL_ID_2).build());
    userInfoList.add(UserInfo.builder().email(EMAIL_ID_3).build());
    when(RestClientUtils.getResponse(any())).thenReturn(userInfoList);
    List<String> emails = notificationSettingsService.getEmailsForUserIds(userIds, ACCOUNT_ID);
    assertEquals(EMAIL_ID_1, emails.get(0));
    assertEquals(EMAIL_ID_2, emails.get(1));
    assertEquals(EMAIL_ID_3, emails.get(2));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetEmailsForEmptyUserIds() {
    List<String> emails = notificationSettingsService.getEmailsForUserIds(new ArrayList<>(), ACCOUNT_ID);
    assertTrue(emails.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationSettings() {
    List<UserInfo> userInfoList = new ArrayList<>();
    userInfoList.add(UserInfo.builder().email(EMAIL_ID_1).build());
    when(RestClientUtils.getResponse(any())).thenReturn(userInfoList);
    List<UserGroupDTO> userGroupDTOList = new ArrayList<>();
    EmailConfigDTO emailConfigDTO = EmailConfigDTO.builder().groupEmail(EMAIL_ID_1).build();
    userGroupDTOList.add(UserGroupDTO.builder().notificationConfigs(Collections.singletonList(emailConfigDTO)).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertEquals(EMAIL_ID_1, emails.get(0));
  }
}
