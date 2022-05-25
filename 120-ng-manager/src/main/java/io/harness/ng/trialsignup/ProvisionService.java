/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.trialsignup;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;
import static io.harness.ng.NextGenModule.CONNECTOR_DECORATOR_SERVICE;

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
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.network.Http;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.trialsignup.ProvisionResponse.DelegateStatus;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.rest.RestResponse;
import io.harness.service.ScmClient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import retrofit2.Response;

@Slf4j
public class ProvisionService {
  @Inject SecretCrudService ngSecretService;
  @Inject DelegateNgManagerCgManagerClient delegateTokenNgClient;
  @Inject NextGenConfiguration configuration;
  @Inject LicenseService licenseService;
  @Inject @Named(CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  @Inject private ScmClient scmClient;
  //  @Inject private GitSyncConnectorHelper gitSyncConnectorHelper;

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

  public ProvisionResponse.SetupStatus provisionCIResources(String accountId) {
    if (!licenceValid(accountId)) {
      return ProvisionResponse.SetupStatus.INCOMPATIBLE_LICENSE;
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

    if (connectorInfoDTO.getConnectorType() != ConnectorType.GITHUB
        && connectorInfoDTO.getConnectorType() != ConnectorType.BITBUCKET
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

  public List<UserRepoResponse> getAllUserRepos(
      String accountId, String orgIdentifier, String projectIdentifier, String repoRef) {
    Optional<ConnectorResponseDTO> connector =
        connectorService.getByRef(accountId, orgIdentifier, projectIdentifier, repoRef);
    connector.orElseThrow(
        () -> new InvalidArgumentsException(format("connector %s was not found in account %s", repoRef, accountId)));
    //    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(
    //        accountId, null, null, (ScmConnector) connector.get().getConnector().getConnectorConfig());
    return convertToUserRepo(scmClient.getAllUserRepos(null));
  }

  private List<UserRepoResponse> convertToUserRepo(GetUserReposResponse allUserRepos) {
    ArrayList userRepoResponses = new ArrayList();
    for (Repository userRepo : allUserRepos.getReposList()) {
      userRepoResponses.add(
          UserRepoResponse.builder().namespace(userRepo.getNamespace()).name(userRepo.getName()).build());
    }
    return userRepoResponses;
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
}
