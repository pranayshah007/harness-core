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
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactoryArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaCustomArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaEcrArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaJenkinsArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaNexusArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaS3ArtifactConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(CDP)
public class AwsLambdaEntityHelper {
  private final List<String> NEXUS3_PACKAGE_SUPPORTED_TYPES = Arrays.asList("maven", "npm", "nuget", "raw");
  private final List<String> NEXUS2_PACKAGE_SUPPORTED_TYPES = Arrays.asList("maven", "npm", "nuget");
  private final String CUSTOM_ARTIFACT_KEY = "key";
  private final String CUSTOM_ARTIFACT_BUCKET_NAME = "bucketName";
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
        List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
        if (connectorDTO.getConnectorConfig().getDecryptableEntities() != null) {
          for (DecryptableEntity decryptableEntity : connectorDTO.getConnectorConfig().getDecryptableEntities()) {
            encryptedDataDetails.addAll(secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntity));
          }
        }
        return encryptedDataDetails;
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
    } else if (artifactOutcome instanceof NexusArtifactOutcome) {
      NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(nexusArtifactOutcome.getConnectorRef(), ngAccess);

      if (isNexusRepoTypeSupported(connectorDTO, nexusArtifactOutcome.getRepositoryFormat())) {
        return AwsLambdaNexusArtifactConfig.builder()
            .identifier(nexusArtifactOutcome.getIdentifier())
            .connectorConfig(connectorDTO.getConnectorConfig())
            .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
            .isCertValidationRequired(false)
            .artifactUrl(nexusArtifactOutcome.getMetadata().get("url"))
            .metadata(nexusArtifactOutcome.getMetadata())
            .repositoryFormat(nexusArtifactOutcome.getRepositoryFormat())
            .build();
      } else {
        throw new UnsupportedOperationException(
            format("Unsupported Nexus Repository Format: [%s]", nexusArtifactOutcome.getRepositoryFormat()));
      }
    } else if (artifactOutcome instanceof ArtifactoryGenericArtifactOutcome) {
      ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
          (ArtifactoryGenericArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(artifactoryGenericArtifactOutcome.getConnectorRef(), ngAccess);
      return AwsLambdaArtifactoryArtifactConfig.builder()
          .repository(artifactoryGenericArtifactOutcome.getRepositoryName())
          .identifier(artifactoryGenericArtifactOutcome.getIdentifier())
          .connectorConfig(connectorDTO.getConnectorConfig())
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .artifactPaths(
              new ArrayList<>(Collections.singletonList(artifactoryGenericArtifactOutcome.getArtifactPath())))
          .repositoryFormat(artifactoryGenericArtifactOutcome.getRepositoryFormat())
          .build();
    } else if (artifactOutcome instanceof JenkinsArtifactOutcome) {
      JenkinsArtifactOutcome jenkinsArtifactOutcome = (JenkinsArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(jenkinsArtifactOutcome.getConnectorRef(), ngAccess);
      return AwsLambdaJenkinsArtifactConfig.builder()
          .artifactPath(jenkinsArtifactOutcome.getArtifactPath())
          .jobName(jenkinsArtifactOutcome.getJobName())
          .connectorConfig(connectorDTO.getConnectorConfig())
          .identifier(jenkinsArtifactOutcome.getIdentifier())
          .build(jenkinsArtifactOutcome.getBuild())
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .build();
    } else if (artifactOutcome instanceof CustomArtifactOutcome) {
      CustomArtifactOutcome customArtifactOutcome = (CustomArtifactOutcome) artifactOutcome;
      Map<String, String> metadata = customArtifactOutcome.getMetadata();
      validateCustomArtifactMetaData(metadata);
      return AwsLambdaCustomArtifactConfig.builder()
          .identifier(customArtifactOutcome.getIdentifier())
          .primaryArtifact(customArtifactOutcome.isPrimaryArtifact())
          .version(customArtifactOutcome.getVersion())
          .metadata(metadata)
          .bucketName(metadata.get(CUSTOM_ARTIFACT_BUCKET_NAME))
          .filePath(metadata.get(CUSTOM_ARTIFACT_KEY))
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }

  private boolean isNexusRepoTypeSupported(ConnectorInfoDTO connectorDTO, String repoType) {
    if (isNexusTwo(connectorDTO)) {
      return NEXUS2_PACKAGE_SUPPORTED_TYPES.contains(repoType);
    } else {
      return NEXUS3_PACKAGE_SUPPORTED_TYPES.contains(repoType);
    }
  }

  private boolean isNexusTwo(ConnectorInfoDTO connectorDTO) {
    NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
    return nexusConnectorDTO.getVersion() == null || nexusConnectorDTO.getVersion().equalsIgnoreCase("2.x");
  }

  private void validateCustomArtifactMetaData(Map<String, String> metadata) {
    if (!(metadata.get(CUSTOM_ARTIFACT_BUCKET_NAME) != null && metadata.get(CUSTOM_ARTIFACT_KEY) != null)) {
      throw new UnsupportedOperationException(
          format("Invalid Custom Artifact Configuration\n Check if Additional Attributes [%s], [%s] are configured in "
                  + "Custom Artifact",
              CUSTOM_ARTIFACT_BUCKET_NAME, CUSTOM_ARTIFACT_KEY));
    }
  }
}
