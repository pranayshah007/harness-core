/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(CDP)
public class TasEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  public List<EncryptedDataDetail> getEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    if (connectorDTO.getConnectorType() == ConnectorType.TAS) {
      TasConnectorDTO tasConnectorDTO = (TasConnectorDTO) connectorDTO.getConnectorConfig();
      List<DecryptableEntity> tasDecryptableEntities = tasConnectorDTO.getDecryptableEntities();
      if (isNotEmpty(tasDecryptableEntities)) {
        return secretManagerClientService.getEncryptionDetails(ngAccess, tasDecryptableEntities.get(0));
      } else {
        return emptyList();
      }
    }
    throw new UnsupportedOperationException(
        format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
  }

  public ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (connectorDTO.isPresent()) {
      return connectorDTO.get().getConnector();
    }
    throw new InvalidRequestException(format("Connector not found for identifier : [%s] ", connectorId), USER);
  }
  public BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
  public TasInfraConfig getTasInfraConfig(InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
    if (InfrastructureKind.TAS.equals(infrastructureOutcome.getKind())) {
      TanzuApplicationServiceInfrastructureOutcome tasInfrastructureOutcome =
          (TanzuApplicationServiceInfrastructureOutcome) infrastructureOutcome;
      return TasInfraConfig.builder()
          .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .organization(tasInfrastructureOutcome.getOrganization())
          .tasConnectorDTO((TasConnectorDTO) connectorDTO.getConnectorConfig())
          .space(tasInfrastructureOutcome.getSpace())
          .build();
    }
    throw new UnsupportedOperationException(
        format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
  }
}
