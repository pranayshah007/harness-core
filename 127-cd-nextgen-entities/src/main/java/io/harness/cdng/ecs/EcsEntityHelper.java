package io.harness.cdng.ecs;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.IdentifierRefHelper;

import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.ECS;
import static java.lang.String.format;

@Singleton
@OwnedBy(CDP)
public class EcsEntityHelper {
    @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

    public void getEcsInfraDelegateConfig(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
        NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
        switch (infrastructureOutcome.getKind()) {
            case ECS:
                ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
                EcsInfrastructureOutcome ecsInfrastructureOutcome = (EcsInfrastructureOutcome) infrastructureOutcome;
                //todo: return ecsInfraConfig based on infra request schema for delegate task
                break;
            default:
                throw new UnsupportedOperationException(
                        format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
        }
    }

    //todo: refactor it
    public ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
        IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
                connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
        Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
                identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
        if (!connectorDTO.isPresent()) {
            throw new InvalidRequestException(format("Connector not found for identifier : [%s] ", connectorId), USER);
        }
        return connectorDTO.get().getConnector();
    }
}
