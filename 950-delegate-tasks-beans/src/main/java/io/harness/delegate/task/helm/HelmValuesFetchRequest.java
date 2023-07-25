/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class HelmValuesFetchRequest implements TaskParameters, ExecutionCapabilityDemander {
  private String accountId;
  private long timeout;
  private HelmChartManifestDelegateConfig helmChartManifestDelegateConfig;
  private boolean closeLogStream;
  @Default private boolean openNewLogStream = true;
  private List<HelmFetchFileConfig> helmFetchFileConfigList;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    HelmVersion helmVersion = helmChartManifestDelegateConfig.getHelmVersion();
    StoreDelegateConfig storeDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();

    return getHelmExecutionCapabilities(
        helmVersion, storeDelegateConfig, maskingEvaluator, helmChartManifestDelegateConfig.isIgnoreResponseCode());
  }

  public static List<ExecutionCapability> getHelmExecutionCapabilities(HelmVersion helmVersion,
      StoreDelegateConfig storeDelegateConfig, ExpressionEvaluator maskingEvaluator, boolean ignoreResponseCode) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (helmVersion != null) {
      capabilities.add(HelmInstallationCapability.builder()
                           .version(helmVersion)
                           .criteria(String.format("Helm %s Installed", helmVersion))
                           .build());
    }

    switch (storeDelegateConfig.getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpHelmStoreConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
        if (httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl() != null) {
          if (ignoreResponseCode) {
            capabilities.add(
                HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
                    httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), maskingEvaluator, true));
          } else {
            SocketConnectivityCapabilityGenerator.addSocketConnectivityExecutionCapability(
                httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), capabilities);
          }
        }
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            httpHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
        populateDelegateSelectorCapability(
            capabilities, httpHelmStoreConfig.getHttpHelmConnector().getDelegateSelectors());
        break;

      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelmStoreConfig = (OciHelmStoreDelegateConfig) storeDelegateConfig;
        if (ociHelmStoreConfig.getOciHelmConnector().getHelmRepoUrl() != null) {
          OciHelmConnectorDTO ociHelmConnector = ociHelmStoreConfig.getOciHelmConnector();
          capabilities.add(HelmInstallationCapability.builder()
                               .version(HelmVersion.V380)
                               .criteria("OCI_HELM_REPO: " + ociHelmConnector.getHelmRepoUrl())
                               .build());
        }
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            ociHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
        populateDelegateSelectorCapability(
            capabilities, ociHelmStoreConfig.getOciHelmConnector().getDelegateSelectors());
        break;

      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
            s3HelmStoreConfig.getAwsConnector(), maskingEvaluator));
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            s3HelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
        break;

      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
            gcsHelmStoreDelegateConfig.getGcpConnector(), maskingEvaluator));
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            gcsHelmStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
        break;

      default:
        // No capabilities to add
    }
    return capabilities;
  }
}
