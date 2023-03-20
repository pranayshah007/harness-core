/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.task.k8s.ManifestType.HELM_CHART;
import static io.harness.delegate.task.k8s.ManifestType.KUSTOMIZE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(CDP)
public interface K8sDeployRequest extends TaskParameters, ExecutionCapabilityDemander {
  K8sTaskType getTaskType();
  String getCommandName();
  K8sInfraDelegateConfig getK8sInfraDelegateConfig();
  List<String> getValuesYamlList();
  List<String> getKustomizePatchesList();
  List<String> getOpenshiftParamList();
  ManifestDelegateConfig getManifestDelegateConfig();
  Integer getTimeoutIntervalInMin();
  CommandUnitsProgress getCommandUnitsProgress();
  String getReleaseName();
  boolean isUseLatestKustomizeVersion();
  boolean isUseNewKubectlVersion();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = getK8sInfraDelegateConfig();
    List<EncryptedDataDetail> cloudProviderEncryptionDetails = k8sInfraDelegateConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            cloudProviderEncryptionDetails, maskingEvaluator));

    if (k8sInfraDelegateConfig instanceof DirectK8sInfraDelegateConfig) {
      capabilities.addAll(K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((DirectK8sInfraDelegateConfig) k8sInfraDelegateConfig).getKubernetesClusterConfigDTO(), maskingEvaluator,
          k8sInfraDelegateConfig.useSocketCapability()));
    }

    if (k8sInfraDelegateConfig instanceof GcpK8sInfraDelegateConfig) {
      capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((GcpK8sInfraDelegateConfig) k8sInfraDelegateConfig).getGcpConnectorDTO(), maskingEvaluator));
    }

    if (k8sInfraDelegateConfig instanceof AzureK8sInfraDelegateConfig) {
      capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((AzureK8sInfraDelegateConfig) k8sInfraDelegateConfig).getAzureConnectorDTO(), maskingEvaluator));
    }

    if (getManifestDelegateConfig() != null) {
      if (KUSTOMIZE == getManifestDelegateConfig().getManifestType()) {
        KustomizeManifestDelegateConfig kustomizeManifestConfig =
            (KustomizeManifestDelegateConfig) getManifestDelegateConfig();
        if (isNotEmpty(kustomizeManifestConfig.getPluginPath())) {
          capabilities.add(new KustomizeCapability(kustomizeManifestConfig.getPluginPath()));
        }
      }

      if (GIT == getManifestDelegateConfig().getStoreDelegateConfig().getType()) {
        GitStoreDelegateConfig gitStoreDelegateConfig =
            (GitStoreDelegateConfig) getManifestDelegateConfig().getStoreDelegateConfig();
        capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
            ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
      }

      if (HELM_CHART == getManifestDelegateConfig().getManifestType()) {
        HelmChartManifestDelegateConfig helManifestConfig =
            (HelmChartManifestDelegateConfig) getManifestDelegateConfig();
        capabilities.add(HelmInstallationCapability.builder()
                             .version(helManifestConfig.getHelmVersion())
                             .criteria(String.format("Helm %s Installed", helManifestConfig.getHelmVersion()))
                             .build());

        switch (helManifestConfig.getStoreDelegateConfig().getType()) {
          case HTTP_HELM:
            HttpHelmStoreDelegateConfig httpHelmStoreConfig =
                (HttpHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
            capabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
                httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), maskingEvaluator));
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
    }

    return capabilities;
  }
}
