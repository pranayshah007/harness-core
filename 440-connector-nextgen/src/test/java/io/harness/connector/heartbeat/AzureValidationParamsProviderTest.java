/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static io.harness.connector.ConnectorTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.CONNECTOR_NAME;
import static io.harness.connector.ConnectorTestConstants.ORG_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.PROJECT_IDENTIFIER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class AzureValidationParamsProviderTest extends CategoryTest {
  public static final HashSet<String> DELEGATE_SELECTORS = Sets.newHashSet("delegateGroup1, delegateGroup2");
  private static final String clientId = "c-l-i-e-n-t-I-d";
  private static final String tenantId = "t-e-n-a-n-t-I-d";
  private static final String secretKey = "s-e-c-r-e-t-k-e-y";
  private static final String secretCert = "c-e-r-t-c-o-n-t-e-n-t";

  @InjectMocks private AzureValidationParamsProvider azureValidationParamsProvider;
  @Mock private EncryptionHelper encryptionHelper;

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParamsForSPSecretConnector() {
    ConnectorConfigDTO connectorConfigDTO =
        getAzureConnectorDTO(AzureCredentialType.MANUAL_CREDENTIALS, AzureSecretType.SECRET_KEY);
    verifyConnectorValidationParams(connectorConfigDTO);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParamsForSPCertConnector() {
    ConnectorConfigDTO connectorConfigDTO =
        getAzureConnectorDTO(AzureCredentialType.MANUAL_CREDENTIALS, AzureSecretType.KEY_CERT);
    verifyConnectorValidationParams(connectorConfigDTO);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParamsForUserAssignedMSIConnector() {
    ConnectorConfigDTO connectorConfigDTO = getAzureConnectorDTO(
        AzureCredentialType.INHERIT_FROM_DELEGATE, AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY);
    verifyConnectorValidationParams(connectorConfigDTO);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationParamsForSystemAssignedMSIConnector() {
    ConnectorConfigDTO connectorConfigDTO = getAzureConnectorDTO(
        AzureCredentialType.INHERIT_FROM_DELEGATE, AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY);
    verifyConnectorValidationParams(connectorConfigDTO);
  }

  private void verifyConnectorValidationParams(ConnectorConfigDTO connectorConfigDTO) {
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.AZURE)
                                            .name(CONNECTOR_NAME)
                                            .connectorConfig(connectorConfigDTO)
                                            .build();

    ConnectorValidationParams connectorValidationParams = azureValidationParamsProvider.getConnectorValidationParams(
        connectorInfoDTO, CONNECTOR_NAME, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(connectorValidationParams).isNotNull();
    assertThat(connectorValidationParams.getConnectorName()).isEqualTo(CONNECTOR_NAME);
    assertThat(connectorValidationParams.getConnectorType()).isEqualTo(ConnectorType.AZURE);

    List<ExecutionCapability> executionCapabilityList =
        connectorValidationParams.fetchRequiredExecutionCapabilities(null);

    assertThat(executionCapabilityList).isNotNull();
    assertThat(executionCapabilityList)
        .contains(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            "https://login.microsoftonline.com/", null));
    assertThat(executionCapabilityList)
        .contains(SelectorCapability.builder().selectors(DELEGATE_SELECTORS).selectorOrigin("connector").build());
  }

  private AzureConnectorDTO getAzureConnectorDTO(
      AzureCredentialType azureCredentialType, AzureSecretType azureSecretType) {
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .delegateSelectors(DELEGATE_SELECTORS)
        .credential(getManualAzureCredentialDTO(azureCredentialType, azureSecretType))
        .build();
  }

  private AzureConnectorDTO getAzureConnectorDTO(
      AzureCredentialType azureCredentialType, AzureManagedIdentityType azureManagedIdentityType) {
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .delegateSelectors(DELEGATE_SELECTORS)
        .credential(getInhertiFromDelegateAzureCredentialDTO(azureCredentialType, azureManagedIdentityType))
        .build();
  }

  private AzureCredentialDTO getManualAzureCredentialDTO(
      AzureCredentialType azureCredentialType, AzureSecretType azureSecretType) {
    if (azureCredentialType == AzureCredentialType.MANUAL_CREDENTIALS) {
      if (azureSecretType == AzureSecretType.SECRET_KEY) {
        return AzureCredentialDTO.builder()
            .config(AzureManualDetailsDTO.builder()
                        .clientId(clientId)
                        .tenantId(tenantId)
                        .authDTO(getAzureSPSecretAuthDTO())
                        .build())
            .azureCredentialType(azureCredentialType)
            .build();
      }

      if (azureSecretType == AzureSecretType.KEY_CERT) {
        return AzureCredentialDTO.builder()
            .config(AzureManualDetailsDTO.builder()
                        .clientId(clientId)
                        .tenantId(tenantId)
                        .authDTO(getAzureSPCertAuthDTO())
                        .build())
            .azureCredentialType(azureCredentialType)
            .build();
      }
    }

    return null;
  }

  private AzureCredentialDTO getInhertiFromDelegateAzureCredentialDTO(
      AzureCredentialType azureCredentialType, AzureManagedIdentityType azureManagedIdentityType) {
    if (azureCredentialType == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      return AzureCredentialDTO.builder()
          .config(getAzureInheritFromDelegateDetailsDTO(azureManagedIdentityType))
          .azureCredentialType(azureCredentialType)
          .build();
    }

    return null;
  }

  private AzureAuthDTO getAzureSPSecretAuthDTO() {
    return AzureAuthDTO.builder()
        .azureSecretType(AzureSecretType.SECRET_KEY)
        .credentials(AzureClientSecretKeyDTO.builder()
                         .secretKey(SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                         .build())
        .build();
  }

  private AzureAuthDTO getAzureSPCertAuthDTO() {
    return AzureAuthDTO.builder()
        .azureSecretType(AzureSecretType.KEY_CERT)
        .credentials(AzureClientKeyCertDTO.builder()
                         .clientCertRef(SecretRefData.builder().decryptedValue(secretCert.toCharArray()).build())
                         .build())
        .build();
  }

  private AzureInheritFromDelegateDetailsDTO getAzureInheritFromDelegateDetailsDTO(
      AzureManagedIdentityType azureManagedIdentityType) {
    if (azureManagedIdentityType == AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY) {
      return AzureInheritFromDelegateDetailsDTO.builder().authDTO(getAzureUserAssignedMSIAuthDTO()).build();
    }

    if (azureManagedIdentityType == AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY) {
      return AzureInheritFromDelegateDetailsDTO.builder().authDTO(getAzureUserAssignedMSIAuthDTO()).build();
    }

    return null;
  }

  private AzureMSIAuthDTO getAzureUserAssignedMSIAuthDTO() {
    return AzureMSIAuthUADTO.builder()
        .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
        .credentials(AzureUserAssignedMSIAuthDTO.builder().clientId(clientId).build())
        .build();
  }

  private AzureMSIAuthDTO getAzureSystemAssignedMSIAuthDTO() {
    return AzureMSIAuthUADTO.builder()
        .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
        .build();
  }
}
