/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.chartmuseum.ChartmuseumClientFactory;
import io.harness.delegate.chartmuseum.CgChartmuseumClientFactory;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CgChartmuseumClientFactoryTest extends CategoryTest {
  private static final String CLI_PATH_OLD = "/usr/bin/v0.8/chartmuseum";
  private static final String CLI_PATH_NEW = "/usr/bin/v0.14/chartmuseum";
  private static final String BUCKET = "test-bucket";
  private static final String BASE_PATH = "/test/path";
  private static final String REGION = "us-east-1";
  private static final char[] ACCESS_KEY = "access-key".toCharArray();
  private static final char[] SECRET_KEY = "secret-key".toCharArray();
  private static final char[] SERVICE_ACCOUNT_KEY = "service-account-key".toCharArray();
  private static final String RESOURCE_DIRECTORY = "./resources";

  @Mock private ChartmuseumClientFactory clientFactory;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;

  @Mock private ChartmuseumClient chartmuseumS3Client;
  @Mock private ChartmuseumClient chartmuseumGcsClient;

  @InjectMocks private CgChartmuseumClientFactory cgClientFactory;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(CLI_PATH_OLD).when(k8sGlobalConfigService).getChartMuseumPath(false);
    doReturn(CLI_PATH_NEW).when(k8sGlobalConfigService).getChartMuseumPath(true);

    doReturn(chartmuseumS3Client)
        .when(clientFactory)
        .s3(or(eq(CLI_PATH_OLD), eq(CLI_PATH_NEW)), eq(BUCKET), or(eq(BASE_PATH), eq(null)), eq(REGION), anyBoolean(),
            or(eq(ACCESS_KEY), eq(null)), or(eq(SECRET_KEY), eq(null)), anyBoolean());
    doReturn(chartmuseumGcsClient)
        .when(clientFactory)
        .gcs(or(eq(CLI_PATH_OLD), eq(CLI_PATH_NEW)), eq(BUCKET), or(eq(BASE_PATH), eq(null)),
            or(eq(SERVICE_ACCOUNT_KEY), eq(null)), eq(RESOURCE_DIRECTORY));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateClientS3() {
    AmazonS3HelmRepoConfig s3HelmRepoConfig =
        AmazonS3HelmRepoConfig.builder().bucketName(BUCKET).region(REGION).build();
    AwsConfig manualCredentialConnector = AwsConfig.builder().accessKey(ACCESS_KEY).secretKey(SECRET_KEY).build();
    AwsConfig useIamCredentialsConnector = AwsConfig.builder().useEc2IamCredentials(true).build();
    AwsConfig useIRSAConnector = AwsConfig.builder().useIRSA(true).build();

    ChartmuseumClient result =
        cgClientFactory.createClient(s3HelmRepoConfig, manualCredentialConnector, RESOURCE_DIRECTORY, BASE_PATH, true);
    assertThat(result).isSameAs(chartmuseumS3Client);
    verify(clientFactory).s3(CLI_PATH_NEW, BUCKET, BASE_PATH, REGION, false, ACCESS_KEY, SECRET_KEY, false);

    result =
        cgClientFactory.createClient(s3HelmRepoConfig, useIamCredentialsConnector, RESOURCE_DIRECTORY, null, false);
    assertThat(result).isSameAs(chartmuseumS3Client);
    verify(clientFactory).s3(CLI_PATH_OLD, BUCKET, null, REGION, true, null, null, false);

    result = cgClientFactory.createClient(s3HelmRepoConfig, useIRSAConnector, RESOURCE_DIRECTORY, BASE_PATH, false);
    assertThat(result).isSameAs(chartmuseumS3Client);
    verify(clientFactory).s3(CLI_PATH_OLD, BUCKET, BASE_PATH, REGION, false, null, null, true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateClientGcs() {
    GCSHelmRepoConfig gcsHelmRepoConfig = GCSHelmRepoConfig.builder().bucketName(BUCKET).build();
    GcpConfig manualCredentials = GcpConfig.builder().serviceAccountKeyFileContent(SERVICE_ACCOUNT_KEY).build();
    GcpConfig useDelegateCredentials = GcpConfig.builder().useDelegate(true).build();

    ChartmuseumClient result =
        cgClientFactory.createClient(gcsHelmRepoConfig, manualCredentials, RESOURCE_DIRECTORY, BASE_PATH, false);
    assertThat(result).isEqualTo(chartmuseumGcsClient);
    verify(clientFactory).gcs(CLI_PATH_OLD, BUCKET, BASE_PATH, SERVICE_ACCOUNT_KEY, RESOURCE_DIRECTORY);

    result =
        cgClientFactory.createClient(gcsHelmRepoConfig, useDelegateCredentials, RESOURCE_DIRECTORY, BASE_PATH, true);
    assertThat(result).isEqualTo(chartmuseumGcsClient);
    verify(clientFactory).gcs(CLI_PATH_NEW, BUCKET, BASE_PATH, null, RESOURCE_DIRECTORY);
  }
}