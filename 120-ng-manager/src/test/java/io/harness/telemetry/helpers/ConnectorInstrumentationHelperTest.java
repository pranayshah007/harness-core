/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class ConnectorInstrumentationHelperTest {
  @InjectMocks ConnectorInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;
  @Mock ConnectorValidationResult connectorValidationResult;

  String userName = "userName";
  String masterUrl = "https://abc.com";
  String identifier = "identifier";
  String name = "name";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String connectorIdentifier = "connectorIdentifier";
  String delegateId = "delegateId";
  String errorSummary = "errorSummary";
  String errorMessage = "dummy error message";
  String errorReason = "dummy error reason";
  String ACCOUNT_ID = "account_id";
  String TEST_CONNECTION_SUCCESS = "test_connection_success";
  String TEST_CONNECTION_FAILURE = "test_connection_failure";
  String CONNECTOR_ID = "connector_id";
  String CONNECTOR_PROJECT = "connector_project";
  String CONNECTOR_ORG = "connector_org";
  String CONNECTOR_NAME = "connector_name";
  String CONNECTOR_TYPE = "connector_type";
  ConnectorType connectorType = ConnectorType.getConnectorType("Git");
  String CONNECTIVITY_STATUS = "connectivity_status";
  String DELEGATE_ID = "delegate_id";
  String ERROR_SUMMARY = "error_summary";
  String ERROR_DETAILS = "error_details";

  long testedAt = 10;
  SecretRefData passwordSecretRef;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ConnectorDTO createKubernetesConnectorRequestDTO(String connectorIdentifier, String name) {
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(userName).passwordRef(passwordSecretRef).build())
            .build();
    KubernetesCredentialDTO connectorDTOWithDelegateCreds =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
            .build();
    KubernetesClusterConfigDTO k8sClusterConfig =
        KubernetesClusterConfigDTO.builder().credential(connectorDTOWithDelegateCreds).build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(connectorIdentifier)
                                         .connectorType(KUBERNETES_CLUSTER)
                                         .connectorConfig(k8sClusterConfig)
                                         .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private ConnectorResponseDTO createConnector(String connectorIdentifier, String name) {
    ConnectorDTO connectorRequestDTO = createKubernetesConnectorRequestDTO(connectorIdentifier, name);

    return ConnectorResponseDTO.builder().connector(connectorRequestDTO.getConnectorInfo()).build();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateConnectorTrackSend() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(identifier, name);
    CompletableFuture telemetryTask =
        instrumentationHelper.sendConnectorCreateEvent(connectorDTOOutput.getConnector(), accountIdentifier);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteConnectorTrackSend() {
    CompletableFuture telemetryTask = instrumentationHelper.sendConnectorDeleteEvent(
        orgIdentifier, projectIdentifier, connectorIdentifier, accountIdentifier);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testConnectionTestTrackSend() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(identifier, name);
    CompletableFuture telemetryTask = instrumentationHelper.sendTestConnectionEvent(
        connectorValidationResult, connectorDTOOutput.getConnector(), accountIdentifier);
    telemetryTask.join();
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testConnectionTestSuccessMapBuilder() {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .connectorType(connectorType)
                                         .name(CONNECTOR_NAME)
                                         .orgIdentifier(CONNECTOR_ORG)
                                         .projectIdentifier(CONNECTOR_PROJECT)
                                         .identifier(CONNECTOR_NAME)
                                         .build();
    ArgumentCaptor<String> eventMessageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HashMap> eventPropertiesMap = ArgumentCaptor.forClass(HashMap.class);
    ErrorDetail error = ErrorDetail.builder().code(500).message(errorMessage).reason(errorReason).build();
    List<ErrorDetail> errors = new ArrayList<>();
    errors.add(error);
    ConnectorValidationResult connectorValidationSuccessResult = getConnectorValidationSuccesResult();
    CompletableFuture telemetryTask = instrumentationHelper.sendTestConnectionEvent(
        connectorValidationSuccessResult, connectorInfo, accountIdentifier);
    telemetryTask.join();
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventMessageCaptor.capture(), any(), any(), eventPropertiesMap.capture(), any(), any(), any());
    assertThat(eventMessageCaptor.getValue()).isEqualTo(TEST_CONNECTION_SUCCESS);
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(CONNECTOR_PROJECT, connectorInfo.getProjectIdentifier());
    propertiesMap.put(CONNECTOR_ORG, connectorInfo.getOrgIdentifier());
    getSuccessPropertyMap(connectorInfo, propertiesMap);
    assertTrue(eventPropertiesMap.getValue().equals(propertiesMap));
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testConnectionTestFailureMapBuilder() {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .connectorType(connectorType)
                                         .name(CONNECTOR_NAME)
                                         .orgIdentifier(CONNECTOR_ORG)
                                         .projectIdentifier(CONNECTOR_PROJECT)
                                         .identifier(CONNECTOR_NAME)
                                         .build();
    ArgumentCaptor<String> eventMessageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HashMap> eventPropertiesMap = ArgumentCaptor.forClass(HashMap.class);
    ErrorDetail error = ErrorDetail.builder().code(500).message(errorMessage).reason(errorReason).build();
    List<ErrorDetail> errors = new ArrayList<>();
    errors.add(error);
    ConnectorValidationResult connectorValidationFailureResult = getConnectorValidationFailureResult(errors);
    CompletableFuture telemetryTask = instrumentationHelper.sendTestConnectionEvent(
        connectorValidationFailureResult, connectorInfo, accountIdentifier);
    telemetryTask.join();
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventMessageCaptor.capture(), any(), any(), eventPropertiesMap.capture(), any(), any(), any());
    assertThat(eventMessageCaptor.getValue()).isEqualTo(TEST_CONNECTION_FAILURE);
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(CONNECTOR_PROJECT, connectorInfo.getProjectIdentifier());
    propertiesMap.put(CONNECTOR_ORG, connectorInfo.getOrgIdentifier());
    getFailurePropertyMap(connectorInfo, errors, propertiesMap);
    assertTrue(eventPropertiesMap.getValue().equals(propertiesMap));
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testConnectionTestFailureMapBuilder_NoOrg() {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .connectorType(connectorType)
                                         .name(CONNECTOR_NAME)
                                         .projectIdentifier(CONNECTOR_PROJECT)
                                         .identifier(CONNECTOR_NAME)
                                         .build();
    ArgumentCaptor<String> eventMessageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HashMap> eventPropertiesMap = ArgumentCaptor.forClass(HashMap.class);
    ErrorDetail error = ErrorDetail.builder().code(500).message(errorMessage).reason(errorReason).build();
    List<ErrorDetail> errors = new ArrayList<>();
    errors.add(error);
    ConnectorValidationResult connectorValidationFailureResult = getConnectorValidationFailureResult(errors);
    CompletableFuture telemetryTask = instrumentationHelper.sendTestConnectionEvent(
        connectorValidationFailureResult, connectorInfo, accountIdentifier);
    telemetryTask.join();
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventMessageCaptor.capture(), any(), any(), eventPropertiesMap.capture(), any(), any(), any());
    assertThat(eventMessageCaptor.getValue()).isEqualTo(TEST_CONNECTION_FAILURE);
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(CONNECTOR_PROJECT, connectorInfo.getProjectIdentifier());
    getFailurePropertyMap(connectorInfo, errors, propertiesMap);
    assertTrue(eventPropertiesMap.getValue().equals(propertiesMap));
    assertTrue(telemetryTask.isDone());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testConnectionTestFailureMapBuilder_NoOrgAndProject() {
    ConnectorInfoDTO connectorInfo =
        ConnectorInfoDTO.builder().connectorType(connectorType).name(CONNECTOR_NAME).identifier(CONNECTOR_NAME).build();
    ArgumentCaptor<String> eventMessageCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HashMap> eventPropertiesMap = ArgumentCaptor.forClass(HashMap.class);
    ErrorDetail error = ErrorDetail.builder().code(500).message(errorMessage).reason(errorReason).build();
    List<ErrorDetail> errors = new ArrayList<>();
    errors.add(error);
    ConnectorValidationResult connectorValidationFailureResult = getConnectorValidationFailureResult(errors);
    CompletableFuture telemetryTask = instrumentationHelper.sendTestConnectionEvent(
        connectorValidationFailureResult, connectorInfo, accountIdentifier);
    telemetryTask.join();
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eventMessageCaptor.capture(), any(), any(), eventPropertiesMap.capture(), any(), any(), any());
    assertThat(eventMessageCaptor.getValue()).isEqualTo(TEST_CONNECTION_FAILURE);
    HashMap<String, Object> propertiesMap = new HashMap<>();
    getFailurePropertyMap(connectorInfo, errors, propertiesMap);
    assertTrue(eventPropertiesMap.getValue().equals(propertiesMap));
    assertTrue(telemetryTask.isDone());
  }

  private void getFailurePropertyMap(
      ConnectorInfoDTO connectorInfo, List<ErrorDetail> errors, HashMap<String, Object> propertiesMap) {
    propertiesMap.put(DELEGATE_ID, delegateId);
    propertiesMap.put(ACCOUNT_ID, accountIdentifier);
    propertiesMap.put(CONNECTOR_ID, connectorInfo.getIdentifier());
    propertiesMap.put(CONNECTOR_TYPE, connectorInfo.getConnectorType());
    propertiesMap.put(CONNECTOR_NAME, connectorInfo.getName());
    propertiesMap.put(CONNECTIVITY_STATUS, ConnectivityStatus.FAILURE);
    propertiesMap.put(ERROR_SUMMARY, errorSummary);
    propertiesMap.put(ERROR_DETAILS, errors);
  }

  private void getSuccessPropertyMap(ConnectorInfoDTO connectorInfo, HashMap<String, Object> propertiesMap) {
    propertiesMap.put(DELEGATE_ID, delegateId);
    propertiesMap.put(ACCOUNT_ID, accountIdentifier);
    propertiesMap.put(CONNECTOR_ID, connectorInfo.getIdentifier());
    propertiesMap.put(CONNECTOR_TYPE, connectorInfo.getConnectorType());
    propertiesMap.put(CONNECTOR_NAME, connectorInfo.getName());
    propertiesMap.put(CONNECTIVITY_STATUS, ConnectivityStatus.SUCCESS);
  }

  private ConnectorValidationResult getConnectorValidationSuccesResult() {
    return ConnectorValidationResult.builder()
        .delegateId(delegateId)
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(testedAt)
        .build();
  }

  private ConnectorValidationResult getConnectorValidationFailureResult(List<ErrorDetail> errors) {
    return ConnectorValidationResult.builder()
        .delegateId(delegateId)
        .status(ConnectivityStatus.FAILURE)
        .testedAt(testedAt)
        .errorSummary(errorSummary)
        .errors(errors)
        .build();
  }
}
