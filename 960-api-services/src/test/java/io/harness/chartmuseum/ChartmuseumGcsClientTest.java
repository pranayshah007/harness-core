/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.DISABLE_STATEFILES;
import static io.harness.chartmuseum.ChartMuseumConstants.GCS_COMMAND_TEMPLATE;
import static io.harness.chartmuseum.ChartMuseumConstants.GOOGLE_APPLICATION_CREDENTIALS;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartmuseumGcsClient.ChartmuseumGcsClientBuilder;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ChartmuseumGcsClientTest extends CategoryTest {
  private static final String SERVICE_ACCOUNT_KEY = "service-account-key-json";
  private static final String BUCKET = "test-bucket";
  private static final String BASE_PATH = "charts";
  private static final String CLI_PATH = "/usr/local/bin/chartmuseum";

  @Mock private ChartMuseumClientHelper clientHelper;
  private ChartmuseumGcsClientBuilder baseClientBuilder;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    baseClientBuilder = ChartmuseumGcsClient.builder().clientHelper(clientHelper).cliPath(CLI_PATH);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartServiceAccountKey() throws IOException {
    final String resourcesPath = Files.createTempDirectory("ChartmuseumGcsClientTest").toAbsolutePath().toString();
    final String expectedPath = Paths.get(resourcesPath, "credentials.json").toString();
    final ChartmuseumClient client = baseClientBuilder.version(Version.parse("0.14"))
                                         .bucket(BUCKET)
                                         .basePath(BASE_PATH)
                                         .serviceAccountKey(SERVICE_ACCOUNT_KEY.toCharArray())
                                         .resourceDirectory(resourcesPath)
                                         .build();

    final String expectedArguments =
        GCS_COMMAND_TEMPLATE.replace("${BUCKET_NAME}", BUCKET).replace("${FOLDER_PATH}", BASE_PATH);
    final Map<String, String> expectedEnv = ImmutableMap.of(GOOGLE_APPLICATION_CREDENTIALS, expectedPath);

    try {
      client.start();

      verify(clientHelper)
          .startServer(format("%s %s %s", CLI_PATH, expectedArguments, DISABLE_STATEFILES), expectedEnv);
      assertThat(new File(expectedPath)).exists();
    } finally {
      FileUtils.deleteQuietly(new File(resourcesPath));
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartInherit() throws IOException {
    final String resourcesPath = Files.createTempDirectory("ChartmuseumGcsClientTest").toAbsolutePath().toString();
    final String credentialsPath = Paths.get(resourcesPath, "credentials.json").toString();
    final ChartmuseumClient client = baseClientBuilder.version(Version.parse("0.8")).bucket(BUCKET).build();
    final String expectedArguments =
        GCS_COMMAND_TEMPLATE.replace("${BUCKET_NAME}", BUCKET).replace("${FOLDER_PATH}", "");
    final Map<String, String> expectedEnv = Collections.emptyMap();

    client.start();

    verify(clientHelper).startServer(format("%s %s", CLI_PATH, expectedArguments), expectedEnv);
    assertThat(new File(credentialsPath)).doesNotExist();
  }
}