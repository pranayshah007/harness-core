/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.notification.entities.NotificationRule.CVNGEmailChannel;
import io.harness.cvng.notification.entities.NotificationRule.CVNGMSTeamsChannel;
import io.harness.cvng.notification.entities.NotificationRule.CVNGPagerDutyChannel;
import io.harness.cvng.notification.entities.NotificationRule.CVNGSlackChannel;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.MSTeamChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.PagerDutyChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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
    CVNGSlackChannel cvngSlackChannel = new CVNGSlackChannel(
        userGroups, "url"); // CVNGSlackChannel.builder().webhookUrl("url").userGroups(userGroups).build();
    NotificationChannel notificationChannel = cvngSlackChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());

    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
    assertThat(((SlackChannel) notificationChannel).getWebhookUrls()).isEqualTo(Arrays.asList("url"));

    cvngSlackChannel.setUserGroups(null);
    notificationChannel = cvngSlackChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forEmail() {
    List<String> recipients = new ArrayList<>();
    recipients.addAll(Arrays.asList("test_user1@harness.io", "test_user2@harness.io"));
    CVNGEmailChannel cvngEmailChannel = new CVNGEmailChannel(userGroups, recipients);
    // CVNGEmailChannel.builder().recipients(recipients).userGroups(userGroups).build();
    NotificationChannel notificationChannel = cvngEmailChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());

    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
    assertThat(((EmailChannel) notificationChannel).getRecipients()).isEqualTo(recipients);

    cvngEmailChannel.setUserGroups(null);
    notificationChannel = cvngEmailChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forMSTeams() {
    List<String> msTeamKeys = new ArrayList<>();
    msTeamKeys.addAll(Arrays.asList("key1", "key2"));
    CVNGMSTeamsChannel cvngmsTeamsChannel = new CVNGMSTeamsChannel(msTeamKeys, userGroups);
    // CVNGMSTeamsChannel.builder().msTeamKeys(msTeamKeys).userGroups(userGroups).build();
    NotificationChannel notificationChannel = cvngmsTeamsChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());

    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
    assertThat(((MSTeamChannel) notificationChannel).getMsTeamKeys()).isEqualTo(msTeamKeys);

    cvngmsTeamsChannel.setUserGroups(null);
    notificationChannel = cvngmsTeamsChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testToNotificationChannel_forPagerduty() {
    CVNGPagerDutyChannel cvngPagerDutyChannel = new CVNGPagerDutyChannel(userGroups, "key1");
    //  CVNGPagerDutyChannel.builder().integrationKey("key1").userGroups(userGroups).build();
    NotificationChannel notificationChannel = cvngPagerDutyChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());

    assertThat(notificationChannel.getAccountId()).isEqualTo(accountIdentifier);
    assertThat(notificationChannel.getTeam()).isEqualTo(Team.CV);
    assertThat(notificationChannel.getTemplateId()).isEqualTo(templateId);
    assertThat(notificationChannel.getUserGroups()).isNotEqualTo(null);
    assertThat(((PagerDutyChannel) notificationChannel).getIntegrationKeys()).isEqualTo(Arrays.asList("key1"));

    cvngPagerDutyChannel.setUserGroups(null);
    notificationChannel = cvngPagerDutyChannel.toNotificationChannel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateId, new HashMap<>());
    assertThat(notificationChannel.getUserGroups()).isEqualTo(Collections.emptyList());
  }
}
