/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.harness.category.element.UnitTests;
import io.harness.ng.NextGenApplication;
import io.harness.ng.NextGenConfiguration;
import io.harness.rule.Owner;
import io.harness.yaml.YamlSdkModule;

import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Objects;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class NGSwaggerTest extends CategoryTest {
  public static MongoServer MONGO_SERVER;
  public static DropwizardTestSupport<NextGenConfiguration> SUPPORT;
  private static final String CONDITIONAL_ON_ENV = "swaggerGeneration";
  private MockedStatic<YamlSdkModule> aStatic;

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

  @Before
  public void setup() {
    aStatic = mockStatic(YamlSdkModule.class);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    if (!Objects.equals("true", System.getProperty(CONDITIONAL_ON_ENV))) {
      return;
    }
    MONGO_SERVER = startMongoServer();
    //    initializeDefaultInstance(any());
    SUPPORT = new DropwizardTestSupport<NextGenConfiguration>(NextGenApplication.class,
        ResourceHelpers.resourceFilePath("test-config.yml"), ConfigOverride.config("mongo.uri", getMongoUri()));
    SUPPORT.before();
  }

  @AfterClass
  public static void afterClass() throws AssumptionViolatedException {
    if (!Objects.equals("true", System.getProperty(CONDITIONAL_ON_ENV))) {
      return;
    }
    SUPPORT.after();
    stopMongoServer();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category({UnitTests.class})
  public void testSwaggerGeneration() throws IOException {
    if (!Objects.equals("true", System.getProperty(CONDITIONAL_ON_ENV))) {
      return;
    }
    log.info("Running swagger generation test");
    final Client client = new JerseyClientBuilder().build();
    final Response response =
        client.target(String.format("http://localhost:%d/swagger.json", SUPPORT.getLocalPort())).request().get();
    InputStream inputStream = response.readEntity(InputStream.class);
    saveToFile(inputStream);
    inputStream.close();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    response.close();
  }

  /**
   * Store contents of file from response to local disk using java 7
   * java.nio.file.Files
   */
  private void saveToFile(InputStream is) throws IOException {
    File downloadFile = new File("swagger.json");
    byte[] byteArray = IOUtils.toByteArray(is);
    FileOutputStream fos = new FileOutputStream(downloadFile);
    fos.write(byteArray);
    fos.flush();
    fos.close();
  }
}
