package io.harness.cdng.googlefunctions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GoogleCloudStorageArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunctionArtifactConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactoryArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessEcrArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessS3ArtifactConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

@Singleton
@OwnedBy(CDP)
public class GoogleFunctionsEntityHelper {
    @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
    @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

    public List<EncryptedDataDetail> getEncryptionDataDetails(
            @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
        switch (connectorDTO.getConnectorType()) {
            case GCP:
                GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
                List<DecryptableEntity> gcpDecryptableEntities = gcpConnectorDTO.getDecryptableEntities();
                if (isNotEmpty(gcpDecryptableEntities)) {
                    return secretManagerClientService.getEncryptionDetails(ngAccess, gcpDecryptableEntities.get(0));
                } else {
                    return emptyList();
                }
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
            throw new InvalidRequestException(format("Connector not found for identifier : [%s] ", connectorId), USER);
        }
        return connectorDTO.get().getConnector();
    }

    public GoogleFunctionInfraConfig getInfraConfig(InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
        ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
        switch (infrastructureOutcome.getKind()) {
            case GOOGLE_CLOUD_FUNCTIONS:
                GoogleFunctionsInfrastructureOutcome googleFunctionsInfrastructureOutcome =
                        (GoogleFunctionsInfrastructureOutcome) infrastructureOutcome;
                return GcpGoogleFunctionInfraConfig.builder()
                        .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
                        .gcpConnectorDTO((GcpConnectorDTO) connectorDTO.getConnectorConfig())
                        .region(googleFunctionsInfrastructureOutcome.getRegion())
                        .project(googleFunctionsInfrastructureOutcome.getProject())
                        .infraStructureKey(googleFunctionsInfrastructureOutcome.getInfrastructureKey())
                        .build();
            default:
                throw new UnsupportedOperationException(
                        format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
        }
    }

    public GoogleFunctionArtifactConfig getArtifactConfig(ArtifactOutcome artifactOutcome, NGAccess ngAccess) {
        if (artifactOutcome instanceof GoogleCloudStorageArtifactOutcome) {
            GoogleCloudStorageArtifactOutcome googleCloudStorageArtifactOutcome =
                    (GoogleCloudStorageArtifactOutcome) artifactOutcome;
            return GoogleCloudStorageArtifactConfig.builder()
                    .bucket(googleCloudStorageArtifactOutcome.getBucket())
                    .filePath(googleCloudStorageArtifactOutcome.getArtifactPath())
                    .build();
        } else {
            throw new UnsupportedOperationException(
                    format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
        }
    }
}
