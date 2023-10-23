/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import io.harness.azure.AzureEnvironmentType;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorInfoDTO.ConnectorInfoDTOBuilder;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsAuthType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.spec.server.connector.v1.model.AppdynamicsClientIdConnectorSpec;
import io.harness.spec.server.connector.v1.model.AppdynamicsConnectorSpec;
import io.harness.spec.server.connector.v1.model.ArtifactoryAnonymousConnectorSpec;
import io.harness.spec.server.connector.v1.model.ArtifactoryConnectorSpec;
import io.harness.spec.server.connector.v1.model.ArtifactoryEncryptedConnectorSpec;
import io.harness.spec.server.connector.v1.model.AzureClientCertificateConnectorSpec;
import io.harness.spec.server.connector.v1.model.AzureClientSecretKeyConnectorSpec;
import io.harness.spec.server.connector.v1.model.AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec;
import io.harness.spec.server.connector.v1.model.AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec;
import io.harness.spec.server.connector.v1.model.Connector;
import io.harness.spec.server.connector.v1.model.ConnectorConnectivityDetail;
import io.harness.spec.server.connector.v1.model.ConnectorRequest;
import io.harness.spec.server.connector.v1.model.ConnectorResponse;
import io.harness.spec.server.connector.v1.model.ConnectorSpec;
import io.harness.spec.server.connector.v1.model.ConnectorTestConnectionErrorDetail;
import io.harness.spec.server.connector.v1.model.ConnectorTestConnectionResponse;
import io.harness.spec.server.connector.v1.model.GitHttpConnectorSpec;
import io.harness.spec.server.connector.v1.model.GitHttpEncryptedConnectorSpec;
import io.harness.spec.server.connector.v1.model.GitSshConnectorSpec;

import com.google.api.client.util.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.apache.commons.collections.CollectionUtils;

public class ConnectorApiUtils {
  public static final String CONNECTOR_TYPE_S_IS_NOT_SUPPORTED = "Connector type [%s] is not supported";
  private final Validator validator;

  @Inject
  public ConnectorApiUtils(Validator validator) {
    this.validator = validator;
  }

  public List<ConnectorResponse> toConnectorResponses(List<ConnectorResponseDTO> connectorResponseDTOs) {
    if (CollectionUtils.isEmpty(connectorResponseDTOs)) {
      return Collections.emptyList();
    }
    return connectorResponseDTOs.stream().map(this::toConnectorResponse).collect(Collectors.toList());
  }

  public ConnectorResponse toConnectorResponse(ConnectorResponseDTO connectorResponseDTO) {
    ConnectorResponse connectorResponse = new ConnectorResponse();

    ConnectorInfoDTO connectorInfoDTO = connectorResponseDTO.getConnector();
    Connector connector = new Connector();
    connector.setName(connectorInfoDTO.getName());
    connector.setIdentifier(connectorInfoDTO.getIdentifier());
    connector.setOrg(connectorInfoDTO.getOrgIdentifier());
    connector.setProject(connectorInfoDTO.getProjectIdentifier());
    connector.setDescription(connectorInfoDTO.getDescription());
    connector.setTags(connectorInfoDTO.getTags());

    connector.setSpec(getConnectorSpec(connectorInfoDTO));

    connectorResponse.setConnector(connector);
    connectorResponse.created(connectorResponseDTO.getCreatedAt());
    connectorResponse.updated(connectorResponseDTO.getLastModifiedAt());
    connectorResponse.setGovernanceMetadata(connectorResponseDTO.getGovernanceMetadata());
    connectorResponse.setHarnessManaged(connectorResponseDTO.isHarnessManaged());

    if (connectorResponseDTO.getStatus() != null) {
      ConnectorConnectivityDetails connectivityDetails = connectorResponseDTO.getStatus();
      ConnectorConnectivityDetail status = new ConnectorConnectivityDetail();
      status.setStatus(ConnectorConnectivityDetail.StatusEnum.fromValue(connectivityDetails.getStatus().name()));
      status.setErrorSummary(connectivityDetails.getErrorSummary());
      status.setErrors(toConnectorTestConnectionErrorDetails(connectivityDetails.getErrors()));
      status.setConnectedAt(connectivityDetails.getLastConnectedAt());
      status.setTestedAt(connectivityDetails.getTestedAt());
      connectorResponse.setStatus(status);
    }
    return connectorResponse;
  }

