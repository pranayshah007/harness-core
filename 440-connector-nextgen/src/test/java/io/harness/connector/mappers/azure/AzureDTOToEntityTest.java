/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.azureconnector.AzureCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.azureconnector.AzureCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.azureconnector.AzureConfig;
import io.harness.connector.entities.embedded.azureconnector.AzureManagedIdentityCredential;
import io.harness.connector.entities.embedded.azureconnector.AzureManualCredential;
import io.harness.connector.mappers.azuremapper.AzureDTOToEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureDTOToEntityTest extends CategoryTest {
  @InjectMocks AzureDTOToEntity azureDTOToEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testManualCredentialsConnectorWithSecretToConnectorEntity() {
    String clientId = "clientId";
    String tenantId = "tenantId";
    SecretRefData keySecretRef = SecretRefData.builder().identifier("secretRef").scope(Scope.ACCOUNT).build();
    AzureAuthDTO azureAuthDTO = AzureAuthDTO.builder()
                                    .azureSecretType(AzureSecretType.SECRET_KEY)
                                    .credentials(AzureClientSecretKeyDTO.builder().secretKey(keySecretRef).build())
                                    .build();
    AzureCredentialSpecDTO azureCredentialSpecDTO =
        AzureManualDetailsDTO.builder().clientId(clientId).tenantId(tenantId).authDTO(azureAuthDTO).build();

    AzureCredentialDTO azureCredentialDTO =
        AzureCredentialDTO.builder().azureCredentialType(MANUAL_CREDENTIALS).config(azureCredentialSpecDTO).build();

    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder()
                                              .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                              .credential(azureCredentialDTO)
                                              .build();
    final AzureConfig azureConfig = azureDTOToEntity.toConnectorEntity(azureConnectorDTO);

    assertThat(azureConfig).isNotNull();
    assertThat(azureConfig.getCredentialType()).isEqualTo(AzureCredentialType.MANUAL_CREDENTIALS);
    assertThat(azureConfig.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(azureConfig.getCredential()).isNotNull();

    AzureManualCredential azureManualCredential = (AzureManualCredential) azureConfig.getCredential();
    assertThat(azureManualCredential.getAzureSecretType()).isEqualTo(AzureSecretType.SECRET_KEY);
    assertThat(azureManualCredential.getClientId()).isEqualTo(clientId);
    assertThat(azureManualCredential.getTenantId()).isEqualTo(tenantId);
    assertThat(azureManualCredential.getSecretKeyRef()).isEqualTo(keySecretRef.toSecretRefStringValue());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testManualCredentialsConnectorWithCertificateToConnectorEntity() {
    String clientId = "clientId";
    String tenantId = "tenantId";
    SecretRefData secretCertRef = SecretRefData.builder().identifier("certRef").scope(Scope.ACCOUNT).build();
    AzureAuthDTO azureAuthDTO = AzureAuthDTO.builder()
                                    .azureSecretType(AzureSecretType.KEY_CERT)
                                    .credentials(AzureClientKeyCertDTO.builder().clientCertRef(secretCertRef).build())
                                    .build();
    AzureCredentialSpecDTO azureCredentialSpecDTO =
        AzureManualDetailsDTO.builder().clientId(clientId).tenantId(tenantId).authDTO(azureAuthDTO).build();

    AzureCredentialDTO azureCredentialDTO =
        AzureCredentialDTO.builder().azureCredentialType(MANUAL_CREDENTIALS).config(azureCredentialSpecDTO).build();

    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder()
                                              .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                              .credential(azureCredentialDTO)
                                              .build();
    final AzureConfig azureConfig = azureDTOToEntity.toConnectorEntity(azureConnectorDTO);

    assertThat(azureConfig).isNotNull();
    assertThat(azureConfig.getCredentialType()).isEqualTo(AzureCredentialType.MANUAL_CREDENTIALS);
    assertThat(azureConfig.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(azureConfig.getCredential()).isNotNull();

    AzureManualCredential azureManualCredential = (AzureManualCredential) azureConfig.getCredential();
    assertThat(azureManualCredential.getAzureSecretType()).isEqualTo(AzureSecretType.KEY_CERT);
    assertThat(azureManualCredential.getClientId()).isEqualTo(clientId);
    assertThat(azureManualCredential.getTenantId()).isEqualTo(tenantId);
    assertThat(azureManualCredential.getSecretKeyRef()).isEqualTo(secretCertRef.toSecretRefStringValue());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testInheritFromDelegateSystemMSIConnectorToConnectorEntity() {
    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthSADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
            .build();

    AzureCredentialSpecDTO azureCredentialSpecDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    AzureCredentialDTO azureCredentialDTO =
        AzureCredentialDTO.builder().azureCredentialType(INHERIT_FROM_DELEGATE).config(azureCredentialSpecDTO).build();

    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder()
                                              .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                              .credential(azureCredentialDTO)
                                              .build();

    final AzureConfig azureConfig = azureDTOToEntity.toConnectorEntity(azureConnectorDTO);

    assertThat(azureConfig).isNotNull();
    assertThat(azureConfig.getCredentialType()).isEqualTo(INHERIT_FROM_DELEGATE);
    assertThat(azureConfig.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(azureConfig.getCredential()).isNotNull();

    AzureManagedIdentityCredential azureManagedIdentityCredential =
        (AzureManagedIdentityCredential) azureConfig.getCredential();
    assertThat(azureManagedIdentityCredential.getAzureManagedIdentityType())
        .isEqualTo(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY);
    assertThat(azureManagedIdentityCredential.getClientId()).isNull();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testInheritFromDelegateUserMSIConnectorToConnectorEntity() {
    AzureUserAssignedMSIAuthDTO azureAuthCredentialDTO =
        AzureUserAssignedMSIAuthDTO.builder().clientId("testClientId").build();

    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
            .credentials(azureAuthCredentialDTO)
            .build();

    AzureCredentialSpecDTO azureCredentialSpecDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    AzureCredentialDTO azureCredentialDTO =
        AzureCredentialDTO.builder().azureCredentialType(INHERIT_FROM_DELEGATE).config(azureCredentialSpecDTO).build();

    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder()
                                              .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                              .credential(azureCredentialDTO)
                                              .build();

    final AzureConfig azureConfig = azureDTOToEntity.toConnectorEntity(azureConnectorDTO);

    assertThat(azureConfig).isNotNull();
    assertThat(azureConfig.getCredentialType()).isEqualTo(INHERIT_FROM_DELEGATE);
    assertThat(azureConfig.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(azureConfig.getCredential()).isNotNull();

    AzureManagedIdentityCredential azureManagedIdentityCredential =
        (AzureManagedIdentityCredential) azureConfig.getCredential();
    assertThat(azureManagedIdentityCredential.getAzureManagedIdentityType())
        .isEqualTo(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY);
    assertThat(azureManagedIdentityCredential.getClientId()).isEqualTo("testClientId");
  }
}
