/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.rule.OwnerRule.XIN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.app.DelegateServiceApplication;
import io.harness.delegate.app.DelegateServiceConfig;
import io.harness.network.Http;
import io.harness.resource.Project;
import io.harness.rule.Owner;

import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateServiceAppStartupTest extends DelegateServiceAppTestBase {
  public static MongoServer MONGO_SERVER;

  public static DropwizardTestSupport<DelegateServiceConfig> SUPPORT;

  private static MongoServer startMongoServer() {
    final MongoServer mongoServer = new MongoServer(new MemoryBackend());
    mongoServer.bind("localhost", 0);
    return mongoServer;
  }

  private static void stopMongoServer() {
    if (MONGO_SERVER != null) {
      MONGO_SERVER.shutdownNow();
    }
  }

  private static String getMongoUri() {
    InetSocketAddress serverAddress = MONGO_SERVER.getLocalAddress();
    final ServerAddress addr = new ServerAddress(serverAddress);
    return String.format("mongodb://%s:%s/ng-harness", addr.getHost(), addr.getPort());
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    MONGO_SERVER = startMongoServer();
    String directoryPath = Project.moduleDirectory(DelegateServiceAppStartupTest.class);
    String configPath = Paths.get(directoryPath, "delegate-service-config.yml").toString();

    // Handle sandboxed bazel path - remove path starting from /sandbox till execroot. This is because the source
    // code is present outside the sandbox directory created by bazel.
    if (!Files.exists(Paths.get(configPath)) && configPath.contains("/sandbox/")) {
      configPath = configPath.substring(0, configPath.indexOf("/sandbox/"))
          + configPath.substring(configPath.indexOf("/execroot"));
    }

    SUPPORT = new DropwizardTestSupport<DelegateServiceConfig>(DelegateServiceApplication.class,
        String.valueOf(new File(configPath)), ConfigOverride.config("server.applicationConnectors[0].port", "0"),
        ConfigOverride.config("server.applicationConnectors[0].type", "https"),
        ConfigOverride.config("server.adminConnectors[0].type", "https"),
        ConfigOverride.config("server.adminConnectors[0].port", "0"),
        ConfigOverride.config("eventsFramework.redis.redisUrl", "dummyRedisUrl"),
        ConfigOverride.config("mongo.uri", getMongoUri()));
    SUPPORT.before();
  }

  @AfterClass
  public static void afterClass() {
    SUPPORT.after();
    stopMongoServer();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  @Ignore("Revist after enabling DMS")
  public void testAppStartup() {
    final Client client = new JerseyClientBuilder().sslContext(Http.getSslContext()).build();
    final Response response =
        client.target(String.format("https://localhost:%d/api/swagger.json", SUPPORT.getLocalPort())).request().get();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    response.close();
  }
}