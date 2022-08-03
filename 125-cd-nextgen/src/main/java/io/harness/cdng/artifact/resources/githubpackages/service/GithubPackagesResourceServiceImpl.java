/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.graphql.datafetcher.connector.types.GitConnector;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.security.authentication.oauth.GithubConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GithubPackagesResourceServiceImpl implements GithubPackagesResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject
  public GithubPackagesResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public GithubPackagesResponseDTO getPackageDetails(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public List<BuildDetails> getVersionsOfPackage(IdentifierRef connectorRef, String packageName, String versionRegex,
      String accountId, String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isEmpty(versionRegex)) {
      return null;
    }

    GithubConnectorDTO githubConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(githubConnector, baseNGAccess);

    return null;
  }

  private GithubConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAGithubConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (GithubConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAGithubConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.GITHUB == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull GithubConnectorDTO githubConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (githubConnectorDTO.getAuthentication() != null
        && githubConnectorDTO.getAuthentication().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, githubConnectorDTO.getAuthentication().getCredentials());
    }
    return new ArrayList<>();
  }
}
