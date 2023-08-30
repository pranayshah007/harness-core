/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.specto.hoverfly.junit.core.SslConfigurer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Sample test to use Hoverfly with OkHttpClient.
 */
@Slf4j
public class HoverflyExampleTestSuiteChild extends DSLHoverflyTestSuiteChildBase {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testQAAPI() throws IOException {
    SslConfigurer sslConfigurer = DSLSuiteTest.HOVERFLY_RULE.getSslConfigurer();
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .sslSocketFactory(sslConfigurer.getSslContext().getSocketFactory(), sslConfigurer.getTrustManager())
            .build();
    Request request = new Request.Builder().url("https://qa.harness.io/api/version").method("GET", null).build();
    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProdAPI() throws IOException {
    SslConfigurer sslConfigurer = DSLSuiteTest.HOVERFLY_RULE.getSslConfigurer();
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .sslSocketFactory(sslConfigurer.getSslContext().getSocketFactory(), sslConfigurer.getTrustManager())
            .build();
    Request request = new Request.Builder().url("https://app.harness.io/api/version").method("GET", null).build();
    Response response = client.newCall(request).execute();
    assertThat(response.code()).isEqualTo(200);
  }
}
