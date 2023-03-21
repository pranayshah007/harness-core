/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.delegate.task.k8s.ManifestType.HELM_CHART;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

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
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.helm.HelmCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
public class HelmCommandRequestNG implements TaskParameters, ExecutionCapabilityDemander {
  @Expression(DISALLOW_SECRETS) private String releaseName;
  @NotEmpty private HelmCommandType helmCommandType;
  @Expression(ALLOW_SECRETS) List<String> valuesYamlList;
  private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Expression(ALLOW_SECRETS) private ManifestDelegateConfig manifestDelegateConfig;
  private String accountId;
  private boolean k8SteadyStateCheckEnabled;
  @Builder.Default private boolean shouldOpenFetchFilesLogStream = true;
  private CommandUnitsProgress commandUnitsProgress;
  private transient LogCallback logCallback;
  private String namespace;
  private HelmVersion helmVersion;
  @Expression(ALLOW_SECRETS) private String commandFlags;
  private String repoName;
  private String workingDir;
  private String kubeConfigLocation;
  private String ocPath;
  private String commandName;
  private boolean useLatestKubectlVersion;
  private String gcpKeyPath;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<EncryptedDataDetail> cloudProviderEncryptionDetails = k8sInfraDelegateConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            cloudProviderEncryptionDetails, maskingEvaluator));

    if (k8sInfraDelegateConfig instanceof DirectK8sInfraDelegateConfig) {
      capabilities.addAll(K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((DirectK8sInfraDelegateConfig) k8sInfraDelegateConfig).getKubernetesClusterConfigDTO(), maskingEvaluator));
    }

    if (k8sInfraDelegateConfig instanceof GcpK8sInfraDelegateConfig) {
      capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((GcpK8sInfraDelegateConfig) k8sInfraDelegateConfig).getGcpConnectorDTO(), maskingEvaluator));
    }

    if (k8sInfraDelegateConfig instanceof AzureK8sInfraDelegateConfig) {
      capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((AzureK8sInfraDelegateConfig) k8sInfraDelegateConfig).getAzureConnectorDTO(), maskingEvaluator));
    }

    if (k8sInfraDelegateConfig instanceof EksK8sInfraDelegateConfig) {
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((EksK8sInfraDelegateConfig) k8sInfraDelegateConfig).getAwsConnectorDTO(), maskingEvaluator));
    }

    if (manifestDelegateConfig != null) {
      HelmChartManifestDelegateConfig helManifestConfig = (HelmChartManifestDelegateConfig) manifestDelegateConfig;
      capabilities.add(HelmInstallationCapability.builder()
                           .version(helManifestConfig.getHelmVersion())
                           .criteria(String.format("Helm %s Installed", helManifestConfig.getHelmVersion()))
                           .build());

      if (GIT == manifestDelegateConfig.getStoreDelegateConfig().getType()) {
        GitStoreDelegateConfig gitStoreDelegateConfig =
            (GitStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
            ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));
        capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
      }

      if (HELM_CHART == manifestDelegateConfig.getManifestType()) {
        switch (helManifestConfig.getStoreDelegateConfig().getType()) {
          case HTTP_HELM:
            HttpHelmStoreDelegateConfig httpHelmStoreConfig =
                (HttpHelmStoreDelegateConfig) helManifestConfig.getStoreDelegateConfig();
            /*
            We are henceforth using SocketConnectivityExecutionCapability instead of HttpConnectionExecutionCapability
            this is to ensure that we don't fail Helm Repo Connector Validation in case the url returns 400
            ref: https://harness.atlassian.net/browse/CDS-36189
            */
            SocketConnectivityCapabilityGenerator.addSocketConnectivityExecutionCapability(
                httpHelmStoreConfig.getHttpHelmConnector().getHelmRepoUrl(), capabilities);
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
