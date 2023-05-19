/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.rule.OwnerRule.MEHUL;

import static software.wings.common.NotificationConstants.BLUE_COLOR;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_NOTIFICATION;
import static software.wings.utils.WingsTestConstants.APPLICATION;
import static software.wings.utils.WingsTestConstants.APPLICATION_URL;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACTS;
import static software.wings.utils.WingsTestConstants.ARTIFACTS_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACTS_URL;
import static software.wings.utils.WingsTestConstants.ENVIRONMENT;
import static software.wings.utils.WingsTestConstants.ENVIRONMENT_URL;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.PIPELINE_URL;
import static software.wings.utils.WingsTestConstants.SERVICE;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_URL;
import static software.wings.utils.WingsTestConstants.TRIGGER_NAME;
import static software.wings.utils.WingsTestConstants.TRIGGER_URL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.VERB;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_URL;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.service.intfc.MicrosoftTeamsNotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class MicrosoftTeamsMessageDispatcherTest extends WingsBaseTest {
  @InjectMocks @Inject private MicrosoftTeamsMessageDispatcher microsoftTeamsMessageDispatcher;
  @Mock private MicrosoftTeamsNotificationService microsoftTeamsNotificationService;

  private static final String[] names = {"name1", "name2", "name3"};
  private static final String ASTERISK_VALUE = "*Hello*";
  private static final String EXPECTED_ASTERISK_VALUE = "**Hello**";
  private static final String APPLICATION_VALUE = "*Application:* <<<APPLICATON_URL|-|APP_NAME>>>";
  private static final String ARTIFACTS_NAME_VALUE = "artifact_1, artifact_2, artifact_3";
  private static final String EXPECTED_ARTIFACTS_NAME_VALUE = "artifact\\\\_1, artifact\\\\_2, artifact\\\\_3";
  private static final String ARTIFACTS_VALUE_WITHOUT_UNDERSCORE =
      "http://testUrl1.com: 52201, https://testUrl2.com: 53202";
  private static final String ALERT_MESSAGE = "alert_message";
  private static final String ALERT_MESSAGE_VALUE =
      "24/7 Service Guard detected anomalies.\nStatus: Open\nName: CV\nApplication: App";
  private static final String EXPECTED_ALERT_MESSAGE_VALUE =
      "24/7 Service Guard detected anomalies.\n\nStatus: Open\n\nName: CV\n\nApplication: App";
  private static final String EXPECTED_ARTIFACTS_VALUE_WITHOUT_UNDERSCORE =
      "[http://testUrl1.com:](http://testUrl1.com:) 52201, [https://testUrl2.com:](https://testUrl2.com:) 53202";
  private static final String ARTIFACTS_VALUE_WITH_UNDERSCORE =
      "http://testUrl1.com/test_file: 52201_test, https://testUrl2.com/test_file: 53202_test";
  private static final String EXPECTED_ARTIFACTS_VALUE_WITH_UNDERSCORE =
      "[http://testUrl1.com/test\\\\_file:](http://testUrl1.com/test_file:) 52201\\\\_test, [https://testUrl2.com/test\\\\_file:](https://testUrl2.com/test_file:) 53202\\\\_test";
  private static final String EXPECTED_APP_NAME = "APP\\\\_NAME";
  private static final String EXPECTED_APPLICATION_VALUE =
      String.format("[%s](%s)", EXPECTED_APP_NAME, APPLICATION_URL);
  private static final String EXPECTED_ENVIRONMENT_NAME = "ENV\\\\_NAME";
  private static final String EXPECTED_SERVICE_VALUE = "[NAME1](URL1), NAME2, [NAME3](URL3)";
  private static final String EXPECTED_ENVIRONMENT_VALUE =
      String.format("[%s](%s)", EXPECTED_ENVIRONMENT_NAME, ENVIRONMENT_URL);
  public static final String EXPECTED_WORKFLOW_NOTIFICATION_FILE =
      "/microsoftteams/expected_workflow_notification.json";
  public static final String EXPECTED_WORKFLOW_NOTIFICATION_ERROR_FILE =
      "/microsoftteams/expected_workflow_notification_error.json";
  private static final String ERRORS = "ERRORS";
  private static final String MICROSOFT_TEAMS_WEBHOOK_URL = "webhookUrl";
  private static final String NA = "N / A";
  private static final String SERVICE_NAME_VALUE = "NAME1,NAME2,NAME3";
  private static final String SERVICE_URL_VALUE = "URL1,,URL3";
  private static final String SERVICE_VALUE = "*Services:* <<<URL1|-|NAME1>>>, <<<URL2|-|NAME2>>>, <<<URL3|-|NAME3>>>";
  private static final String URL_WITH_UNDERSCORE = "Rtsp://testurl1.io/test_file";
  private static final String EXPECTED_URL_WITH_UNDERSCORE =
      "[Rtsp://testurl1.io/test\\\\_file](Rtsp://testurl1.io/test_file)";
  private static final String TEMPLATE_FILE_NAME_WITHOUT_ERROR = "/microsoftteams/workflow_notification.json";
  private static final String TEMPLATE_FILE_NAME_WITH_ERROR = "/microsoftteams/workflow_notification_error.json";
  private static final String APPLICATION_NAME = "APPLICATION_NAME";
  private static final String ENVIRONMENT_NAME = "ENVIRONMENT_NAME";
  private static final ImmutableMap<String, String> templateVariablesWithoutError =
      ImmutableMap.<String, String>builder()
          .put(ALERT_MESSAGE, ALERT_MESSAGE_VALUE)
          .put(APPLICATION, APPLICATION_VALUE)
          .put(APP_NAME, APP_NAME)
          .put(APPLICATION_NAME, APP_NAME)
          .put(APPLICATION_URL, APPLICATION_URL)
          .put(ARTIFACTS, ARTIFACTS_VALUE_WITH_UNDERSCORE)
          .put(ARTIFACTS_NAME, ARTIFACTS_VALUE_WITH_UNDERSCORE)
          .put(ARTIFACTS_URL, EMPTY)
          .put(ENVIRONMENT, ENVIRONMENT)
          .put(ENVIRONMENT_NAME, ENV_NAME)
          .put(ENVIRONMENT_URL, ENVIRONMENT_URL)
          .put(PIPELINE_NAME, EMPTY)
          .put(PIPELINE_URL, EMPTY)
          .put(SERVICE, SERVICE_VALUE)
          .put(SERVICE_NAME, SERVICE_NAME_VALUE)
          .put(SERVICE_URL, SERVICE_URL_VALUE)
          .put("START_DATE", "START-DATE")
          .put("END_DATE", "END-DATE")
          .put(USER_NAME, "USERNAME")
          .put(TRIGGER_NAME, "*ABC*")
          .put(TRIGGER_URL, EMPTY)
          .put(VERB, EMPTY)
          .put(WORKFLOW_NAME, "WORKFLOW-NAME")
          .put(WORKFLOW_URL, WORKFLOW_URL)
          .build();
  private static final InformationNotification notificationWithoutError =
      InformationNotification.builder()
          .notificationTemplateId(WORKFLOW_NOTIFICATION.name())
          .notificationTemplateVariables(templateVariablesWithoutError)
          .build();
  private static final InformationNotification notificationWithError =
      InformationNotification.builder()
          .notificationTemplateId(WORKFLOW_NOTIFICATION.name())
          .notificationTemplateVariables(new HashMap<>(templateVariablesWithoutError))
          .build();
  private static final InformationNotification unsupportedNotification =
      InformationNotification.builder()
          .notificationTemplateId("Unsupported")
          .notificationTemplateVariables(templateVariablesWithoutError)
          .build();

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetTemplateFileName() {
    assertThat(microsoftTeamsMessageDispatcher.getTemplateFileName(notificationWithoutError))
        .isEqualTo(TEMPLATE_FILE_NAME_WITHOUT_ERROR);
    notificationWithError.getNotificationTemplateVariables().put(ERRORS, ERRORS);
    assertThat(microsoftTeamsMessageDispatcher.getTemplateFileName(notificationWithError))
        .isEqualTo(TEMPLATE_FILE_NAME_WITH_ERROR);
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetTemplateWithoutError() throws IOException {
    final String expectedTemplate = getFileContentsAsString(TEMPLATE_FILE_NAME_WITHOUT_ERROR);
    assertEquals(expectedTemplate, microsoftTeamsMessageDispatcher.getTemplate(notificationWithoutError));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetTemplateWithError() throws IOException {
    String expectedTemplate = getFileContentsAsString(TEMPLATE_FILE_NAME_WITH_ERROR);
    notificationWithError.getNotificationTemplateVariables().put(ERRORS, ERRORS);
    assertEquals(expectedTemplate, microsoftTeamsMessageDispatcher.getTemplate(notificationWithError));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetTemplateForUnsupportedNotification() {
    assertEquals(EMPTY, microsoftTeamsMessageDispatcher.getTemplate(unsupportedNotification));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetUpdatedValue() {
    final String[] urls = {"url1", "url2", "url3"};
    final String expectedValue = "[name1](url1), [name2](url2), [name3](url3)";
    assertEquals(expectedValue, microsoftTeamsMessageDispatcher.getUpdatedValue(names, urls));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetUpdatedValueWithLengthMismatch() {
    final String[] urls = {"url1", "url2"};
    assertEquals(EMPTY, microsoftTeamsMessageDispatcher.getUpdatedValue(names, urls));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetUpdatedValueWithMissingUrls() {
    final String[] urls = {"", "url2", ""};
    final String expectedValue = "name1, [name2](url2), name3";
    assertEquals(expectedValue, microsoftTeamsMessageDispatcher.getUpdatedValue(names, urls));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testProcessTemplateVariables() {
    Map<String, String> expectedTemplateVariables = new HashMap<>(templateVariablesWithoutError);
    populateExpectedEntries(expectedTemplateVariables);
    Map<String, String> actualTemplateVariables =
        microsoftTeamsMessageDispatcher.processTemplateVariables(templateVariablesWithoutError);
    assertThat(actualTemplateVariables).containsAllEntriesOf(expectedTemplateVariables);
  }

  private void populateExpectedEntries(Map<String, String> expectedTemplateVariables) {
    expectedTemplateVariables.put(ALERT_MESSAGE, EXPECTED_ALERT_MESSAGE_VALUE);
    expectedTemplateVariables.put(APP_NAME, EXPECTED_APP_NAME);
    expectedTemplateVariables.put(APPLICATION_NAME, EXPECTED_APP_NAME);
    expectedTemplateVariables.put(APPLICATION, EXPECTED_APPLICATION_VALUE);
    expectedTemplateVariables.put(ARTIFACTS, EXPECTED_ARTIFACTS_VALUE_WITH_UNDERSCORE);
    expectedTemplateVariables.put(ARTIFACTS_NAME, EXPECTED_ARTIFACTS_VALUE_WITH_UNDERSCORE);
    expectedTemplateVariables.put(ENVIRONMENT, EXPECTED_ENVIRONMENT_VALUE);
    expectedTemplateVariables.put(ENVIRONMENT_NAME, EXPECTED_ENVIRONMENT_NAME);
    expectedTemplateVariables.put(PIPELINE, "");
    expectedTemplateVariables.put(PIPELINE_NAME, NA);
    expectedTemplateVariables.put(SERVICE, EXPECTED_SERVICE_VALUE);
    expectedTemplateVariables.put("THEME_COLOR", BLUE_COLOR);
    expectedTemplateVariables.put("TRIGGER", "**ABC**");
    expectedTemplateVariables.put(TRIGGER_NAME, "**ABC**");
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDispatchWorkflowNotification() throws IOException {
    List<Notification> notifications = Collections.singletonList(notificationWithoutError);
    microsoftTeamsMessageDispatcher.dispatch(notifications, MICROSOFT_TEAMS_WEBHOOK_URL);
    final String expectedMessage = getFileContentsAsString(EXPECTED_WORKFLOW_NOTIFICATION_FILE);
    Mockito.verify(microsoftTeamsNotificationService).sendMessage(expectedMessage, MICROSOFT_TEAMS_WEBHOOK_URL);
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDispatchMultipleNotifications() throws IOException {
    List<Notification> notifications = new ArrayList<>();
    notificationWithError.getNotificationTemplateVariables().put(ERRORS, ERRORS);
    notifications.add(notificationWithError);
    notifications.add(unsupportedNotification);
    notifications.add(notificationWithoutError);
    ArgumentCaptor<String> messageArgument = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> webhookUrlArgument = ArgumentCaptor.forClass(String.class);
    microsoftTeamsMessageDispatcher.dispatch(notifications, MICROSOFT_TEAMS_WEBHOOK_URL);
    Mockito.verify(microsoftTeamsNotificationService, Mockito.times(2))
        .sendMessage(messageArgument.capture(), webhookUrlArgument.capture());
    assertEquals(getExpectedMessageValues(), messageArgument.getAllValues());
    List<String> expectedWebhookUrlArguments = Arrays.asList(MICROSOFT_TEAMS_WEBHOOK_URL, MICROSOFT_TEAMS_WEBHOOK_URL);
    assertEquals(expectedWebhookUrlArguments, webhookUrlArgument.getAllValues());
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDispatchEmptyNotification() {
    List<Notification> notifications = new ArrayList<>();
    microsoftTeamsMessageDispatcher.dispatch(notifications, MICROSOFT_TEAMS_WEBHOOK_URL);
    Mockito.verifyZeroInteractions(microsoftTeamsNotificationService);
  }

  private List<String> getExpectedMessageValues() throws IOException {
    List<String> expectedValues = new ArrayList<>();
    expectedValues.add(getFileContentsAsString(EXPECTED_WORKFLOW_NOTIFICATION_ERROR_FILE));
    expectedValues.add(getFileContentsAsString(EXPECTED_WORKFLOW_NOTIFICATION_FILE));
    return expectedValues;
  }

  private String getFileContentsAsString(String fileName) throws IOException {
    InputStream in = getClass().getResourceAsStream(fileName);

    String fileContentasString =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
    return fileContentasString;
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testHandleUnderscoreInValue() {
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(APPLICATION_NAME, APP_NAME))
        .isEqualTo(EXPECTED_APP_NAME);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ENVIRONMENT_NAME, ENV_NAME))
        .isEqualTo(EXPECTED_ENVIRONMENT_NAME);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(APPLICATION_URL, APPLICATION_URL))
        .isEqualTo(APPLICATION_URL);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ENVIRONMENT_URL, ENVIRONMENT_URL))
        .isEqualTo(ENVIRONMENT_URL);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ARTIFACTS, ARTIFACTS_NAME_VALUE))
        .isEqualTo(EXPECTED_ARTIFACTS_NAME_VALUE);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(APP_NAME, URL_WITH_UNDERSCORE))
        .isEqualTo(EXPECTED_URL_WITH_UNDERSCORE);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ARTIFACTS, ARTIFACTS_VALUE_WITHOUT_UNDERSCORE))
        .isEqualTo(EXPECTED_ARTIFACTS_VALUE_WITHOUT_UNDERSCORE);
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ARTIFACTS, ARTIFACTS_VALUE_WITH_UNDERSCORE))
        .isEqualTo(EXPECTED_ARTIFACTS_VALUE_WITH_UNDERSCORE);
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testHandleNewLineInValue() {
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ALERT_MESSAGE, ALERT_MESSAGE_VALUE))
        .isEqualTo(EXPECTED_ALERT_MESSAGE_VALUE);
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testHandleAsteriskInValue() {
    assertThat(microsoftTeamsMessageDispatcher.handleSpecialCharacters(ALERT_MESSAGE, ASTERISK_VALUE))
        .isEqualTo(EXPECTED_ASTERISK_VALUE);
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testCheckIfStringIsValidUrl() {
    assertThat(microsoftTeamsMessageDispatcher.checkIfStringIsValidUrl("http://app.harness.io")).isTrue();
    assertThat(microsoftTeamsMessageDispatcher.checkIfStringIsValidUrl("Http://www.abc.com")).isTrue();
    assertThat(microsoftTeamsMessageDispatcher.checkIfStringIsValidUrl("www.abc")).isFalse();
  }
}
