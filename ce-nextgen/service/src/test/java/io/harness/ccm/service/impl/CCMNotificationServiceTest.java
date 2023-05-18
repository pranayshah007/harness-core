/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.notifications.CCMNotificationsDao;
import io.harness.ccm.commons.entities.notifications.CCMNotificationChannel;
import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting;
import io.harness.ccm.commons.entities.notifications.CCMPerspectiveNotificationChannelsDTO;
import io.harness.ccm.commons.entities.notifications.EmailNotificationChannel;
import io.harness.ccm.commons.entities.notifications.MicrosoftTeamsNotificationChannel;
import io.harness.ccm.commons.entities.notifications.SlackNotificationChannel;
import io.harness.ccm.views.service.CEViewService;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CCMNotificationServiceTest extends CategoryTest {
  @Mock CCMNotificationsDao notificationsDao;
  @Mock CEViewService viewService;
  @InjectMocks CCMNotificationServiceImpl notificationService;

  private CCMNotificationSetting notificationSetting;
  private CCMPerspectiveNotificationChannelsDTO perspectiveNotificationChannels;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String UUID = "UUID";
  private final String PERSPECTIVE_ID = "PERSPECTIVE_ID";
  private final String PERSPECTIVE_NAME = "PERSPECTIVE_NAME";
  private final String SLACK_WEB_HOOK_URL = "SLACK_WEB_HOOK_URL";
  private final String EMAIL = "EMAIL";
  private final String MICROSOFT_TEAMS_URL = "MICROSOFT_TEAMS_URL";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    Map<String, String> perspectiveIdToNameMapping = new HashMap<>();
    perspectiveIdToNameMapping.put(PERSPECTIVE_ID, PERSPECTIVE_NAME);
    SlackNotificationChannel slackNotificationChannel =
        SlackNotificationChannel.builder().slackWebHookUrl(SLACK_WEB_HOOK_URL).build();
    EmailNotificationChannel emailNotificationChannel =
        EmailNotificationChannel.builder().emails(Collections.singletonList(EMAIL)).build();
    MicrosoftTeamsNotificationChannel microsoftTeamsNotificationChannel =
        MicrosoftTeamsNotificationChannel.builder().microsoftTeamsUrl(MICROSOFT_TEAMS_URL).build();

    List<CCMNotificationChannel> channels =
        Arrays.asList(slackNotificationChannel, microsoftTeamsNotificationChannel, emailNotificationChannel);

    notificationSetting = CCMNotificationSetting.builder()
                              .uuid(UUID)
                              .accountId(ACCOUNT_ID)
                              .perspectiveId(PERSPECTIVE_ID)
                              .channels(channels)
                              .build();
    perspectiveNotificationChannels = CCMPerspectiveNotificationChannelsDTO.builder()
                                          .perspectiveId(PERSPECTIVE_ID)
                                          .perspectiveName(PERSPECTIVE_NAME)
                                          .channels(channels)
                                          .build();

    when(notificationsDao.get(PERSPECTIVE_ID, ACCOUNT_ID)).thenReturn(notificationSetting);
    when(notificationsDao.upsert(notificationSetting)).thenReturn(notificationSetting);
    when(notificationsDao.delete(PERSPECTIVE_ID, ACCOUNT_ID)).thenReturn(true);
    when(viewService.getPerspectiveIdToNameMapping(ACCOUNT_ID, Collections.singletonList(PERSPECTIVE_ID)))
        .thenReturn(perspectiveIdToNameMapping);
    when(notificationsDao.list(ACCOUNT_ID)).thenReturn(Collections.singletonList(notificationSetting));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetNotificationSetting() {
    CCMNotificationSetting setting = notificationService.get(PERSPECTIVE_ID, ACCOUNT_ID);
    assertThat(setting).isEqualTo(notificationSetting);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpsertNotificationSetting() {
    CCMNotificationSetting setting = notificationService.upsert(notificationSetting);
    assertThat(setting).isEqualTo(notificationSetting);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDeleteNotificationSetting() {
    boolean response = notificationService.delete(PERSPECTIVE_ID, ACCOUNT_ID);
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testListPerspectiveNotificationChannels() {
    List<CCMPerspectiveNotificationChannelsDTO> perspectiveNotificationChannelsList =
        notificationService.list(ACCOUNT_ID);
    assertThat(perspectiveNotificationChannelsList)
        .isEqualTo(Collections.singletonList(perspectiveNotificationChannels));
  }
}
