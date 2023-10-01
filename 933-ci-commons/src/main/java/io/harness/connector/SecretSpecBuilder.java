/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.FILE;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;
import static io.harness.govern.Switch.unhandled;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitSecretKeyAccessKeyDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.git.GitTokenRetriever;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.secrets.SecretDecryptor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to create spec for image registry and GIT secrets. Generated spec can be used for creation of secrets on
 * a K8 cluster.
 */

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class SecretSpecBuilder {
  private static final String SOURCE = "123456789bcdfghjklmnpqrstvwxyz";
  public static final Integer RANDOM_LENGTH = 8;
  private static final SecureRandom random = new SecureRandom();

  public static final String DOCKER_REGISTRY_SECRET_TYPE = "kubernetes.io/dockercfg";
  public static final String SECRET_KEY = "secret_key";
  public static final String SECRET = "secret";
  public static final String OPAQUE_SECRET_TYPE = "opaque";
  private static final String DRONE_NETRC_PASSWORD = "DRONE_NETRC_PASSWORD";
  private static final String DRONE_NETRC_USERNAME = "DRONE_NETRC_USERNAME";
  private static final String DRONE_AWS_ACCESS_KEY = "DRONE_AWS_ACCESS_KEY";
  private static final String DRONE_AWS_SECRET_KEY = "DRONE_AWS_SECRET_KEY";

  private static final String DRONE_SSH_KEY = "DRONE_SSH_KEY";

  @Inject private ConnectorEnvVariablesHelper connectorEnvVariablesHelper;
  @Inject private SecretDecryptor secretDecryptor;
  @Inject private ImageSecretBuilder imageSecretBuilder;
  @Inject private GitTokenRetriever gitTokenRetriever;
  @Inject private GithubService githubService;

  public Map<String, SecretParams> decryptCustomSecretVariables(
      List<SecretVariableDetails> secretVariableDetails, Map<String, SecretVariableDTO> cache) {
    Map<String, SecretParams> data = new HashMap<>();
    if (isNotEmpty(secretVariableDetails)) {
      for (SecretVariableDetails secretVariableDetail : secretVariableDetails) {
        log.info("Decrypting custom variable name:[{}], type:[{}], secretRef:[{}]",
            secretVariableDetail.getSecretVariableDTO().getName(),
            secretVariableDetail.getSecretVariableDTO().getType(),
            secretVariableDetail.getSecretVariableDTO().getSecret().toSecretRefStringValue());
        SecretVariableDTO secretVariableDTO;

        if (cache.containsKey(secretVariableDetail.getSecretVariableDTO().getName())) {
          secretVariableDTO = cache.get(secretVariableDetail.getSecretVariableDTO().getName());
        } else {
          secretVariableDTO = (SecretVariableDTO) secretDecryptor.decrypt(
              secretVariableDetail.getSecretVariableDTO(), secretVariableDetail.getEncryptedDataDetailList());
          cache.put(secretVariableDetail.getSecretVariableDTO().getName(), secretVariableDTO);
        }

        log.info("Decrypted custom variable name:[{}], type:[{}], secretRef:[{}]",
            secretVariableDetail.getSecretVariableDTO().getName(),
            secretVariableDetail.getSecretVariableDTO().getType(),
            secretVariableDetail.getSecretVariableDTO().getSecret().toSecretRefStringValue());
        String validK8SecretName = getValidK8SecretIdentifier(secretVariableDTO.getName());
        switch (secretVariableDTO.getType()) {
          case FILE:
            data.put(validK8SecretName,
                SecretParams.builder()
                    .secretKey(SECRET_KEY + validK8SecretName)
                    .type(FILE)
                    .value(encodeBase64(secretVariableDTO.getSecret().getDecryptedValue()))
                    .build());
            break;
          case TEXT:
            data.put(validK8SecretName,
                SecretParams.builder()
                    .secretKey(SECRET_KEY + validK8SecretName)
                    .type(TEXT)
                    .value(encodeBase64(secretVariableDTO.getSecret().getDecryptedValue()))
                    .build());
            break;
          default:
            unhandled(secretVariableDTO.getType());
        }
      }
    }

    return data;
  }

  public Map<String, SecretParams> decryptConnectorSecretVariables(Map<String, ConnectorDetails> connectorDetailsMap) {
    Map<String, SecretParams> secretData = new HashMap<>();
    if (isEmpty(connectorDetailsMap)) {
      return secretData;
    }

    for (Map.Entry<String, ConnectorDetails> connectorDetailsEntry : connectorDetailsMap.entrySet()) {
      ConnectorDetails connectorDetails = connectorDetailsEntry.getValue();
      secretData.putAll(decryptConnectorSecret(connectorDetails));
    }
    return secretData;
  }

  public Map<String, SecretParams> decryptConnectorSecret(ConnectorDetails connectorDetails) {
    ConnectorType connectorType = connectorDetails.getConnectorType();
    log.info("Decrypting connector id:[{}], type:[{}]", connectorDetails.getIdentifier(), connectorType);
    Map<String, SecretParams> secretParamsMap = new HashMap<>();
    if (connectorType == ConnectorType.DOCKER) {
      secretParamsMap = connectorEnvVariablesHelper.getDockerSecretVariables(connectorDetails);
    } else if (connectorType == ConnectorType.AWS) {
      secretParamsMap = connectorEnvVariablesHelper.getAwsSecretVariables(connectorDetails);
    } else if (connectorType == ConnectorType.GCP) {
      secretParamsMap = connectorEnvVariablesHelper.getGcpSecretVariables(connectorDetails);
    } else if (connectorType == ConnectorType.ARTIFACTORY) {
      secretParamsMap = connectorEnvVariablesHelper.getArtifactorySecretVariables(connectorDetails);
    } else if (connectorDetails.getConnectorType() == ConnectorType.AZURE) {
      secretParamsMap = connectorEnvVariablesHelper.getAzureSecretVariables(connectorDetails);
    } else if (isScmConnectorType(connectorType)) {
      secretParamsMap = decryptGitSecretVariables(connectorDetails);
    } else {
      log.info("Decrypting connector of unknown type: {}", connectorDetails.getConnectorType());
    }
    log.info("Decrypted connector id:[{}], type:[{}]", connectorDetails.getIdentifier(),
        connectorDetails.getConnectorType());
    return secretParamsMap;
  }

  public Map<String, SecretParams> decryptGitSecretVariables(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      return new HashMap<>();
    }

    if (gitConnector.getConnectorType() == ConnectorType.GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveGitHubSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveAzureRepoSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveGitlabSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveBitbucketSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveAwsCodeCommitSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return retrieveGitSecretParams(gitConfigDTO, gitConnector);
    } else if (gitConnector.getConnectorType() == ConnectorType.HARNESS) {
      HarnessConnectorDTO gitConfigDTO = (HarnessConnectorDTO) gitConnector.getConnectorConfig();
      return retrieveHarnessSecretParams(gitConfigDTO, gitConnector);
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }
  }

  private Map<String, SecretParams> retrieveGitSecretParams(GitConfigDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    if (gitConnector == null) {
      return secretData;
    }
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    log.info(
        "Decrypting git connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
    if (gitConfigDTO.getGitAuthType() == GitAuthType.HTTP) {
      GitHTTPAuthenticationDTO gitHTTPAuthenticationDTO = (GitHTTPAuthenticationDTO) secretDecryptor.decrypt(
          gitConfigDTO.getGitAuth(), gitConnector.getEncryptedDataDetails());

      String key = DRONE_NETRC_PASSWORD;
      secretData.put(key,
          SecretParams.builder()
              .secretKey(key + uniqueIdentifier)
              .value(encodeBase64(new String(gitHTTPAuthenticationDTO.getPasswordRef().getDecryptedValue())))
              .type(TEXT)
              .build());
    } else if (gitConfigDTO.getGitAuthType() == GitAuthType.SSH) {
      SSHKeyDetails sshKeyDetails = gitConnector.getSshKeyDetails();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      SecretRefData key = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
      if (key == null || isEmpty(key.getDecryptedValue())) {
        throw new CIStageExecutionException("Git connector should have not empty sshKey");
      }
      char[] sshKey = key.getDecryptedValue();
      secretData.put(DRONE_SSH_KEY,
          SecretParams.builder()
              .secretKey(DRONE_SSH_KEY + uniqueIdentifier)
              .value(encodeBase64(sshKey))
              .type(TEXT)
              .build());
    }
    return secretData;
  }

  private Map<String, SecretParams> retrieveAwsCodeCommitSecretParams(
      AwsCodeCommitConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();

    if (gitConfigDTO.getAuthentication().getAuthType() == AwsCodeCommitAuthType.HTTPS) {
      log.info("Decrypting AwsCodeCommit connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());

      gitConfigDTO.getDecryptableEntities().forEach(
          decryptableEntity -> secretDecryptor.decrypt(decryptableEntity, gitConnector.getEncryptedDataDetails()));

      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      AwsCodeCommitHttpsCredentialsDTO credentials =
          (AwsCodeCommitHttpsCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      if (credentials.getType() == AwsCodeCommitHttpsAuthType.ACCESS_KEY_AND_SECRET_KEY) {
        AwsCodeCommitSecretKeyAccessKeyDTO secretKeyAccessKeyDTO =
            (AwsCodeCommitSecretKeyAccessKeyDTO) credentials.getHttpCredentialsSpec();

        String accessKey = getSecretAsStringFromPlainTextOrSecretRef(
            secretKeyAccessKeyDTO.getAccessKey(), secretKeyAccessKeyDTO.getAccessKeyRef());
        if (isEmpty(accessKey)) {
          throw new CIStageExecutionException(
              "AwsCodeCommit connector should have not empty accessKey and accessKeyRef");
        }
        secretData.put(DRONE_AWS_ACCESS_KEY,
            SecretParams.builder().secretKey(DRONE_AWS_ACCESS_KEY).value(encodeBase64(accessKey)).type(TEXT).build());

        if (secretKeyAccessKeyDTO.getSecretKeyRef() == null) {
          throw new CIStageExecutionException("AwsCodeCommit connector should have not empty secretKeyRef");
        }
        String secretKey = String.valueOf(secretKeyAccessKeyDTO.getSecretKeyRef().getDecryptedValue());
        if (isEmpty(secretKey)) {
          throw new CIStageExecutionException("AwsCodeCommit connector should have not empty secretKeyRef");
        }
        secretData.put(DRONE_AWS_SECRET_KEY,
            SecretParams.builder().secretKey(DRONE_AWS_SECRET_KEY).value(encodeBase64(secretKey)).type(TEXT).build());
      }
    } else {
      throw new CIStageExecutionException(
          "Unsupported github connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
    return secretData;
  }

  public static String getSecretName(String podName) {
    return podName + "-" + SECRET;
  }

  private Map<String, SecretParams> retrieveGitHubSecretParams(
      GithubConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GithubHttpCredentialsDTO gitHTTPAuthenticationDTO =
          (GithubHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();

      log.info("Decrypting GitHub connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());

      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          gitHTTPAuthenticationDTO.getHttpCredentialsSpec(), gitConnector.getEncryptedDataDetails());

      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      if (gitHTTPAuthenticationDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        GithubUsernamePasswordDTO githubUsernamePasswordDTO = (GithubUsernamePasswordDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            githubUsernamePasswordDTO.getUsername(), githubUsernamePasswordDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Github connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (githubUsernamePasswordDTO.getPasswordRef() == null) {
          throw new CIStageExecutionException("Github connector should have not empty passwordRef");
        }
        String password = String.valueOf(githubUsernamePasswordDTO.getPasswordRef().getDecryptedValue());
        if (isEmpty(password)) {
          throw new CIStageExecutionException(
              "Unsupported github connector auth" + gitConfigDTO.getAuthentication().getAuthType());
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(password))
                .type(TEXT)
                .build());

      } else if (gitHTTPAuthenticationDTO.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
        GithubUsernameTokenDTO githubUsernameTokenDTO = (GithubUsernameTokenDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            githubUsernameTokenDTO.getUsername(), githubUsernameTokenDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Github connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (githubUsernameTokenDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Github connector should have not empty tokenRef");
        }
        String token = String.valueOf(githubUsernameTokenDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Github connector should have not empty token");
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());

      } else if (gitHTTPAuthenticationDTO.getType() == GithubHttpAuthenticationType.OAUTH) {
        GithubOauthDTO githubOauthDTO = (GithubOauthDTO) decryptableEntity;

        String username = GithubOauthDTO.userName;
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Github connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());
        if (githubOauthDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Github connector should have not empty tokenRef");
        }
        String token = String.valueOf(githubOauthDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Github connector should have not empty token");
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());
      } else if (gitHTTPAuthenticationDTO.getType() == GithubHttpAuthenticationType.GITHUB_APP) {
        GithubAppDTO githubAppDTO = (GithubAppDTO) decryptableEntity;
        String username = GithubAppDTO.username;
        String token =
            githubService.getToken(GithubAppConfig.builder()
                                       .appId(getSecretAsStringFromPlainTextOrSecretRef(
                                           githubAppDTO.getApplicationId(), githubAppDTO.getApplicationIdRef()))
                                       .installationId(getSecretAsStringFromPlainTextOrSecretRef(
                                           githubAppDTO.getInstallationId(), githubAppDTO.getInstallationIdRef()))
                                       .privateKey(String.valueOf(githubAppDTO.getPrivateKeyRef().getDecryptedValue()))
                                       .githubUrl(GitClientHelper.getGithubApiURL(gitConfigDTO.getUrl()))
                                       .build());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Unable to get token for github app");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());
      } else {
        throw new CIStageExecutionException(
            "Unsupported github connector auth type" + gitHTTPAuthenticationDTO.getType());
      }

    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      SSHKeyDetails sshKeyDetails = gitConnector.getSshKeyDetails();
      log.info("Decrypting GitHub connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());

      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      SecretRefData key = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
      if (key == null || isEmpty(key.getDecryptedValue())) {
        throw new CIStageExecutionException("Github connector should have not empty sshKey");
      }
      char[] sshKey = key.getDecryptedValue();
      secretData.put(DRONE_SSH_KEY,
          SecretParams.builder()
              .secretKey(DRONE_SSH_KEY + uniqueIdentifier)
              .value(encodeBase64(sshKey))
              .type(TEXT)
              .build());

    } else {
      throw new CIStageExecutionException(
          "Unsupported github connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }

    return secretData;
  }

  private Map<String, SecretParams> retrieveHarnessSecretParams(
      HarnessConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      HarnessHttpCredentialsDTO gitHTTPAuthenticationDTO =
          (HarnessHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();

      log.info("Decrypting Harness connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());

      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          gitHTTPAuthenticationDTO.getHttpCredentialsSpec(), gitConnector.getEncryptedDataDetails());

      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      if (gitHTTPAuthenticationDTO.getType() == HarnessHttpAuthenticationType.USERNAME_AND_TOKEN) {
        HarnessUsernameTokenDTO harnessUsernameTokenDTO = (HarnessUsernameTokenDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(harnessUsernameTokenDTO.getUsername(), null);
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Harness connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (harnessUsernameTokenDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Harness connector should have not empty tokenRef");
        }
        String token = String.valueOf(harnessUsernameTokenDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Harness connector should have not empty token");
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());

      } else {
        throw new CIStageExecutionException(
            "Unsupported harness connector auth type" + gitHTTPAuthenticationDTO.getType());
      }

    } else {
      throw new CIStageExecutionException(
          "Unsupported github connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }

    return secretData;
  }

  private Map<String, SecretParams> retrieveAzureRepoSecretParams(
      AzureRepoConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      AzureRepoHttpCredentialsDTO gitHTTPAuthenticationDTO =
          (AzureRepoHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();

      log.info("Decrypting azure repo connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          gitHTTPAuthenticationDTO.getHttpCredentialsSpec(), gitConnector.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      if (gitHTTPAuthenticationDTO.getType() == AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN) {
        AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO = (AzureRepoUsernameTokenDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            azureRepoUsernameTokenDTO.getUsername(), azureRepoUsernameTokenDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Azure repo connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (azureRepoUsernameTokenDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Azure repo connector should have not empty tokenRef");
        }
        String token = String.valueOf(azureRepoUsernameTokenDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Azure repo connector should have not empty token");
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());

      } else {
        throw new CIStageExecutionException(
            "Unsupported Azure repo connector auth type" + gitHTTPAuthenticationDTO.getType());
      }
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      SSHKeyDetails sshKeyDetails = gitConnector.getSshKeyDetails();
      log.info("Decrypting azure repo connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      SecretRefData key = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
      if (key == null || isEmpty(key.getDecryptedValue())) {
        throw new CIStageExecutionException("Azure repo connector should have not empty sshKey");
      }
      char[] sshKey = key.getDecryptedValue();
      secretData.put(DRONE_SSH_KEY,
          SecretParams.builder()
              .secretKey(DRONE_SSH_KEY + uniqueIdentifier)
              .value(encodeBase64(sshKey))
              .type(TEXT)
              .build());
    } else {
      throw new CIStageExecutionException(
          "Unsupported azure repo connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
    return secretData;
  }

  private Map<String, SecretParams> retrieveGitlabSecretParams(
      GitlabConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      GitlabHttpCredentialsDTO gitHTTPAuthenticationDTO =
          (GitlabHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();

      log.info("Decrypting GitLab connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          gitHTTPAuthenticationDTO.getHttpCredentialsSpec(), gitConnector.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      if (gitHTTPAuthenticationDTO.getType() == GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        GitlabUsernamePasswordDTO gitlabHttpCredentialsSpecDTO = (GitlabUsernamePasswordDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            gitlabHttpCredentialsSpecDTO.getUsername(), gitlabHttpCredentialsSpecDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Gitlab connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (gitlabHttpCredentialsSpecDTO.getPasswordRef() == null) {
          throw new CIStageExecutionException("Gitlab connector should have not empty passwordRef");
        }
        String password = String.valueOf(gitlabHttpCredentialsSpecDTO.getPasswordRef().getDecryptedValue());
        if (isEmpty(password)) {
          throw new CIStageExecutionException(
              "Unsupported gitlab connector auth" + gitConfigDTO.getAuthentication().getAuthType());
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(password))
                .type(TEXT)
                .build());

      } else if (gitHTTPAuthenticationDTO.getType() == GitlabHttpAuthenticationType.USERNAME_AND_TOKEN) {
        GitlabUsernameTokenDTO gitlabUsernameTokenDTO = (GitlabUsernameTokenDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            gitlabUsernameTokenDTO.getUsername(), gitlabUsernameTokenDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Gitlab connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (gitlabUsernameTokenDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Gitlab connector should have not empty tokenRef");
        }
        String token = String.valueOf(gitlabUsernameTokenDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(token)) {
          throw new CIStageExecutionException("Gitlab connector should have not empty token");
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(token))
                .type(TEXT)
                .build());

      } else if (gitHTTPAuthenticationDTO.getType() == GitlabHttpAuthenticationType.OAUTH) {
        GitlabOauthDTO gitlabOauthDTO = (GitlabOauthDTO) decryptableEntity;

        String username = GitlabOauthDTO.userName;

        if (isEmpty(username)) {
          throw new CIStageExecutionException("Gitlab connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (gitlabOauthDTO.getTokenRef() == null) {
          throw new CIStageExecutionException("Gitlab connector should have not empty tokenRef");
        }
        String password = String.valueOf(gitlabOauthDTO.getTokenRef().getDecryptedValue());
        if (isEmpty(password)) {
          throw new CIStageExecutionException(
              "Unsupported gitlab connector auth:" + gitConfigDTO.getAuthentication().getAuthType());
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(password))
                .type(TEXT)
                .build());
      } else {
        throw new CIStageExecutionException(
            "Unsupported gitlab connector auth type" + gitHTTPAuthenticationDTO.getType());
      }
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      SSHKeyDetails sshKeyDetails = gitConnector.getSshKeyDetails();
      log.info("Decrypting GitLab connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      SecretRefData key = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
      if (key == null || isEmpty(key.getDecryptedValue())) {
        throw new CIStageExecutionException("Gitlab connector should have not empty sshKey");
      }
      char[] sshKey = key.getDecryptedValue();
      secretData.put(DRONE_SSH_KEY,
          SecretParams.builder()
              .secretKey(DRONE_SSH_KEY + uniqueIdentifier)
              .value(encodeBase64(sshKey))
              .type(TEXT)
              .build());
    } else {
      throw new CIStageExecutionException(
          "Unsupported gitlab connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }

    return secretData;
  }

  private Map<String, SecretParams> retrieveBitbucketSecretParams(
      BitbucketConnectorDTO gitConfigDTO, ConnectorDetails gitConnector) {
    Map<String, SecretParams> secretData = new HashMap<>();
    String uniqueIdentifier = "_" + generateRandomAlphaNumericString(RANDOM_LENGTH);

    if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
      BitbucketHttpCredentialsDTO gitHTTPAuthenticationDTO =
          (BitbucketHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      log.info("Decrypting Bitbucket connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          gitHTTPAuthenticationDTO.getHttpCredentialsSpec(), gitConnector.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      if (gitHTTPAuthenticationDTO.getType() == BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        BitbucketUsernamePasswordDTO bitbucketHttpCredentialsSpecDTO = (BitbucketUsernamePasswordDTO) decryptableEntity;

        String username = getSecretAsStringFromPlainTextOrSecretRef(
            bitbucketHttpCredentialsSpecDTO.getUsername(), bitbucketHttpCredentialsSpecDTO.getUsernameRef());
        if (isEmpty(username)) {
          throw new CIStageExecutionException("Bitbucket connector should have not empty username");
        }
        secretData.put(DRONE_NETRC_USERNAME,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_USERNAME + uniqueIdentifier)
                .value(encodeBase64(username))
                .type(TEXT)
                .build());

        if (bitbucketHttpCredentialsSpecDTO.getPasswordRef() == null) {
          throw new CIStageExecutionException("Bitbucket connector should have not empty passwordRef");
        }
        String password = String.valueOf(bitbucketHttpCredentialsSpecDTO.getPasswordRef().getDecryptedValue());
        if (isEmpty(password)) {
          throw new CIStageExecutionException(
              "Unsupported bitbucket connector auth" + gitConfigDTO.getAuthentication().getAuthType());
        }
        secretData.put(DRONE_NETRC_PASSWORD,
            SecretParams.builder()
                .secretKey(DRONE_NETRC_PASSWORD + uniqueIdentifier)
                .value(encodeBase64(password))
                .type(TEXT)
                .build());
      }
    } else if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.SSH) {
      SSHKeyDetails sshKeyDetails = gitConnector.getSshKeyDetails();
      log.info("Decrypting Bitbucket connector id:[{}], type:[{}]", gitConnector.getIdentifier(),
          gitConnector.getConnectorType());
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(sshKeyDetails.getSshKeyReference(), sshKeyDetails.getEncryptedDataDetails());
      log.info("Decrypted connector id:[{}], type:[{}]", gitConnector.getIdentifier(), gitConnector.getConnectorType());
      SecretRefData key = ((SSHKeyReferenceCredentialDTO) decryptableEntity).getKey();
      if (key == null || isEmpty(key.getDecryptedValue())) {
        throw new CIStageExecutionException("Bitbucket connector should have not empty sshKey");
      }
      char[] sshKey = key.getDecryptedValue();
      secretData.put(DRONE_SSH_KEY,
          SecretParams.builder()
              .secretKey(DRONE_SSH_KEY + uniqueIdentifier)
              .value(encodeBase64(sshKey))
              .type(TEXT)
              .build());
    } else {
      throw new CIStageExecutionException(
          "Unsupported bitbucket connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }

    return secretData;
  }

  public Map<String, SecretParams> fetchGithubAppToken(Map<String, ConnectorDetails> gitConnectors) {
    Map<String, SecretParams> secretParamsMap = new HashMap<>();
    if (isEmpty(gitConnectors)) {
      return secretParamsMap;
    }
    gitConnectors.forEach((key, gitConnector) -> {
      if (gitConnector.getConnectorType() != GITHUB) {
        throw new CIStageExecutionException(format(
            "Github token functor requires git hub connector, but %s was provided.", gitConnector.getConnectorType()));
      }
      String authToken = gitTokenRetriever.retrieveAuthToken(GitSCMType.GITHUB, gitConnector);
      secretParamsMap.put(key, SecretParams.builder().secretKey(key).type(TEXT).value(encodeBase64(authToken)).build());
    });
    return secretParamsMap;
  }

  public Map<String, SecretParams> createSecretParamsForPlainTextSecret(
      Map<String, String> envVarsWithSecretRef, String containerName) {
    Map<String, SecretParams> secretParamsMap = new HashMap<>();
    envVarsWithSecretRef.forEach((varName, varValue)
                                     -> secretParamsMap.put(varName,
                                         SecretParams.builder()
                                             .secretKey(SECRET_KEY + "_" + varName + "_" + containerName)
                                             .type(TEXT)
                                             .value(encodeBase64(varValue))
                                             .build()));
    return secretParamsMap;
  }

  private static String generateRandomAlphaNumericString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SOURCE.charAt(random.nextInt(SOURCE.length())));
    }
    return sb.toString();
  }

  public static boolean isScmConnectorType(ConnectorType type) {
    return type == GITHUB || type == AZURE_REPO || type == GITLAB || type == BITBUCKET || type == CODECOMMIT
        || type == GIT;
  }

  private String getValidK8SecretIdentifier(String identifier) {
    if (isEmpty(identifier)) {
      return identifier;
    }
    return identifier.replaceAll("[^_a-zA-Z0-9]", "_");
  }
}
