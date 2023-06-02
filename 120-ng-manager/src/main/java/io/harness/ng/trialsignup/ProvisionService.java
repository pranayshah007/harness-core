/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.trialsignup;

import static io.harness.beans.FeatureName.HOSTED_BUILDS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;
import static io.harness.ng.NextGenModule.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.account.ProvisionStep;
import io.harness.account.ProvisionStep.ProvisionStepKeys;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSize;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.K8sConfigDetails;
import io.harness.delegate.beans.K8sPermissionType;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.network.Http;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.trialsignup.AutogenInput.AutogenInputBuilder;
import io.harness.ng.trialsignup.ProvisionResponse.DelegateStatus;
import io.harness.rest.RestResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import retrofit2.Response;

@Slf4j
public class ProvisionService {
  @Inject SecretCrudService ngSecretService;

  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private SecretManagerClientService ngSecret;
  @Inject FeatureFlagService featureFlagService;
  @Inject DelegateNgManagerCgManagerClient delegateTokenNgClient;
  @Inject NextGenConfiguration configuration;
  @Inject LicenseService licenseService;
  @Inject @Named(CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  @Inject ScmClient scmClient;
  @Inject TelemetryReporter telemetryReporter;
  @Inject DecryptionHelper decryptionHelper;

  private static final String K8S_CONNECTOR_NAME = "Harness Kubernetes Cluster";
  private static final String K8S_CONNECTOR_DESC =
      "Kubernetes Cluster Connector created by Harness for connecting to Harness Builds environment";
  private static final String K8S_CONNECTOR_IDENTIFIER = "Harness_Kubernetes_Cluster";

  private static final String K8S_DELEGATE_NAME = "harness-kubernetes-delegate";
  private static final String K8S_DELEGATE_DESC =
      "Kubernetes Delegate created by Harness for communication with Harness Kubernetes Cluster";

  private static final String DEFAULT_TOKEN = "default_token";

  private static final String GENERATE_SAMPLE_DELEGATE_CURL_COMMAND_FORMAT_STRING =
      "curl -s -X POST -H 'content-type: application/json' "
      + "--url https://app.harness.io/gateway/api/webhooks/WLwBdpY6scP0G9oNsGcX2BHrY4xH44W7r7HWYC94?accountId=gz4oUAlfSgONuOrWmphHif "
      + "-d '{\"application\":\"4qPkwP5dQI2JduECqGZpcg\","
      + "\"parameters\":{\"Environment\":\"%s\",\"delegate\":\"delegate-ci\","
      + "\"account_id\":\"%s\",\"account_id_short\":\"%s\",\"account_secret\":\"%s\"}}'";

  private static final String SAMPLE_DELEGATE_STATUS_ENDPOINT_FORMAT_STRING = "http://%s/account-%s.txt";
  private static final String PROVISION_STARTED = "Provision Started";
  private static final String PROVISION_COMPLETED = "Provision Completed";
  private static final String MODULE = "module";
  private static final String CI_MODULE = "CI";

  public ProvisionResponse.SetupStatus provisionCIResources(String accountId) {
    if (!provisioningAllowed(accountId)) {
      return ProvisionResponse.SetupStatus.PROVISIONING_DISABLED;
    }

    Boolean delegateUpsertStatus = updateDelegateGroup(accountId);
    if (!delegateUpsertStatus) {
      return ProvisionResponse.SetupStatus.DELEGATE_PROVISION_FAILURE;
    }

    Boolean installConnectorStatus = installConnector(accountId);
    if (!installConnectorStatus) {
      return ProvisionResponse.SetupStatus.DELEGATE_PROVISION_FAILURE;
    }

    Boolean delegateInstallStatus = installDelegate(accountId);
    if (!delegateInstallStatus) {
      return ProvisionResponse.SetupStatus.DELEGATE_PROVISION_FAILURE;
    }
    return ProvisionResponse.SetupStatus.SUCCESS;
  }

  private Boolean installDelegate(String accountId) {
    Response<RestResponse<String>> tokenRequest = null;
    String token;
    try {
      tokenRequest = delegateTokenNgClient.getDelegateTokenValue(accountId, null, null, DEFAULT_TOKEN).execute();
    } catch (IOException e) {
      log.error("failed to fetch delegate token from Manager", e);
      return FALSE;
    }

    if (tokenRequest.isSuccessful()) {
      token = tokenRequest.body().getResource();
    } else {
      log.error(format("failed to fetch delegate token from Manager. error is %s", tokenRequest.errorBody()));
      return FALSE;
    }

    // TODO(Aman) assert for trial account

    String script = format(GENERATE_SAMPLE_DELEGATE_CURL_COMMAND_FORMAT_STRING, configuration.getSignupTargetEnv(),
        accountId, getAccountIdentifier(accountId), token);
    Logger scriptLogger = LoggerFactory.getLogger("generate-delegate-" + accountId);
    try {
      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .timeout(10, TimeUnit.MINUTES)
                                            .command("/bin/bash", "-c", script)
                                            .readOutput(true)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                scriptLogger.info(line);
                                              }
                                            })
                                            .redirectError(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                scriptLogger.error(line);
                                              }
                                            });
      int exitCode = processExecutor.execute().getExitValue();
      if (exitCode == 0) {
        return TRUE;
      }
      log.error("Curl script to generate delegate returned non-zero exit code: {}", exitCode);
    } catch (IOException e) {
      log.error("Error executing generate delegate curl command", e);
    } catch (InterruptedException e) {
      log.info("Interrupted", e);
    } catch (TimeoutException e) {
      log.info("Timed out", e);
    }
    String err = "Failed to provision";
    log.warn(err);
    return FALSE;
  }

  private Boolean installConnector(String accountId) {
    try {
      Optional<ConnectorResponseDTO> connectorResponseDTO =
          connectorService.get(accountId, null, null, K8S_CONNECTOR_IDENTIFIER);
      if (connectorResponseDTO.isPresent()) {
        return TRUE;
      }

      HashMap<String, Object> provisionMap = new HashMap<>();
      provisionMap.put(MODULE, CI_MODULE);
      telemetryReporter.sendTrackEvent(PROVISION_STARTED, null, accountId, provisionMap,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);

      KubernetesCredentialDTO kubernetesCredentialDTO =
          KubernetesCredentialDTO.builder()
              .kubernetesCredentialType(KubernetesCredentialType.INHERIT_FROM_DELEGATE)
              .build();

      KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
          KubernetesClusterConfigDTO.builder()
              .credential(kubernetesCredentialDTO)
              .delegateSelectors(new HashSet<>(Collections.singletonList(K8S_DELEGATE_NAME)))
              .build();

      ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                              .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                                              .identifier(K8S_CONNECTOR_IDENTIFIER)
                                              .name(K8S_CONNECTOR_NAME)
                                              .description(K8S_CONNECTOR_DESC)
                                              .connectorConfig(kubernetesClusterConfigDTO)
                                              .build();

      ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();

      ConnectorResponseDTO connectorResponse = connectorService.create(connectorDTO, accountId);
    } catch (Exception e) {
      log.error("Error adding hosted k8s connector", e);
      return FALSE;
    }

    return true;
  }

  Boolean updateDelegateGroup(String accountId) {
    DelegateSetupDetails delegateSetupDetails =
        DelegateSetupDetails.builder()
            .name(K8S_DELEGATE_NAME)
            .description(K8S_DELEGATE_DESC)
            .size(DelegateSize.SMALL)
            .k8sConfigDetails(K8sConfigDetails.builder()
                                  .k8sPermissionType(K8sPermissionType.NAMESPACE_ADMIN)
                                  .namespace(accountId)
                                  .build())
            .delegateType(DelegateType.KUBERNETES)
            .tokenName(DEFAULT_TOKEN)
            .build();

    try {
      Response<RestResponse<DelegateGroup>> delegateGroup =
          delegateTokenNgClient.upsert(K8S_DELEGATE_NAME, accountId, delegateSetupDetails).execute();
      if (delegateGroup == null) {
        log.error("Upserting delegate group failed. Account ID {}", accountId);
        return FALSE;
      }
    } catch (IOException e) {
      log.error("Upserting delegate group failed. Account ID {}. Exception:", accountId, e);
      return FALSE;
    }

    return TRUE;
  }

  /*
      Response from the delegate status service is of format:
      [{
        step: 'Delegate Ready',
        done: false / true
      }]
   */
  public DelegateStatus getDelegateInstallStatus(String accountId) {
    try {
      String url = format(SAMPLE_DELEGATE_STATUS_ENDPOINT_FORMAT_STRING, configuration.getDelegateStatusEndpoint(),
          getAccountIdentifier(accountId));
      log.info("Fetching delegate provisioning progress for account {} from {}", accountId, url);
      String result = Http.getResponseStringFromUrl(url, 30, 10).trim();
      if (isNotEmpty(result)) {
        log.info("Provisioning progress for account {}: {}", accountId, result);
        if (result.contains("<title>404 Not Found</title>")) {
          return DelegateStatus.IN_PROGRESS;
        }
        List<ProvisionStep> steps = new ArrayList<>();
        for (JsonElement element : new JsonParser().parse(result).getAsJsonArray()) {
          JsonObject jsonObject = element.getAsJsonObject();
          steps.add(ProvisionStep.builder()
                        .step(jsonObject.get(ProvisionStepKeys.step).getAsString())
                        .done(jsonObject.get(ProvisionStepKeys.done).getAsBoolean())
                        .build());
        }
        if (steps.size() > 0 && steps.get(0).isDone()) {
          HashMap<String, Object> provisionMap = new HashMap<>();
          provisionMap.put(MODULE, CI_MODULE);
          telemetryReporter.sendTrackEvent(PROVISION_COMPLETED, null, accountId, provisionMap,
              Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
          return DelegateStatus.SUCCESS;
        } else if (steps.size() > 0 && !steps.get(0).isDone()) {
          return DelegateStatus.IN_PROGRESS;
        }
      }
      return DelegateStatus.FAILURE;
    } catch (SocketTimeoutException e) {
      // Timed out for some reason. Return empty list to indicate unknown progress. UI can ignore and try again.
      log.info(format("Timed out getting progress. Returning empty list for account: %s", accountId));
      return DelegateStatus.IN_PROGRESS;
    } catch (IOException e) {
      throw new UnexpectedException(
          format("Exception in fetching delegate provisioning progress for account %s", accountId), e);
    }
  }

  public ScmConnectorResponse createDefaultScm(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnectorDTO scmConnectorDTO) {
    ConnectorResponseDTO connectorResponseDTO = null;
    SecretResponseWrapper secretResponseWrapper = null;
    ScmConnectorResponse scmConnectorResponse = null;

    SecretDTOV2 secretDTOV2 = scmConnectorDTO.getSecret();
    ConnectorInfoDTO connectorInfoDTO = scmConnectorDTO.getConnectorInfo();

    if (connectorInfoDTO.getConnectorType() != GITHUB && connectorInfoDTO.getConnectorType() != ConnectorType.BITBUCKET
        && connectorInfoDTO.getConnectorType() != ConnectorType.GITLAB) {
      log.error("Connector type for SCM not valid: {}", connectorInfoDTO.getConnectorType());
      return ScmConnectorResponse.builder()
          .connectorValidationResult(ConnectorValidationResult.builder()
                                         .status(ConnectivityStatus.FAILURE)
                                         .errorSummary("Connector type for SCM not valid")
                                         .build())
          .build();
    }

    Optional<SecretResponseWrapper> secretResponseWrapperOptional =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2.getIdentifier());

    if (secretResponseWrapperOptional.isPresent()) {
      secretResponseWrapper = ngSecretService.update(
          accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2.getIdentifier(), secretDTOV2);
    } else {
      secretResponseWrapper = ngSecretService.create(accountIdentifier, secretDTOV2);
    }

    Optional<ConnectorResponseDTO> connectorResponseDTOOptional =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorInfoDTO.getIdentifier());

    if (!connectorResponseDTOOptional.isPresent()) {
      connectorResponseDTO =
          connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    } else {
      connectorResponseDTO =
          connectorService.update(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }

    ConnectorValidationResult connectorValidationResult = connectorService.testConnection(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorInfoDTO.getIdentifier());

    return ScmConnectorResponse.builder()
        .connectorResponseDTO(connectorResponseDTO)
        .secretResponseWrapper(secretResponseWrapper)
        .connectorValidationResult(connectorValidationResult)
        .build();
  }

  // project / org and account information will always come from UI.
  // based on the connectorIdentifier, decide the type of connector.
  // for account type connectors expecting repo to come like : harness-core or harness/harness-core
  // for repo type connectors expecting repo to be empty.
  public String generateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String repo, String version) {
    BaseNGAccess build = BaseNGAccess.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .build();
    // check if `account.` needs to be trimmed in case of
    Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    if (!connectorResponseDTO.isPresent()) {
      throw new InvalidRequestException(String.format("connector %s doesn't exists", connectorIdentifier));
    }
    AutogenInputBuilder builder;
    ConnectorInfoDTO connectorConfig = connectorResponseDTO.get().getConnector();
    if (connectorResponseDTO.get().getConnector().getConnectorType() == GITHUB) {
      builder = getGithubCredentials(build, connectorConfig);
    } else if (connectorResponseDTO.get().getConnector().getConnectorType() == GITLAB) {
      builder = getGitlabCredentials(build, connectorConfig);
    } else if (connectorResponseDTO.get().getConnector().getConnectorType() == BITBUCKET) {
      builder = getBitbucketCredentials(build, connectorConfig);
    } else if (connectorResponseDTO.get().getConnector().getConnectorType() == AZURE_REPO) {
      builder = getAzureCredentials(build, connectorConfig);
    } else {
      builder = AutogenInput.builder();
    }
    AutogenInput autogenInput = builder.build();
    return scmClient.autogenerateStageYamlForCI(updateUrl(autogenInput, repo), version).getYaml();
  }

  @NotNull
  private AutogenInputBuilder getAzureCredentials(BaseNGAccess build, ConnectorInfoDTO connectorConfig) {
    AutogenInputBuilder builder;
    builder = AutogenInput.builder();
    AzureRepoConnectorDTO connectorDTO = (AzureRepoConnectorDTO) connectorConfig.getConnectorConfig();
    AzureRepoAuthenticationDTO credential = connectorDTO.getAuthentication();
    AzureRepoHttpCredentialsDTO credentials = (AzureRepoHttpCredentialsDTO) credential.getCredentials();
    AzureRepoUsernameTokenDTO httpCredentialsSpec = (AzureRepoUsernameTokenDTO) credentials.getHttpCredentialsSpec();
    List<EncryptedDataDetail> authenticationEncryptedDataDetails =
        ngSecret.getEncryptionDetails(build, httpCredentialsSpec);
    AzureRepoUsernameTokenDTO decrypt =
        (AzureRepoUsernameTokenDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
    builder.username(httpCredentialsSpec.getUsername())
        .password(String.copyValueOf(decrypt.getTokenRef().getDecryptedValue()))
        .repo(connectorDTO.getGitConnectionUrl());
    return builder;
  }

  private AutogenInputBuilder getGithubCredentials(BaseNGAccess ngAccess, ConnectorInfoDTO connectorConfig) {
    GithubConnectorDTO connectorConfig1 = (GithubConnectorDTO) connectorConfig.getConnectorConfig();
    AutogenInputBuilder inputBuilder = AutogenInput.builder();
    inputBuilder.repo(connectorConfig1.getUrl());
    GithubAuthenticationDTO authentication = connectorConfig1.getAuthentication();
    GithubHttpCredentialsDTO credentials = (GithubHttpCredentialsDTO) authentication.getCredentials();
    GithubHttpAuthenticationType type = credentials.getType();
    if (type == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
      GithubUsernamePasswordDTO httpCredentialsSpec = (GithubUsernamePasswordDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GithubUsernamePasswordDTO decrypt =
          (GithubUsernamePasswordDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getPasswordRef().getDecryptedValue()));
    } else if (type == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
      GithubUsernameTokenDTO httpCredentialsSpec = (GithubUsernameTokenDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GithubUsernameTokenDTO decrypt =
          (GithubUsernameTokenDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getTokenRef().getDecryptedValue()));
    } else if (type == GithubHttpAuthenticationType.OAUTH) {
      GithubOauthDTO httpCredentialsSpec = (GithubOauthDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GithubOauthDTO decrypt =
          (GithubOauthDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getTokenRef().getDecryptedValue()));
    }
    return inputBuilder;
  }

  private AutogenInputBuilder getGitlabCredentials(BaseNGAccess ngAccess, ConnectorInfoDTO connectorConfig) {
    AutogenInputBuilder inputBuilder = AutogenInput.builder();
    GitlabConnectorDTO connectorConfig1 = (GitlabConnectorDTO) connectorConfig.getConnectorConfig();
    inputBuilder.repo(connectorConfig1.getUrl());
    GitlabAuthenticationDTO authentication = connectorConfig1.getAuthentication();
    GitlabHttpCredentialsDTO credentials = (GitlabHttpCredentialsDTO) authentication.getCredentials();
    GitlabHttpAuthenticationType type = credentials.getType();
    if (type == GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD) {
      GitlabUsernamePasswordDTO httpCredentialsSpec = (GitlabUsernamePasswordDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GitlabUsernamePasswordDTO decrypt =
          (GitlabUsernamePasswordDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getPasswordRef().getDecryptedValue()));
    } else if (type == GitlabHttpAuthenticationType.USERNAME_AND_TOKEN) {
      GitlabUsernameTokenDTO httpCredentialsSpec = (GitlabUsernameTokenDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GitlabUsernameTokenDTO decrypt =
          (GitlabUsernameTokenDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getTokenRef().getDecryptedValue()));
    } else if (type == GitlabHttpAuthenticationType.OAUTH) {
      GitlabOauthDTO httpCredentialsSpec = (GitlabOauthDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      GitlabOauthDTO decrypt =
          (GitlabOauthDTO) decryptionHelper.decrypt(httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getTokenRef().getDecryptedValue()));
    }
    return inputBuilder;
  }

  private AutogenInputBuilder getBitbucketCredentials(BaseNGAccess ngAccess, ConnectorInfoDTO connectorConfig) {
    AutogenInputBuilder inputBuilder = AutogenInput.builder();
    BitbucketConnectorDTO connectorConfig1 = (BitbucketConnectorDTO) connectorConfig.getConnectorConfig();
    inputBuilder.repo(connectorConfig1.getUrl());
    BitbucketAuthenticationDTO authentication = connectorConfig1.getAuthentication();
    BitbucketHttpCredentialsDTO credentials = (BitbucketHttpCredentialsDTO) authentication.getCredentials();
    BitbucketHttpAuthenticationType type = credentials.getType();
    if (type == BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD) {
      BitbucketUsernamePasswordDTO httpCredentialsSpec =
          (BitbucketUsernamePasswordDTO) credentials.getHttpCredentialsSpec();
      List<EncryptedDataDetail> authenticationEncryptedDataDetails =
          ngSecret.getEncryptionDetails(ngAccess, httpCredentialsSpec);
      BitbucketUsernamePasswordDTO decrypt = (BitbucketUsernamePasswordDTO) decryptionHelper.decrypt(
          httpCredentialsSpec, authenticationEncryptedDataDetails);
      inputBuilder.password(String.copyValueOf(decrypt.getPasswordRef().getDecryptedValue()));
    }
    return inputBuilder;
  }

  public void refreshCode(String clientId, String clientSecret, String endpoint, String refreshCode) {
    scmClient.refreshToken(null, clientId, clientSecret, endpoint, refreshCode);
  }

  private boolean provisioningAllowed(String accountId) {
    return featureFlagService.isEnabled(HOSTED_BUILDS, accountId) || licenceValid(accountId);
  }
  private boolean licenceValid(String accountId) {
    ModuleLicenseDTO moduleLicenseDTO = licenseService.getModuleLicense(accountId, ModuleType.CI);

    if (moduleLicenseDTO == null) {
      log.info("Empty licence");
      return false;
    }

    if ((moduleLicenseDTO.getEdition() == Edition.FREE)
        || (moduleLicenseDTO.getLicenseType() == LicenseType.TRIAL
            && moduleLicenseDTO.getStatus() == LicenseStatus.ACTIVE)) {
      return true;
    }

    log.info("Incompatible licence provided: {}:{}:{}", moduleLicenseDTO.getEdition(),
        moduleLicenseDTO.getLicenseType(), moduleLicenseDTO.getStatus());

    return false;
  }

  @VisibleForTesting
  String updateUrl(AutogenInput input, String inputRepo) {
    String cloneUrl = "";
    String userName = input.getUsername();
    if (StringUtils.isEmpty(userName)) {
      userName = "default";
    }
    String repo = input.getRepo();
    URI uri;
    try {
      uri = new URI(repo);
    } catch (URISyntaxException e) {
      throw new InvalidRequestException("format of url is incorrect");
    }

    String urlPath = uri.getPath();
    String[] split = urlPath.split("/");
    if (StringUtils.isEmpty(urlPath) || urlPath.equals("/")) {
      cloneUrl = input.getRepo() + "/" + inputRepo;
    } else if (split.length == 2) {
      // account level
      cloneUrl = input.getRepo() + inputRepo.substring(inputRepo.lastIndexOf('/'));
    } else {
      // repo level
      // do nothing as the url already contains complete path
      cloneUrl = input.getRepo();
    }

    cloneUrl = cloneUrl.replace("https://", "");

    // sanity to remove multiple // in url path
    cloneUrl = cloneUrl.replace("//", "/");
    cloneUrl = "https://" + userName + ":" + input.getPassword() + "@" + cloneUrl;
    return cloneUrl;
  }
}
