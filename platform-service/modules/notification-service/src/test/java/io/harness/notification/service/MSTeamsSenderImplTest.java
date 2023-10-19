/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.rule.OwnerRule.BHAVYA;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.notification.senders.MSTeamsSenderImpl;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MSTeamsSenderImplTest extends CategoryTest {
  private MSTeamsSenderImpl msTeamsSender;
  private OkHttpClient okHttpClient;

  @Before
  public void setUp() throws Exception {
    okHttpClient = mock(OkHttpClient.class);
    msTeamsSender = new MSTeamsSenderImpl(okHttpClient);
  }

  @SneakyThrows
  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void send_ValidArguments_withDomainFilters() {
    List<String> msTeamUrls = Arrays.asList("http://harness.webhook.com/webhook1",
        "http://harness.webhook.com/webhook2", "http://test.webhook.com/webhook2");
    String message = "test message";
    String notificationId = "test";
    Request mockrequest1 = new Request.Builder().url(msTeamUrls.get(0)).build();
    Request mockrequest2 = new Request.Builder().url(msTeamUrls.get(1)).build();
    Response responseSuccess = new Response.Builder()
                                   .code(200)
                                   .message("success")
                                   .request(mockrequest1)
                                   .protocol(Protocol.HTTP_2)
                                   .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                                   .build();
    Response responseFailure = new Response.Builder()
                                   .code(401)
                                   .message("failure")
                                   .request(mockrequest2)
                                   .protocol(Protocol.HTTP_2)
                                   .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                                   .build();

    Call call = mock(Call.class);
    when(call.execute()).thenReturn(responseSuccess).thenReturn(responseFailure);
    when(okHttpClient.newCall(any())).thenReturn(call);

    NotificationProcessingResponse notificationProcessingResponse =
        msTeamsSender.send(msTeamUrls, message, notificationId, Arrays.asList("harness.webhook.com"));

    verify(okHttpClient, times(2)).newCall(any());

    assertTrue(notificationProcessingResponse.getResult().get(0));
    assertFalse(notificationProcessingResponse.getResult().get(1));
  }
}
