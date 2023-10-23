/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import static io.harness.cvng.beans.DataCollectionType.KUBERNETES;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.InternalServerErrorException;

public class VerificationManagerServiceImpl implements VerificationManagerService {
  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private RequestExecutor requestExecutor;
  @Inject private NextGenService nextGenService;

  @Override
  public String createDataCollectionTask(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    // Need to write this to handle retries, exception etc in a proper way.
    Preconditions.checkNotNull(bundle.getConnectorIdentifier());
    Preconditions.checkNotNull(bundle.getSourceIdentifier());
    Preconditions.checkNotNull(bundle.getDataCollectionWorkerId());
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, bundle.getConnectorIdentifier(), orgIdentifier, projectIdentifier);
    if (!connectorDTO.isPresent()) {
      throw new InternalServerErrorException(
          "Failed to retrieve ConnectorDisconnectHandler with id: " + bundle.getConnectorIdentifier());
    }

    bundle.setConnectorDTO(connectorDTO.get());

    return requestExecutor
        .execute(verificationManagerClient.createDataCollectionPerpetualTask(
            accountId, orgIdentifier, projectIdentifier, bundle))
        .getResource();
  }

  @Override
  public void resetDataCollectionTask(String accountId, String orgIdentifier, String projectIdentifier,
      String perpetualTaskId, DataCollectionConnectorBundle bundle) {
    Preconditions.checkNotNull(bundle.getConnectorIdentifier());
    Preconditions.checkNotNull(bundle.getSourceIdentifier());
    Preconditions.checkNotNull(bundle.getDataCollectionWorkerId());
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, bundle.getConnectorIdentifier(), orgIdentifier, projectIdentifier);
    if (!connectorDTO.isPresent()) {
      throw new InternalServerErrorException(
          "Failed to retrieve ConnectorDisconnectHandler with id: " + bundle.getConnectorIdentifier());
    }

    bundle.setConnectorDTO(connectorDTO.get());

    requestExecutor
        .execute(verificationManagerClient.resetDataCollectionPerpetualTask(
            accountId, orgIdentifier, projectIdentifier, perpetualTaskId, bundle))
        .getResource();
  }

  @Override
  public void deletePerpetualTask(String accountId, String perpetualTaskId) {
    requestExecutor.execute(verificationManagerClient.deleteDataCollectionPerpetualTask(accountId, perpetualTaskId));
  }

  @Override
  public String getDataCollectionResponse(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionRequest request) {
    return requestExecutor
        .execute(
            verificationManagerClient.getDataCollectionResponse(accountId, orgIdentifier, projectIdentifier, request))
        .getResource();
  }

  @Override
  public List<String> getKubernetesNamespaces(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String filter) {
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, connectorIdentifier, orgIdentifier, projectIdentifier);
    if (!connectorDTO.isPresent()) {
      throw new InternalServerErrorException("Failed to retrieve ConnectorDisconnectHandler with id: " + connectorIdentifier);
    }

    DataCollectionConnectorBundle bundle =
        DataCollectionConnectorBundle.builder().connectorDTO(connectorDTO.get()).dataCollectionType(KUBERNETES).build();

    return requestExecutor
        .execute(verificationManagerClient.getKubernetesNamespaces(
            accountId, orgIdentifier, projectIdentifier, filter, bundle))
        .getResource();
  }

  @Override
  public List<String> checkCapabilityToGetKubernetesEvents(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, connectorIdentifier, orgIdentifier, projectIdentifier);
    if (!connectorDTO.isPresent()) {
      throw new InternalServerErrorException("Failed to retrieve ConnectorDisconnectHandler with id: " + connectorIdentifier);
    }

    DataCollectionConnectorBundle bundle =
        DataCollectionConnectorBundle.builder().connectorDTO(connectorDTO.get()).dataCollectionType(KUBERNETES).build();

    return requestExecutor
        .execute(
            verificationManagerClient.checkCapabilityToGetEvents(accountId, orgIdentifier, projectIdentifier, bundle))
        .getResource();
  }

  @Override
  public List<String> getKubernetesWorkloads(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String namespace, String filter) {
    Optional<ConnectorInfoDTO> connectorDTO =
        nextGenService.get(accountId, connectorIdentifier, orgIdentifier, projectIdentifier);
    if (!connectorDTO.isPresent()) {
      throw new InternalServerErrorException("Failed to retrieve ConnectorDisconnectHandler with id: " + connectorIdentifier);
    }

    DataCollectionConnectorBundle bundle =
        DataCollectionConnectorBundle.builder().connectorDTO(connectorDTO.get()).dataCollectionType(KUBERNETES).build();

    return requestExecutor
        .execute(verificationManagerClient.getKubernetesWorkloads(
            accountId, orgIdentifier, projectIdentifier, namespace, filter, bundle))
        .getResource();
  }

  @Override
  public List<HarnessCDCurrentGenEventMetadata> getCurrentGenEvents(String accountId, String harnessApplicationId,
      String harnessEnvironmentId, String harnessServiceId, Instant startTime, Instant endTime) {
    return requestExecutor
        .execute(verificationManagerClient.getCDCurrentGenChangeEvents(accountId, harnessApplicationId,
            harnessServiceId, harnessEnvironmentId, startTime.toEpochMilli(), endTime.toEpochMilli()))
        .getResource();
  }

  @Override
  public CVNGPerpetualTaskDTO getPerpetualTaskStatus(String perpetualTaskId) {
    return requestExecutor.execute(verificationManagerClient.getDataCollectionPerpetualTaskStatus(perpetualTaskId))
        .getResource();
  }
}
