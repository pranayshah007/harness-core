/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.ConnectorType.RANCHER;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_RANCHER;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.ng.core.NGAccess;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class K8sEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private AccountClient accountClient;

  public static final String CLASS_CAST_EXCEPTION_ERROR =
      "Unsupported Connector for Infrastructure type: [%s]. Connector provided is of type: [%s]. Configure connector of type: [%s] to resolve the issue";
  public static final String K8S_INFRA_NAMESPACE_REGEX_PATTERN = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$";
  public List<EncryptedDataDetail> getEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    switch (connectorDTO.getConnectorType()) {
      case KUBERNETES_CLUSTER:
        KubernetesClusterConfigDTO connectorConfig = (KubernetesClusterConfigDTO) connectorDTO.getConnectorConfig();
        if (connectorConfig.getCredential().getKubernetesCredentialType()
            == KubernetesCredentialType.MANUAL_CREDENTIALS) {
          KubernetesClusterDetailsDTO clusterDetailsDTO =
              (KubernetesClusterDetailsDTO) connectorConfig.getCredential().getConfig();

          KubernetesAuthCredentialDTO authCredentialDTO = clusterDetailsDTO.getAuth().getCredentials();
          return secretManagerClientService.getEncryptionDetails(ngAccess, authCredentialDTO);
        } else {
          return emptyList();
        }

      case HTTP_HELM_REPO:
        HttpHelmConnectorDTO httpHelmConnectorDTO = (HttpHelmConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, httpHelmConnectorDTO.getDecryptableEntities());

      case OCI_HELM_REPO:
        OciHelmConnectorDTO ociHelmConnectorDTO = (OciHelmConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, ociHelmConnectorDTO.getDecryptableEntities());

      case AWS:
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, awsConnectorDTO.getDecryptableEntities());

      case GCP:
        GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, gcpConnectorDTO.getDecryptableEntities());

      case AZURE:
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, azureConnectorDTO.getDecryptableEntities());

      case RANCHER:
        RancherConnectorDTO rancherConnectorDTO = (RancherConnectorDTO) connectorDTO.getConnectorConfig();
        return getEncryptionDataDetailsInternal(ngAccess, rancherConnectorDTO.getDecryptableEntities());

      case APP_DYNAMICS:
      case SPLUNK:
      case GIT:
      default:
        throw new UnsupportedOperationException(
            format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
    }
  }

  private List<EncryptedDataDetail> getEncryptionDataDetailsInternal(
      @Nonnull NGAccess ngAccess, List<DecryptableEntity> decryptableEntities) {
    if (isEmpty(decryptableEntities)) {
      return emptyList();
    }
    return secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntities.get(0));
  }

  public ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorId), USER);
    }
    return connectorDTO.get().getConnector();
  }

  public K8sInfraDelegateConfig getK8sInfraDelegateConfig(InfrastructureOutcome infrastructure, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructure.getConnectorRef(), ngAccess);
    try {
      switch (infrastructure.getKind()) {
        case KUBERNETES_DIRECT:
          K8sDirectInfrastructureOutcome k8SDirectInfrastructure = (K8sDirectInfrastructureOutcome) infrastructure;
          KubernetesHelperService.validateNamespace(k8SDirectInfrastructure.getNamespace());

          return DirectK8sInfraDelegateConfig.builder()
              .namespace(k8SDirectInfrastructure.getNamespace())
              .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
              .useSocketCapability(useK8sDirectSocketCapability(ngAccess.getAccountIdentifier()))
              .build();

        case KUBERNETES_GCP:
          K8sGcpInfrastructureOutcome k8sGcpInfrastructure = (K8sGcpInfrastructureOutcome) infrastructure;
          KubernetesHelperService.validateNamespace(k8sGcpInfrastructure.getNamespace());
          KubernetesHelperService.validateCluster(k8sGcpInfrastructure.getCluster());

          return GcpK8sInfraDelegateConfig.builder()
              .namespace(k8sGcpInfrastructure.getNamespace())
              .cluster(k8sGcpInfrastructure.getCluster())
              .gcpConnectorDTO((GcpConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
              .build();

        case KUBERNETES_AZURE:
          K8sAzureInfrastructureOutcome k8sAzureInfrastructure = (K8sAzureInfrastructureOutcome) infrastructure;
          KubernetesHelperService.validateNamespace(k8sAzureInfrastructure.getNamespace());
          KubernetesHelperService.validateSubscription(k8sAzureInfrastructure.getSubscription());
          KubernetesHelperService.validateResourceGroup(k8sAzureInfrastructure.getResourceGroup());
          KubernetesHelperService.validateCluster(k8sAzureInfrastructure.getCluster());

          return AzureK8sInfraDelegateConfig.builder()
              .namespace(k8sAzureInfrastructure.getNamespace())
              .cluster(k8sAzureInfrastructure.getCluster())
              .subscription(k8sAzureInfrastructure.getSubscription())
              .resourceGroup(k8sAzureInfrastructure.getResourceGroup())
              .azureConnectorDTO((AzureConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
              .useClusterAdminCredentials(k8sAzureInfrastructure.getUseClusterAdminCredentials() != null
                  && k8sAzureInfrastructure.getUseClusterAdminCredentials())
              .build();

        case KUBERNETES_AWS:
          K8sAwsInfrastructureOutcome k8sAwsInfrastructure = (K8sAwsInfrastructureOutcome) infrastructure;
          KubernetesHelperService.validateNamespace(k8sAwsInfrastructure.getNamespace());
          KubernetesHelperService.validateCluster(k8sAwsInfrastructure.getCluster());

          return EksK8sInfraDelegateConfig.builder()
              .namespace(k8sAwsInfrastructure.getNamespace())
              .cluster(k8sAwsInfrastructure.getCluster())
              .awsConnectorDTO((AwsConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
              .build();

        case KUBERNETES_RANCHER:
          K8sRancherInfrastructureOutcome k8sRancherInfrastructure = (K8sRancherInfrastructureOutcome) infrastructure;
          KubernetesHelperService.validateNamespace(k8sRancherInfrastructure.getNamespace());
          KubernetesHelperService.validateCluster(k8sRancherInfrastructure.getClusterName());

          return RancherK8sInfraDelegateConfig.builder()
              .namespace(k8sRancherInfrastructure.getNamespace())
              .cluster(k8sRancherInfrastructure.getClusterName())
              .rancherConnectorDTO((RancherConnectorDTO) connectorDTO.getConnectorConfig())
              .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
              .build();

        default:
          throw new UnsupportedOperationException(
              format("Unsupported Infrastructure type: [%s]", infrastructure.getKind()));
      }
    } catch (ClassCastException ex) {
      if (Set.of(KUBERNETES_DIRECT, KUBERNETES_GCP, KUBERNETES_AZURE, KUBERNETES_AWS, KUBERNETES_RANCHER)
              .contains(infrastructure.getKind())) {
        String requiredConnectorType = getRequiredConnectorType(infrastructure.getKind());
        throw new InvalidArgumentsException(Pair.of("connectorRef",
            String.format(CLASS_CAST_EXCEPTION_ERROR, infrastructure.getKind(), connectorDTO.getConnectorType(),
                requiredConnectorType)));
      }
      throw ex;
    }
  }

  public String getRequiredConnectorType(String infrastructureType) {
    switch (infrastructureType) {
      case KUBERNETES_DIRECT:
        return KUBERNETES_CLUSTER.getDisplayName();
      case KUBERNETES_GCP:
        return GCP.getDisplayName();
      case KUBERNETES_AZURE:
        return AZURE.getDisplayName();
      case KUBERNETES_AWS:
        return AWS.getDisplayName();
      case KUBERNETES_RANCHER:
        return RANCHER.getDisplayName();
      default:
        return StringUtils.EMPTY;
    }
  }

  private boolean useK8sDirectSocketCapability(String accountIdentifier) {
    try {
      return CGRestUtils.getResponse(
          accountClient.isFeatureFlagEnabled(CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(), accountIdentifier));
    } catch (Exception e) {
      log.warn("Unable to evaluate FF {} for account {}", CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(), accountIdentifier);
    }

    return false;
  }
}
