/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
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
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureConnectorMapperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private AzureConnectorMapper connectorMapper;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureConfigInheritFromDelegateUserAssigned() {
    AzureConnectorDTO connectorDTO =
        AzureConnectorDTO.builder()
            .azureEnvironmentType(AzureEnvironmentType.AZURE)
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                    .config(
                        AzureInheritFromDelegateDetailsDTO.builder()
                            .authDTO(
                                AzureMSIAuthUADTO.builder()
                                    .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                                    .credentials(AzureUserAssignedMSIAuthDTO.builder().clientId("client-id").build())
                                    .build())
                            .build())
                    .build())
            .build();

    AzureConfig config = connectorMapper.toAzureConfig(connectorDTO);
    assertThat(config.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(config.getAzureAuthenticationType()).isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
    assertThat(config.getClientId()).isEqualTo("client-id");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureConfigInheritFromDelegateSystemAssigned() {
    AzureConnectorDTO connectorDTO =
        AzureConnectorDTO.builder()
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
    AzureConfig config = connectorMapper.toAzureConfig(connectorDTO);
    assertThat(config.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(config.getAzureAuthenticationType()).isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
    assertThat(config.getClientId()).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureConfigManualCredentialsSecretKey() {
    AzureConnectorDTO connectorDTO =
        AzureConnectorDTO.builder()
            .azureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT)
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                    .config(AzureManualDetailsDTO.builder()
                                .clientId("client-id")
                                .tenantId("tenant-id")
                                .authDTO(AzureAuthDTO.builder()
                                             .azureSecretType(AzureSecretType.SECRET_KEY)
                                             .credentials(AzureClientSecretKeyDTO.builder()
                                                              .secretKey(SecretRefData.builder()
                                                                             .decryptedValue("secret-key".toCharArray())
                                                                             .build())
                                                              .build())
                                             .build())
                                .build())
                    .build())
            .build();

    AzureConfig config = connectorMapper.toAzureConfig(connectorDTO);
    assertThat(config.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE_US_GOVERNMENT);
    assertThat(config.getAzureAuthenticationType()).isEqualTo(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET);
    assertThat(config.getClientId()).isEqualTo("client-id");
    assertThat(config.getTenantId()).isEqualTo("tenant-id");
    assertThat(config.getKey()).isEqualTo("secret-key".toCharArray());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureConfigManualCredentialsServicePrincipalCert() {
    AzureConnectorDTO connectorDTO =
        AzureConnectorDTO.builder()
            .azureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT)
            .credential(
                AzureCredentialDTO.builder()
                    .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                    .config(AzureManualDetailsDTO.builder()
                                .clientId("client-id")
                                .tenantId("tenant-id")
                                .authDTO(
                                    AzureAuthDTO.builder()
                                        .azureSecretType(AzureSecretType.KEY_CERT)
                                        .credentials(AzureClientKeyCertDTO.builder()
                                                         .clientCertRef(SecretRefData.builder()
                                                                            .decryptedValue("client-cert".toCharArray())
                                                                            .build())
                                                         .build())
                                        .build())
                                .build())
                    .build())
            .build();

    AzureConfig config = connectorMapper.toAzureConfig(connectorDTO);
    assertThat(config.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE_US_GOVERNMENT);
    assertThat(config.getAzureAuthenticationType()).isEqualTo(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
    assertThat(config.getClientId()).isEqualTo("client-id");
    assertThat(config.getTenantId()).isEqualTo("tenant-id");
    assertThat(config.getCert()).isEqualTo("client-cert".getBytes());
  }
}