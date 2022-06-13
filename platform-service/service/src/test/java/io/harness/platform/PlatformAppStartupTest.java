/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ANKUSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.File;
import java.net.InetSocketAddress;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PlatformAppStartupTest extends CategoryTest {
  public static MongoServer MONGO_SERVER;
  public static DropwizardTestSupport<PlatformConfiguration> SUPPORT;

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

  private static String getMongoUri(String dbname) {
    InetSocketAddress serverAddress = MONGO_SERVER.getLocalAddress();
    final ServerAddress addr = new ServerAddress(serverAddress);
    return String.format("mongodb://%s:%s/%s", addr.getHost(), addr.getPort(), dbname);
  }

  @BeforeClass
  public static void beforeClass() throws Exception{
    MONGO_SERVER = startMongoServer();
    SUPPORT = new DropwizardTestSupport<PlatformConfiguration>(PlatformApplication.class,
        String.valueOf(new File("platform-service/service/src/test/resources/test-config.yml")),
        ConfigOverride.config("notificationServiceConfig.mongo.uri", getMongoUri("notification")),
        ConfigOverride.config("auditServiceConfig.mongo.uri", getMongoUri("ng-audits")),
        ConfigOverride.config("notificationClient.messageBroker.uri", getMongoUri("notificationChannel")),
        ConfigOverride.config("resourceGroupServiceConfig.mongo.uri", getMongoUri("resourcegroup")),
        ConfigOverride.config("hostname", "localhost"), ConfigOverride.config("basePathPrefix", ""),
        ConfigOverride.config("secretsConfiguration.secretResolutionEnabled", "false"));
    SUPPORT.before();
  }

  @AfterClass
  public static void afterClass() {
    SUPPORT.after();
    stopMongoServer();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testAppStartup() {
    final Client client = new JerseyClientBuilder().build();
    final Response response =
        client.target(String.format("http://localhost:%d/api/swagger.json", SUPPORT.getLocalPort())).request().get();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    response.close();
  }
}
