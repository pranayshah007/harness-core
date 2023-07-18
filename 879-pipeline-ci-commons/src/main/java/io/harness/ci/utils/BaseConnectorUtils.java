/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.ConnectorType.HARNESS;
import static io.harness.delegate.beans.connector.azureconnector.AzureCredentialType.MANUAL_CREDENTIALS;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails.ConnectorDetailsBuilder;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessJWTTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@Slf4j
public class BaseConnectorUtils {
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private SecretUtils secretUtils;

  public final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  public final int MAX_ATTEMPTS = 6;

  private final String SAAS = "SaaS";
  private final String SELF_MANAGED = "Self-Managed";
  private final String GIT_DOT = "git.";

  public Map<String, ConnectorDetails> getConnectorDetailsMap(NGAccess ngAccess, Set<String> connectorNameSet) {
    Map<String, ConnectorDetails> connectorDetailsMap = new HashMap<>();
    if (isNotEmpty(connectorNameSet)) {
      for (String connectorIdentifier : connectorNameSet) {
        ConnectorDetails connectorDetails = getConnectorDetails(ngAccess, connectorIdentifier);
        connectorDetailsMap.put(connectorDetails.getIdentifier(), connectorDetails);
      }
    }

    return connectorDetailsMap;
  }

  public ConnectorDetails getConnectorDetailsWithConversionInfo(
      NGAccess ngAccess, ConnectorConversionInfo connectorConversionInfo) {
    ConnectorDetails connectorDetails = getConnectorDetails(ngAccess, connectorConversionInfo.getConnectorRef());
    connectorDetails.setEnvToSecretsMap(connectorConversionInfo.getEnvToSecretsMap());
    return connectorDetails;
  }

  public ConnectorDetails getConnectorDetails(NGAccess ngAccess, String connectorIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifier,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    return getConnectorDetailsInternalWithRetries(ngAccess, connectorRef);
  }

  public ConnectorDetails getConnectorDetailsWithIdentifier(NGAccess ngAccess, IdentifierRef identifierRef) {
    return getConnectorDetailsInternalWithRetries(ngAccess, identifierRef);
  }

  public ConnectorDetails getHarnessConnectorDetails(NGAccess ngAccess, String baseUrl, String authToken) {
    log.info("Generated harness scm baseurl : {}", baseUrl);
    String accountId = ngAccess.getAccountIdentifier();
    HarnessConnectorDTO connectorConfigDTO =
        HarnessConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url(baseUrl + "/" + accountId)
            .authentication(
                HarnessAuthenticationDTO.builder()
                    .authType(GitAuthType.HTTP)
                    .credentials(
                        HarnessHttpCredentialsDTO.builder()
                            .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                            .httpCredentialsSpec(
                                HarnessUsernameTokenDTO.builder()
                                    .username("admin")
                                    .tokenRef(SecretRefData.builder().decryptedValue(authToken.toCharArray()).build())
                                    .build())
                            .build())
                    .build())
            .apiAccess(HarnessApiAccessDTO.builder()
                           .type(HarnessApiAccessType.JWT_TOKEN)
                           .spec(HarnessJWTTokenSpecDTO.builder()
                                     .tokenRef(SecretRefData.builder().decryptedValue(authToken.toCharArray()).build())
                                     .build())
                           .build())
            .build();

    HarnessHttpCredentialsDTO harnessHttpCredentialsDTO =
        (HarnessHttpCredentialsDTO) connectorConfigDTO.getAuthentication().getCredentials();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, harnessHttpCredentialsDTO.getHttpCredentialsSpec());
    encryptedDataDetails.addAll(
        secretManagerClientService.getEncryptionDetails(ngAccess, connectorConfigDTO.getApiAccess().getSpec()));

    ConnectorDetailsBuilder connectorDetailsBuilder = ConnectorDetails.builder()
                                                          .connectorType(HARNESS)
                                                          .connectorConfig(connectorConfigDTO)
                                                          .identifier("HARNESS_SCM")
                                                          .executeOnDelegate(false)
                                                          .orgIdentifier(ngAccess.getOrgIdentifier())
                                                          .projectIdentifier(ngAccess.getProjectIdentifier())
                                                          .encryptedDataDetails(encryptedDataDetails);

