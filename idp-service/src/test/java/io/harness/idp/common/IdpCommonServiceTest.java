/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.notification.templates.PredefinedTemplate.IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK;
import static io.harness.rule.OwnerRule.SATHISH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpCommonServiceTest extends CategoryTest {
  static final String ACCOUNT_IDENTIFIER = "123";
  static final String ACCOUNT_NAME = "Harness";
  static final String NOTIFICATION_REQUEST_ID = "notificationRequestId";
  static final String SLACK_WEBHOOK = "https://hooks.slack.com/services/dummy/dummy/dummy";
  @InjectMocks IdpCommonService idpCommonService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) AccountClient accountClient;
  @Mock NotificationClient notificationClient;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(
            new RestResponse<>(AccountDTO.builder().identifier(ACCOUNT_IDENTIFIER).name(ACCOUNT_NAME).build())));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSendSlackNotification() throws IOException {
    AccountDTO accountDTO = AccountDTO.builder().identifier(ACCOUNT_IDENTIFIER).name(ACCOUNT_NAME).build();
    when(accountClient.getAccountDTO(ACCOUNT_IDENTIFIER).execute())
        .thenReturn(Response.success(new RestResponse<>(accountDTO)));
    NotificationResult notificationResult =
        NotificationResultWithoutStatus.builder().notificationId(NOTIFICATION_REQUEST_ID).build();
    when(notificationClient.sendNotificationAsync(any())).thenReturn(notificationResult);
    SlackChannel slackChannel = SlackChannel.builder()
                                    .accountId(ACCOUNT_IDENTIFIER)
                                    .userGroups(Collections.emptyList())
                                    .templateId(IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK.getIdentifier())
                                    .templateData(new HashMap<>())
                                    .team(Team.IDP)
                                    .webhookUrls(Collections.singletonList(SLACK_WEBHOOK))
                                    .build();
    idpCommonService.sendSlackNotification(slackChannel);
  }
}
