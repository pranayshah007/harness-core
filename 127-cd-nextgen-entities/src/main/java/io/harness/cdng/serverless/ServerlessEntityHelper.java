/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.serverless.ServerlessInfraType.AWS_INFRA;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactoryArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.exception.InvalidRequestException;
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

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ServerlessEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  public List<EncryptedDataDetail> getEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    switch (connectorDTO.getConnectorType()) {
      case AWS:
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> awsDecryptableEntities = awsConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(awsDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, awsDecryptableEntities.get(0));
        } else {
          return emptyList();
        }
      case ARTIFACTORY:
        ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> artifactoryDecryptableEntities = artifactoryConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(artifactoryDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
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

  public ServerlessInfraConfig getServerlessInfraConfig(
      InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
    switch (infrastructureOutcome.getKind()) {
      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
            (ServerlessAwsLambdaInfrastructureOutcome) infrastructureOutcome;
        return ServerlessAwsLambdaInfraConfig.builder()
            .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
            .awsConnectorDTO((AwsConnectorDTO) connectorDTO.getConnectorConfig())
            .serverlessInfraType(AWS_INFRA)
            .region(serverlessAwsLambdaInfrastructureOutcome.getRegion())
            .stage(serverlessAwsLambdaInfrastructureOutcome.getStage())
            .infraStructureKey(serverlessAwsLambdaInfrastructureOutcome.getInfrastructureKey())
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
    }
  }

  public ServerlessArtifactConfig getServerlessArtifactConfig(ArtifactOutcome artifactOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO;
    if (artifactOutcome instanceof ArtifactoryGenericArtifactOutcome) {
      ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
          (ArtifactoryGenericArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(artifactoryGenericArtifactOutcome.getConnectorRef(), ngAccess);
      return ServerlessArtifactoryArtifactConfig.builder()
          .repositoryName(artifactoryGenericArtifactOutcome.getRepositoryName())
          .identifier(artifactoryGenericArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .artifactDirectory(artifactoryGenericArtifactOutcome.getArtifactDirectory())
          .artifactPath(artifactoryGenericArtifactOutcome.getArtifactPath())
          .repositoryFormat(artifactoryGenericArtifactOutcome.getRepositoryFormat())
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }
}
