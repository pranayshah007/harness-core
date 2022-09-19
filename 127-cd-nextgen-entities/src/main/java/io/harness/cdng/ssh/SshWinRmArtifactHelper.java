/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusDockerArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
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
public class SshWinRmArtifactHelper {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;

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
      connectorDTO = getConnectorInfoDTO(nexusArtifactOutcome.getConnectorRef(), ngAccess);
      return NexusDockerArtifactDelegateConfig.builder()
          .identifier(nexusArtifactOutcome.getIdentifier())
          .connectorDTO(connectorDTO)
          .artifactPath(nexusArtifactOutcome.getArtifactPath())
          .repositoryFormat(nexusArtifactOutcome.getRepositoryFormat())
          .tag(nexusArtifactOutcome.getTag())
          .image(nexusArtifactOutcome.getImage())
          .encryptedDataDetails(getArtifactEncryptionDataDetails(connectorDTO, ngAccess))
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
}
