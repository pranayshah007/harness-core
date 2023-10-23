/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.CCMKubernetesConnectorHelper;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.CEK8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.exception.InvalidRequestException;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.constraints.NotNull;

public class CEK8sConnectorValidationParamsProvider extends K8sConnectorValidationParamsProvider {
  @Inject @Named("defaultConnectorService") private ConnectorService connectorService;

  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final String scopedConnectorIdentifier =
        ((CEKubernetesClusterConfigDTO) connectorInfoDTO.getConnectorConfig()).getConnectorRef();

    Optional<ConnectorInfoDTO> k8sConnectorInfoDTO =
        getReferencedConnectorConfig(scopedConnectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    if (!k8sConnectorInfoDTO.isPresent()) {
      throw new InvalidRequestException(
          String.format("There does not exist a K8sCluster Cloud Provider ConnectorDisconnectHandler with kubernetesConnectorRef=[%s]",
              scopedConnectorIdentifier));
    }
    ConnectorValidationParams refConnectorValidationParams = super.getConnectorValidationParams(
        k8sConnectorInfoDTO.get(), connectorName, accountIdentifier, orgIdentifier, projectIdentifier);
    final K8sValidationParams k8sValidationParams = (K8sValidationParams) refConnectorValidationParams;
    return CEK8sValidationParams.builder()
        .kubernetesClusterConfigDTO(k8sValidationParams.getKubernetesClusterConfigDTO())
        .connectorName(k8sValidationParams.getConnectorName())
        .encryptedDataDetails(k8sValidationParams.getEncryptedDataDetails())
        .featuresEnabled(ImmutableList.of(CEFeatures.BILLING))
        .build();
  }

  private Optional<ConnectorInfoDTO> getReferencedConnectorConfig(@NotNull String scopedConnectorIdentifier,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    IdentifierRef connectorRef = CCMKubernetesConnectorHelper.getReferencedConnectorIdentifier(
        scopedConnectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);

    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    return connectorResponseDTO.map(ConnectorResponseDTO::getConnector);
  }
}
