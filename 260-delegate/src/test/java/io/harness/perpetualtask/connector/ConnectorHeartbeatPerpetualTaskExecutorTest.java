/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.connector;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsValidationParams;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.task.k8s.KubernetesValidationHandler;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class ConnectorHeartbeatPerpetualTaskExecutorTest extends DelegateTestBase {
  private final String ERROR_MSG = "error";

  @InjectMocks ConnectorHeartbeatPerpetualTaskExecutor connectorHeartbeatPerpetualTaskExecutor;
  @Inject KryoSerializer kryoSerializer;
  @Mock KubernetesValidationHandler KubernetesValidationHandler;
  @Mock AwsValidationHandler awsValidationHandler;
  @Mock DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private Call<RestResponse<Boolean>> call;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(connectorHeartbeatPerpetualTaskExecutor, "kryoSerializer", kryoSerializer, true);
    doReturn(KubernetesValidationHandler)
        .when(connectorTypeToConnectorValidationHandlerMap)
        .get(ArgumentMatchers.any());
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishConnectorHeartbeatResult(anyString(), anyString(), any(ConnectorHeartbeatDelegateResponse.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category({UnitTests.class})
  public void runOnce() {
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(generateUuid()).build();
    when(KubernetesValidationHandler.validate(any(), any()))
        .thenReturn(ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build());
    connectorHeartbeatPerpetualTaskExecutor.runOnce(perpetualTaskId, getPerpetualTaskParams(), Instant.EPOCH);
    verify(delegateAgentManagerClient, times(1)).publishConnectorHeartbeatResult(anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category({UnitTests.class})
  public void runOnceAwsValidationHandler() {
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(generateUuid()).build();
    doThrow(new InvalidRequestException(ERROR_MSG))
        .when(awsValidationHandler)
        .validate(any(), ArgumentMatchers.anyString());
    doReturn(awsValidationHandler).when(connectorTypeToConnectorValidationHandlerMap).get(any());
    AwsValidationParams awsValidationParams =
        AwsValidationParams.builder().awsConnectorDTO(AwsConnectorDTO.builder().build()).build();
    ConnectorValidationParameterResponse connectorValidationParameterResponse =
        ConnectorValidationParameterResponse.builder()
            .connectorValidationParams(awsValidationParams)
            .isInvalid(false)
            .build();
    ByteString connectorConfigBytes = ByteString.copyFrom(kryoSerializer.asBytes(connectorValidationParameterResponse));
    ConnectorHeartbeatTaskParams connectorHeartbeatTaskParams =
        ConnectorHeartbeatTaskParams.newBuilder()
            .setAccountIdentifier("accountIdentifier")
            .setConnectorIdentifier("connectorIdentifier")
            .setConnectorValidationParameterResponse(connectorConfigBytes)
            .build();
    PerpetualTaskExecutionParams taskParams =
        PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(connectorHeartbeatTaskParams)).build();
    doReturn(ERROR_MSG).when(ngErrorHelper).getErrorSummary(anyString());
    PerpetualTaskResponse perpetualTaskResponse =
        connectorHeartbeatPerpetualTaskExecutor.runOnce(perpetualTaskId, taskParams, Instant.EPOCH);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo(ERROR_MSG);
    verify(delegateAgentManagerClient, times(1)).publishConnectorHeartbeatResult(anyString(), anyString(), any());
  }

  PerpetualTaskExecutionParams getPerpetualTaskParams() {
    ConnectorDTO connectorDTO = ConnectorDTO.builder()
                                    .connectorInfo(ConnectorInfoDTO.builder()
                                                       .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                                                       .connectorConfig(KubernetesClusterConfigDTO.builder().build())
                                                       .build())
                                    .build();
    K8sValidationParams k8sValidationParams =
        K8sValidationParams.builder().kubernetesClusterConfigDTO(KubernetesClusterConfigDTO.builder().build()).build();
    ConnectorValidationParameterResponse connectorValidationParameterResponse =
        ConnectorValidationParameterResponse.builder()
            .connectorValidationParams(k8sValidationParams)
            .isInvalid(false)
            .build();
    ByteString connectorConfigBytes = ByteString.copyFrom(kryoSerializer.asBytes(connectorValidationParameterResponse));
    ConnectorHeartbeatTaskParams connectorHeartbeatTaskParams =
        ConnectorHeartbeatTaskParams.newBuilder()
            .setAccountIdentifier("accountIdentifier")
            .setConnectorIdentifier("connectorIdentifier")
            .setConnectorValidationParameterResponse(connectorConfigBytes)
            .build();
    return PerpetualTaskExecutionParams.newBuilder()
        .setCustomizedParams(Any.pack(connectorHeartbeatTaskParams))
        .build();
  }
}
