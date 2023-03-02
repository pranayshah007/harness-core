/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.AWS_LAMBDA;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaEcrArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaS3ArtifactConfig;
import io.harness.exception.InvalidRequestException;
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

@Singleton
@OwnedBy(CDP)
public class AwsLambdaEntityHelper {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  public List<EncryptedDataDetail> getEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    switch (connectorDTO.getConnectorType()) {
      case AWS:
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> gcpDecryptableEntities = awsConnectorDTO.getDecryptableEntities();
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

  public AwsLambdaFunctionsInfraConfig getInfraConfig(InfrastructureOutcome infrastructureOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO = getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), ngAccess);
    switch (infrastructureOutcome.getKind()) {
      case AWS_LAMBDA:
        AwsLambdaInfrastructureOutcome awsLambdaInfrastructureOutcome =
            (AwsLambdaInfrastructureOutcome) infrastructureOutcome;
        return AwsLambdaFunctionsInfraConfig.builder()
            .encryptionDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
            .awsConnectorDTO((AwsConnectorDTO) connectorDTO.getConnectorConfig())
            .region(awsLambdaInfrastructureOutcome.getRegion())
            .infraStructureKey(awsLambdaInfrastructureOutcome.getInfrastructureKey())
            .build();
      default:
        throw new UnsupportedOperationException(
            format("Unsupported Infrastructure type: [%s]", infrastructureOutcome.getKind()));
    }
  }

  public AwsLambdaArtifactConfig getAwsLambdaArtifactConfig(ArtifactOutcome artifactOutcome, NGAccess ngAccess) {
    ConnectorInfoDTO connectorDTO;
    if (artifactOutcome instanceof S3ArtifactOutcome) {
      S3ArtifactOutcome s3ArtifactOutcome = (S3ArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(s3ArtifactOutcome.getConnectorRef(), ngAccess);
      return AwsLambdaS3ArtifactConfig.builder()
          .bucketName(s3ArtifactOutcome.getBucketName())
          .region(s3ArtifactOutcome.getRegion())
          .filePath(s3ArtifactOutcome.getFilePath())
          .identifier(s3ArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .primaryArtifact(s3ArtifactOutcome.isPrimaryArtifact())
          .type(s3ArtifactOutcome.getType())
          .build();
    } else if (artifactOutcome instanceof EcrArtifactOutcome) {
      EcrArtifactOutcome ecrArtifactOutcome = (EcrArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(ecrArtifactOutcome.getConnectorRef(), ngAccess);
      return AwsLambdaEcrArtifactConfig.builder()
          .imagePath(ecrArtifactOutcome.getImagePath())
          .identifier(ecrArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .region(ecrArtifactOutcome.getRegion())
          .image(ecrArtifactOutcome.getImage())
          .primaryArtifact(ecrArtifactOutcome.isPrimaryArtifact())
          .tag(ecrArtifactOutcome.getTag())
          .type(ecrArtifactOutcome.getType())
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }
}
