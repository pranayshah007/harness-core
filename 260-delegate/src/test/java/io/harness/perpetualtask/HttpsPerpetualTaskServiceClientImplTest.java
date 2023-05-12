/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.MARKO;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.callback.BasicAuthCredentials;
import io.harness.callback.HttpsClientEntrypoint;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.https.HttpsPerpetualTaskParams;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.api.client.util.Base64;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpsPerpetualTaskServiceClientImplTest extends WingsBaseTest {
  private static final String USERNAME = "test@harness.io";
  private static final String PASSWORD = "test";

  HttpsPerpetualTaskServiceClientImpl httpsPerpetualTaskServiceClient;

  @Rule public WireMockRule wireMockRule = new WireMockRule(7777, 9999);

  @Before
  public void setUp() {
    HttpsClientEntrypoint entrypoint =
        HttpsClientEntrypoint.newBuilder()
            .setUrl("https://localhost:9999")
            .setBasicAuthCredentials(
                BasicAuthCredentials.newBuilder().setUsername(USERNAME).setPassword(PASSWORD).build())
            .build();
    httpsPerpetualTaskServiceClient = new HttpsPerpetualTaskServiceClientImpl(entrypoint);

    String expectedAuthHeaderValue =
        "Basic " + Base64.encodeBase64String(format("%s:%s", USERNAME, PASSWORD).getBytes(StandardCharsets.UTF_8));

    wireMockRule.stubFor(post(urlEqualTo("/"))
                             .withHeader("Authorization", equalTo(expectedAuthHeaderValue))
                             .willReturn(aResponse().withStatus(200).withBody("getTaskParamsResponse".getBytes())));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldInvokeGetTaskParamsSuccessfully() {
    Map<String, String> testParamsMap = new HashMap();
    testParamsMap.put("key", "value");
    PerpetualTaskClientContext context = PerpetualTaskClientContext.builder().clientParams(testParamsMap).build();

    HttpsPerpetualTaskParams taskParams =
        (HttpsPerpetualTaskParams) httpsPerpetualTaskServiceClient.getTaskParams(context, true);
    assertThat(taskParams).isNotNull();
    assertThat(taskParams.getTaskParams().toString(Charset.defaultCharset())).isEqualTo("getTaskParamsResponse");
  }
}
