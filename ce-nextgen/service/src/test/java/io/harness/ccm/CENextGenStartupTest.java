/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.CENextGenConfiguration.SERVICE_ROOT_PATH;
import static io.harness.rule.OwnerRule.UTSAV;

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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CE)
public class CENextGenStartupTest extends CategoryTest {
  private static DropwizardTestSupport<CENextGenConfiguration> support;
  private static MongoServer mongoServer;

  private static MongoServer startMongoServer() {
    final MongoServer mongoServer = new MongoServer(new MemoryBackend());
    mongoServer.bind("localhost", 0);
    return mongoServer;
  }

  private static void stopMongoServer() {
    if (mongoServer != null) {
      mongoServer.shutdownNow();
    }
  }

  private static String getMongoUri() {
    InetSocketAddress serverAddress = mongoServer.getLocalAddress();
    final ServerAddress addr = new ServerAddress(serverAddress);
    return String.format("mongodb://%s:%s/events", addr.getHost(), addr.getPort());
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    mongoServer = startMongoServer();
    support = new DropwizardTestSupport<>(CENextGenApplication.class,
        String.valueOf(new File("ce-nextgen/service/src/test/resources/test-config.yml")),
        ConfigOverride.config("server.applicationConnectors[0].port", "0"),
        ConfigOverride.config("events-mongo.uri", getMongoUri()), ConfigOverride.config("hostname", "localhost"),
        ConfigOverride.config("basePathPrefix", SERVICE_ROOT_PATH));
    support.before();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("Failing in CI. Passing in local.")
  public void testSchemaUrlAccessible() {
    final Client client = new JerseyClientBuilder().build();
    final String URL = String.format("http://localhost:%d%s/graphql/schema", support.getLocalPort(), SERVICE_ROOT_PATH);
    final Response response = client.target(URL).request().get();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    response.close();
  }

  @AfterClass
  public static void afterClass() {
    support.after();
    stopMongoServer();
  }
}
