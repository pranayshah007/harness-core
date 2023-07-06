/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.expressions.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.k8s.model.ImageDetails.ImageDetailsBuilder;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GithubPackagesArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConstant;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.NextGenModule;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class ImagePullSecretUtils {
  @Inject private EcrImagePullSecretHelper ecrImagePullSecretHelper;
  @Inject private AzureHelperService azureHelperService;
  @Inject @Named(NextGenModule.CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  private static final String ACR_DUMMY_DOCKER_USERNAME = "00000000-0000-0000-0000-000000000000";

  public String getImagePullSecret(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetails imageDetails = getImageDetails(artifactOutcome, ambiance);
    if (isNotEmpty(imageDetails.getRegistryUrl()) && isNotBlank(imageDetails.getUsername())
        && isNotBlank(imageDetails.getPassword())) {
      return getArtifactRegistryCredentials(imageDetails);
    } else if (isNotEmpty(imageDetails.getRegistryUrl()) && isNotBlank(imageDetails.getUsernameRef())
        && isNotBlank(imageDetails.getPassword())) {
      return getArtifactRegistryCredentialsFromUsernameRef(imageDetails);
    }
    return "";
  }

  public String getDockerConfigJson(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetails imageDetails = getImageDetails(artifactOutcome, ambiance);
    if (StringUtils.isNoneBlank(
            imageDetails.getRegistryUrl(), imageDetails.getUsername(), imageDetails.getPassword())) {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", \""
          + imageDetails.getUsername() + "\", " + imageDetails.getPassword() + ")}";
    } else if (StringUtils.isNoneBlank(
                   imageDetails.getRegistryUrl(), imageDetails.getUsernameRef(), imageDetails.getPassword())) {
      return "${dockerConfigJsonSecretFunc.create(\"" + imageDetails.getRegistryUrl() + "\", "
          + imageDetails.getUsernameRef() + ", " + imageDetails.getPassword() + ")}";
    }
    return "";
  }

  private ImageDetails getImageDetails(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    ImageDetailsBuilder imageDetailsBuilder = ImageDetails.builder();
    switch (artifactOutcome.getArtifactType()) {
      case ArtifactSourceConstants.DOCKER_REGISTRY_NAME:
        getImageDetailsFromDocker((DockerArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AMAZON_S3_NAME:
        getImageDetailsFromS3((S3ArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GCR_NAME:
        getImageDetailsFromGcr((GcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ECR_NAME:
        getImageDetailsFromEcr((EcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.NEXUS3_REGISTRY_NAME:
        getImageDetailsFromNexus((NexusArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME:
        getImageDetailsFromArtifactory((ArtifactoryArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.ACR_NAME:
        getImageDetailsFromAcr((AcrArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.JENKINS_NAME:
        getBuildDetailsFromJenkins((JenkinsArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GITHUB_PACKAGES_NAME:
        getImageDetailsFromGithubPackages(
            (GithubPackagesArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME:
        getImageDetailsFromGar((GarArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AZURE_ARTIFACTS_NAME:
        getImageDetailsFromAzureArtifacts((AzureArtifactsOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      case ArtifactSourceConstants.AMI_ARTIFACTS_NAME:
        getImageDetailsForAMI((AMIArtifactOutcome) artifactOutcome, imageDetailsBuilder, ambiance);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactOutcome.getArtifactType()));
    }
    return imageDetailsBuilder.build();
  }

  private void getImageDetailsForAMI(
      AMIArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    AwsConnectorDTO connectorConfig = (AwsConnectorDTO) connectorDTO.getConnectorConfig();

    if (connectorConfig.getCredential() != null && connectorConfig.getCredential().getConfig() != null
        && connectorConfig.getCredential().getAwsCredentialType() == MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO credentials = (AwsManualConfigSpecDTO) connectorConfig.getCredential().getConfig();

      String passwordRef = credentials.getSecretKeyRef().toSecretRefStringValue();

      if (credentials.getAccessKeyRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getAccessKeyRef().toSecretRefStringValue(), ambiance));
      }

      imageDetailsBuilder.username(credentials.getAccessKey());

      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private void getImageDetailsFromS3(
      S3ArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    AwsConnectorDTO connectorConfig = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getCredential() != null && connectorConfig.getCredential().getConfig() != null
        && connectorConfig.getCredential().getAwsCredentialType() == MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO credentials = (AwsManualConfigSpecDTO) connectorConfig.getCredential().getConfig();
      String passwordRef = credentials.getSecretKeyRef().toSecretRefStringValue();
      if (credentials.getAccessKeyRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getAccessKeyRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getAccessKey());
      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private void getImageDetailsFromAzureArtifacts(
      AzureArtifactsOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        (AzureArtifactsConnectorDTO) connectorDTO.getConnectorConfig();

    String password = "";

    if (azureArtifactsConnectorDTO.getAuth() != null && azureArtifactsConnectorDTO.getAuth().getCredentials() != null) {
      AzureArtifactsCredentialsDTO httpDTO = azureArtifactsConnectorDTO.getAuth().getCredentials();

      if (httpDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
        AzureArtifactsTokenDTO azureArtifactsHttpCredentialsSpecDTO = httpDTO.getCredentialsSpec();

        password = new String(azureArtifactsHttpCredentialsSpecDTO.getTokenRef().getDecryptedValue());

      } else {
        throw new InvalidRequestException("Please select the Auth type as Username-Token");
      }
    }

    if (password == null) {
      throw new InvalidRequestException("The token is null");
    }

    imageDetailsBuilder.password(password);

    imageDetailsBuilder.registryUrl(azureArtifactsConnectorDTO.getAzureArtifactsUrl());
  }

  private void getImageDetailsFromGithubPackages(
      GithubPackagesArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();

    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);

    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorDTO.getConnectorConfig();

    String username = "";
    String password = "";

    if (githubConnectorDTO.getAuthentication() != null
        && githubConnectorDTO.getAuthentication().getCredentials() != null) {
      if (githubConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
        GithubHttpCredentialsDTO httpDTO =
            (GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();

        if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
          GithubUsernamePasswordDTO githubUsernamePasswordDTO =
              (GithubUsernamePasswordDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernamePasswordDTO.getUsername(), githubUsernamePasswordDTO.getUsernameRef());

        } else if (httpDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
          GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) httpDTO.getHttpCredentialsSpec();

          username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
              githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());
        }
      }
    }

    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();

    if (githubApiAccessDTO == null) {
      throw new InvalidRequestException("Please enable the API Access for the Github Connector");
    }

    GithubApiAccessType githubApiAccessType = githubApiAccessDTO.getType();

    if (githubApiAccessType == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      if (githubTokenSpecDTO.getTokenRef() != null) {
        password = EmptyPredicate.isNotEmpty(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            ? new String(githubTokenSpecDTO.getTokenRef().getDecryptedValue())
            : getPasswordExpression(githubTokenSpecDTO.getTokenRef().toSecretRefStringValue() == null
                    ? ""
                    : githubTokenSpecDTO.getTokenRef().toSecretRefStringValue(),
                ambiance);

      } else {
        throw new InvalidRequestException("The token reference for the Github Connector is null");
      }

    } else {
      throw new InvalidRequestException("Please select the API Access auth type to Token");
    }

    imageDetailsBuilder.username(username);
    imageDetailsBuilder.password(password);
    imageDetailsBuilder.registryUrl("https://ghcr.io");
  }

  public static String getArtifactRegistryCredentials(ImageDetails imageDetails) {
    return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", \"" + imageDetails.getUsername() + "\", "
        + imageDetails.getPassword() + ")}";
  }

  public static String getArtifactRegistryCredentialsFromUsernameRef(ImageDetails imageDetails) {
    return "${imageSecret.create(\"" + imageDetails.getRegistryUrl() + "\", " + imageDetails.getUsernameRef() + ", "
        + imageDetails.getPassword() + ")}";
  }

  private void getImageDetailsFromDocker(
      DockerArtifactOutcome dockerArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = dockerArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    DockerConnectorDTO connectorConfig = (DockerConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == DockerAuthType.USER_PASSWORD) {
      DockerUserNamePasswordDTO credentials = (DockerUserNamePasswordDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
      imageDetailsBuilder.registryUrl(connectorConfig.getDockerRegistryUrl());
    }
  }

  private void getImageDetailsFromGcr(
      GcrArtifactOutcome gcrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = gcrArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    GcpConnectorDTO connectorConfig = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
    String imageName = gcrArtifactOutcome.getRegistryHostname() + "/" + gcrArtifactOutcome.getImagePath();
    imageDetailsBuilder.registryUrl(imageName);
    imageDetailsBuilder.username("_json_key");
    if (connectorConfig.getCredential() != null
        && connectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO config = (GcpManualDetailsDTO) connectorConfig.getCredential().getConfig();
      imageDetailsBuilder.password(getPasswordExpression(config.getSecretKeyRef().toSecretRefStringValue(), ambiance));
    }
  }

  private void getImageDetailsFromGar(
      GarArtifactOutcome garArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = garArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorInfoDTO = getConnector(connectorRef, ambiance);
    GcpConnectorDTO connectorConfig = (GcpConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String imageName = garArtifactOutcome.getImage();
    imageDetailsBuilder.registryUrl(imageName);
    imageDetailsBuilder.username("_json_key");
    if (connectorConfig.getCredential() != null
        && connectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO config = (GcpManualDetailsDTO) connectorConfig.getCredential().getConfig();
      imageDetailsBuilder.password(getPasswordExpression(config.getSecretKeyRef().toSecretRefStringValue(), ambiance));
    }
  }

  private void getImageDetailsFromEcr(
      EcrArtifactOutcome ecrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = ecrArtifactOutcome.getConnectorRef();
    BaseNGAccess baseNGAccess = ecrImagePullSecretHelper.getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    ConnectorInfoDTO connectorIntoDTO = getConnector(connectorRef, ambiance);
    AwsConnectorDTO connectorDTO = (AwsConnectorDTO) connectorIntoDTO.getConnectorConfig();
    List<EncryptedDataDetail> encryptionDetails =
        ecrImagePullSecretHelper.getEncryptionDetails(connectorDTO, baseNGAccess);
    EcrArtifactDelegateRequest ecrRequest = ArtifactDelegateRequestUtils.getEcrDelegateRequest(
        ecrArtifactOutcome.getRegistryId(), ecrArtifactOutcome.getImagePath(), ecrArtifactOutcome.getTag(), null, null,
        ecrArtifactOutcome.getRegion(), connectorRef, connectorDTO, encryptionDetails, ArtifactSourceType.ECR);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponseForImageUrl = ecrImagePullSecretHelper.executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_IMAGE_URL, baseNGAccess, "Ecr Get image URL failure due to error");
    String imageUrl =
        ((EcrArtifactDelegateResponse) artifactTaskExecutionResponseForImageUrl.getArtifactDelegateResponses().get(0))
            .getImageUrl();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponseForAuthToken = ecrImagePullSecretHelper.executeSyncTask(
        ecrRequest, ArtifactTaskType.GET_AUTH_TOKEN, baseNGAccess, "Ecr Get Auth-token failure due to error");
    String authToken =
        ((EcrArtifactDelegateResponse) artifactTaskExecutionResponseForAuthToken.getArtifactDelegateResponses().get(0))
            .getAuthToken();
    String decoded = new String(Base64.getDecoder().decode(authToken));
    String password = decoded.split(":")[1];
    imageDetailsBuilder.name(imageUrl)
        .sourceName(ArtifactSourceType.ECR.getDisplayName())
        .registryUrl(imageUrlToRegistryUrl(imageUrl))
        .username("AWS");
    imageDetailsBuilder.password("\"" + password + "\"");
  }

  private void getImageDetailsFromNexus(
      NexusArtifactOutcome nexusArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = nexusArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    NexusConnectorDTO connectorConfig = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == NexusAuthType.USER_PASSWORD) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
      if (isNotEmpty(nexusArtifactOutcome.getRegistryHostname())) {
        imageDetailsBuilder.registryUrl(nexusArtifactOutcome.getRegistryHostname());
      } else {
        imageDetailsBuilder.registryUrl(connectorConfig.getNexusServerUrl());
      }
    }
  }

  private void getImageDetailsFromArtifactory(ArtifactoryArtifactOutcome artifactoryArtifactOutcome,
      ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactoryArtifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    ArtifactoryConnectorDTO connectorConfig = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
      if (isNotEmpty(artifactoryArtifactOutcome.getRegistryHostname())) {
        imageDetailsBuilder.registryUrl(artifactoryArtifactOutcome.getRegistryHostname());
      } else {
        imageDetailsBuilder.registryUrl(connectorConfig.getArtifactoryServerUrl());
      }
    }
  }

  private void getImageDetailsFromAcr(
      AcrArtifactOutcome acrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    try {
      String connectorRef = acrArtifactOutcome.getConnectorRef();
      ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
      AzureConnectorDTO connectorConfig = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
      imageDetailsBuilder.registryUrl(acrArtifactOutcome.getRegistry());
      if (connectorConfig.getCredential() != null
          && connectorConfig.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
        AzureManualDetailsDTO config = (AzureManualDetailsDTO) connectorConfig.getCredential().getConfig();
        if (config.getAuthDTO().getAzureSecretType() == AzureSecretType.SECRET_KEY) {
          log.info("Generating image pull credentials for SP with secret");
          imageDetailsBuilder.username(config.getClientId());
          imageDetailsBuilder.password(getPasswordExpression(
              ((AzureClientSecretKeyDTO) config.getAuthDTO().getCredentials()).getSecretKey().toSecretRefStringValue(),
              ambiance));
        } else {
          log.info(format(
              "Generating image pull credentials for SP with certificate. Fetching access token for clientId: %s",
              ((AzureManualDetailsDTO) connectorConfig.getCredential().getConfig()).getClientId()));
          generateAcrImageDetailsBuilder(ambiance, connectorConfig, acrArtifactOutcome, imageDetailsBuilder);
        }
      } else if (connectorConfig.getCredential() != null
          && connectorConfig.getCredential().getAzureCredentialType() == AzureCredentialType.INHERIT_FROM_DELEGATE) {
        AzureInheritFromDelegateDetailsDTO config =
            (AzureInheritFromDelegateDetailsDTO) connectorConfig.getCredential().getConfig();
        if (config.getAuthDTO() instanceof AzureMSIAuthUADTO) {
          log.info(
              format("Generating image pull credentials for User-Assigned MSI. Fetching access token for clientId: %s",
                  ((AzureMSIAuthUADTO) config.getAuthDTO()).getCredentials().getClientId()));
        } else {
          log.info("Generating image pull credentials for System-Assigned MSI");
        }
        generateAcrImageDetailsBuilder(ambiance, connectorConfig, acrArtifactOutcome, imageDetailsBuilder);
      } else {
        if (connectorConfig.getCredential() == null) {
          throw new Exception(format("Connector credentials are missing. Can not generate Image details."));
        }

        throw new Exception(
            format("AzureCredentialType [%s] is invalid", connectorConfig.getCredential().getAzureCredentialType()));
      }
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private void generateAcrImageDetailsBuilder(Ambiance ambiance, AzureConnectorDTO connectorConfig,
      AcrArtifactOutcome acrArtifactOutcome, ImageDetailsBuilder imageDetailsBuilder) {
    log.info("Generating ACR image details");
    BaseNGAccess baseNGAccess = azureHelperService.getBaseNGAccess(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
      SecurityContextBuilder.setContext(principal);
    }
    log.info(format("SecurityContext is %s service", principal.getName()));

    List<EncryptedDataDetail> encryptionDetails =
        azureHelperService.getEncryptionDetails(connectorConfig, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.CONTAINER_REGISTRY, acrArtifactOutcome.getRegistry());

    AzureTaskParams azureTaskParams = AzureTaskParams.builder()
                                          .azureTaskType(AzureTaskType.GET_ACR_TOKEN)
                                          .azureConnector(connectorConfig)
                                          .encryptionDetails(encryptionDetails)
                                          .delegateSelectors(connectorConfig.getDelegateSelectors())
                                          .additionalParams(additionalParams)
                                          .build();

    AzureAcrTokenTaskResponse accessTokenResponse = (AzureAcrTokenTaskResponse) azureHelperService.executeSyncTask(
        ambiance, azureTaskParams, baseNGAccess, "Azure get ACR access token task failure due to error");

    String token = format("\"%s\"", accessTokenResponse.getToken());

    imageDetailsBuilder.username(ACR_DUMMY_DOCKER_USERNAME);
    imageDetailsBuilder.password(token);
  }

  private void getBuildDetailsFromJenkins(
      JenkinsArtifactOutcome artifactOutcome, ImageDetailsBuilder imageDetailsBuilder, Ambiance ambiance) {
    String connectorRef = artifactOutcome.getConnectorRef();
    ConnectorInfoDTO connectorDTO = getConnector(connectorRef, ambiance);
    JenkinsConnectorDTO connectorConfig = (JenkinsConnectorDTO) connectorDTO.getConnectorConfig();
    if (connectorConfig.getAuth().getCredentials() != null
        && connectorConfig.getAuth().getAuthType().getDisplayName() == JenkinsConstant.USERNAME_PASSWORD) {
      JenkinsUserNamePasswordDTO credentials = (JenkinsUserNamePasswordDTO) connectorConfig.getAuth().getCredentials();
      String passwordRef = credentials.getPasswordRef().toSecretRefStringValue();
      if (credentials.getUsernameRef() != null) {
        imageDetailsBuilder.usernameRef(
            getPasswordExpression(credentials.getUsernameRef().toSecretRefStringValue(), ambiance));
      }
      imageDetailsBuilder.username(credentials.getUsername());
      imageDetailsBuilder.password(getPasswordExpression(passwordRef, ambiance));
    }
  }

  private String imageUrlToRegistryUrl(String imageUrl) {
    String fullImageUrl = "https://" + imageUrl + (imageUrl.endsWith("/") ? "" : "/");
    fullImageUrl = fullImageUrl.substring(0, fullImageUrl.length() - 1);
    int index = fullImageUrl.lastIndexOf('/');
    return fullImageUrl.substring(0, index + 1);
  }

  private ConnectorInfoDTO getConnector(String connectorIdentifierRef, Ambiance ambiance) {
    try {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
          connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef), WingsException.USER);
      }
      return connectorDTO.get().getConnector();
    } catch (Exception e) {
      log.error(format("Unable to get connector information : [%s] ", connectorIdentifierRef), e);
      throw new InvalidRequestException(format("Unable to get connector information : [%s] ", connectorIdentifierRef));
    }
  }

  private String getPasswordExpression(String passwordRef, Ambiance ambiance) {
    return "${ngSecretManager.obtain(\"" + passwordRef + "\", " + ambiance.getExpressionFunctorToken() + ")}";
  }
}
