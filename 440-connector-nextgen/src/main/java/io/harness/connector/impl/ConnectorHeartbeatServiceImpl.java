/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.TaskClientParams;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.util.Durations;
import io.grpc.StatusRuntimeException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

@Singleton
public class ConnectorHeartbeatServiceImpl implements ConnectorHeartbeatService {
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConnectorHeartbeatServiceImpl.class);
  private final long CONNECTIVITY_CHECK_INTERVAL = 30;
  private final long CONNECTIVITY_CHECK_TIMEOUT = 2;
  DelegateServiceGrpcClient delegateServiceGrpcClient;
  ConnectorService connectorService;
  private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;

  @Inject
  public ConnectorHeartbeatServiceImpl(DelegateServiceGrpcClient delegateServiceGrpcClient,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap) {
    this.delegateServiceGrpcClient = delegateServiceGrpcClient;
    this.connectorService = connectorService;
    this.connectorValidationParamsProviderMap = connectorValidationParamsProviderMap;
  }

  private PerpetualTaskId createPerpetualTask(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier) {
    try {
      AccountId accountId = AccountId.newBuilder().setId(accountIdentifier).build();
      PerpetualTaskSchedule perpetualTaskSchedule = PerpetualTaskSchedule.newBuilder()
                                                        .setInterval(Durations.fromMinutes(CONNECTIVITY_CHECK_INTERVAL))
                                                        .setTimeout(Durations.fromMinutes(CONNECTIVITY_CHECK_TIMEOUT))
                                                        .build();
      Map<String, String> clientParamsMap = new HashMap<>();
      clientParamsMap.put(ACCOUNT_KEY, accountIdentifier);
      if (isNotBlank(connectorOrgIdentifier)) {
        clientParamsMap.put(ORG_KEY, connectorOrgIdentifier);
      }
      if (isNotBlank(connectorProjectIdentifier)) {
        clientParamsMap.put(PROJECT_KEY, connectorProjectIdentifier);
      }
      clientParamsMap.put(CONNECTOR_IDENTIFIER_KEY, connectorIdentifier);
      TaskClientParams clientParams = TaskClientParams.newBuilder().putAllParams(clientParamsMap).build();
      PerpetualTaskClientContextDetails taskContext =
          PerpetualTaskClientContextDetails.newBuilder().setTaskClientParams(clientParams).build();
      return delegateServiceGrpcClient.createPerpetualTask(accountId, "CONNECTOR_TEST_CONNECTION",
          perpetualTaskSchedule, taskContext, false, "Connector Test Connection Task");
    } catch (Exception ex) {
      log.info("{} Error Creating Perpetual task for the connector {} in account {}, org {} with taskId {}",
          CONNECTOR_HEARTBEAT_LOG_PREFIX, connectorIdentifier, connectorOrgIdentifier, connectorProjectIdentifier, ex);
      return null;
    }
  }

  public PerpetualTaskId createConnectorHeatbeatTask(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier) {
    PerpetualTaskId perpetualTaskId =
        createPerpetualTask(accountIdentifier, connectorOrgIdentifier, connectorProjectIdentifier, connectorIdentifier);
    if (perpetualTaskId == null || perpetualTaskId.getId() == null) {
      log.error("{} Error in creating Perpetual task for the {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
          String.format(CONNECTOR_STRING, connectorIdentifier, accountIdentifier, connectorOrgIdentifier,
              connectorProjectIdentifier));
      return null;
    }
    log.info("{} Created perpetual task for the {} with the id {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
        String.format(CONNECTOR_STRING, connectorIdentifier, accountIdentifier, connectorOrgIdentifier,
            connectorProjectIdentifier),
        perpetualTaskId.getId());
    return perpetualTaskId;
  }

  @Override
  public boolean deletePerpetualTask(String accountIdentifier, String perpetualTaskId, String connectorFQN) {
    try {
      delegateServiceGrpcClient.deletePerpetualTask(AccountId.newBuilder().setId(accountIdentifier).build(),
          PerpetualTaskId.newBuilder().setId(perpetualTaskId).build());
      return true;
    } catch (Exception ex) {
      log.error("{} Exception while deleting the heartbeat task for the connector {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
          connectorFQN);
      return false;
    }
  }

  @Override
  public ConnectorValidationParameterResponse getConnectorValidationParams(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    final Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    return connectorResponseDTO
        .map(connectorResponse -> {
          final ConnectorType connectorType = connectorResponse.getConnector().getConnectorType();
          ConnectorValidationParams connectorValidationParams =
              connectorValidationParamsProviderMap.get(connectorType.getDisplayName())
                  .getConnectorValidationParams(connectorResponse.getConnector(),
                      connectorResponse.getConnector().getName(), accountIdentifier, orgIdentifier, projectIdentifier);
          return ConnectorValidationParameterResponse.builder()
              .connectorValidationParams(connectorValidationParams)
              .isInvalid(!connectorResponse.getEntityValidityDetails().isValid())
              .build();
        })
        .orElseThrow(()
                         -> new InvalidRequestException(String.format(CONNECTOR_STRING, connectorIdentifier,
                             accountIdentifier, orgIdentifier, projectIdentifier)));
  }

  @Override
  public void resetPerpetualTask(String accountIdentifier, String perpetualTaskId) {
    try {
      delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(accountIdentifier).build(),
          PerpetualTaskId.newBuilder().setId(perpetualTaskId).build(),
          PerpetualTaskExecutionBundle.getDefaultInstance());
    } catch (StatusRuntimeException ex) {
      log.error("Unable to reset perpetual task {} while updating connector with exception - {}", perpetualTaskId, ex);
    }
  }
}
