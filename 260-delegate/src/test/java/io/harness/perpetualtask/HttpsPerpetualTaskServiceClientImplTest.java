/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.MARKO;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.callback.BasicAuthCredentials;
import io.harness.callback.HttpsClientEntrypoint;
import io.harness.category.element.IntegrationTests;
import io.harness.perpetualtask.https.HttpsPerpetualTaskParams;
import io.harness.rule.Owner;

import com.google.api.client.util.Base64;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpsPerpetualTaskServiceClientImplTest {
  private static final String USERNAME = "test@harness.io";
  private static final String PASSWORD = "test";

  private MockWebServer server;
  private HttpsPerpetualTaskServiceClientImpl httpsPerpetualTaskServiceClient;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    server.start();

    final HttpsClientEntrypoint entrypoint =
        HttpsClientEntrypoint.newBuilder()
            .setUrl(server.url("/").toString())
            .setBasicAuthCredentials(
                BasicAuthCredentials.newBuilder().setUsername(USERNAME).setPassword(PASSWORD).build())
            .build();
    httpsPerpetualTaskServiceClient = new HttpsPerpetualTaskServiceClientImpl(entrypoint);
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testGetTaskParams() throws Exception {
    final String authHeader =
        "Basic " + Base64.encodeBase64String(format("%s:%s", USERNAME, PASSWORD).getBytes(StandardCharsets.UTF_8));

    final String url = server.url("/").toString();

    server.enqueue(new MockResponse()
                       .setResponseCode(HttpURLConnection.HTTP_OK)
                       .addHeader("Authorization", authHeader)
                       .setBody("getTaskParamsResponse"));

    final Map<String, String> testParamsMap = Map.of("key", "value");
    final PerpetualTaskClientContext context = PerpetualTaskClientContext.builder().clientParams(testParamsMap).build();
    final HttpsPerpetualTaskParams actual =
        (HttpsPerpetualTaskParams) httpsPerpetualTaskServiceClient.getTaskParams(context);

    assertThat(actual).isNotNull();
    assertThat(actual.getTaskParams().toString(Charset.defaultCharset())).isEqualTo("getTaskParamsResponse");
  }
}
