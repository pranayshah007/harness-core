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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
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
import io.harness.delegate.task.utils.K8sCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ServiceHookDelegateConfig;

import java.util.ArrayList;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_COMMON_STEPS})
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
  List<ServiceHookDelegateConfig> getServiceHooks();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig = getK8sInfraDelegateConfig();
    List<EncryptedDataDetail> cloudProviderEncryptionDetails = k8sInfraDelegateConfig.getEncryptionDataDetails();
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            cloudProviderEncryptionDetails, maskingEvaluator));
    capabilities.addAll(K8sCapabilityGenerator.generateExecutionCapabilities(maskingEvaluator, k8sInfraDelegateConfig));

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
            gitStoreDelegateConfig, gitStoreDelegateConfig.getEncryptedDataDetails()));
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
            capabilities.add(
                HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapabilityWithIgnoreResponseCode(
                    httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), maskingEvaluator, true));
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                httpHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
            populateDelegateSelectorCapability(
                capabilities, httpHelmStoreConfig.getHttpHelmConnector().getDelegateSelectors());
            break;

          case OCI_HELM:
            OciHelmStoreDelegateConfig ociHelmStoreConfig =
                (OciHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
            String criteria = null;
            if (ociHelmStoreConfig.getAwsConnectorDTO() != null) {
              criteria = helManifestConfig.getChartName() + ":" + ociHelmStoreConfig.getRegion();
              capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
                  ociHelmStoreConfig.getAwsConnectorDTO(), maskingEvaluator));
            } else if (ociHelmStoreConfig.getOciHelmConnector() != null) {
              criteria = ociHelmStoreConfig.getRepoUrl();
              OciHelmConnectorDTO ociHelmConnector = ociHelmStoreConfig.getOciHelmConnector();
              populateDelegateSelectorCapability(capabilities, ociHelmConnector.getDelegateSelectors());
            }
            if (isNotEmpty(criteria)) {
              capabilities.add(HelmInstallationCapability.builder()
                                   .version(HelmVersion.V380)
                                   .criteria("OCI_HELM_REPO: " + criteria)
                                   .build());
            }
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                ociHelmStoreConfig.getEncryptedDataDetails(), maskingEvaluator));
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