  public ConnectorSpec getConnectorSpec(ConnectorInfoDTO connectorInfoDTO) {
    switch (connectorInfoDTO.getConnectorType()) {
      case GIT:
        GitConfigDTO gitConfigDTO = (GitConfigDTO) connectorInfoDTO.getConnectorConfig();
        if (GitAuthType.HTTP.equals(gitConfigDTO.getGitAuthType())) {
          GitHTTPAuthenticationDTO gitAuth = (GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth();
          if (gitAuth.getUsername() != null) {
            return getGitHttpConnectorSpec(gitConfigDTO, gitAuth);
          } else {
            return getGitHttpEncryptedConnectorSpec(gitConfigDTO, gitAuth);
          }
        } else if (GitAuthType.SSH.equals(gitConfigDTO.getGitAuthType())) {
          return getGitSshConnectorSpec(gitConfigDTO);
        }
        throw new InvalidRequestException(
            String.format("Git auth type [%s] is not supported", gitConfigDTO.getGitAuthType()));
      case ARTIFACTORY:
        ArtifactoryConnectorDTO artifactoryConnectorDTO =
            (ArtifactoryConnectorDTO) connectorInfoDTO.getConnectorConfig();
        if (ArtifactoryAuthType.USER_PASSWORD.equals(artifactoryConnectorDTO.getAuth().getAuthType())) {
          ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
              (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnectorDTO.getAuth().getCredentials();
          if (artifactoryUsernamePasswordAuthDTO.getUsername() != null) {
            return getArtifactoryConnectorSpec(artifactoryConnectorDTO, artifactoryUsernamePasswordAuthDTO);
          } else {
            return getArtifactoryEncryptedConnectorSpec(artifactoryConnectorDTO, artifactoryUsernamePasswordAuthDTO);
          }
        } else if (ArtifactoryAuthType.ANONYMOUS.equals(artifactoryConnectorDTO.getAuth().getAuthType())) {
          return getArtifactoryAnonymousConnectorSpec(artifactoryConnectorDTO);
        }
        throw new InvalidRequestException(String.format(
            "Artifactory auth type [%s] is not supported", artifactoryConnectorDTO.getAuth().getAuthType()));
      case APP_DYNAMICS:
        AppDynamicsConnectorDTO appDynamicsConnectorDTO =
            (AppDynamicsConnectorDTO) connectorInfoDTO.getConnectorConfig();
        if (AppDynamicsAuthType.USERNAME_PASSWORD.equals(appDynamicsConnectorDTO.getAuthType())) {
          return getAppdynamicsConnectorSpec(appDynamicsConnectorDTO);
        }
        if (AppDynamicsAuthType.API_CLIENT_TOKEN.equals(appDynamicsConnectorDTO.getAuthType())) {
          return getAppdynamicsClientIdConnectorSpec(appDynamicsConnectorDTO);
        }
        throw new InvalidRequestException(
            String.format("Appdynamics auth type [%s] is not supported", appDynamicsConnectorDTO.getAuthType()));
      case AZURE:
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorInfoDTO.getConnectorConfig();
        AzureCredentialDTO credential = azureConnectorDTO.getCredential();
        if (AzureCredentialType.MANUAL_CREDENTIALS.equals(credential.getAzureCredentialType())) {
          return getAzureManualCredentialsConnectorDTOConnectorSpec(azureConnectorDTO);
        }
        if (AzureCredentialType.INHERIT_FROM_DELEGATE.equals(credential.getAzureCredentialType())) {
          return getAzureInheritFromDelegateConnectorDTOConnectorSpec(azureConnectorDTO);
        }
        throw new InvalidRequestException(
            String.format("AzureConnector credential type [%s] is not supported", credential.getAzureCredentialType()));
      default:
        throw new InvalidRequestException(
            String.format(CONNECTOR_TYPE_S_IS_NOT_SUPPORTED, connectorInfoDTO.getConnectorType()));
    }
  }

  private ConnectorSpec getAzureInheritFromDelegateConnectorDTOConnectorSpec(AzureConnectorDTO azureConnectorDTO) {
    AzureInheritFromDelegateDetailsDTO config =
        (AzureInheritFromDelegateDetailsDTO) azureConnectorDTO.getCredential().getConfig();

    if (config.getAuthDTO() instanceof AzureMSIAuthSADTO) {
      AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec spec =
          new AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec();

      spec.setType(ConnectorSpec.TypeEnum.AZUREINHERITFROMDELEGATESYSTEMASSIGNEDMANAGEDIDENTITY);
      spec.setDelegateSelectors(Lists.newArrayList(azureConnectorDTO.getDelegateSelectors()));
      spec.setExecuteOnDelegate(azureConnectorDTO.getExecuteOnDelegate());
      spec.setAzureEnvironmentType(
          AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec.AzureEnvironmentTypeEnum.valueOf(
              azureConnectorDTO.getAzureEnvironmentType().name()));
      return spec;
    }
    if (config.getAuthDTO() instanceof AzureMSIAuthUADTO) {
      AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec spec =
          new AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec();

      spec.setType(ConnectorSpec.TypeEnum.AZUREINHERITFROMDELEGATEUSERASSIGNEDMANAGEDIDENTITY);
      spec.setDelegateSelectors(Lists.newArrayList(azureConnectorDTO.getDelegateSelectors()));
      spec.setExecuteOnDelegate(azureConnectorDTO.getExecuteOnDelegate());
      spec.setAzureEnvironmentType(
          AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec.AzureEnvironmentTypeEnum.valueOf(
              azureConnectorDTO.getAzureEnvironmentType().name()));

      AzureUserAssignedMSIAuthDTO credentials = ((AzureMSIAuthUADTO) config.getAuthDTO()).getCredentials();
      spec.setClientId(credentials.getClientId());
      return spec;
    }
    throw new InvalidRequestException(String.format(
        "Azure inherit from delegate managed entity type [%s] is not supported", config.getAuthDTO().getClass()));
  }

  private ConnectorSpec getAzureManualCredentialsConnectorDTOConnectorSpec(AzureConnectorDTO azureConnectorDTO) {
    AzureManualDetailsDTO config = (AzureManualDetailsDTO) azureConnectorDTO.getCredential().getConfig();

    if (AzureSecretType.SECRET_KEY.equals(config.getAuthDTO().getAzureSecretType())) {
      AzureClientSecretKeyConnectorSpec azureClientSecretKeyConnectorSpec = new AzureClientSecretKeyConnectorSpec();

      azureClientSecretKeyConnectorSpec.setType(ConnectorSpec.TypeEnum.AZURECLIENTSECRETKEY);
      azureClientSecretKeyConnectorSpec.setDelegateSelectors(
          Lists.newArrayList(azureConnectorDTO.getDelegateSelectors()));
      azureClientSecretKeyConnectorSpec.setExecuteOnDelegate(azureConnectorDTO.getExecuteOnDelegate());
      azureClientSecretKeyConnectorSpec.setAzureEnvironmentType(
          AzureClientSecretKeyConnectorSpec.AzureEnvironmentTypeEnum.valueOf(
              azureConnectorDTO.getAzureEnvironmentType().name()));

      azureClientSecretKeyConnectorSpec.setApplicationId(config.getClientId());
      azureClientSecretKeyConnectorSpec.setTenantId(config.getTenantId());
      AzureClientSecretKeyDTO azureClientSecretKeyDTO = (AzureClientSecretKeyDTO) config.getAuthDTO().getCredentials();
      azureClientSecretKeyConnectorSpec.setSecretRef(azureClientSecretKeyDTO.getSecretKey().toSecretRefStringValue());
      return azureClientSecretKeyConnectorSpec;
    }
    if (AzureSecretType.KEY_CERT.equals(config.getAuthDTO().getAzureSecretType())) {
      AzureClientCertificateConnectorSpec azureClientCertificateConnectorSpec =
          new AzureClientCertificateConnectorSpec();

      azureClientCertificateConnectorSpec.setType(ConnectorSpec.TypeEnum.AZURECLIENTCERTIFICATE);
      azureClientCertificateConnectorSpec.setDelegateSelectors(
          Lists.newArrayList(azureConnectorDTO.getDelegateSelectors()));
      azureClientCertificateConnectorSpec.setExecuteOnDelegate(azureConnectorDTO.getExecuteOnDelegate());
      azureClientCertificateConnectorSpec.setAzureEnvironmentType(
          AzureClientCertificateConnectorSpec.AzureEnvironmentTypeEnum.valueOf(
              azureConnectorDTO.getAzureEnvironmentType().name()));

      azureClientCertificateConnectorSpec.setApplicationId(config.getClientId());
      azureClientCertificateConnectorSpec.setTenantId(config.getTenantId());
      AzureClientKeyCertDTO azureClientKeyCertDTO = (AzureClientKeyCertDTO) config.getAuthDTO().getCredentials();
      azureClientCertificateConnectorSpec.certificateRef(
          azureClientKeyCertDTO.getClientCertRef().toSecretRefStringValue());
      return azureClientCertificateConnectorSpec;
    }
    throw new InvalidRequestException(
        String.format("Azure secret type [%s] is not supported", config.getAuthDTO().getAzureSecretType()));
  }

  private AppdynamicsClientIdConnectorSpec getAppdynamicsClientIdConnectorSpec(
      AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    AppdynamicsClientIdConnectorSpec appdynamicsClientIdConnectorSpec = new AppdynamicsClientIdConnectorSpec();
    appdynamicsClientIdConnectorSpec.setType(ConnectorSpec.TypeEnum.APPDYNAMICSCLIENTID);
    appdynamicsClientIdConnectorSpec.setAccountName(appDynamicsConnectorDTO.getAccountname());
    appdynamicsClientIdConnectorSpec.setControllerUrl(appDynamicsConnectorDTO.getControllerUrl());
    appdynamicsClientIdConnectorSpec.setClientId(appDynamicsConnectorDTO.getClientId());
    appdynamicsClientIdConnectorSpec.setClientSecretRef(
        appDynamicsConnectorDTO.getClientSecretRef().toSecretRefStringValue());
    appdynamicsClientIdConnectorSpec.setDelegateSelectors(
        Lists.newArrayList(appDynamicsConnectorDTO.getDelegateSelectors()));

    return appdynamicsClientIdConnectorSpec;
  }

  private AppdynamicsConnectorSpec getAppdynamicsConnectorSpec(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    AppdynamicsConnectorSpec appdynamicsConnectorSpec = new AppdynamicsConnectorSpec();
    appdynamicsConnectorSpec.setType(ConnectorSpec.TypeEnum.APPDYNAMICS);
    appdynamicsConnectorSpec.setAccountName(appDynamicsConnectorDTO.getAccountname());
    appdynamicsConnectorSpec.setControllerUrl(appDynamicsConnectorDTO.getControllerUrl());
    appdynamicsConnectorSpec.setUsername(appDynamicsConnectorDTO.getUsername());
    appdynamicsConnectorSpec.setPasswordRef(appDynamicsConnectorDTO.getPasswordRef().toSecretRefStringValue());
    appdynamicsConnectorSpec.setDelegateSelectors(Lists.newArrayList(appDynamicsConnectorDTO.getDelegateSelectors()));

    return appdynamicsConnectorSpec;
  }

  private ArtifactoryAnonymousConnectorSpec getArtifactoryAnonymousConnectorSpec(
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    ArtifactoryAnonymousConnectorSpec artifactoryAnonymousConnectorSpec = new ArtifactoryAnonymousConnectorSpec();
    artifactoryAnonymousConnectorSpec.setType(ConnectorSpec.TypeEnum.ARTIFACTORYANONYMOUS);
    artifactoryAnonymousConnectorSpec.setUrl(artifactoryConnectorDTO.getArtifactoryServerUrl());
    artifactoryAnonymousConnectorSpec.setDelegateSelectors(
        Lists.newArrayList(artifactoryConnectorDTO.getDelegateSelectors()));
    artifactoryAnonymousConnectorSpec.setExecuteOnDelegate(artifactoryConnectorDTO.getExecuteOnDelegate());
    return artifactoryAnonymousConnectorSpec;
  }

  private ArtifactoryEncryptedConnectorSpec getArtifactoryEncryptedConnectorSpec(
      ArtifactoryConnectorDTO artifactoryConnectorDTO,
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO) {
    ArtifactoryEncryptedConnectorSpec artifactoryEncryptedConnectorSpec = new ArtifactoryEncryptedConnectorSpec();
    artifactoryEncryptedConnectorSpec.setType(ConnectorSpec.TypeEnum.ARTIFACTORYENCRYPTED);
    artifactoryEncryptedConnectorSpec.setUrl(artifactoryConnectorDTO.getArtifactoryServerUrl());
    artifactoryEncryptedConnectorSpec.setUsernameRef(
        artifactoryUsernamePasswordAuthDTO.getUsernameRef().toSecretRefStringValue());
    artifactoryEncryptedConnectorSpec.setPasswordRef(
        artifactoryUsernamePasswordAuthDTO.getPasswordRef().toSecretRefStringValue());
    artifactoryEncryptedConnectorSpec.setDelegateSelectors(
        Lists.newArrayList(artifactoryConnectorDTO.getDelegateSelectors()));
    artifactoryEncryptedConnectorSpec.setExecuteOnDelegate(artifactoryConnectorDTO.getExecuteOnDelegate());
    return artifactoryEncryptedConnectorSpec;
  }

  private ArtifactoryConnectorSpec getArtifactoryConnectorSpec(ArtifactoryConnectorDTO artifactoryConnectorDTO,
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO) {
    ArtifactoryConnectorSpec artifactoryConnectorSpec = new ArtifactoryConnectorSpec();
    artifactoryConnectorSpec.setType(ConnectorSpec.TypeEnum.ARTIFACTORY);
    artifactoryConnectorSpec.setUrl(artifactoryConnectorDTO.getArtifactoryServerUrl());
    artifactoryConnectorSpec.setUsername(artifactoryUsernamePasswordAuthDTO.getUsername());
    artifactoryConnectorSpec.setPasswordRef(
        artifactoryUsernamePasswordAuthDTO.getPasswordRef().toSecretRefStringValue());
    artifactoryConnectorSpec.setDelegateSelectors(Lists.newArrayList(artifactoryConnectorDTO.getDelegateSelectors()));
    artifactoryConnectorSpec.setExecuteOnDelegate(artifactoryConnectorDTO.getExecuteOnDelegate());
    return artifactoryConnectorSpec;
  }

  private GitSshConnectorSpec getGitSshConnectorSpec(GitConfigDTO gitConfigDTO) {
    GitSshConnectorSpec gitSshConnectorSpec = new GitSshConnectorSpec();
    gitSshConnectorSpec.setType(ConnectorSpec.TypeEnum.GITSSH);
    gitSshConnectorSpec.setConnectionType(
        GitSshConnectorSpec.ConnectionTypeEnum.valueOf(gitConfigDTO.getGitConnectionType().toString()));
    gitSshConnectorSpec.setUrl(gitConfigDTO.getUrl());
    gitSshConnectorSpec.setBranch(gitConfigDTO.getBranchName());
    GitSSHAuthenticationDTO gitAuth = (GitSSHAuthenticationDTO) gitConfigDTO.getGitAuth();
    gitSshConnectorSpec.setSshKeyRef(gitAuth.getEncryptedSshKey().toSecretRefStringValue());
    gitSshConnectorSpec.setValidationRepo(gitConfigDTO.getValidationRepo());
    gitSshConnectorSpec.setDelegateSelectors(Lists.newArrayList(gitConfigDTO.getDelegateSelectors()));
    gitSshConnectorSpec.setExecuteOnDelegate(gitConfigDTO.getExecuteOnDelegate());
    return gitSshConnectorSpec;
  }

  private GitHttpEncryptedConnectorSpec getGitHttpEncryptedConnectorSpec(
      GitConfigDTO gitConfigDTO, GitHTTPAuthenticationDTO gitAuth) {
    GitHttpEncryptedConnectorSpec gitHttpEncryptedConnectorSpec = new GitHttpEncryptedConnectorSpec();
    gitHttpEncryptedConnectorSpec.setType(ConnectorSpec.TypeEnum.GITHTTPENCRYPTED);
    gitHttpEncryptedConnectorSpec.setConnectionType(
        GitHttpEncryptedConnectorSpec.ConnectionTypeEnum.valueOf(gitConfigDTO.getGitConnectionType().toString()));
    gitHttpEncryptedConnectorSpec.setUrl(gitConfigDTO.getUrl());
    gitHttpEncryptedConnectorSpec.setBranch(gitConfigDTO.getBranchName());
    gitHttpEncryptedConnectorSpec.setUsernameRef(gitAuth.getUsernameRef().toSecretRefStringValue());
    gitHttpEncryptedConnectorSpec.setPasswordRef(gitAuth.getPasswordRef().toSecretRefStringValue());
    gitHttpEncryptedConnectorSpec.setValidationRepo(gitConfigDTO.getValidationRepo());
    gitHttpEncryptedConnectorSpec.setDelegateSelectors(Lists.newArrayList(gitConfigDTO.getDelegateSelectors()));
    gitHttpEncryptedConnectorSpec.setExecuteOnDelegate(gitConfigDTO.getExecuteOnDelegate());
    return gitHttpEncryptedConnectorSpec;
  }

  private GitHttpConnectorSpec getGitHttpConnectorSpec(GitConfigDTO gitConfigDTO, GitHTTPAuthenticationDTO gitAuth) {
    GitHttpConnectorSpec gitHttpConnectorSpec = new GitHttpConnectorSpec();
    gitHttpConnectorSpec.setType(ConnectorSpec.TypeEnum.GITHTTP);
    gitHttpConnectorSpec.setConnectionType(
        GitHttpConnectorSpec.ConnectionTypeEnum.valueOf(gitConfigDTO.getGitConnectionType().toString()));
    gitHttpConnectorSpec.setUrl(gitConfigDTO.getUrl());
    gitHttpConnectorSpec.setBranch(gitConfigDTO.getBranchName());
    gitHttpConnectorSpec.setUsername(gitAuth.getUsername());
    gitHttpConnectorSpec.setPasswordRef(gitAuth.getPasswordRef().toSecretRefStringValue());
    gitHttpConnectorSpec.setValidationRepo(gitConfigDTO.getValidationRepo());
    gitHttpConnectorSpec.setDelegateSelectors(
        gitConfigDTO.getDelegateSelectors() != null ? Lists.newArrayList(gitConfigDTO.getDelegateSelectors()) : null);
    gitHttpConnectorSpec.setExecuteOnDelegate(gitConfigDTO.getExecuteOnDelegate());
    return gitHttpConnectorSpec;
  }

  public ConnectorDTO toConnectorDTO(ConnectorRequest request) {
    Connector connector = request.getConnector();
    ConnectorInfoDTO connectorInfo = toConnectorInfoDTO(connector);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    // add javax validation here on ConnectorDisconnectHandler dto
    Set<ConstraintViolation<ConnectorDTO>> violations = validator.validate(connectorDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return connectorDTO;
  }

  public ConnectorInfoDTO toConnectorInfoDTO(Connector connector) {
    ConnectorInfoDTOBuilder connectorInfoDTOBuilder = ConnectorInfoDTO.builder()
                                                          .name(connector.getName())
                                                          .identifier(connector.getIdentifier())
                                                          .description(connector.getDescription())
                                                          .orgIdentifier(connector.getOrg())
                                                          .projectIdentifier(connector.getProject())
                                                          .tags(connector.getTags());

    ConnectorSpec.TypeEnum type = connector.getSpec().getType();

    switch (type) {
      case GITHTTP:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.GIT)
            .connectorConfig(getGitHttpConnectorConfigDTO(connector.getSpec()))
            .build();
      case GITHTTPENCRYPTED:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.GIT)
            .connectorConfig(getGitHttpEncryptedConnectorConfigDTO(connector.getSpec()))
            .build();
      case GITSSH:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.GIT)
            .connectorConfig(getGitSshConnectorConfigDTO(connector.getSpec()))
            .build();
      case ARTIFACTORYANONYMOUS:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.ARTIFACTORY)
            .connectorConfig(getArtifactoryAnonymousConnectorConfigDTO(connector.getSpec()))
            .build();
      case ARTIFACTORY:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.ARTIFACTORY)
            .connectorConfig(getArtifactoryConnectorConfigDTO(connector.getSpec()))
            .build();
      case ARTIFACTORYENCRYPTED:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.ARTIFACTORY)
            .connectorConfig(getArtifactoryEncryptedConnectorConfigDTO(connector.getSpec()))
            .build();
      case APPDYNAMICS:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.APP_DYNAMICS)
            .connectorConfig(getAppdynamicsConnectorConfigDTO(connector.getSpec()))
            .build();
      case APPDYNAMICSCLIENTID:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.APP_DYNAMICS)
            .connectorConfig(getAppdynamicsClientIdConnectorConfigDTO(connector.getSpec()))
            .build();
      case AZURECLIENTSECRETKEY:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.AZURE)
            .connectorConfig(getAzureClientSecretKeyConnectorConfigDTO(connector.getSpec()))
            .build();
      case AZURECLIENTCERTIFICATE:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.AZURE)
            .connectorConfig(getAzureClientCertificateConnectorConfigDTO(connector.getSpec()))
            .build();
      case AZUREINHERITFROMDELEGATEUSERASSIGNEDMANAGEDIDENTITY:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.AZURE)
            .connectorConfig(
                getAzureInheritFromDelegateUserAssignedManagedIdentityConnectorConfigDTO(connector.getSpec()))
            .build();
      case AZUREINHERITFROMDELEGATESYSTEMASSIGNEDMANAGEDIDENTITY:
        return connectorInfoDTOBuilder.connectorType(ConnectorType.AZURE)
            .connectorConfig(
                getAzureInheritFromDelegateSystemAssignedManagedIdentityConnectorConfigDTO(connector.getSpec()))
            .build();
      default:
        throw new InvalidRequestException(String.format(CONNECTOR_TYPE_S_IS_NOT_SUPPORTED, type.value()));
    }
  }

  private ConnectorConfigDTO getAzureInheritFromDelegateUserAssignedManagedIdentityConnectorConfigDTO(
      ConnectorSpec connectorSpec) {
    AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec spec =
        (AzureInheritFromDelegateUserAssignedManagedIdentityConnectorSpec) connectorSpec;

    AzureUserAssignedMSIAuthDTO credentials =
        AzureUserAssignedMSIAuthDTO.builder().clientId(spec.getClientId()).build();
    AzureMSIAuthUADTO authDTO = AzureMSIAuthUADTO.builder()
                                    .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                                    .credentials(credentials)
                                    .build();
    AzureInheritFromDelegateDetailsDTO config = AzureInheritFromDelegateDetailsDTO.builder().authDTO(authDTO).build();
    AzureCredentialDTO credential = AzureCredentialDTO.builder()
                                        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                                        .config(config)
                                        .build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.valueOf(spec.getAzureEnvironmentType().value()))
        .delegateSelectors(Sets.newHashSet(spec.getDelegateSelectors()))
        .executeOnDelegate(spec.isExecuteOnDelegate())
        .credential(credential)
        .build();
  }

  private ConnectorConfigDTO getAzureInheritFromDelegateSystemAssignedManagedIdentityConnectorConfigDTO(
      ConnectorSpec connectorSpec) {
    AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec spec =
        (AzureInheritFromDelegateSystemAssignedManagedIdentityConnectorSpec) connectorSpec;

    AzureMSIAuthSADTO authDTO = AzureMSIAuthSADTO.builder()
                                    .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                                    .build();
    AzureInheritFromDelegateDetailsDTO config = AzureInheritFromDelegateDetailsDTO.builder().authDTO(authDTO).build();
    AzureCredentialDTO credential = AzureCredentialDTO.builder()
                                        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                                        .config(config)
                                        .build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.valueOf(spec.getAzureEnvironmentType().value()))
        .delegateSelectors(Sets.newHashSet(spec.getDelegateSelectors()))
        .executeOnDelegate(spec.isExecuteOnDelegate())
        .credential(credential)
        .build();
  }

  private ConnectorConfigDTO getAppdynamicsConnectorConfigDTO(ConnectorSpec connector) {
    AppdynamicsConnectorSpec appdynamicsConnectorSpec = (AppdynamicsConnectorSpec) connector;

    return AppDynamicsConnectorDTO.builder()
        .authType(AppDynamicsAuthType.USERNAME_PASSWORD)
        .accountname(appdynamicsConnectorSpec.getAccountName())
        .controllerUrl(appdynamicsConnectorSpec.getControllerUrl())
        .username(appdynamicsConnectorSpec.getUsername())
        .passwordRef(appdynamicsConnectorSpec.getPasswordRef() != null
                ? new SecretRefData(appdynamicsConnectorSpec.getPasswordRef())
                : null)
        .delegateSelectors(Sets.newHashSet(appdynamicsConnectorSpec.getDelegateSelectors()))
        .build();
  }

  private ConnectorConfigDTO getAppdynamicsClientIdConnectorConfigDTO(ConnectorSpec connector) {
    AppdynamicsClientIdConnectorSpec appdynamicsClientIdConnectorSpec = (AppdynamicsClientIdConnectorSpec) connector;

    return AppDynamicsConnectorDTO.builder()
        .authType(AppDynamicsAuthType.API_CLIENT_TOKEN)
        .accountname(appdynamicsClientIdConnectorSpec.getAccountName())
        .controllerUrl(appdynamicsClientIdConnectorSpec.getControllerUrl())
        .clientId(appdynamicsClientIdConnectorSpec.getClientId())
        .clientSecretRef(appdynamicsClientIdConnectorSpec.getClientSecretRef() != null
                ? new SecretRefData(appdynamicsClientIdConnectorSpec.getClientSecretRef())
                : null)
        .delegateSelectors(Sets.newHashSet(appdynamicsClientIdConnectorSpec.getDelegateSelectors()))
        .build();
  }

  private ConnectorConfigDTO getAzureClientSecretKeyConnectorConfigDTO(ConnectorSpec connector) {
    AzureClientSecretKeyConnectorSpec azureClientSecretKeyConnectorSpec = (AzureClientSecretKeyConnectorSpec) connector;

    AzureAuthCredentialDTO credAuthCredentials =
        AzureClientSecretKeyDTO.builder()
            .secretKey(new SecretRefData(azureClientSecretKeyConnectorSpec.getSecretRef()))
            .build();
    AzureAuthDTO authDTO =
        AzureAuthDTO.builder().azureSecretType(AzureSecretType.SECRET_KEY).credentials(credAuthCredentials).build();
    AzureManualDetailsDTO config = AzureManualDetailsDTO.builder()
                                       .clientId(azureClientSecretKeyConnectorSpec.getApplicationId())
                                       .tenantId(azureClientSecretKeyConnectorSpec.getTenantId())
                                       .authDTO(authDTO)
                                       .build();
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder().azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS).config(config).build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(
            AzureEnvironmentType.valueOf(azureClientSecretKeyConnectorSpec.getAzureEnvironmentType().value()))
        .delegateSelectors(Sets.newHashSet(azureClientSecretKeyConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(azureClientSecretKeyConnectorSpec.isExecuteOnDelegate())
        .credential(credential)
        .build();
  }

  private ConnectorConfigDTO getAzureClientCertificateConnectorConfigDTO(ConnectorSpec connector) {
    AzureClientCertificateConnectorSpec azureClientCertificateConnectorSpec =
        (AzureClientCertificateConnectorSpec) connector;

    AzureAuthCredentialDTO credAuthCredentials =
        AzureClientKeyCertDTO.builder()
            .clientCertRef(new SecretRefData(azureClientCertificateConnectorSpec.getCertificateRef()))
            .build();
    AzureAuthDTO authDTO =
        AzureAuthDTO.builder().azureSecretType(AzureSecretType.KEY_CERT).credentials(credAuthCredentials).build();
    AzureManualDetailsDTO config = AzureManualDetailsDTO.builder()
                                       .clientId(azureClientCertificateConnectorSpec.getApplicationId())
                                       .tenantId(azureClientCertificateConnectorSpec.getTenantId())
                                       .authDTO(authDTO)
                                       .build();
    AzureCredentialDTO credential =
        AzureCredentialDTO.builder().azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS).config(config).build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(
            AzureEnvironmentType.valueOf(azureClientCertificateConnectorSpec.getAzureEnvironmentType().value()))
        .delegateSelectors(Sets.newHashSet(azureClientCertificateConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(azureClientCertificateConnectorSpec.isExecuteOnDelegate())
        .credential(credential)
        .build();
  }

  public ConnectorConfigDTO getGitHttpConnectorConfigDTO(ConnectorSpec connector) {
    GitHttpConnectorSpec gitHttpConnectorSpec = (GitHttpConnectorSpec) connector;

    return GitConfigDTO.builder()
        .gitAuthType(GitAuthType.HTTP)
        .gitConnectionType(GitConnectionType.valueOf(gitHttpConnectorSpec.getConnectionType().name()))
        .url(gitHttpConnectorSpec.getUrl())
        .validationRepo(gitHttpConnectorSpec.getValidationRepo())
        .branchName(gitHttpConnectorSpec.getBranch())
        .delegateSelectors(Sets.newHashSet(gitHttpConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(gitHttpConnectorSpec.isExecuteOnDelegate())
        .gitAuth(GitHTTPAuthenticationDTO.builder()
                     .username(gitHttpConnectorSpec.getUsername())
                     .passwordRef(gitHttpConnectorSpec.getPasswordRef() != null
                             ? new SecretRefData(gitHttpConnectorSpec.getPasswordRef())
                             : null)
                     .build())
        .build();
  }

  public ConnectorConfigDTO getGitHttpEncryptedConnectorConfigDTO(ConnectorSpec connector) {
    GitHttpEncryptedConnectorSpec gitHttpEncryptedConnectorSpec = (GitHttpEncryptedConnectorSpec) connector;

    return GitConfigDTO.builder()
        .gitAuthType(GitAuthType.HTTP)
        .gitConnectionType(GitConnectionType.valueOf(gitHttpEncryptedConnectorSpec.getConnectionType().name()))
        .url(gitHttpEncryptedConnectorSpec.getUrl())
        .validationRepo(gitHttpEncryptedConnectorSpec.getValidationRepo())
        .branchName(gitHttpEncryptedConnectorSpec.getBranch())
        .delegateSelectors(Sets.newHashSet(gitHttpEncryptedConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(gitHttpEncryptedConnectorSpec.isExecuteOnDelegate())
        .gitAuth(GitHTTPAuthenticationDTO.builder()
                     .usernameRef(gitHttpEncryptedConnectorSpec.getUsernameRef() != null
                             ? new SecretRefData(gitHttpEncryptedConnectorSpec.getUsernameRef())
                             : null)
                     .passwordRef(gitHttpEncryptedConnectorSpec.getPasswordRef() != null
                             ? new SecretRefData(gitHttpEncryptedConnectorSpec.getPasswordRef())
                             : null)
                     .build())
        .build();
  }

  public ConnectorConfigDTO getGitSshConnectorConfigDTO(ConnectorSpec connector) {
    GitSshConnectorSpec gitSshConnectorSpec = (GitSshConnectorSpec) connector;

    return GitConfigDTO.builder()
        .gitAuthType(GitAuthType.SSH)
        .gitConnectionType(GitConnectionType.valueOf(gitSshConnectorSpec.getConnectionType().name()))
        .url(gitSshConnectorSpec.getUrl())
        .validationRepo(gitSshConnectorSpec.getValidationRepo())
        .branchName(gitSshConnectorSpec.getBranch())
        .delegateSelectors(Sets.newHashSet(gitSshConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(gitSshConnectorSpec.isExecuteOnDelegate())
        .gitAuth(GitSSHAuthenticationDTO.builder()
                     .encryptedSshKey(gitSshConnectorSpec.getSshKeyRef() != null
                             ? new SecretRefData(gitSshConnectorSpec.getSshKeyRef())
                             : null)
                     .build())
        .build();
  }

  public ConnectorConfigDTO getArtifactoryAnonymousConnectorConfigDTO(ConnectorSpec connector) {
    ArtifactoryAnonymousConnectorSpec artifactoryAnonymousConnectorSpec = (ArtifactoryAnonymousConnectorSpec) connector;

    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryAnonymousConnectorSpec.getUrl())
        .delegateSelectors(Sets.newHashSet(artifactoryAnonymousConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(artifactoryAnonymousConnectorSpec.isExecuteOnDelegate())
        .auth(ArtifactoryAuthenticationDTO.builder().authType(ArtifactoryAuthType.ANONYMOUS).build())
        .build();
  }

  public ConnectorConfigDTO getArtifactoryConnectorConfigDTO(ConnectorSpec connector) {
    ArtifactoryConnectorSpec artifactoryConnectorSpec = (ArtifactoryConnectorSpec) connector;

    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(artifactoryConnectorSpec.getUsername())
            .passwordRef(new SecretRefData(artifactoryConnectorSpec.getPasswordRef()))
            .build();
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                                                    .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                    .credentials(artifactoryUsernamePasswordAuthDTO)
                                                                    .build();

    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryConnectorSpec.getUrl())
        .delegateSelectors(Sets.newHashSet(artifactoryConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(artifactoryConnectorSpec.isExecuteOnDelegate())
        .auth(artifactoryAuthenticationDTO)
        .build();
  }

  public ConnectorConfigDTO getArtifactoryEncryptedConnectorConfigDTO(ConnectorSpec connector) {
    ArtifactoryEncryptedConnectorSpec artifactoryEncryptedConnectorSpec = (ArtifactoryEncryptedConnectorSpec) connector;

    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .usernameRef(new SecretRefData(artifactoryEncryptedConnectorSpec.getUsernameRef()))
            .passwordRef(new SecretRefData(artifactoryEncryptedConnectorSpec.getPasswordRef()))
            .build();
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                                                    .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                    .credentials(artifactoryUsernamePasswordAuthDTO)
                                                                    .build();

    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryEncryptedConnectorSpec.getUrl())
        .delegateSelectors(Sets.newHashSet(artifactoryEncryptedConnectorSpec.getDelegateSelectors()))
        .executeOnDelegate(artifactoryEncryptedConnectorSpec.isExecuteOnDelegate())
        .auth(artifactoryAuthenticationDTO)
        .build();
  }

  public ConnectorType getConnectorType(Connector connector) {
    ConnectorSpec.TypeEnum type = connector.getSpec().getType();

    switch (type) {
      case GITHTTP:
      case GITHTTPENCRYPTED:
      case GITSSH:
        return ConnectorType.GIT;
      case APPDYNAMICS:
      case APPDYNAMICSCLIENTID:
        return ConnectorType.APP_DYNAMICS;
      case ARTIFACTORY:
      case ARTIFACTORYENCRYPTED:
      case ARTIFACTORYANONYMOUS:
        return ConnectorType.ARTIFACTORY;
      case AZURECLIENTSECRETKEY:
      case AZURECLIENTCERTIFICATE:
      case AZUREINHERITFROMDELEGATEUSERASSIGNEDMANAGEDIDENTITY:
      case AZUREINHERITFROMDELEGATESYSTEMASSIGNEDMANAGEDIDENTITY:
        return ConnectorType.AZURE;
      default:
        throw new InvalidRequestException(String.format(CONNECTOR_TYPE_S_IS_NOT_SUPPORTED, type.value()));
    }
  }

  public ConnectorTestConnectionResponse toConnectorTestConnectionResponse(
      ConnectorValidationResult connectorValidationResult) {
    ConnectorTestConnectionResponse connectorTestConnectionResponse = new ConnectorTestConnectionResponse();
    connectorTestConnectionResponse.setErrorSummary(connectorValidationResult.getErrorSummary());
    connectorTestConnectionResponse.setTestedAt(connectorValidationResult.getTestedAt());
    connectorTestConnectionResponse.setDelegateId(connectorValidationResult.getDelegateId());
    connectorTestConnectionResponse.setStatus(
        ConnectorTestConnectionResponse.StatusEnum.fromValue(connectorValidationResult.getStatus().name()));

    connectorTestConnectionResponse.setErrors(
        toConnectorTestConnectionErrorDetails(connectorValidationResult.getErrors()));
    return connectorTestConnectionResponse;
  }

  public List<ConnectorTestConnectionErrorDetail> toConnectorTestConnectionErrorDetails(
      List<ErrorDetail> errorDetails) {
    if (CollectionUtils.isEmpty(errorDetails)) {
      return Collections.emptyList();
    }

    return errorDetails.stream().map(this::toConnectorTestConnectionErrorDetail).collect(Collectors.toList());
  }

  public ConnectorTestConnectionErrorDetail toConnectorTestConnectionErrorDetail(ErrorDetail errorDetail) {
    ConnectorTestConnectionErrorDetail connectorTestConnectionErrorDetail = new ConnectorTestConnectionErrorDetail();
    connectorTestConnectionErrorDetail.setCode(errorDetail.getCode());
    connectorTestConnectionErrorDetail.setMessage(errorDetail.getMessage());
    connectorTestConnectionErrorDetail.setReason(errorDetail.getReason());
    return connectorTestConnectionErrorDetail;
  }
}
