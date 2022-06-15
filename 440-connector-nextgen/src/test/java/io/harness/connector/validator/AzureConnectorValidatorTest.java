/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
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
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(CDP)
public class AzureConnectorValidatorTest extends CategoryTest {
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private SecretManagerClientService ngSecretService;
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks private AzureConnectorValidator azureConnectorValidator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
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

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(AzureValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
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

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(AzureValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
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

    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(AzureValidateTaskResponse.builder()
                        .connectorValidationResult(
                            ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build())
                        .build());
    azureConnectorValidator.validate(
        azureConnectorDTO, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());

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
    verify(delegateGrpcClientWrapper, times(2)).executeSyncTask(any());
  }
}
