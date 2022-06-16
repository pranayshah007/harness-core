package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.notification.repositories.NotificationSettingRepository;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
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
  private static final String PAGERDUTY_SECRET = "<+secrets.getValue('PagerDutyWebhookUrlSecret')>";
  private long expressionFunctorToken;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    expressionFunctorToken = HashGenerator.generateIntegerHash();
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
        NotificationChannelType.SLACK, notificationSettings, expressionFunctorToken);
    String expectedUserGroup1 =
        String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret1\", %d)}", expressionFunctorToken);
    String expectedUserGroup2 =
        String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret2\", %d)}", expressionFunctorToken);
    assertEquals(expectedUserGroup1, resolvedUserGroups.get(0));
    assertEquals(expectedUserGroup2, resolvedUserGroups.get(1));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForInvalidExpression() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add("<+adbc>");
    assertThatThrownBy(()
                           -> notificationSettingsService.resolveUserGroups(
                               NotificationChannelType.SLACK, notificationSettings, expressionFunctorToken))
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
        NotificationChannelType.SLACK, notificationSettings, expressionFunctorToken);
    assertEquals(SLACK_WEBHOOK_URL, resolvedUserGroups.get(0));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForSecretExpressionPagerDutyUserGroups() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(PAGERDUTY_SECRET);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, expressionFunctorToken);
    String expectedUserGroup =
        String.format("${ngSecretManager.obtain(\"PagerDutyWebhookUrlSecret\", %d)}", expressionFunctorToken);
    assertEquals(expectedUserGroup, resolvedUserGroups.get(0));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetNotificationRequestForEmptyUserGroups() {
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, new ArrayList<>(), expressionFunctorToken);
    assertTrue(resolvedUserGroups.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testRegex() {
    List<String> notificationSettings = new ArrayList<>();
    notificationSettings.add(SLACK_SECRET_3);
    List<String> resolvedUserGroups = notificationSettingsService.resolveUserGroups(
        NotificationChannelType.SLACK, notificationSettings, expressionFunctorToken);
    String expectedUserGroup1 =
        String.format("${ngSecretManager.obtain(\"SlackWebhookUrlSecret3\", %d)}", expressionFunctorToken);
    assertEquals(expectedUserGroup1, resolvedUserGroups.get(0));
  }
}
