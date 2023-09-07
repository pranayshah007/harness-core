/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.notification.NotificationRequest;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.SlackSettingDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class ChannelServiceImplTest extends CategoryTest {
  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private WebhookServiceImpl webhookService;
  @Mock private SlackServiceImpl slackService;
  @Mock private PagerDutyServiceImpl pagerDutyService;
  @Mock private MSTeamsServiceImpl msTeamsService;
  @Mock private MailServiceImpl mailService;

  private ChannelServiceImpl channelService;
  private final String ACCOUNT_ID = "accountId";
  private SettingValueResponseDTO settingValueResponseDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    channelService = new ChannelServiceImpl(
        mailService, slackService, pagerDutyService, msTeamsService, webhookService, ngSettingsClient);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void sendNotification_channelEnabled_willSend() throws IOException {
    NotificationRequest notificationRequest = NotificationRequest.newBuilder()
                                                  .setAccountId(ACCOUNT_ID)
                                                  .setSlack(NotificationRequest.Slack.newBuilder().build())
                                                  .build();
    when(ngSettingsClient.getSetting(SettingIdentifiers.ENABLE_SLACK_NOTIFICATION_IDENTIFIER, ACCOUNT_ID, null, null))
        .thenReturn(request);
    settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    NotificationProcessingResponse expectedResponse = NotificationProcessingResponse.allSent(1);
    when(slackService.send(notificationRequest)).thenReturn(expectedResponse);

    NotificationProcessingResponse notificationProcessingResponse = channelService.send(notificationRequest);
    assertThat(notificationProcessingResponse).isEqualTo(expectedResponse);
    verify(slackService, times(1)).send(any());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void sendTestNotification_channelEnabled_willSend() throws IOException {
    NotificationSettingDTO notificationSettingDTO = SlackSettingDTO.builder().accountId(ACCOUNT_ID).build();
    when(ngSettingsClient.getSetting(SettingIdentifiers.ENABLE_SLACK_NOTIFICATION_IDENTIFIER, ACCOUNT_ID, null, null))
        .thenReturn(request);
    settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    when(slackService.sendTestNotification(notificationSettingDTO)).thenReturn(true);

    assertThat(channelService.sendTestNotification(notificationSettingDTO)).isEqualTo(true);
    verify(slackService, times(1)).sendTestNotification(any());
  }
}
