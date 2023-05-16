/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting;
import io.harness.ccm.commons.entities.notifications.SlackNotificationChannel;
import io.harness.ccm.service.intf.CCMNotificationService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CCMNotificationSettingResourceTest extends CategoryTest {
  @Mock private CCMNotificationService notificationService;
  @InjectMocks private CCMNotificationSettingResource notificationSettingResource;

  private CCMNotificationSetting notificationSetting;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String UUID = "UUID";
  private final String PERSPECTIVE_ID = "PERSPECTIVE_ID";
  private final String SLACK_WEB_HOOK_URL = "SLACK_WEB_HOOK_URL";

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    SlackNotificationChannel slackNotificationChannel =
        SlackNotificationChannel.builder().slackWebHookUrl(SLACK_WEB_HOOK_URL).build();
    notificationSetting = CCMNotificationSetting.builder()
                              .uuid(UUID)
                              .accountId(ACCOUNT_ID)
                              .perspectiveId(PERSPECTIVE_ID)
                              .channels(Collections.singletonList(slackNotificationChannel))
                              .build();
    when(notificationService.get(PERSPECTIVE_ID, ACCOUNT_ID)).thenReturn(notificationSetting);
    when(notificationService.upsert(notificationSetting)).thenReturn(notificationSetting);
    when(notificationService.delete(PERSPECTIVE_ID, ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testCreateNotificationSetting() {
    ResponseDTO<CCMNotificationSetting> response =
        notificationSettingResource.save(ACCOUNT_ID, PERSPECTIVE_ID, notificationSetting);
    assertThat(response.getData()).isEqualTo(notificationSetting);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGetNotificationSetting() {
    ResponseDTO<CCMNotificationSetting> response = notificationSettingResource.get(ACCOUNT_ID, PERSPECTIVE_ID);
    assertThat(response.getData()).isEqualTo(notificationSetting);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testUpdateNotificationSetting() {
    ResponseDTO<CCMNotificationSetting> response =
        notificationSettingResource.update(ACCOUNT_ID, PERSPECTIVE_ID, notificationSetting);
    assertThat(response.getData()).isEqualTo(notificationSetting);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDeleteNotificationSetting() {
    ResponseDTO<Boolean> response = notificationSettingResource.delete(ACCOUNT_ID, PERSPECTIVE_ID);
    assertThat(response.getData()).isEqualTo(true);
  }
}