    return connectorDetailsBuilder.build();
  }

  public String getSCMBaseUrl(String baseUrl) {
    try {
      URL url = new URL(baseUrl);
      String host = url.getHost();
      String protocol = url.getProtocol();
      if (host.equals("localhost")) {
        return "";
      }
      return baseUrl + "/code/git";
    } catch (Exception e) {
      log.error("There was error while generating scm base URL", e);
    }
    return "";
  }

  public ConnectorDetails getConnectorDetailsInternalWithRetries(NGAccess ngAccess, IdentifierRef connectorRef) {
    Instant startTime = Instant.now();
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch connector: [%s], scope: [%s], attempt: {}",
                           connectorRef.getIdentifier(), connectorRef.getScope()),
            format("Failed to fetch connector: [%s] scope: [%s] after retrying {} times", connectorRef.getIdentifier(),
                connectorRef.getScope()));

    ConnectorDetails connectorDetails =
        Failsafe.with(retryPolicy).get(() -> { return getConnectorDetailsInternal(ngAccess, connectorRef); });

    long elapsedTimeInSecs = Duration.between(startTime, Instant.now()).toMillis() / 1000;

    log.info(
        "Fetched connector details for connector ref successfully {} with scope {} in {} seconds accountId {}, projectId {}",
        connectorRef.getIdentifier(), connectorRef.getScope(), elapsedTimeInSecs, ngAccess.getAccountIdentifier(),
        ngAccess.getProjectIdentifier());

    return connectorDetails;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private ConnectorDetails getConnectorDetailsInternal(NGAccess ngAccess, IdentifierRef connectorRef)
      throws IOException {
    log.info("Getting connector details for connector ref with scope: [{}] and identifier: [{}]",
        connectorRef.getScope(), connectorRef.getIdentifier());

    ConnectorDTO connectorDTO = getConnector(connectorRef);
    ConnectorType connectorType = connectorDTO.getConnectorInfo().getConnectorType();

    ConnectorDetailsBuilder connectorDetailsBuilder =
        ConnectorDetails.builder()
            .connectorType(connectorType)
            .connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig())
            .identifier(connectorDTO.getConnectorInfo().getIdentifier())
            .orgIdentifier(connectorDTO.getConnectorInfo().getOrgIdentifier())
            .projectIdentifier(connectorDTO.getConnectorInfo().getProjectIdentifier());

    log.info("Fetching encryption details for connector details for connector id:[{}] type:[{}] scope: [{}]",
        connectorRef.getIdentifier(), connectorType, connectorRef.getScope());
    ConnectorDetails connectorDetails;

    switch (connectorType) {
      case DOCKER:
        connectorDetails = getDockerConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case KUBERNETES_CLUSTER:
        connectorDetails = getK8sConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
      case CODECOMMIT:
      case AZURE_REPO:
        connectorDetails = getGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case GCP:
        connectorDetails = getGcpConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case AWS:
        connectorDetails = getAwsConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case AZURE:
        connectorDetails = getAzureConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case ARTIFACTORY:
        connectorDetails = getArtifactoryConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      default:
        throw new InvalidArgumentsException(format("Unexpected connector type:[%s]", connectorType));
    }
    log.info("Successfully fetched encryption details for  connector id:[{}] type:[{}] scope:[{}]",
        connectorRef.getIdentifier(), connectorType, connectorRef.getScope());
    return connectorDetails;
  }

  public String fetchUserName(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return fetchUserNameFromGithubConnector(gitConfigDTO);
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return fetchUserNameFromGitlabConnector(gitConfigDTO);
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return fetchUserNameFromBitbucketConnector(gitConfigDTO, gitConnector.getIdentifier());
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      // Username not needed for Azure.
      return null;
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return fetchUserNameFromHarnessConnector(gitConfigDTO);
    } else {
      throw new CIStageExecutionException("Unsupported git connector " + gitConnector.getConnectorType());
    }
  }

  public String retrieveURL(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported.");
    }
  }

  public boolean hasApiAccess(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getApiAccess() != null;
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getApiAccess() != null;
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getApiAccess() != null;
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getApiAccess() != null;
    } else if (gitConnector.getConnectorType() == GIT || gitConnector.getConnectorType() == CODECOMMIT) {
      return false;
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getApiAccess() != null;
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  public String getScmAuthType(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getAuthentication().getAuthType().getDisplayName();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getAuthentication().getAuthType().getDisplayName();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getAuthentication().getAuthType().getDisplayName();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getAuthentication().getAuthType().getDisplayName();
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitAuthType().getDisplayName();
    } else if (gitConnector.getConnectorType() == HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getAuthentication().getAuthType().getDisplayName();
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  private String fetchUserNameFromHarnessConnector(HarnessConnectorDTO gitConfigDTO) {
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      HarnessHttpCredentialsDTO harnessHttpCredentialsDTO =
          (HarnessHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      if (harnessHttpCredentialsDTO.getType() == HarnessHttpAuthenticationType.USERNAME_AND_TOKEN) {
        HarnessUsernameTokenDTO harnessHttpCredentialsSpecDTO =
            (HarnessUsernameTokenDTO) harnessHttpCredentialsDTO.getHttpCredentialsSpec();
        return harnessHttpCredentialsSpecDTO.getUsername();
      }
    }
    return null;
  }

  public String getScmHostType(ConnectorDetails gitConnector) {
    String url = retrieveURL(gitConnector);
    if (GitClientHelper.isGithubSAAS(url) || GitClientHelper.isGitlabSAAS(url) || GitClientHelper.isBitBucketSAAS(url)
        || GitClientHelper.isAzureRepoSAAS(url)) {
      return SAAS;
    } else {
      return SELF_MANAGED;
    }
  }

  private ConnectorDetails getArtifactoryConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (artifactoryConnectorDTO.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO auth =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnectorDTO.getAuth().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, auth);
      return connectorDetailsBuilder.executeOnDelegate(artifactoryConnectorDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    }
    throw new InvalidArgumentsException(format("Unsupported artifactory auth type:[%s] on connector:[%s]",
        artifactoryConnectorDTO.getAuth().getAuthType(), artifactoryConnectorDTO));
  }

  private ConnectorDetails getAwsConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    AwsCredentialDTO awsCredentialDTO = awsConnectorDTO.getCredential();
    if (awsCredentialDTO.getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) awsCredentialDTO.getConfig();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, awsManualConfigSpecDTO);
      return connectorDetailsBuilder.executeOnDelegate(awsConnectorDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (awsCredentialDTO.getAwsCredentialType() == AwsCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.executeOnDelegate(awsConnectorDTO.getExecuteOnDelegate()).build();
    } else if (awsCredentialDTO.getAwsCredentialType() == AwsCredentialType.IRSA) {
      return connectorDetailsBuilder.executeOnDelegate(awsConnectorDTO.getExecuteOnDelegate()).build();
    }
    throw new InvalidArgumentsException(format("Unsupported aws credential type:[%s] on connector:[%s]",
        awsCredentialDTO.getAwsCredentialType(), awsConnectorDTO));
  }

  private ConnectorDetails getAzureConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    AzureCredentialDTO credentialDTO = azureConnectorDTO.getCredential();
    if (credentialDTO.getAzureCredentialType() == MANUAL_CREDENTIALS) {
      AzureManualDetailsDTO config = (AzureManualDetailsDTO) credentialDTO.getConfig();
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, config.getAuthDTO().getCredentials());
      return connectorDetailsBuilder.executeOnDelegate(azureConnectorDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else {
      return connectorDetailsBuilder.executeOnDelegate(azureConnectorDTO.getExecuteOnDelegate()).build();
    }
  }

  private ConnectorDetails getGcpConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    GcpConnectorCredentialDTO credential = gcpConnectorDTO.getCredential();
    if (credential.getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO credentialConfig = (GcpManualDetailsDTO) credential.getConfig();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, credentialConfig);
      return connectorDetailsBuilder.executeOnDelegate(gcpConnectorDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (credential.getGcpCredentialType() == GcpCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.executeOnDelegate(gcpConnectorDTO.getExecuteOnDelegate()).build();
    }
    throw new InvalidArgumentsException(format("Unsupported gcp credential type:[%s] on connector:[%s]",
        gcpConnectorDTO.getCredential().getGcpCredentialType(), gcpConnectorDTO));
  }

  private ConnectorDetails getGitConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    if (connectorDTO.getConnectorInfo().getConnectorType() == GITHUB) {
      return buildGithubConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == AZURE_REPO) {
      return buildAzureRepoConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == GITLAB) {
      return buildGitlabConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == BITBUCKET) {
      return buildBitBucketConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == CODECOMMIT) {
      return buildAwsCodeCommitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else if (connectorDTO.getConnectorInfo().getConnectorType() == GIT) {
      return buildGitConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector " + connectorDTO.getConnectorInfo().getConnectorType());
    }
  }

  private ConnectorDetails buildAzureRepoConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      AzureRepoHttpCredentialsDTO azureRepoHttpCredentialsDTO =
          (AzureRepoHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
          ngAccess, azureRepoHttpCredentialsDTO.getHttpCredentialsSpec());
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails.addAll(
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      }
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails)
          .executeOnDelegate(gitConfigDTO.getExecuteOnDelegate())
          .build();
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      AzureRepoSshCredentialsDTO azureRepoSshCredentialsDTO =
          (AzureRepoSshCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      SSHKeyDetails sshKey = secretUtils.getSshKey(ngAccess, azureRepoSshCredentialsDTO.getSshKeyRef());
      if (sshKey.getSshKeyReference().getEncryptedPassphrase() != null) {
        throw new CIStageExecutionException("Unsupported ssh key format, passphrase is unsupported in git connector: "
            + gitConfigDTO.getAuthentication().getAuthType());
      }
      connectorDetailsBuilder.sshKeyDetails(sshKey);
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails =
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec());
        connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails);
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate()).build();
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildGitlabConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
          (GitlabHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, gitlabHttpCredentialsDTO.getHttpCredentialsSpec());
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails.addAll(
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      GitlabSshCredentialsDTO gitlabSshCredentialsDTO =
          (GitlabSshCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      SSHKeyDetails sshKey = secretUtils.getSshKey(ngAccess, gitlabSshCredentialsDTO.getSshKeyRef());
      if (sshKey.getSshKeyReference().getEncryptedPassphrase() != null) {
        throw new CIStageExecutionException("Unsupported ssh key format, passphrase is unsupported in git connector: "
            + gitConfigDTO.getAuthentication().getAuthType());
      }
      connectorDetailsBuilder.sshKeyDetails(sshKey);
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails =
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec());
        connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails);
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate()).build();
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildGithubConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GithubHttpCredentialsDTO githubHttpCredentialsDTO =
          (GithubHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, githubHttpCredentialsDTO.getHttpCredentialsSpec());
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails.addAll(
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      GithubSshCredentialsDTO githubSshCredentialsDTO =
          (GithubSshCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      SSHKeyDetails sshKey = secretUtils.getSshKey(ngAccess, githubSshCredentialsDTO.getSshKeyRef());
      if (sshKey.getSshKeyReference().getEncryptedPassphrase() != null) {
        throw new CIStageExecutionException("Unsupported ssh key format, passphrase is unsupported in git connector: "
            + gitConfigDTO.getAuthentication().getAuthType());
      }
      connectorDetailsBuilder.sshKeyDetails(sshKey);
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails =
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec());
        connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails);
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate()).build();
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildBitBucketConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
          (BitbucketHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
          ngAccess, bitbucketHttpCredentialsDTO.getHttpCredentialsSpec());
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails.addAll(
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec()));
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      BitbucketSshCredentialsDTO bitbucketSshCredentialsDTO =
          (BitbucketSshCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      SSHKeyDetails sshKey = secretUtils.getSshKey(ngAccess, bitbucketSshCredentialsDTO.getSshKeyRef());
      connectorDetailsBuilder.sshKeyDetails(sshKey);
      if (sshKey.getSshKeyReference().getEncryptedPassphrase() != null) {
        throw new CIStageExecutionException("Unsupported ssh key format, passphrase is unsupported in git connector: "
            + gitConfigDTO.getAuthentication().getAuthType());
      }
      if (gitConfigDTO.getApiAccess() != null && gitConfigDTO.getApiAccess().getSpec() != null) {
        encryptedDataDetails =
            secretManagerClientService.getEncryptionDetails(ngAccess, gitConfigDTO.getApiAccess().getSpec());
        connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails);
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate()).build();
    } else {
      throw new CIStageExecutionException(
          "Unsupported git connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private ConnectorDetails buildAwsCodeCommitConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    AwsCodeCommitConnectorDTO gitConfigDTO =
        (AwsCodeCommitConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    gitConfigDTO.getDecryptableEntities().forEach(decryptableEntity
        -> encryptedDataDetails.addAll(secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntity)));
    connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails);
    return connectorDetailsBuilder.build();
  }

  private ConnectorDetails buildGitConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    GitConfigDTO gitConfigDTO = (GitConfigDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    GitAuthenticationDTO gitAuth = gitConfigDTO.getGitAuth();
    if (gitConfigDTO.getGitAuthType() == GitAuthType.HTTP) {
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, gitAuth);
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (gitConfigDTO.getGitAuthType() == GitAuthType.SSH) {
      GitSSHAuthenticationDTO gitSSHAuthenticationDTO = (GitSSHAuthenticationDTO) gitAuth;
      SSHKeyDetails sshKey = secretUtils.getSshKey(ngAccess, gitSSHAuthenticationDTO.getEncryptedSshKey());
      connectorDetailsBuilder.sshKeyDetails(sshKey);
      if (sshKey.getSshKeyReference().getEncryptedPassphrase() != null) {
        throw new CIStageExecutionException(
            "Unsupported ssh key format, passphrase is unsupported in git connector: " + gitConfigDTO.getGitAuthType());
      }
      return connectorDetailsBuilder.executeOnDelegate(gitConfigDTO.getExecuteOnDelegate()).build();
    } else {
      throw new CIStageExecutionException("Unsupported git connector auth" + gitConfigDTO.getGitAuthType());
    }
  }

  private ConnectorDetails getDockerConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    DockerAuthType dockerAuthType = dockerConnectorDTO.getAuth().getAuthType();
    if (dockerAuthType == DockerAuthType.USER_PASSWORD) {
      encryptedDataDetails =
          secretManagerClientService.getEncryptionDetails(ngAccess, dockerConnectorDTO.getAuth().getCredentials());
      return connectorDetailsBuilder.executeOnDelegate(dockerConnectorDTO.getExecuteOnDelegate())
          .encryptedDataDetails(encryptedDataDetails)
          .build();
    } else if (dockerAuthType == DockerAuthType.ANONYMOUS) {
      return connectorDetailsBuilder.executeOnDelegate(dockerConnectorDTO.getExecuteOnDelegate()).build();
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported docker credential type:[%s] on connector:[%s]", dockerAuthType, dockerConnectorDTO));
    }
  }

  private ConnectorDetails getK8sConnectorDetails(
      NGAccess ngAccess, ConnectorDTO connectorDTO, ConnectorDetailsBuilder connectorDetailsBuilder) {
    List<EncryptedDataDetail> encryptedDataDetails;
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        (KubernetesClusterConfigDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    KubernetesCredentialDTO config = kubernetesClusterConfigDTO.getCredential();
    if (config.getKubernetesCredentialType() == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesClusterDetailsDTO kubernetesCredentialSpecDTO = (KubernetesClusterDetailsDTO) config.getConfig();
      KubernetesAuthCredentialDTO kubernetesAuthCredentialDTO = kubernetesCredentialSpecDTO.getAuth().getCredentials();
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, kubernetesAuthCredentialDTO);
      return connectorDetailsBuilder.encryptedDataDetails(encryptedDataDetails).build();
    } else if (config.getKubernetesCredentialType() == KubernetesCredentialType.INHERIT_FROM_DELEGATE) {
      return connectorDetailsBuilder.build();
    }
    throw new InvalidArgumentsException(format("Unsupported gcp credential type:[%s] on connector:[%s]",
        kubernetesClusterConfigDTO.getCredential().getKubernetesCredentialType(), kubernetesClusterConfigDTO));
  }

  private ConnectorDTO getConnector(IdentifierRef connectorRef) throws IOException {
    log.info("Fetching connector details for connector id:[{}] acc:[{}] project:[{}] org:[{}]",
        connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(), connectorRef.getProjectIdentifier(),
        connectorRef.getOrgIdentifier());

    Response<ResponseDTO<Optional<ConnectorDTO>>> response =
        connectorResourceClient
            .get(connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
                connectorRef.getProjectIdentifier())
            .execute();
    if (response.isSuccessful()) {
      Optional<ConnectorDTO> connectorDTO = response.body().getData();
      if (!connectorDTO.isPresent()) {
        throw new CIStageExecutionException(format("Connector not present for identifier : [%s] with scope: [%s]",
            connectorRef.getIdentifier(), connectorRef.getScope()));
      }
      return connectorDTO.get();
    } else {
      ErrorCode errorCode = getResponseErrorCode(response);
      if (errorCode == ErrorCode.RESOURCE_NOT_FOUND_EXCEPTION) {
        throw new ConnectorNotFoundException(format("Connector not found for identifier : [%s] with scope: [%s]",
                                                 connectorRef.getIdentifier(), connectorRef.getScope()),
            USER);
      } else {
        throw new CIStageExecutionException(
            format("Failed to find connector for identifier: [%s] with scope: [%s] with error: %s",
                connectorRef.getIdentifier(), connectorRef.getScope(), errorCode));
      }
    }
  }

  private String fetchUserNameFromGithubConnector(GithubConnectorDTO gitConfigDTO) {
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GithubHttpCredentialsDTO githubHttpCredentialsDTO =
          (GithubHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      if (githubHttpCredentialsDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        GithubUsernamePasswordDTO githubHttpCredentialsSpecDTO =
            (GithubUsernamePasswordDTO) githubHttpCredentialsDTO.getHttpCredentialsSpec();
        return githubHttpCredentialsSpecDTO.getUsername();
      } else if (githubHttpCredentialsDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
        GithubUsernameTokenDTO githubHttpCredentialsSpecDTO =
            (GithubUsernameTokenDTO) githubHttpCredentialsDTO.getHttpCredentialsSpec();
        return githubHttpCredentialsSpecDTO.getUsername();
      }
    }
    return null;
  }

  private String fetchUserNameFromGitlabConnector(GitlabConnectorDTO gitConfigDTO) {
    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
          (GitlabHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      if (gitlabHttpCredentialsDTO.getType() == GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        GitlabUsernamePasswordDTO GitlabHttpCredentialsSpecDTO =
            (GitlabUsernamePasswordDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
        return GitlabHttpCredentialsSpecDTO.getUsername();
      } else if (gitlabHttpCredentialsDTO.getType() == GitlabHttpAuthenticationType.USERNAME_AND_TOKEN) {
        GitlabUsernameTokenDTO GitlabHttpCredentialsSpecDTO =
            (GitlabUsernameTokenDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
        return GitlabHttpCredentialsSpecDTO.getUsername();
      }
    }

    return null;
  }

  private String fetchUserNameFromBitbucketConnector(BitbucketConnectorDTO gitConfigDTO, String identifier) {
    try {
      if (gitConfigDTO.getApiAccess().getType() == BitbucketApiAccessType.USERNAME_AND_TOKEN) {
        return ((BitbucketUsernameTokenApiAccessDTO) gitConfigDTO.getApiAccess().getSpec()).getUsername();
      }
    } catch (Exception ex) {
      log.error(format("Unable to get username information from api access for identifier %s", identifier), ex);
      throw new CIStageExecutionException(
          format("Unable to get username information from api access for identifier %s", identifier));
    }
    throw new CIStageExecutionException(
        format("Unable to get username information from api access for identifier %s", identifier));
  }

  private <T> ErrorCode getResponseErrorCode(Response<ResponseDTO<T>> response) throws IOException {
    try {
      FailureDTO failureResponse =
          JsonUtils.asObject(response.errorBody().string(), new TypeReference<FailureDTO>() {});
      return failureResponse.getCode();
    } catch (Exception e) {
      ErrorDTO errResponse = JsonUtils.asObject(response.errorBody().string(), new TypeReference<ErrorDTO>() {});
      return errResponse.getCode();
    }
  }
}
