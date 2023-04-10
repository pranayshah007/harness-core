/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.connector;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.annotations.dev.HarnessModule._890_SM_CORE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.exception.UnexpectedException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@TargetModule(_890_SM_CORE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class ConnectorHeartbeatPerpetualTaskClient implements PerpetualTaskServiceClient {
  private KryoSerializer kryoSerializer;
  private ConnectorResourceClient connectorResourceClient;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String accountIdentifier = clientParams.get(ACCOUNT_KEY);
    String orgIdentifier = clientParams.get(ORG_KEY);
    String projectIdentifier = clientParams.get(PROJECT_KEY);
    String connectorIdentifier = clientParams.get(CONNECTOR_IDENTIFIER_KEY);
    final ConnectorValidationParameterResponse connectorValidationParameterResponse =
        getConnectorValidationParameterResponse(clientParams);
    ByteString connectorValidatorBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(connectorValidationParameterResponse));
    ConnectorHeartbeatTaskParams.Builder connectorHeartbeatTaskParamsBuilder =
        ConnectorHeartbeatTaskParams.newBuilder()
            .setAccountIdentifier(accountIdentifier)
            .setConnectorIdentifier(connectorIdentifier)
            .setConnectorValidationParameterResponse(
                connectorValidatorBytes); // change it to paramResponse add in proto
    if (isNotBlank(orgIdentifier)) {
      connectorHeartbeatTaskParamsBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotBlank(projectIdentifier)) {
      connectorHeartbeatTaskParamsBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }
    return connectorHeartbeatTaskParamsBuilder.build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    Map<String, String> clientParams = clientContext.getClientParams();
    ConnectorValidationParams connectorValidationParams =
        getConnectorValidationParameterResponse(clientParams).getConnectorValidationParams();
    List<ExecutionCapability> executionCapabilities = Collections.emptyList();
    if (connectorValidationParams instanceof ExecutionCapabilityDemander) {
      executionCapabilities =
          ((ExecutionCapabilityDemander) connectorValidationParams).fetchRequiredExecutionCapabilities(null);
    }

    String orgIdentifier = clientParams.get(ORG_KEY);
    String projectIdentifier = clientParams.get(PROJECT_KEY);
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountId, orgIdentifier, projectIdentifier);

    final List<ExecutionCapability> nonSelectorExecutionCapabilities =
        executionCapabilities.stream()
            .filter(executionCapability -> !(executionCapability instanceof SelectorCapability))
            .collect(Collectors.toList());
    return DelegateTask.builder()
        .accountId(accountId)
        .executionCapabilities(executionCapabilities)
        .setupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.CAPABILITY_VALIDATION.name())
                  .parameters(nonSelectorExecutionCapabilities.toArray())
                  .timeout(TimeUnit.MINUTES.toMillis(1))
                  .build())
        .build();
  }

  private ConnectorValidationParameterResponse getConnectorValidationParameterResponse(
      Map<String, String> clientParams) {
    String accountIdentifier = clientParams.get(ACCOUNT_KEY);
    String orgIdentifier = clientParams.get(ORG_KEY);
    String projectIdentifier = clientParams.get(PROJECT_KEY);
    String connectorIdentifier = clientParams.get(CONNECTOR_IDENTIFIER_KEY);
    ConnectorValidationParameterResponse connectorValidationParameterResponse =
        NGRestUtils.getResponse(connectorResourceClient.getConnectorValidationParams(
            connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    if (connectorValidationParameterResponse == null) {
      log.info(
          "{} The connector doesn't exists with the following ids accountId: {}, orgId: {}, projectId:{}, identifier:{}",
          CONNECTOR_HEARTBEAT_LOG_PREFIX, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      throw new UnexpectedException(String.format("The " + CONNECTOR_STRING + " doesn't exists.", connectorIdentifier,
          accountIdentifier, orgIdentifier, projectIdentifier));
    }
    return connectorValidationParameterResponse;
  }
}
