/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.repositories.NotificationSettingRepository;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class NotificationSettingsServiceImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private UserClient userClient;
  @Mock private NotificationSettingRepository notificationSettingRepository;
  @Mock private SmtpConfigClient smtpConfigClient;
  private MockedStatic<CGRestUtils> cgRestUtilsMockedStatic;
  private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
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
  private static final String RESOLVED_SLACK_SWEEPING_OUTPUT_SECRET_1 =
      "${sweepingOutputSecrets.obtain(\"ovar3\",\"BASE_64\")}";
  private static final String RESOLVED_SLACK_SWEEPING_OUTPUT_SECRET_2 =
      "${sweepingOutputSecrets.obtain(\"output1\",\"sa32zupgqijF2be+H2lEAw7yfMwGDFtC5zciKbzQGEtm5Vq+cjo7RclAhPVLTig7\")}";
  private static final String EMAIL_ID_1 = "user1@gmail.com";
  private static final String EMAIL_ID_2 = "user2@gmail.com";
  private static final String EMAIL_ID_3 = "user3@gmail.com";
  private static final String ACCOUNT_ID = "vpCkHKsDSxK9_KYfjCT";
  private static final String USER_ID_1 = "kIbAmAVeQIaUPntB2jDBKA";
  private static final String USER_ID_2 = "imsuYBJ1TKG4j1ycwZEqOA";
  private static final String USER_ID_3 = "EiTE4Ij2RXqazZlvlvHpMQ";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "testProject";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cgRestUtilsMockedStatic = mockStatic(CGRestUtils.class);
    taskSetupAbstractionHelper = new TaskSetupAbstractionHelper();
    notificationSettingsService = new NotificationSettingsServiceImpl(
        userGroupClient, userClient, notificationSettingRepository, smtpConfigClient, taskSetupAbstractionHelper);
  }

  @After
  public void cleanup() {
    cgRestUtilsMockedStatic.close();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForSecretExpressionSlackUserGroups() {
    List<String> notificationSettings = Arrays.asList(SLACK_SECRET_1, SLACK_SECRET_2);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(Arrays.asList(RESOLVED_SLACK_SECRET_1, RESOLVED_SLACK_SECRET_2), resolvedUserGroups);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForOrgSecretAndAccountSecretExpressionSlackUserGroups() {
    List<String> notificationSettings = Arrays.asList(SLACK_ORG_SECRET, SLACK_ACCOUNT_SECRET);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(Arrays.asList(RESOLVED_SLACK_ORG_SECRET, RESOLVED_SLACK_ACCOUNT_SECRET), resolvedUserGroups);
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
    List<String> notificationSettings = Arrays.asList(SLACK_WEBHOOK_URL);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(Arrays.asList(SLACK_WEBHOOK_URL), resolvedUserGroups);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForSecretExpressionPagerDutyUserGroups() {
    List<String> notificationSettings = Arrays.asList(PAGERDUTY_SECRET);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, EXPRESSION_FUNCTOR_TOKEN_1);
    assertEquals(Arrays.asList(RESOLVED_PAGER_DUTY_SECRET), resolvedUserGroups);
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
    assertEquals(Arrays.asList(RESOLVED_SLACK_SECRET_3), resolvedUserGroups);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookSecret() {
    List<String> notificationSettings =
        Arrays.asList(RESOLVED_SLACK_SECRET_1, RESOLVED_SLACK_SECRET_2, RESOLVED_SLACK_SECRET_WITH_FUNCTOR_ZERO);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookNonSecret() {
    List<String> notificationSettings = Arrays.asList(SLACK_WEBHOOK_URL);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertFalse(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSlackWebhookSecretAndNonSecret() {
    List<String> notificationSettings = Arrays.asList(SLACK_WEBHOOK_URL, RESOLVED_SLACK_SECRET_1);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetEmailsForUserIds() {
    List<String> userIds = Arrays.asList(USER_ID_1, USER_ID_2, USER_ID_3);
    List<UserInfo> userInfoList = Arrays.asList(UserInfo.builder().email(EMAIL_ID_1).build(),
        UserInfo.builder().email(EMAIL_ID_2).build(), UserInfo.builder().email(EMAIL_ID_3).build());
    cgRestUtilsMockedStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(userInfoList);
    List<String> emails = notificationSettingsService.getEmailsForUserIds(userIds, ACCOUNT_ID);
    List<String> expected = Arrays.asList(EMAIL_ID_1, EMAIL_ID_2, EMAIL_ID_3);
    assertEquals(expected, emails);
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
    List<String> userIds = Arrays.asList(USER_ID_1);
    List<UserInfo> userInfoList = Arrays.asList(UserInfo.builder().email(EMAIL_ID_1).build());
    cgRestUtilsMockedStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(userInfoList);
    List<UserGroupDTO> userGroupDTOList = new ArrayList<>();
    EmailConfigDTO emailConfigDTO = EmailConfigDTO.builder().groupEmail(EMAIL_ID_1).build();
    userGroupDTOList.add(
        UserGroupDTO.builder().notificationConfigs(Arrays.asList(emailConfigDTO)).users(userIds).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertEquals(Arrays.asList(EMAIL_ID_1), emails);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testEmailNotificationWhenSendAllBooleanIsTrue() {
    List<String> userIds = Arrays.asList(USER_ID_1);
    List<UserInfo> userInfoList = Arrays.asList(UserInfo.builder().email(EMAIL_ID_1).build());
    cgRestUtilsMockedStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(userInfoList);
    List<UserGroupDTO> userGroupDTOList = new ArrayList<>();
    EmailConfigDTO emailConfigDTO = EmailConfigDTO.builder().groupEmail(EMAIL_ID_2).sendEmailToAllUsers(true).build();
    userGroupDTOList.add(
        UserGroupDTO.builder().notificationConfigs(Arrays.asList(emailConfigDTO)).users(userIds).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertEquals(Arrays.asList(EMAIL_ID_1, EMAIL_ID_2), emails);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testEmailNotificationWhenSendAllBooleanIsEmpty() {
    List<String> userIds = Arrays.asList(USER_ID_1);
    List<UserInfo> userInfoList = Arrays.asList(UserInfo.builder().email(EMAIL_ID_1).build());
    cgRestUtilsMockedStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(userInfoList);
    List<UserGroupDTO> userGroupDTOList = new ArrayList<>();
    EmailConfigDTO emailConfigDTO = EmailConfigDTO.builder().groupEmail(EMAIL_ID_2).build();
    userGroupDTOList.add(
        UserGroupDTO.builder().notificationConfigs(Arrays.asList(emailConfigDTO)).users(userIds).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertEquals(Arrays.asList(EMAIL_ID_1, EMAIL_ID_2), emails);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testEmailNotificationWhenSendAllBooleanIsFalse() {
    List<String> userIds = Arrays.asList(USER_ID_1);
    List<UserInfo> userInfoList = Arrays.asList(UserInfo.builder().email(EMAIL_ID_1).build());
    cgRestUtilsMockedStatic.when(() -> CGRestUtils.getResponse(any())).thenReturn(userInfoList);
    List<UserGroupDTO> userGroupDTOList = new ArrayList<>();
    EmailConfigDTO emailConfigDTO = EmailConfigDTO.builder().sendEmailToAllUsers(false).groupEmail(EMAIL_ID_2).build();
    userGroupDTOList.add(
        UserGroupDTO.builder().notificationConfigs(Arrays.asList(emailConfigDTO)).users(userIds).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertEquals(Arrays.asList(EMAIL_ID_2), emails);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationSettingsWithDifferentChannelTypes() {
    List<UserGroupDTO> userGroupDTOList =
        Arrays.asList(UserGroupDTO.builder()
                          .notificationConfigs(Arrays.asList(EmailConfigDTO.builder().groupEmail(EMAIL_ID_1).build()))
                          .build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.SLACK, userGroupDTOList, ACCOUNT_ID);
    assertTrue(emails.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationSettingsWhenNotificationConfigsIsEmpty() {
    List<UserGroupDTO> userGroupDTOList = Arrays.asList(UserGroupDTO.builder().build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertTrue(emails.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationSettingsWhenSettingIsNotPresent() {
    List<UserGroupDTO> userGroupDTOList =
        Arrays.asList(UserGroupDTO.builder().notificationConfigs(new ArrayList<>()).build());
    List<String> emails = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.EMAIL, userGroupDTOList, ACCOUNT_ID);
    assertTrue(emails.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationSettingsForSlack() {
    List<UserGroupDTO> userGroupDTOList = Arrays.asList(
        UserGroupDTO.builder()
            .notificationConfigs(Arrays.asList(SlackConfigDTO.builder().slackWebhookUrl(SLACK_WEBHOOK_URL).build()))
            .build());
    List<String> slackWebhooks = notificationSettingsService.getNotificationSettings(
        NotificationChannelType.SLACK, userGroupDTOList, ACCOUNT_ID);
    assertEquals(Arrays.asList(SLACK_WEBHOOK_URL), slackWebhooks);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildAbstractions() {
    Map<String, String> abstractionMap =
        notificationSettingsService.buildTaskAbstractions(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    assertEquals(String.format("%s/%s", ORG_ID, PROJECT_ID), abstractionMap.get(NG_DELEGATE_OWNER_CONSTANT));
    assertEquals(ACCOUNT_ID, abstractionMap.get(ACCOUNT_IDENTIFIER));
    assertEquals(ORG_ID, abstractionMap.get(ORG_IDENTIFIER));
    assertEquals(PROJECT_ID, abstractionMap.get(PROJECT_IDENTIFIER));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testSlackWebhookSweepingOutputSecret() {
    List<String> notificationSettings = Arrays.asList(RESOLVED_SLACK_SWEEPING_OUTPUT_SECRET_1);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testSlackWebhookSweepingOutputSecret2() {
    List<String> notificationSettings = Arrays.asList(RESOLVED_SLACK_SWEEPING_OUTPUT_SECRET_2);
    boolean isSecret = notificationSettingsService.checkIfWebhookIsSecret(notificationSettings);
    assertTrue(isSecret);
  }
}
