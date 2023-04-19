/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.validator.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.HOSTS_NUMBER_VALIDATION_LIMIT;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.TRUE_STR;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractHostnameFromHost;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractPortFromHost;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.getPortOrSSHDefault;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.SSHTaskParams;
import io.harness.delegate.beans.WinRmTaskParams;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskParams;
import io.harness.delegate.beans.connector.pdcconnector.HostConnectivityTaskResponse;
import io.harness.delegate.beans.secrets.BaseConfigValidationTaskResponse;
import io.harness.delegate.task.utils.PhysicalDataCenterConstants;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.manage.ManagedExecutorService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class NGHostValidationServiceImpl implements NGHostValidationService {
  @Inject private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Inject private WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  @Inject private NGSecretServiceV2 ngSecretServiceV2;
  @Inject private TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @Inject NGErrorHelper ngErrorHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final Executor hostsConnectivityExecutor = new ManagedExecutorService(Executors.newFixedThreadPool(4));
  private final Executor hostsSSHExecutor = new ManagedExecutorService(Executors.newFixedThreadPool(4));

  @Override
  public List<HostValidationDTO> validateHostsConnectivity(@NotNull List<String> hosts,
      @Nullable String accountIdentifier, @Nullable String orgIdentifier, @Nullable String projectIdentifier,
      Set<String> delegateSelectors) {
    if (isEmpty(hosts)) {
      throw new InvalidArgumentsException("No hosts to test");
    }

    CompletableFutures<HostValidationDTO> validateHostSocketConnectivityTasks =
        new CompletableFutures<>(hostsConnectivityExecutor);
    for (String hostName : limitHosts(hosts)) {
      validateHostSocketConnectivityTasks.supplyAsync(()
                                                          -> validateHostConnectivity(hostName, accountIdentifier,
                                                              orgIdentifier, projectIdentifier, delegateSelectors));
    }

    return executeParallelTasks(validateHostSocketConnectivityTasks);
  }

  @Override
  public HostValidationDTO validateHostConnectivity(@NotNull String host, String accountIdentifier,
      @Nullable String orgIdentifier, @Nullable String projectIdentifier, Set<String> delegateSelectors) {
    if (isBlank(host)) {
      throw new InvalidArgumentsException("Host cannot be null or empty", USER_SRE);
    }

    String portOrSSHDefault = getPortOrSSHDefault(host);
    String hostName = extractHostnameFromHost(host).orElseThrow(
        ()
            -> new InvalidArgumentsException(
                format("Not found hostName, host: %s, extracted port: %s", host, portOrSSHDefault), USER_SRE));

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(accountIdentifier)
            .taskType(TaskType.NG_HOST_CONNECTIVITY_TASK.name())
            .taskParameters(HostConnectivityTaskParams.builder()
                                .hostName(hostName)
                                .port(Integer.parseInt(portOrSSHDefault))
                                .delegateSelectors(delegateSelectors)
                                .build())
            .taskSetupAbstractions(setupTaskAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .executionTimeout(Duration.ofSeconds(PhysicalDataCenterConstants.EXECUTION_TIMEOUT_IN_SECONDS))
            .build();

    log.info("Start host connectivity validation, host:{}, hostName:{}, accountIdent:{}, orgIdent:{}, projIdent:{},",
        host, hostName, accountIdentifier, orgIdentifier, projectIdentifier);
    DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);

    HostConnectivityTaskResponse responseData = (HostConnectivityTaskResponse) delegateResponseData;

    return HostValidationDTO.builder()
        .host(hostName)
        .status(HostValidationDTO.HostValidationStatus.fromBoolean(responseData.isConnectionSuccessful()))
        .error(responseData.isConnectionSuccessful() ? buildErrorDetails()
                                                     : buildErrorDetails(responseData.getErrorMessage()))
        .build();
  }

  @Override
  public List<HostValidationDTO> validateHosts(@NotNull List<String> hosts, @Nullable String accountIdentifier,
      @Nullable String orgIdentifier, @Nullable String projectIdentifier, @NotNull String secretIdentifierWithScope,
      @Nullable Set<String> delegateSelectors) {
    if (isEmpty(hosts)) {
      return Collections.emptyList();
    }
    if (isBlank(secretIdentifierWithScope)) {
      throw new InvalidArgumentsException("Secret identifier cannot be null or empty", USER_SRE);
    }

    CompletableFutures<HostValidationDTO> validateSSHHostTasks = new CompletableFutures<>(hostsSSHExecutor);
    for (String hostName : limitHosts(hosts)) {
      validateSSHHostTasks.supplyAsync(()
                                           -> validateHost(hostName, accountIdentifier, orgIdentifier,
                                               projectIdentifier, secretIdentifierWithScope, delegateSelectors));
    }

    return executeParallelTasks(validateSSHHostTasks);
  }

  @Override
  public HostValidationDTO validateHost(@NotNull String host, String accountIdentifier, @Nullable String orgIdentifier,
      @Nullable String projectIdentifier, @NotNull String secretIdentifierWithScope,
      @Nullable Set<String> delegateSelectors) {
    if (isBlank(host)) {
      throw new InvalidArgumentsException("Host cannot be null or empty", USER_SRE);
    }
    if (isBlank(secretIdentifierWithScope)) {
      throw new InvalidArgumentsException("Secret identifier cannot be null or empty", USER_SRE);
    }

    Optional<Secret> secretOptional =
        findSecret(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifierWithScope);
    if (!secretOptional.isPresent()) {
      throw new InvalidArgumentsException(
          format("Not found secret for host validation, secret identifier: %s", secretIdentifierWithScope), USER_SRE);
    }

    final Secret secret = secretOptional.get();
    final DelegateTaskRequest delegateTaskRequest;
    final String hostName;

    switch (secret.getType()) {
      case SSHKey:
        delegateTaskRequest = generateSshDelegateTaskRequest(
            secret, host, accountIdentifier, orgIdentifier, projectIdentifier, delegateSelectors);
        hostName = ((SSHTaskParams) delegateTaskRequest.getTaskParameters()).getHost();
        break;
      case WinRmCredentials:
        delegateTaskRequest = generateWinRmDelegateTaskRequest(
            secret, host, accountIdentifier, orgIdentifier, projectIdentifier, delegateSelectors);
        hostName = ((WinRmTaskParams) delegateTaskRequest.getTaskParameters()).getHost();
        break;
      default:
        throw new InvalidArgumentsException(
            format("Invalid secret type, secret identifier: %s", secretIdentifierWithScope), USER_SRE);
    }

    log.info(
        "Start validation host:{}, hostName:{}, secretIdent:{}, accountIdent:{}, orgIdent:{}, projIdent:{}, tags:{}",
        host, hostName, secretIdentifierWithScope, accountIdentifier, orgIdentifier, projectIdentifier,
        delegateSelectors);

    DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);

    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorResponseData = (ErrorNotifyResponseData) delegateResponseData;

      return HostValidationDTO.builder()
          .host(hostName)
          .status(HostValidationDTO.HostValidationStatus.fromBoolean(false))
          .error(buildErrorDetails("Host connectivity validation failed.", errorResponseData.getErrorMessage(),
              ngErrorHelper.getCode(errorResponseData.getErrorMessage())))
          .build();
    } else if (delegateResponseData instanceof BaseConfigValidationTaskResponse) {
      BaseConfigValidationTaskResponse responseData = (BaseConfigValidationTaskResponse) delegateResponseData;

      return HostValidationDTO.builder()
          .host(hostName)
          .status(HostValidationDTO.HostValidationStatus.fromBoolean(responseData.isConnectionSuccessful()))
          .error(responseData.isConnectionSuccessful() ? buildErrorDetails()
                                                       : buildErrorDetails(responseData.getErrorMessage()))
          .build();
    } else {
      return HostValidationDTO.builder()
          .host(hostName)
          .status(HostValidationDTO.HostValidationStatus.fromBoolean(false))
          .error(buildErrorDetails("Host validation check failed"))
          .build();
    }
  }

  private DelegateTaskRequest generateSshDelegateTaskRequest(Secret secret, String host, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Set<String> delegateSelectors) {
    SSHKeySpecDTO secretSpecDTO = (SSHKeySpecDTO) secret.getSecretSpec().toDTO();
    if (null != secretSpecDTO.getAuth()) {
      secretSpecDTO.getAuth().setUseSshj(
          ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_SSH_SSHJ));
      secretSpecDTO.getAuth().setUseSshClient(
          ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_SSH_CLIENT));
    }
    List<EncryptedDataDetail> encryptionDetails = sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(
        secretSpecDTO, getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    String hostName = getHostnameWithoutPort(host);

    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskType(TaskType.NG_SSH_VALIDATION.name())
        .taskParameters(SSHTaskParams.builder()
                            .host(hostName)
                            .encryptionDetails(encryptionDetails)
                            .sshKeySpec(secretSpecDTO)
                            .delegateSelectors(delegateSelectors)
                            .build())
        .taskSetupAbstractions(setupTaskAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
        .executionTimeout(Duration.ofSeconds(PhysicalDataCenterConstants.EXECUTION_TIMEOUT_IN_SECONDS))
        .build();
  }

  private DelegateTaskRequest generateWinRmDelegateTaskRequest(Secret secret, String host, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Set<String> delegateSelectors) {
    WinRmCredentialsSpecDTO secretSpecDTO = (WinRmCredentialsSpecDTO) secret.getSecretSpec().toDTO();
    List<EncryptedDataDetail> encryptionDetails = winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(
        secretSpecDTO, getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier));
    String hostName = getHostnameWithoutPort(host);

    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskType(TaskType.NG_WINRM_VALIDATION.name())
        .taskParameters(WinRmTaskParams.builder()
                            .host(hostName)
                            .encryptionDetails(encryptionDetails)
                            .spec(secretSpecDTO)
                            .delegateSelectors(delegateSelectors)
                            .build())
        .taskSetupAbstractions(setupTaskAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
        .executionTimeout(Duration.ofSeconds(PhysicalDataCenterConstants.EXECUTION_TIMEOUT_IN_SECONDS))
        .build();
  }

  private String getHostnameWithoutPort(String host) {
    Optional<Integer> portFromHost = extractPortFromHost(host);

    if (portFromHost.isEmpty()) {
      return host;
    }

    return extractHostnameFromHost(host).orElseThrow(
        ()
            -> new InvalidArgumentsException(
                format("Not found hostName, host: %s, extracted port: %s", host, portFromHost.get()), USER_SRE));
  }

  @NotNull
  private List<String> limitHosts(@NotNull List<String> hosts) {
    int numberOfHosts = hosts.size();
    if (numberOfHosts > HOSTS_NUMBER_VALIDATION_LIMIT) {
      log.warn("Limiting validation hosts to {}", HOSTS_NUMBER_VALIDATION_LIMIT);
    }

    return hosts.subList(0, Math.min(HOSTS_NUMBER_VALIDATION_LIMIT, numberOfHosts));
  }

  private List<HostValidationDTO> executeParallelTasks(
      @NotNull CompletableFutures<HostValidationDTO> validateHostTasks) {
    CompletableFuture<List<HostValidationDTO>> hostValidationResults = validateHostTasks.allOf();
    try {
      return new ArrayList<>(hostValidationResults.get());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), USER);
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof WingsException) {
        throw(WingsException) ex.getCause();
      }
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private BaseNGAccess getBaseNGAccess(
      final String accountIdentifier, final String orgIdentifier, final String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private Map<String, String> setupTaskAbstractions(
      final String accountIdIdentifier, final String orgIdentifier, final String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    final String owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, TRUE_STR);
    return abstractions;
  }

  private DelegateResponseData executeDelegateSyncTask(DelegateTaskRequest delegateTaskRequest) {
    final DelegateResponseData delegateResponseData;
    try {
      delegateResponseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(
              ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), ex, WingsException.USER));
    }
    return delegateResponseData;
  }

  private ErrorDetail buildErrorDetails(final String errorMsg) {
    return ErrorDetail.builder()
        .message(errorMsg)
        .reason(ngErrorHelper.getReason(errorMsg))
        .code(ngErrorHelper.getCode(errorMsg))
        .build();
  }

  private ErrorDetail buildErrorDetails(final String errorMsg, final String errorReason, final int code) {
    return ErrorDetail.builder().message(errorMsg).reason(errorReason).code(code).build();
  }

  private ErrorDetail buildErrorDetails() {
    return ErrorDetail.builder().build();
  }

  private Optional<Secret> findSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifierWithScope) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretIdentifierWithScope);
    IdentifierRef secretIdentifiers = IdentifierRefHelper.getIdentifierRef(
        secretIdentifierWithScope, accountIdentifier, orgIdentifier, projectIdentifier);
    return ngSecretServiceV2.get(secretIdentifiers.getAccountIdentifier(), secretIdentifiers.getOrgIdentifier(),
        secretIdentifiers.getProjectIdentifier(), secretRefData.getIdentifier());
  }
}
