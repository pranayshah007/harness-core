/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MILOS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SlackMessage;
import software.wings.beans.SlackMessageJSON;
import software.wings.service.impl.SlackMessageSenderImpl.SlackHttpClient;

import allbegray.slack.type.Payload;
import allbegray.slack.webhook.SlackWebhookClient;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDC)
public class SlackMessageSenderTest extends WingsBaseTest {
  SlackMessageSenderImpl slackMessageSender = spy(new SlackMessageSenderImpl());
  @Mock private OkHttpClient okHttpClient;
  @Mock private SlackWebhookClient slackWebhookClient;
  @Mock private SlackHttpClient slackHttpClient;
  @Mock private Call<ResponseBody> responseBodyCall;

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testMessageSendingJson() throws IOException {
    okhttp3.Call call = mock(okhttp3.Call.class);
    SlackMessageJSON slackMessageJSON = new SlackMessageJSON("https://hooks.slack.com/services/", "message");

    doReturn(okHttpClient).when(slackMessageSender).getHttpClient();
    doReturn(call).when(okHttpClient).newCall(any());
    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);

    slackMessageSender.sendJSON(slackMessageJSON);

    RequestBody body =
        RequestBody.create(MediaType.parse("application/json; charset=utf-8"), slackMessageJSON.getMessage());

    verify(okHttpClient).newCall(argumentCaptor.capture());

    Request request = argumentCaptor.getValue();
    assertThat(request.body()).isEqualToComparingFieldByField(body);
    assertThat(request.url().toString()).isEqualTo(slackMessageJSON.getOutgoingWebhookUrl());

    verify(call).execute();
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void testMessageSending() throws IOException {
    SlackMessage slackMessage = new SlackMessage("https://hooks.slack.com/services/", "#channel", "sender",
        "message||something||something||completed||something");

    doReturn(slackWebhookClient).when(slackMessageSender).getWebhookClient(anyString());

    ArgumentCaptor<Payload> argumentCaptor = ArgumentCaptor.forClass(Payload.class);

    slackMessageSender.send(slackMessage, false, false);
    verify(slackWebhookClient).post(argumentCaptor.capture());

    Payload payload = argumentCaptor.getValue();
    assertThat(payload.getChannel()).isEqualTo("#channel");
    assertThat(payload.getUsername()).isEqualTo("sender");
    assertThat(payload.getText()).isEqualTo("message");

    doReturn(slackHttpClient).when(slackMessageSender).getSlackHttpClient(anyString(), anyBoolean());
    doReturn(responseBodyCall).when(slackHttpClient).PostMsg(any(), any());
    doReturn(null).when(responseBodyCall).execute();
    slackMessage = new SlackMessage("https://hooks.test.com/services/", "#channel", "sender",
        "message||something||something||completed||something");

    slackMessageSender.send(slackMessage, false, false);

    verify(slackHttpClient).PostMsg(anyString(), argumentCaptor.capture());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testMessageSendingWithCertValidation() throws IOException {
    SlackMessage slackMessage = new SlackMessage("https://hooks.slack.com/services/", "#channel", "sender",
        "message||something||something||completed||something");

    doReturn(slackWebhookClient).when(slackMessageSender).getWebhookClient(anyString());

    ArgumentCaptor<Payload> argumentCaptor = ArgumentCaptor.forClass(Payload.class);

    slackMessageSender.send(slackMessage, false, true);
    verify(slackWebhookClient).post(argumentCaptor.capture());

    Payload payload = argumentCaptor.getValue();
    assertThat(payload.getChannel()).isEqualTo("#channel");
    assertThat(payload.getUsername()).isEqualTo("sender");
    assertThat(payload.getText()).isEqualTo("message");

    doReturn(slackHttpClient).when(slackMessageSender).getSlackHttpClient(anyString(), anyBoolean());
    doReturn(responseBodyCall).when(slackHttpClient).PostMsg(any(), any());
    doReturn(null).when(responseBodyCall).execute();
    slackMessage = new SlackMessage("https://hooks.test.com/services/", "#channel", "sender",
        "message||something||something||completed||something");

    slackMessageSender.send(slackMessage, false, true);

    verify(slackHttpClient).PostMsg(anyString(), argumentCaptor.capture());
  }
}
