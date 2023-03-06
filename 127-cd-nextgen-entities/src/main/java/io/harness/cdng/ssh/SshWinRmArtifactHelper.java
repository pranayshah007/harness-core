/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.ssh.artifact.AcrArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.DockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.EcrArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.GcrArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

@Singleton
@OwnedBy(CDP)
public class SshWinRmArtifactHelper {
  private static final List<String> NEXUS_PACKAGE_SUPPORTED_TYPES = Arrays.asList("maven", "npm", "nuget");

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private FeatureFlagService featureFlagService;

  public SshWinRmArtifactDelegateConfig getArtifactDelegateConfigConfig(
      ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO;
    if (artifactOutcome instanceof ArtifactoryGenericArtifactOutcome) {
      ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
          (ArtifactoryGenericArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(artifactoryGenericArtifactOutcome.getConnectorRef(), ngAccess);
      return ArtifactoryArtifactDelegateConfig.builder()
          .repositoryName(artifactoryGenericArtifactOutcome.getRepositoryName())
          .identifier(artifactoryGenericArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .artifactDirectory(artifactoryGenericArtifactOutcome.getArtifactDirectory())
          .artifactPath(artifactoryGenericArtifactOutcome.getArtifactPath())
          .repositoryFormat(artifactoryGenericArtifactOutcome.getRepositoryFormat())
          .build();
    } else if (artifactOutcome instanceof JenkinsArtifactOutcome) {
      JenkinsArtifactOutcome jenkinsArtifactOutcome = (JenkinsArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(jenkinsArtifactOutcome.getConnectorRef(), ngAccess);
      return JenkinsArtifactDelegateConfig.builder()
          .artifactPath(jenkinsArtifactOutcome.getArtifactPath())
          .jobName(jenkinsArtifactOutcome.getJobName())
          .connectorDTO(connectorDTO)
          .identifier(jenkinsArtifactOutcome.getIdentifier())
          .build(jenkinsArtifactOutcome.getBuild())
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .build();
    } else if (artifactOutcome instanceof ArtifactoryArtifactOutcome) {
      ArtifactoryArtifactOutcome artifactoryArtifactOutcome = (ArtifactoryArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(artifactoryArtifactOutcome.getConnectorRef(), ngAccess);
      return ArtifactoryDockerArtifactDelegateConfig.builder()
          .identifier(artifactoryArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .artifactPath(artifactoryArtifactOutcome.getArtifactPath())
          .repositoryFormat(artifactoryArtifactOutcome.getRepositoryFormat())
          .tag(artifactoryArtifactOutcome.getTag())
          .image(artifactoryArtifactOutcome.getImage())
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .build();
    } else if (artifactOutcome instanceof CustomArtifactOutcome) {
      CustomArtifactOutcome customArtifactOutcome = (CustomArtifactOutcome) artifactOutcome;
      return CustomArtifactDelegateConfig.builder()
          .identifier(customArtifactOutcome.getIdentifier())
          .primaryArtifact(customArtifactOutcome.isPrimaryArtifact())
          .version(customArtifactOutcome.getVersion())
          .metadata(customArtifactOutcome.getMetadata())
          .build();
    } else if (artifactOutcome instanceof NexusArtifactOutcome) {
      NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
      validateArtifactOutcome(nexusArtifactOutcome);

      connectorDTO = getConnectorInfoDTO(nexusArtifactOutcome.getConnectorRef(), ngAccess);
      if (NEXUS_PACKAGE_SUPPORTED_TYPES.contains(nexusArtifactOutcome.getRepositoryFormat())) {
        return NexusArtifactDelegateConfig.builder()
            .identifier(nexusArtifactOutcome.getIdentifier())
            .connectorDTO(connectorDTO)
            .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
            .isCertValidationRequired(false)
            .artifactUrl(nexusArtifactOutcome.getMetadata().get("url"))
            .metadata(nexusArtifactOutcome.getMetadata())
            .repositoryFormat(nexusArtifactOutcome.getRepositoryFormat())
            .build();
      }

      return NexusDockerArtifactDelegateConfig.builder()
          .identifier(nexusArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .artifactPath(nexusArtifactOutcome.getArtifactPath())
          .repositoryFormat(nexusArtifactOutcome.getRepositoryFormat())
          .tag(nexusArtifactOutcome.getTag())
          .image(nexusArtifactOutcome.getImage())
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .build();
    } else if (artifactOutcome instanceof S3ArtifactOutcome) {
      S3ArtifactOutcome s3ArtifactOutcome = (S3ArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(s3ArtifactOutcome.getConnectorRef(), ngAccess);
      AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
      S3FileDetailRequest request = S3FileDetailRequest.builder()
                                        .fileKey(s3ArtifactOutcome.getFilePath())
                                        .bucketName(s3ArtifactOutcome.getBucketName())
                                        .build();
      return AwsS3ArtifactDelegateConfig.builder()
          .identifier(s3ArtifactOutcome.getIdentifier())
          .awsConnector(awsConnectorDTO)
          .encryptionDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .region(s3ArtifactOutcome.getRegion())
          .certValidationRequired(
              featureFlagService.isEnabled(FeatureName.ENABLE_CERT_VALIDATION, ngAccess.getAccountIdentifier()))
          .accountId(ngAccess.getAccountIdentifier())
          .artifactPath(request.getFileKey())
          .bucketName(request.getBucketName())
          .build();
    } else if (artifactOutcome instanceof AzureArtifactsOutcome) {
      AzureArtifactsOutcome azureArtifactsOutcome = (AzureArtifactsOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(azureArtifactsOutcome.getConnectorRef(), ngAccess);
      return AzureArtifactDelegateConfig.builder()
          .identifier(azureArtifactsOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .project(azureArtifactsOutcome.getProject())
          .feed(azureArtifactsOutcome.getFeed())
          .scope(azureArtifactsOutcome.getScope())
          .packageType(azureArtifactsOutcome.getPackageType())
          .packageId(azureArtifactsOutcome.getPackageId())
          .packageName(azureArtifactsOutcome.getPackageName())
          .version(azureArtifactsOutcome.getVersion())
          .versionRegex(azureArtifactsOutcome.getVersionRegex())
          .identifier(azureArtifactsOutcome.getIdentifier())
          .type(azureArtifactsOutcome.getType())
          .image(azureArtifactsOutcome.getImage())
          .imagePullSecret(azureArtifactsOutcome.getImagePullSecret())
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
          .build();
    } else if (artifactOutcome instanceof EcrArtifactOutcome) {
      EcrArtifactOutcome ecrArtifactOutcome = (EcrArtifactOutcome) artifactOutcome;
      return EcrArtifactDelegateConfig.builder()
          .identifier(ecrArtifactOutcome.getIdentifier())
          .primaryArtifact(ecrArtifactOutcome.isPrimaryArtifact())
          .version(ecrArtifactOutcome.getTag())
          .build();
    } else if (artifactOutcome instanceof AcrArtifactOutcome) {
      AcrArtifactOutcome acrArtifactOutcome = (AcrArtifactOutcome) artifactOutcome;
      return AcrArtifactDelegateConfig.builder()
          .identifier(acrArtifactOutcome.getIdentifier())
          .primaryArtifact(acrArtifactOutcome.isPrimaryArtifact())
          .subscription(acrArtifactOutcome.getSubscription())
          .registry(acrArtifactOutcome.getRegistry())
          .image(acrArtifactOutcome.getImage())
          .tag(acrArtifactOutcome.getTag())
          .build();
    } else if (artifactOutcome instanceof GcrArtifactOutcome) {
      GcrArtifactOutcome gcrArtifactOutcome = (GcrArtifactOutcome) artifactOutcome;
      return GcrArtifactDelegateConfig.builder()
          .identifier(gcrArtifactOutcome.getIdentifier())
          .primaryArtifact(gcrArtifactOutcome.isPrimaryArtifact())
          .version(gcrArtifactOutcome.getTag())
          .build();
    } else if (artifactOutcome instanceof DockerArtifactOutcome) {
      DockerArtifactOutcome dockerArtifactOutcome = (DockerArtifactOutcome) artifactOutcome;
      return DockerArtifactDelegateConfig.builder()
          .identifier(dockerArtifactOutcome.getIdentifier())
          .primaryArtifact(dockerArtifactOutcome.isPrimaryArtifact())
          .imagePath(dockerArtifactOutcome.getImagePath())
          .tag(dockerArtifactOutcome.getTag())
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }

  private List<EncryptedDataDetail> getArtifactEncryptionDataDetails(
      @Nonnull ConnectorInfoDTO connectorDTO, @Nonnull NGAccess ngAccess) {
    switch (connectorDTO.getConnectorType()) {
      case ARTIFACTORY:
        ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> artifactoryDecryptableEntities = artifactoryConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(artifactoryDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
        } else {
          return emptyList();
        }
      case JENKINS:
        JenkinsConnectorDTO jenkinsConnectorDTO = (JenkinsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> jenkinsDecryptableEntities = jenkinsConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(jenkinsDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, jenkinsConnectorDTO.getAuth().getCredentials());
        } else {
          return emptyList();
        }
      case NEXUS:
        NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> nexusDecryptableEntities = nexusConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(nexusDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, nexusConnectorDTO.getAuth().getCredentials());
        } else {
          return emptyList();
        }
      case AWS:
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> awsDecryptableEntities = awsConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(awsDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(ngAccess, awsConnectorDTO.getCredential().getConfig());
        } else {
          return emptyList();
        }
      case AZURE_ARTIFACTS:
        AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
            (AzureArtifactsConnectorDTO) connectorDTO.getConnectorConfig();
        List<DecryptableEntity> azureArtifactsDecryptableEntities = azureArtifactsConnectorDTO.getDecryptableEntities();
        if (isNotEmpty(azureArtifactsDecryptableEntities)) {
          return secretManagerClientService.getEncryptionDetails(
              ngAccess, azureArtifactsConnectorDTO.getAuth().getCredentials().getCredentialsSpec());
        } else {
          return emptyList();
        }
      default:
        throw new UnsupportedOperationException(
            format("Unsupported connector type : [%s]", connectorDTO.getConnectorType()));
    }
  }

  private ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorId), USER);
    }
    return connectorDTO.get().getConnector();
  }

  private void validateArtifactOutcome(ArtifactOutcome artifactOutcome) {
    if (artifactOutcome instanceof NexusArtifactOutcome
        && NEXUS_PACKAGE_SUPPORTED_TYPES.contains(((NexusArtifactOutcome) artifactOutcome).getRepositoryFormat())) {
      NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
      if (isEmpty(nexusArtifactOutcome.getMetadata())) {
        throw new InvalidRequestException("Nexus artifact outcome metadata cannot be null or empty");
      }

      if (isEmpty(nexusArtifactOutcome.getMetadata().get("url"))) {
        throw new InvalidRequestException("Nexus artifact outcome metadata url cannot be null or empty");
      }
    }
  }
}
