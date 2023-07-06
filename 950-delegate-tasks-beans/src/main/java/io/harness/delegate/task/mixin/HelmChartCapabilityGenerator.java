/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.task.k8s.ManifestType.HELM_CHART;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartCapabilityGenerator {
  public static List<ExecutionCapability> generateCapabilities(
      HelmChartManifestDelegateConfig helManifestConfig, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    capabilities.add(HelmInstallationCapability.builder()
                         .version(helManifestConfig.getHelmVersion())
                         .criteria(String.format("Helm %s Installed", helManifestConfig.getHelmVersion()))
                         .build());

    if (GIT == helManifestConfig.getStoreDelegateConfig().getType()) {
      GitStoreDelegateConfig gitStoreDelegateConfig =
          (GitStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    if (HELM_CHART == helManifestConfig.getManifestType()) {
      switch (helManifestConfig.getStoreDelegateConfig().getType()) {
        case HTTP_HELM:
          HttpHelmStoreDelegateConfig httpHelmStoreConfig =
              (HttpHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
          if (helManifestConfig.isIgnoreResponseCode()) {
            capabilities.add(
                HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
                    httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), maskingEvaluator, true));
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
              (OciHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
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
              (S3HelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
          capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
              s3HelmStoreConfig.getAwsConnector(), maskingEvaluator));
          capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
              s3HelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
          break;

        case GCS_HELM:
          GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
              (GcsHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
          capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
              gcsHelmStoreDelegateConfig.getGcpConnector(), maskingEvaluator));
          capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
              gcsHelmStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
          break;

        default:
          // No capabilities to add
      }
    }

    return capabilities;
  }
}
