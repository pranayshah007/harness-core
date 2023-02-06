/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.rule.OwnerRule.ANUPAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.dms.app.DelegateServiceApp;
import io.harness.dms.app.DelegateServiceConfiguration;
import io.harness.network.Http;
import io.harness.rule.Owner;

import io.dropwizard.testing.DropwizardTestSupport;
import java.io.File;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateServiceApplicationStartupTest {
  public static DropwizardTestSupport<DelegateServiceConfiguration> SUPPORT;

  @BeforeClass
  public static void beforeClass() throws Exception {
    SUPPORT = new DropwizardTestSupport<DelegateServiceConfiguration>(DelegateServiceApp.class,
        String.valueOf(new File("419-delegate-service-app/src/test/resources/test-config.yml")));
    SUPPORT.before();
  }

  @AfterClass
  public static void afterClass() {
    SUPPORT.after();
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testAppStartup() {
    final Client client = new JerseyClientBuilder().sslContext(Http.getSslContext()).build();
    final Response response =
        client.target(String.format("http://localhost:%d/api/swagger.json", SUPPORT.getLocalPort())).request().get();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    response.close();
  }
}
