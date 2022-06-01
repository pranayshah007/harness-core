/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.harness.rule.OwnerRule.KAPIL;

public class CVNGNotificationChannelTest {
  List<String> userGroups = new ArrayList<>();
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String templateId;

  @Before
  public void setUp() {
    accountIdentifier = "accountIdentifier";
    orgIdentifier = "orgIdentifier";
    projectIdentifier = "projectIdentifier";
    templateId = "templateId";
    userGroups.addAll(Arrays.asList("user", "org.user"));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forSlack() {
//    CVNGSlackChannel cvngSlackChannel = CVNGSlackChannel.builder().webhookUrl("url").userGroups(userGroups).build();
//    NotificationChannel notificationChannel = cvngSlackChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//
//    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
//    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
//    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
//    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
//    assertThat(((SlackChannel) notificationChannel).getWebhookUrls()).isEqualTo(Arrays.asList("url"));
//
//    cvngSlackChannel.setUserGroups(null);
//    notificationChannel = cvngSlackChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forEmail() {
//    List<String> recipients = new ArrayList<>();
//    recipients.addAll(Arrays.asList("test_user1@harness.io", "test_user2@harness.io"));
//    CVNGEmailChannel cvngEmailChannel =
//        CVNGEmailChannel.builder().recipients(recipients).userGroups(userGroups).build();
//    NotificationChannel notificationChannel = cvngEmailChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//
//    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
//    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
//    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
//    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
//    assertThat(((EmailChannel) notificationChannel).getRecipients()).isEqualTo(recipients);
//
//    cvngEmailChannel.setUserGroups(null);
//    notificationChannel = cvngEmailChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forMSTeams() {
//    List<String> msTeamKeys = new ArrayList<>();
//    msTeamKeys.addAll(Arrays.asList("key1", "key2"));
//    CVNGMSTeamsChannel cvngmsTeamsChannel =
//        CVNGMSTeamsChannel.builder().msTeamKeys(msTeamKeys).userGroups(userGroups).build();
//    NotificationChannel notificationChannel = cvngmsTeamsChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//
//    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
//    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
//    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
//    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
//    assertThat(((MSTeamChannel) notificationChannel).getMsTeamKeys()).isEqualTo(msTeamKeys);
//
//    cvngmsTeamsChannel.setUserGroups(null);
//    notificationChannel = cvngmsTeamsChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forPagerduty() {
//    CVNGPagerDutyChannel cvngPagerDutyChannel =
//        CVNGPagerDutyChannel.builder().integrationKey("key1").userGroups(userGroups).build();
//    NotificationChannel notificationChannel = cvngPagerDutyChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//
//    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
//    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
//    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
//    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
//    assertThat(((PagerDutyChannel) notificationChannel).getIntegrationKeys()).isEqualTo(Arrays.asList("key1"));
//
//    cvngPagerDutyChannel.setUserGroups(null);
//    notificationChannel = cvngPagerDutyChannel.toNotificationChannel(
//        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
//    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }
}
