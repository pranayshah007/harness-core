/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.commandlibrary.server.CommandLibraryServerTestBase;
import io.harness.network.Http;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.File;
import java.net.InetSocketAddress;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CommandLibraryServerApplicationTest extends CommandLibraryServerTestBase {
  public static DropwizardTestSupport<CommandLibraryServerConfig> SUPPORT;
  public static MongoServer MONGO_SERVER;

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
    return String.format("mongodb://%s:%s/harness", addr.getHost(), addr.getPort());
  }

  @BeforeClass
  public static void beforeClass() {
    MONGO_SERVER = startMongoServer();
    SUPPORT = new DropwizardTestSupport<CommandLibraryServerConfig>(CommandLibraryServerApplication.class,
        String.valueOf(
            new File("210-command-library-server/src/test/resources/command-library-server-config-test.yml")),
        ConfigOverride.config("server.applicationConnectors[0].port", "0"),
        ConfigOverride.config("server.applicationConnectors[0].type", "https"),
        ConfigOverride.config("server.adminConnectors[0].type", "https"),
        ConfigOverride.config("server.adminConnectors[0].port", "0"),
        ConfigOverride.config("mongo.uri", getMongoUri()));

    SUPPORT.before();
  }

  @AfterClass
  public static void afterClass() {
    SUPPORT.after();
    stopMongoServer();
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_initialisation() {
    final Client client = new JerseyClientBuilder().sslContext(Http.getSslContext()).build();

    final Response response =
        client
            .target(String.format(
                "https://localhost:%d/command-library-service/command-stores/test", SUPPORT.getLocalPort()))
            .request()
            .get();
    final RestResponse<String> restResponse = response.readEntity(new GenericType<RestResponse<String>>() {});
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(restResponse.getResource()).isEqualTo("hello world");
  }
}
