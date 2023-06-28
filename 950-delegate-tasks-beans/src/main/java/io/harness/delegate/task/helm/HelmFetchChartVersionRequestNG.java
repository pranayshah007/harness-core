/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.task.k8s.ManifestType.HELM_CHART;

import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class HelmFetchChartVersionRequestNG implements TaskParameters, ExecutionCapabilityDemander {
  HelmChartManifestDelegateConfig helmChartManifestDelegateConfig;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    if (helmChartManifestDelegateConfig != null) {
      capabilities.add(
          HelmInstallationCapability.builder()
              .version(helmChartManifestDelegateConfig.getHelmVersion())
              .criteria(String.format("Helm %s Installed", helmChartManifestDelegateConfig.getHelmVersion()))
              .build());

      if (HELM_CHART == helmChartManifestDelegateConfig.getManifestType()) {
        switch (helmChartManifestDelegateConfig.getStoreDelegateConfig().getType()) {
          case HTTP_HELM:
            HttpHelmStoreDelegateConfig httpHelmStoreConfig =
                (HttpHelmStoreDelegateConfig) helmChartManifestDelegateConfig.getStoreDelegateConfig();
            if (helmChartManifestDelegateConfig.isIgnoreResponseCode()) {
              capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
                  httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), maskingEvaluator));
            } else {
              SocketConnectivityCapabilityGenerator.addSocketConnectivityExecutionCapability(
                  httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), capabilities);
            }
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                httpHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
            populateDelegateSelectorCapability(
                capabilities, httpHelmStoreConfig.getHttpHelmConnector().getDelegateSelectors());
            break;

          case OCI_HELM:
            OciHelmStoreDelegateConfig ociHelmStoreConfig =
                (OciHelmStoreDelegateConfig) helmChartManifestDelegateConfig.getStoreDelegateConfig();
            OciHelmConnectorDTO ociHelmConnector = ociHelmStoreConfig.getOciHelmConnector();
            capabilities.add(HelmInstallationCapability.builder()
                                 .version(HelmVersion.V380)
                                 .criteria("OCI_HELM_REPO: " + ociHelmConnector.getHelmRepoUrl())
                                 .build());
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                ociHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
            populateDelegateSelectorCapability(
                capabilities, ociHelmStoreConfig.getOciHelmConnector().getDelegateSelectors());
            break;

          case S3_HELM:
            S3HelmStoreDelegateConfig s3HelmStoreConfig =
                (S3HelmStoreDelegateConfig) helmChartManifestDelegateConfig.getStoreDelegateConfig();
            capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
                s3HelmStoreConfig.getAwsConnector(), maskingEvaluator));
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                s3HelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
            break;

          case GCS_HELM:
            GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
                (GcsHelmStoreDelegateConfig) helmChartManifestDelegateConfig.getStoreDelegateConfig();
            capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
                gcsHelmStoreDelegateConfig.getGcpConnector(), maskingEvaluator));
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                gcsHelmStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
            break;

          default:
            // No capabilities to add
        }
      }
    }
    return capabilities;
  }
}
