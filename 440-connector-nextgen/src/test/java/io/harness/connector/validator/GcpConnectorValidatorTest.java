/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectivityStatus.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.GcpValidationParamsProvider;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.gcp.client.GcpClient;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GcpConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private GcpClient gcpClient;
  @Mock private DefaultConnectorServiceImpl connectorService;
  @InjectMocks private GcpConnectorValidator gcpConnectorValidator;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @Mock private ExceptionManager exceptionManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void validateTestWithDelegateSelector() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder()
                    .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                    .config(GcpDelegateDetailsDTO.builder().delegateSelectors(Collections.singleton("foo")).build())
                    .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GcpValidationTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
                        .build());
    gcpConnectorValidator.validate(
        gcpConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category(UnitTests.class)
  public void validateTestWithServiceAccountKey() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("passwordRefIdentifier").scope(Scope.ACCOUNT).build();
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().secretKeyRef(passwordSecretRef).build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GcpValidationTaskResponse.builder()
                        .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
                        .build());
    gcpConnectorValidator.validate(
        gcpConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateTestWithServiceAccountKeyOnManager() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("passwordRefIdentifier").scope(Scope.ACCOUNT).build();
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().secretKeyRef(passwordSecretRef).build())
                            .build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    GcpManualDetailsDTO gcpManualDetailsDTO =
        GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(gcpManualDetailsDTO);
    when(gcpClient.getGkeContainerService(any())).thenReturn(null);
    GcpValidationTaskHandler gcpValidationTaskHandler = mock(GcpValidationTaskHandler.class);
    on(gcpValidationTaskHandler).set("gcpClient", gcpClient);
    on(gcpValidationTaskHandler).set("decryptionHelper", decryptionHelper);
    when(gcpValidationTaskHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Gcp")))
        .thenReturn(gcpValidationTaskHandler);

    GcpValidationParamsProvider gcpValidationParamsProvider = new GcpValidationParamsProvider();
    on(gcpValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Gcp"))).thenReturn(gcpValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.GCP)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
    ConnectorValidationResult connectorValidationResult = gcpConnectorValidator.validate(
        gcpConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateTestWithoutServiceAccountKeyOnManager() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    GcpValidationTaskHandler gcpValidationTaskHandler = mock(GcpValidationTaskHandler.class);
    on(gcpValidationTaskHandler).set("exceptionManager", exceptionManager);
    when(exceptionManager.processException(any(), any(), any()))
        .thenReturn(
            WingsException.builder()
                .message(
                    "Connector with credential type InheritFromDelegate does not support validation through harness")
                .build());
    when(gcpValidationTaskHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Gcp")))
        .thenReturn(gcpValidationTaskHandler);

    GcpValidationParamsProvider gcpValidationParamsProvider = new GcpValidationParamsProvider();
    on(gcpValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Gcp"))).thenReturn(gcpValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.GCP)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
    assertThatThrownBy(()
                           -> gcpConnectorValidator.validate(gcpConnectorDTO, "accountIdentifier", "orgIdentifier",
                               "projectIdentifier", "identifier"))
        .isInstanceOf(WingsException.class)
        .hasMessage("Connector with credential type InheritFromDelegate does not support validation through harness");
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateTestInheritFromDelegateWithExecuteOnDelegateAsTrue() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .executeOnDelegate(true)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    GcpManualDetailsDTO gcpManualDetailsDTO =
        GcpManualDetailsDTO.builder().secretKeyRef(SecretRefData.builder().build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(gcpManualDetailsDTO);
    when(gcpClient.getGkeContainerService(any())).thenReturn(null);
    GcpValidationTaskHandler gcpValidationTaskHandler = mock(GcpValidationTaskHandler.class);
    on(gcpValidationTaskHandler).set("gcpClient", gcpClient);
    on(gcpValidationTaskHandler).set("decryptionHelper", decryptionHelper);
    when(gcpValidationTaskHandler.validate(any(), any())).thenCallRealMethod();
    GcpValidationParamsProvider gcpValidationParamsProvider = new GcpValidationParamsProvider();

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GCP)
                                            .connectorConfig(gcpConnectorDTO)
                                            .identifier("identifier")
                                            .projectIdentifier("projectIdentifier")
                                            .orgIdentifier("orgIdentifier")
                                            .build();
    on(gcpValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    ConnectorValidationResult connectorValidationResult =
        gcpValidationTaskHandler.validate(gcpValidationParamsProvider.getConnectorValidationParams(connectorInfoDTO,
                                              "connector", "accountIdentifier", "projectIdentifier", "orgIdentifier"),
            "accountIdentifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }
}
