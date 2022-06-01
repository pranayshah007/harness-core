/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

@OwnedBy(CDP)
@Singleton
public class K8sEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
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
        List<DecryptableEntity> decryptableEntities = httpHelmConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(decryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntities.get(0));
        } else {
          return emptyList();
        }

      case AWS:
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> awsDecryptableEntities = awsConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(awsDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, awsDecryptableEntities.get(0));
        } else {
          return emptyList();
        }

      case GCP:
        GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> gcpDecryptableEntities = gcpConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(gcpDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, gcpDecryptableEntities.get(0));
        } else {
          return emptyList();
        }

      case AZURE:
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> azureDecryptableEntities = azureConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(azureDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, azureDecryptableEntities.get(0));
        } else {
          return emptyList();
        }

      case APP_DYNAMICS:
      case SPLUNK:
      case GIT:
      default:
        throw new UnsupportedOperationException(
            format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
    }
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
    switch (infrastructure.getKind()) {
      case KUBERNETES_DIRECT:
        K8sDirectInfrastructureOutcome k8SDirectInfrastructure = (K8sDirectInfrastructureOutcome) infrastructure;
        KubernetesHelperService.validateNamespace(k8SDirectInfrastructure.getNamespace());

        return DirectK8sInfraDelegateConfig.builder()
            .namespace(k8SDirectInfrastructure.getNamespace())
            .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) connectorDTO.getConnectorConfig())
            .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
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
            .build();

      default:
        throw new UnsupportedOperationException(
            format("Unsupported Infrastructure type: [%s]", infrastructure.getKind()));
    }
  }
}
