/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.AzureValidationParamsProvider;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.azure.AzureNgConfigMapper;
import io.harness.connector.task.azure.AzureValidationHandler;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(CDP)
public class AzureConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;
  @Mock private DefaultConnectorServiceImpl connectorService;
  @InjectMocks private AzureNgConfigMapper azureNgConfigMapper;
  @InjectMocks private AzureConnectorValidator azureConnectorValidator;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @Mock private ExceptionManager exceptionManager;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(azureNgConfigMapper).set("decryptionHelper", decryptionHelper);
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void validateAzureConnectionWithManualCredentialsWithSecret() {
    SecretRefData secretRef = SecretRefData.builder().identifier("secretKey").scope(Scope.ACCOUNT).build();
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder()
                                        .clientId("client")
                                        .tenantId("tenant")
                                        .authDTO(AzureAuthDTO.builder()
                                                     .azureSecretType(AzureSecretType.SECRET_KEY)
                                                     .credentials(
                                                         AzureClientSecretKeyDTO.builder().secretKey(secretRef).build())
                                                     .build())
                                        .build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx",
            AzureValidateTaskResponse.builder()
                .connectorValidationResult(
                    ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                .build()));
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void validateAzureConnectionWithManualCredentialsWithCertificate() {
    SecretRefData secretRef = SecretRefData.builder().identifier("certificate").scope(Scope.ACCOUNT).build();
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                            .config(AzureManualDetailsDTO.builder()
                                        .clientId("client")
                                        .tenantId("tenant")
                                        .authDTO(AzureAuthDTO.builder()
                                                     .azureSecretType(AzureSecretType.KEY_CERT)
                                                     .credentials(
                                                         AzureClientSecretKeyDTO.builder().secretKey(secretRef).build())
                                                     .build())
                                        .build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx",
            AzureValidateTaskResponse.builder()
                .connectorValidationResult(
                    ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                .build()));
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = {OwnerRule.BUHA, OwnerRule.MLUKIC})
  @Category(UnitTests.class)
  public void validateAzureConnectionWithInherentFromDelegate() {
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .delegateSelectors(Collections.singleton("foo"))
            .azureEnvironmentType(AzureEnvironmentType.AZURE)
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                            .config(AzureInheritFromDelegateDetailsDTO.builder()
                                        .authDTO(AzureMSIAuthSADTO.builder()
                                                     .azureManagedIdentityType(
                                                         AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                                                     .build())
                                        .build())
                            .build())
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    when(delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(any()))
        .thenReturn(Pair.of("xxxxxx",
            AzureValidateTaskResponse.builder()
                .connectorValidationResult(
                    ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                .build()));
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2ReturnTaskId(any());

    azureConnectorDTO =
        AzureConnectorDTO.builder()
            .delegateSelectors(Collections.singleton("foo"))
            .azureEnvironmentType(AzureEnvironmentType.AZURE)
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                    .config(
                        AzureInheritFromDelegateDetailsDTO.builder()
                            .authDTO(
                                AzureMSIAuthUADTO.builder()
                                    .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                                    .credentials(AzureUserAssignedMSIAuthDTO.builder().clientId("testClientId").build())
                                    .build())
                            .build())
                    .build())
            .build();

    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(2)).executeSyncTaskV2ReturnTaskId(any());
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateAzureConnectionWithManualCredentialsOnManager() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("passwordRefIdentifier").scope(Scope.ACCOUNT).build();
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                    .config(
                        AzureManualDetailsDTO.builder()
                            .clientId("clientID")
                            .tenantId("tenantID")
                            .authDTO(AzureAuthDTO.builder()
                                         .azureSecretType(AzureSecretType.SECRET_KEY)
                                         .credentials(
                                             AzureClientKeyCertDTO.builder().clientCertRef(passwordSecretRef).build())
                                         .build())
                            .build())
                    .build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    AzureClientSecretKeyDTO azureClientSecretKeyDTO =
        AzureClientSecretKeyDTO.builder().secretKey(SecretRefData.builder().build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(azureClientSecretKeyDTO);
    when(azureAuthorizationClient.validateAzureConnection(any())).thenReturn(true);
    AzureValidationHandler azureValidationHandler = mock(AzureValidationHandler.class);
    on(azureValidationHandler).set("azureNgConfigMapper", azureNgConfigMapper);
    on(azureValidationHandler).set("azureAuthorizationClient", azureAuthorizationClient);
    when(azureValidationHandler.validate(any(ConnectorValidationParams.class), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Azure")))
        .thenReturn(azureValidationHandler);

    AzureValidationParamsProvider azureValidationParamsProvider = new AzureValidationParamsProvider();
    on(azureValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Azure")))
        .thenReturn(azureValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.AZURE)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
    ConnectorValidationResult connectorValidationResult = azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void validateAzureConnectionWithoutManualCredentialsOnManager() {
    AzureConnectorDTO azureConnectorDTO =
        AzureConnectorDTO.builder()
            .credential(AzureCredentialDTO.builder()
                            .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                            .config(AzureInheritFromDelegateDetailsDTO.builder().build())
                            .build())
            .executeOnDelegate(false)
            .build();
    when(ngSecretService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    AzureValidationHandler azureValidationHandler = mock(AzureValidationHandler.class);
    on(azureValidationHandler).set("azureNgConfigMapper", azureNgConfigMapper);
    on(azureValidationHandler).set("exceptionManager", exceptionManager);
    when(exceptionManager.processException(any(), any(), any()))
        .thenReturn(
            WingsException.builder()
                .message(
                    "Connector with credential type InheritFromDelegate does not support validation through harness")
                .build());
    when(azureValidationHandler.validate(any(ConnectorValidationParams.class), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(ArgumentMatchers.eq("Azure")))
        .thenReturn(azureValidationHandler);

    AzureValidationParamsProvider azureValidationParamsProvider = new AzureValidationParamsProvider();
    on(azureValidationParamsProvider).set("encryptionHelper", encryptionHelper);
    when(connectorValidationParamsProviderMap.get(ArgumentMatchers.eq("Azure")))
        .thenReturn(azureValidationParamsProvider);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.AZURE)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));

    assertThatThrownBy(()
                           -> azureConnectorValidator.validate(azureConnectorDTO, "accountIdentifier", "orgIdentifier",
                               "projectIdentifier", "identifier"))
        .isInstanceOf(WingsException.class)
        .hasMessage("Connector with credential type InheritFromDelegate does not support validation through harness");
  }
}
