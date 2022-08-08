/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.chartmuseum.ChartmuseumClient;
import io.harness.chartmuseum.ChartmuseumClientFactory;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class NgChartmuseumClientFactory {
  @Inject private ChartmuseumClientFactory clientFactory;
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  public ChartmuseumClient createClient(StoreDelegateConfig storeDelegateConfig, String resourceDirectory) {
    switch (storeDelegateConfig.getType()) {
      case S3_HELM:
        S3HelmStoreDelegateConfig s3StoreDelegateConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        AwsConnectorDTO awsConnector = s3StoreDelegateConfig.getAwsConnector();
        return createS3(s3StoreDelegateConfig, awsConnector);
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        GcpConnectorCredentialDTO credentials = gcsHelmStoreDelegateConfig.getGcpConnector().getCredential();
        return createGcs(gcsHelmStoreDelegateConfig, credentials, resourceDirectory);
      default:
        throw new UnsupportedOperationException(
            format("Manifest store config type: [%s]", storeDelegateConfig.getType()));
    }
  }

  private ChartmuseumClient createS3(S3HelmStoreDelegateConfig s3StoreDelegateConfig, AwsConnectorDTO awsConnector) {
    String cliPath = k8sGlobalConfigService.getChartMuseumPath(s3StoreDelegateConfig.isUseLatestChartMuseumVersion());
    boolean inheritFromDelegate = false;
    boolean irsa = false;
    char[] accessKey = null;
    char[] secretKey = null;

    switch (awsConnector.getCredential().getAwsCredentialType()) {
      case MANUAL_CREDENTIALS:
        AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) awsConnector.getCredential().getConfig();
        String accessKeyString = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
            config.getAccessKey(), config.getAccessKeyRef());
        accessKey = accessKeyString == null ? null : accessKeyString.toCharArray();
        secretKey = config.getSecretKeyRef().getDecryptedValue();
        break;

      case INHERIT_FROM_DELEGATE:
        inheritFromDelegate = true;
        break;

      case IRSA:
        irsa = true;
        break;

      default:
        throw new UnsupportedOperationException(
            format("Credentials type %s are not supported", awsConnector.getCredential().getAwsCredentialType()));
    }

    return clientFactory.s3(cliPath, s3StoreDelegateConfig.getBucketName(), s3StoreDelegateConfig.getFolderPath(),
        s3StoreDelegateConfig.getRegion(), inheritFromDelegate, accessKey, secretKey, irsa);
  }

  private ChartmuseumClient createGcs(GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig,
      GcpConnectorCredentialDTO credentials, String resourceDirectory) {
    String cliPath =
        k8sGlobalConfigService.getChartMuseumPath(gcsHelmStoreDelegateConfig.isUseLatestChartMuseumVersion());
    char[] serviceAccountKey = null;

    if (GcpCredentialType.MANUAL_CREDENTIALS == credentials.getGcpCredentialType()) {
      GcpManualDetailsDTO manualCredentials = (GcpManualDetailsDTO) credentials.getConfig();
      serviceAccountKey = manualCredentials.getSecretKeyRef().getDecryptedValue();
    }

    return clientFactory.gcs(cliPath, gcsHelmStoreDelegateConfig.getBucketName(),
        gcsHelmStoreDelegateConfig.getFolderPath(), serviceAccountKey, resourceDirectory);
  }
}
