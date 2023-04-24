/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.infrastructure.InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.GoogleCloudSourceArtifactOutcome;
import io.harness.cdng.artifact.outcome.GoogleCloudStorageArtifactOutcome;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceFetchType;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudSourceArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
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
    ConnectorInfoDTO connectorDTO;
    if (artifactOutcome instanceof GoogleCloudStorageArtifactOutcome) {
      GoogleCloudStorageArtifactOutcome googleCloudStorageArtifactOutcome =
          (GoogleCloudStorageArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(googleCloudStorageArtifactOutcome.getConnectorRef(), ngAccess);
      return GoogleCloudStorageArtifactConfig.builder()
          .project(googleCloudStorageArtifactOutcome.getProject())
          .bucket(googleCloudStorageArtifactOutcome.getBucket())
          .filePath(googleCloudStorageArtifactOutcome.getArtifactPath())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .identifier(googleCloudStorageArtifactOutcome.getIdentifier())
          .build();
    } else if (artifactOutcome instanceof GoogleCloudSourceArtifactOutcome) {
      GoogleCloudSourceArtifactOutcome googleCloudSourceArtifactOutcome =
          (GoogleCloudSourceArtifactOutcome) artifactOutcome;
      connectorDTO = getConnectorInfoDTO(googleCloudSourceArtifactOutcome.getConnectorRef(), ngAccess);
      return GoogleCloudSourceArtifactConfig.builder()
          .project(googleCloudSourceArtifactOutcome.getProject())
          .repository(googleCloudSourceArtifactOutcome.getRepository())
          .sourceDirectory(googleCloudSourceArtifactOutcome.getSourceDirectory())
          .connectorDTO(connectorDTO)
          .encryptedDataDetails(getEncryptionDataDetails(connectorDTO, ngAccess))
          .identifier(googleCloudSourceArtifactOutcome.getIdentifier())
          .branch(googleCloudSourceArtifactOutcome.getBranch())
          .tag(googleCloudSourceArtifactOutcome.getGitTag())
          .commitId(googleCloudSourceArtifactOutcome.getCommitId())
          .googleCloudSourceFetchType(
              GoogleCloudSourceFetchType.BRANCH.getName().equals(googleCloudSourceArtifactOutcome.getFetchType())
                  ? GoogleCloudSourceFetchType.BRANCH
                  : GoogleCloudSourceFetchType.TAG.getName().equals(googleCloudSourceArtifactOutcome.getFetchType())
                  ? GoogleCloudSourceFetchType.TAG
                  : GoogleCloudSourceFetchType.COMMIT)
          .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }
}
