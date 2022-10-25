/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.client;

import static io.harness.rest.RestResponse.Builder.aRestResponse;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.commandlibrary.CommandLibraryServiceConfig;
import io.harness.commandlibrary.api.dto.CommandStoreDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.JsonUtils;

import software.wings.app.MainConfiguration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class CommandLibraryServiceHttpClientFactoryTest extends CategoryTest {
  private MockWebServer mockWebServer;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @After
  public void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_get() throws IOException, IllegalAccessException {
    final String baseUrl = mockWebServer.url("/").url().toString();
    final String publishingSecret = "secret";
    final CommandLibraryServiceHttpClientFactory factory =
        new CommandLibraryServiceHttpClientFactory(baseUrl, new ServiceTokenGenerator(), false, publishingSecret);
    final MainConfiguration mainConfiguration = new MainConfiguration();
    mainConfiguration.setCommandLibraryServiceConfig(
        CommandLibraryServiceConfig.builder().managerToCommandLibraryServiceSecret("secret").build());
    FieldUtils.writeField(factory, "mainConfiguration", mainConfiguration, true);

    mockWebServer.enqueue(new MockResponse()
                              .setResponseCode(HttpURLConnection.HTTP_OK)
                              .setBody(JsonUtils.asJson(
                                  aRestResponse()
                                      .withResource(ImmutableList.of(CommandStoreDTO.builder().name("harness").build()))
                                      .build())));

    final CommandLibraryServiceHttpClient serviceHttpClient = factory.get();

    final Object body1 = serviceHttpClient.getCommandStores(emptyMap()).execute().body();
    final RestResponse<List<CommandStoreDTO>> body =
        factory.getObjectMapper().convertValue(body1, new TypeReference<RestResponse<List<CommandStoreDTO>>>() {});

    assertThat(body.getResource().get(0).getName()).isEqualTo("harness");
  }
}
