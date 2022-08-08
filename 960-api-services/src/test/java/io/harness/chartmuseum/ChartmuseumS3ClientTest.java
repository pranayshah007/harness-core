/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.AMAZON_S3_COMMAND_TEMPLATE;
import static io.harness.chartmuseum.ChartMuseumConstants.AWS_ACCESS_KEY_ID;
import static io.harness.chartmuseum.ChartMuseumConstants.DISABLE_STATEFILES;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartmuseumS3Client.ChartmuseumS3ClientBuilder;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ChartmuseumS3ClientTest extends CategoryTest {
  private static final String ACCESS_KEY_VALUE = "access-key";
  private static final String SECRET_KEY_VALUE = "secret-key";
  private static final String BUCKET = "test-bucket";
  private static final String BASE_PATH = "charts";
  private static final String REGION = "us-east1";
  private static final String CLI_PATH = "/usr/local/bin/chartmuseum";
  private static final Map<String, String> S3_ENV =
      ImmutableMap.of(AWS_ACCESS_KEY_ID, "access-key", "AWS_SECRET_ACCESS_KEY", "secret-key");

  @Mock private ChartMuseumClientHelper clientHelper;

  private ChartmuseumS3ClientBuilder baseClientBuilder;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    baseClientBuilder = ChartmuseumS3Client.builder().clientHelper(clientHelper).cliPath(CLI_PATH);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartManualCredentials() throws IOException {
    final ChartmuseumClient client = baseClientBuilder.version(Version.parse("0.14"))
                                         .accessKey(ACCESS_KEY_VALUE.toCharArray())
                                         .secretKey(SECRET_KEY_VALUE.toCharArray())
                                         .bucket(BUCKET)
                                         .basePath(BASE_PATH)
                                         .region(REGION)
                                         .build();
    final String expectedArguments = AMAZON_S3_COMMAND_TEMPLATE.replace("${BUCKET_NAME}", BUCKET)
                                         .replace("${FOLDER_PATH}", BASE_PATH)
                                         .replace("${REGION}", REGION);

    doReturn(S3_ENV)
        .when(clientHelper)
        .getEnvForAwsConfig(ACCESS_KEY_VALUE.toCharArray(), SECRET_KEY_VALUE.toCharArray(), false, false);

    client.start();

    verify(clientHelper).startServer(format("%s %s %s", CLI_PATH, expectedArguments, DISABLE_STATEFILES), S3_ENV);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartIamCredentials() throws IOException {
    final ChartmuseumClient client = baseClientBuilder.version(Version.parse("0.12"))
                                         .bucket(BUCKET)
                                         .region(REGION)
                                         .useEc2IamCredentials(true)
                                         .build();
    final String expectedArguments = AMAZON_S3_COMMAND_TEMPLATE.replace("${BUCKET_NAME}", BUCKET)
                                         .replace("${FOLDER_PATH}", "")
                                         .replace("${REGION}", REGION);

    doReturn(S3_ENV).when(clientHelper).getEnvForAwsConfig(null, null, true, false);

    client.start();

    verify(clientHelper).startServer(format("%s %s", CLI_PATH, expectedArguments), S3_ENV);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartIrsa() throws IOException {
    final ChartmuseumClient client = baseClientBuilder.version(Version.parse("0.13"))
                                         .bucket(BUCKET)
                                         .region(REGION)
                                         .basePath(BASE_PATH)
                                         .useIRSA(true)
                                         .build();
    final String expectedArguments = AMAZON_S3_COMMAND_TEMPLATE.replace("${BUCKET_NAME}", BUCKET)
                                         .replace("${FOLDER_PATH}", BASE_PATH)
                                         .replace("${REGION}", REGION);
    doReturn(S3_ENV).when(clientHelper).getEnvForAwsConfig(null, null, false, true);

    client.start();

    verify(clientHelper).startServer(format("%s %s %s", CLI_PATH, expectedArguments, DISABLE_STATEFILES), S3_ENV);
  }
}