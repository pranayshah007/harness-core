/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.rule.OwnerRule.ANKUSH;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.SlackSettingDTO;
import io.harness.notification.service.api.ChannelService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ChannelResourceTest extends CategoryTest {
  @Mock private ChannelService channelService;

  private ChannelResource channelResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    channelResource = new ChannelResourceImpl(channelService);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testNotificationSetting_NotificationIdAbsent() {
    NotificationSettingDTO notificationSettingDTO =
        SlackSettingDTO.builder().recipient("slack-webhook").accountId("kmpySmUISimoRrJL6NL73w").build();
    channelResource.testNotificationSetting(notificationSettingDTO);
    verify(channelService, times(1)).sendTestNotification(notificationSettingDTO);
  }
}
