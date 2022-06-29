/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.chartmuseum.ChartmuseumClientFactory;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class NgChartmuseumClientFactoryTest extends CategoryTest {
  private static final String CLI_PATH_OLD = "/usr/local/bin/v08/chartmusem";
  private static final String CLI_PATH_NEW = "/usr/local/bin/v014/chartmusem";
  private static final String RESOURCE_DIRECTORY = "./resources/";

  @Mock private ChartmuseumClientFactory clientFactory;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;

  @InjectMocks private NgChartmuseumClientFactory ngChartmuseumClientFactory;

  @Mock private ChartmuseumClient s3ChartmuseumClient;
  @Mock private ChartmuseumClient gcsChartmuseumClient;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    doReturn(CLI_PATH_OLD).when(k8sGlobalConfigService).getChartMuseumPath(false);
    doReturn(CLI_PATH_NEW).when(k8sGlobalConfigService).getChartMuseumPath(true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateChartMuseumClientUsingAwsS3InheritFromDelegate() {
    testCreateChartmuseumClientUsingAwsS3(AwsCredentialDTO.builder()
                                              .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                                              .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                              .build(),
        true, null, null, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateChartMuseumClientUsingAwsS3UseIRSA() {
    testCreateChartmuseumClientUsingAwsS3(AwsCredentialDTO.builder()
                                              .awsCredentialType(AwsCredentialType.IRSA)
                                              .config(AwsInheritFromDelegateSpecDTO.builder().build())
                                              .build(),
        false, null, null, true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateChartMuseumClientUsingAwsS3ManualCredentials() {
    final char[] accessKey = "access-key".toCharArray();
    final char[] secretKey = "secret-key".toCharArray();

    testCreateChartmuseumClientUsingAwsS3(
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .config(AwsManualConfigSpecDTO.builder()
                        .accessKeyRef(SecretRefData.builder().decryptedValue(accessKey).build())
                        .secretKeyRef(SecretRefData.builder().decryptedValue(secretKey).build())
                        .build())
            .build(),
        false, accessKey, secretKey, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateChartmuseumClientUsingGCSManualCredentials() {
    final char[] serviceAccountKey = "service account key content".toCharArray();

    final GcpConnectorCredentialDTO credentials =
        GcpConnectorCredentialDTO.builder()
            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
            .config(GcpManualDetailsDTO.builder()
                        .secretKeyRef(SecretRefData.builder().decryptedValue(serviceAccountKey).build())
                        .build())
            .build();

    testCreateChartmuseumClientUsingGcs(credentials, serviceAccountKey, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateChartmuseumClientUsingGCSInheritFromDelegate() {
    final GcpConnectorCredentialDTO credentials = GcpConnectorCredentialDTO.builder()
                                                      .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                      .config(GcpDelegateDetailsDTO.builder().build())
                                                      .build();

    testCreateChartmuseumClientUsingGcs(credentials, "no-use".toCharArray(), true);
  }

  private void testCreateChartmuseumClientUsingAwsS3(
      AwsCredentialDTO credentials, boolean inheritFromDelegate, char[] accessKey, char[] secretKey, boolean useIRSA) {
    final String bucketName = "bucketName";
    final String region = "region";
    final String folderPath = "folderPath";

    S3HelmStoreDelegateConfig s3StoreDelegateConfig =
        S3HelmStoreDelegateConfig.builder()
            .bucketName(bucketName)
            .region(region)
            .folderPath(folderPath)
            .awsConnector(AwsConnectorDTO.builder().credential(credentials).build())
            .useLatestChartMuseumVersion(true)
            .build();
    doReturn(s3ChartmuseumClient)
        .when(clientFactory)
        .s3(CLI_PATH_NEW, bucketName, folderPath, region, inheritFromDelegate, accessKey, secretKey, useIRSA);

    ChartmuseumClient result = ngChartmuseumClientFactory.createClient(s3StoreDelegateConfig, RESOURCE_DIRECTORY);
    assertThat(result).isSameAs(s3ChartmuseumClient);

    verify(clientFactory, times(1))
        .s3(CLI_PATH_NEW, bucketName, folderPath, region, inheritFromDelegate, accessKey, secretKey, useIRSA);
  }

  private void testCreateChartmuseumClientUsingGcs(
      GcpConnectorCredentialDTO credentials, char[] serviceAccountKey, boolean inheritFromDelegate) {
    final String bucketName = "bucketName";
    final String folderPath = "folderPath";

    final GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
        GcsHelmStoreDelegateConfig.builder()
            .bucketName(bucketName)
            .folderPath(folderPath)
            .gcpConnector(GcpConnectorDTO.builder().credential(credentials).build())
            .useLatestChartMuseumVersion(false)
            .build();

    doReturn(gcsChartmuseumClient)
        .when(clientFactory)
        .gcs(CLI_PATH_OLD, bucketName, folderPath, inheritFromDelegate ? null : serviceAccountKey, RESOURCE_DIRECTORY);
    ChartmuseumClient result = ngChartmuseumClientFactory.createClient(gcsHelmStoreDelegateConfig, RESOURCE_DIRECTORY);
    assertThat(result).isSameAs(gcsChartmuseumClient);

    verify(clientFactory, times(1))
        .gcs(CLI_PATH_OLD, bucketName, folderPath, inheritFromDelegate ? null : serviceAccountKey, RESOURCE_DIRECTORY);
  }
}