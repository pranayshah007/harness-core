/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.rule.OwnerRule.JENNY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.entities.EmailChannel;
import io.harness.notification.entities.MicrosoftTeamsChannel;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationChannel.NotificationChannelKeys;
import io.harness.notification.entities.PagerDutyChannel;
import io.harness.notification.entities.SlackChannel;
import io.harness.notification.repositories.NotificationChannelRepository;
import io.harness.notification.service.api.NotificationChannelManagementService;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

public class NotificationChannelManagementServiceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "AccountId";
  private static final String ORG_IDENTIFIER = "OrgId";
  private static final String PROJECT_IDENTIFIER = "ProjectId";
  private static final String EMAIL_ID = "test@harness.com";

  NotificationChannelManagementService notificationChannelManagementService;
  @Mock NotificationChannelRepository notificationChannelRepository;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    notificationChannelManagementService = new NotificationChannelManagementServiceImpl(notificationChannelRepository);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateNotificationChannel() {
    NotificationChannel notificationChannel =
        NotificationChannel.builder()
            .identifier("nc1")
            .notificationChannelType(NotificationChannelType.MSTEAMS)
            .channel(MicrosoftTeamsChannel.builder().msTeamKeys(Collections.EMPTY_LIST).build())
            .build();
    notificationChannelManagementService.create(notificationChannel);
    verify(notificationChannelRepository, times(1)).save(notificationChannel);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUpdateNotificationChannel() {
    NotificationChannel notificationChannel =
        NotificationChannel.builder()
            .identifier("nc1")
            .notificationChannelType(NotificationChannelType.SLACK)
            .channel(SlackChannel.builder().slackWebHookUrls(Collections.emptyList()).build())
            .build();
    notificationChannelManagementService.create(notificationChannel);
    verify(notificationChannelRepository, times(1)).save(notificationChannel);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetNotificationChannel() {
    NotificationChannel notificationChannel = NotificationChannel.builder()
                                                  .identifier("nc1")
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                                  .notificationChannelType(NotificationChannelType.EMAIL)
                                                  .channel(EmailChannel.builder().emailIds(List.of(EMAIL_ID)).build())
                                                  .build();
    Criteria criteria = new Criteria();
    criteria.and(NotificationChannelKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);
    criteria.and(NotificationChannelKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(NotificationChannelKeys.projectIdentifier).is(PROJECT_IDENTIFIER);
    criteria.and(NotificationChannelKeys.identifier).is("nc1");

    when(notificationChannelRepository.findOne(criteria)).thenReturn(notificationChannel);
    NotificationChannel nc =
        notificationChannelManagementService.get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "nc1");
    assertEquals("nc1", nc.getIdentifier());
    assertEquals(nc.getChannelType(), NotificationChannelType.EMAIL);
    assertEquals(ACCOUNT_IDENTIFIER, nc.getAccountIdentifier());
    assertEquals(ORG_IDENTIFIER, nc.getOrgIdentifier());
    assertEquals(PROJECT_IDENTIFIER, nc.getProjectIdentifier());
    EmailChannel emailChannel = (EmailChannel) nc.getChannel();
    assertEquals(EMAIL_ID, emailChannel.getEmailIds().get(0));
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAllNotificationChannelsInAccount() {
    NotificationChannel notificationChannel1 = NotificationChannel.builder()
                                                   .identifier("nc1")
                                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                   .orgIdentifier(ORG_IDENTIFIER)
                                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                                   .notificationChannelType(NotificationChannelType.EMAIL)
                                                   .channel(EmailChannel.builder().emailIds(List.of(EMAIL_ID)).build())
                                                   .build();
    NotificationChannel notificationChannel2 =
        NotificationChannel.builder()
            .identifier("nc2")
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .notificationChannelType(NotificationChannelType.SLACK)
            .channel(SlackChannel.builder().slackWebHookUrls(Collections.emptyList()).build())
            .build();
    Criteria criteria = new Criteria();
    criteria.and(NotificationChannelKeys.accountIdentifier).is(ACCOUNT_IDENTIFIER);
    criteria.and(NotificationChannelKeys.orgIdentifier).is(ORG_IDENTIFIER);
    criteria.and(NotificationChannelKeys.projectIdentifier).is(PROJECT_IDENTIFIER);
    when(notificationChannelRepository.findAll(criteria))
        .thenReturn(Lists.newArrayList(notificationChannel1, notificationChannel2));
    List<NotificationChannel> nc = notificationChannelManagementService.getNotificationChannelList(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertEquals(2, nc.size());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteNotificationChannel() {
    NotificationChannel notificationChannel =
        NotificationChannel.builder()
            .identifier("nc1")
            .status(NotificationChannel.Status.DISABLED)
            .notificationChannelType(NotificationChannelType.PAGERDUTY)
            .channel(PagerDutyChannel.builder().pagerDutyIntegrationKeys(Collections.EMPTY_LIST).build())
            .build();
    notificationChannelManagementService.delete(notificationChannel);
    verify(notificationChannelRepository, times(1)).delete(notificationChannel);
  }
}
