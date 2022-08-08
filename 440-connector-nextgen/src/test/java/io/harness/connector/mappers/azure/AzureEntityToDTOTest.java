/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
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
import io.harness.connector.mappers.azuremapper.AzureEntityToDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureEntityToDTOTest extends CategoryTest {
  @InjectMocks AzureEntityToDTO azureEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateConnectorDTO() {
    String clientId = "clientId";
    String tenantId = "tenantId";
    SecretRefData keySecretRef = SecretRefData.builder().identifier("secretRef").scope(Scope.ACCOUNT).build();

    final AzureConfig azureConfig = AzureConfig.builder()
                                        .credentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                                        .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                        .credential(AzureManualCredential.builder()
                                                        .azureSecretType(AzureSecretType.SECRET_KEY)
                                                        .secretKeyRef("account.secretRef")
                                                        .tenantId(tenantId)
                                                        .clientId(clientId)
                                                        .build())
                                        .build();
    final AzureConnectorDTO connectorDTO = azureEntityToDTO.createConnectorDTO(azureConfig);

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(connectorDTO.getCredential()).isNotNull();
    assertThat(connectorDTO.getCredential().getAzureCredentialType()).isEqualTo(AzureCredentialType.MANUAL_CREDENTIALS);
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    AzureManualDetailsDTO azureManualDetailsDTO = (AzureManualDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(((AzureClientSecretKeyDTO) azureManualDetailsDTO.getAuthDTO().getCredentials()).getSecretKey())
        .isEqualTo(keySecretRef);
    assertThat(azureManualDetailsDTO.getAuthDTO().getAzureSecretType()).isEqualTo(AzureSecretType.SECRET_KEY);
    assertThat(azureManualDetailsDTO.getTenantId()).isEqualTo(tenantId);
    assertThat(azureManualDetailsDTO.getClientId()).isEqualTo(clientId);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testCreateInheritFromDelegateSystemMSIConnectorDTO() {
    AzureConfig azureConfig =
        AzureConfig.builder()
            .credentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
            .azureEnvironmentType(AzureEnvironmentType.AZURE)
            .credential(AzureManagedIdentityCredential.builder()
                            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                            .build())
            .build();
    azureConfig.setDelegateSelectors(Collections.singleton("foo"));

    final AzureConnectorDTO connectorDTO = azureEntityToDTO.createConnectorDTO(azureConfig);

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(connectorDTO.getCredential()).isNotNull();
    assertThat(connectorDTO.getCredential().getAzureCredentialType())
        .isEqualTo(AzureCredentialType.INHERIT_FROM_DELEGATE);
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        (AzureInheritFromDelegateDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(azureInheritFromDelegateDetailsDTO.getAuthDTO()).isInstanceOf(AzureMSIAuthSADTO.class);
    assertThat(((AzureMSIAuthSADTO) azureInheritFromDelegateDetailsDTO.getAuthDTO()).getAzureManagedIdentityType())
        .isEqualTo(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testCreateInheritFromDelegateUserMSIConnectorDTO() {
    AzureConfig azureConfig =
        AzureConfig.builder()
            .credentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
            .azureEnvironmentType(AzureEnvironmentType.AZURE)
            .credential(AzureManagedIdentityCredential.builder()
                            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                            .clientId("testClientId")
                            .build())
            .build();
    azureConfig.setDelegateSelectors(Collections.singleton("foo"));

    final AzureConnectorDTO connectorDTO = azureEntityToDTO.createConnectorDTO(azureConfig);

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(connectorDTO.getCredential()).isNotNull();
    assertThat(connectorDTO.getCredential().getAzureCredentialType())
        .isEqualTo(AzureCredentialType.INHERIT_FROM_DELEGATE);
    assertThat(connectorDTO.getCredential().getConfig()).isNotNull();
    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        (AzureInheritFromDelegateDetailsDTO) connectorDTO.getCredential().getConfig();
    assertThat(azureInheritFromDelegateDetailsDTO.getAuthDTO()).isInstanceOf(AzureMSIAuthUADTO.class);
    assertThat(((AzureMSIAuthUADTO) azureInheritFromDelegateDetailsDTO.getAuthDTO()).getAzureManagedIdentityType())
        .isEqualTo(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY);
    assertThat(((AzureMSIAuthUADTO) azureInheritFromDelegateDetailsDTO.getAuthDTO()).getCredentials()).isNotNull();
    assertThat(((AzureMSIAuthUADTO) azureInheritFromDelegateDetailsDTO.getAuthDTO()).getCredentials().getClientId())
        .isNotNull();
    assertThat(((AzureMSIAuthUADTO) azureInheritFromDelegateDetailsDTO.getAuthDTO()).getCredentials().getClientId())
        .isEqualTo("testClientId");
  }
}
