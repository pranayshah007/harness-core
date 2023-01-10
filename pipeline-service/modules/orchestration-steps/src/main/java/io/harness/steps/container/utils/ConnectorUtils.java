/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails.ConnectorDetailsBuilder;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import retrofit2.Response;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ConnectorUtils {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final ContainerExecutionConfig containerExecutionConfig;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 6;

  public ConnectorDetails getConnectorDetails(NGAccess ngAccess, String connectorIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifier,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    return getConnectorDetailsInternalWithRetries(ngAccess, connectorRef);
  }

  public ConnectorDetails getConnectorDetailsInternalWithRetries(NGAccess ngAccess, IdentifierRef connectorRef) {
    Instant startTime = Instant.now();
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch connector: [%s], scope: [%s], attempt: {}",
                           connectorRef.getIdentifier(), connectorRef.getScope()),
            format("Failed to fetch connector: [%s] scope: [%s] after retrying {} times", connectorRef.getIdentifier(),
                connectorRef.getScope()));

    ConnectorDetails connectorDetails =
        Failsafe.with(retryPolicy).get(() -> getConnectorDetailsInternal(ngAccess, connectorRef));

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
      case KUBERNETES_CLUSTER:
        connectorDetails = getK8sConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      case DOCKER:
        connectorDetails = getDockerConnectorDetails(ngAccess, connectorDTO, connectorDetailsBuilder);
        break;
      default:
        throw new InvalidArgumentsException(format("Unexpected connector type:[%s]", connectorType));
    }
    log.info("Successfully fetched encryption details for  connector id:[{}] type:[{}] scope:[{}]",
        connectorRef.getIdentifier(), connectorType, connectorRef.getScope());
    return connectorDetails;
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

  public ConnectorDetails getDefaultInternalConnector(NGAccess ngAccess) {
    ConnectorDetails connectorDetails = null;
    try {
      connectorDetails = getConnectorDetails(ngAccess, containerExecutionConfig.getDefaultInternalImageConnector());
    } catch (ConnectorNotFoundException e) {
      log.info("Default harness image connector does not exist: {}", e.getMessage());
      connectorDetails = null;
    }
    return connectorDetails;
  }
  public ConnectorDetails getConnectorDetailsWithConversionInfo(
      NGAccess ngAccess, ConnectorConversionInfo connectorConversionInfo) {
    ConnectorDetails connectorDetails = getConnectorDetails(ngAccess, connectorConversionInfo.getConnectorRef());
    connectorDetails.setEnvToSecretsMap(connectorConversionInfo.getEnvToSecretsMap());
    return connectorDetails;
  }
}
