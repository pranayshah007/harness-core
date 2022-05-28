/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.azure.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
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
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String ORG_IDENTIFIER = "orgIdentifier";
  private static String PROJECT_IDENTIFIER = "projectIdentifier";
  private final String ACR_SUBSCRIPTION_ID = "123456-5432-5432-543213";
  private final String ACR_RESOURCE_GROUP = "test-rg";
  private final String ACR_SECRET_KEY = "secret";
  private final String ACR_SECRET_CERT = "certificate";
  private final String ACR_CLIENT_ID = "098766-5432-3456-765432";
  private final String ACR_TENANT_ID = "123456-5432-1234-765432";

  @Mock private AzureHelperService azureHelperService;

  @InjectMocks AzureResourceServiceImpl azureResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSubscriptionsSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    String delegateSelector = "test-delegate";
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    Map<String, String> subscriptions = new HashMap<>();
    subscriptions.put("TestSub1", "subscriptionId1");
    subscriptions.put("TestSub2", "subscriptionId2");
    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureSubscriptionsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .subscriptions(subscriptions)
                        .build());

    AzureSubscriptionsDTO azureSubscriptionsDTO =
        azureResourceService.getSubscriptions(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(azureSubscriptionsDTO).isNotNull();
    assertThat(azureSubscriptionsDTO.getSubscriptions().size()).isEqualTo(2);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetResourceGroupsSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    List<String> resourceGroups = new LinkedList<>();
    resourceGroups.add("rg1");
    resourceGroups.add("rg2");
    resourceGroups.add("rg3");
    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureResourceGroupsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .resourceGroups(resourceGroups)
                        .build());

    AzureResourceGroupsDTO azureResourceGroupsDTO =
        azureResourceService.getResourceGroups(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID);
    assertThat(azureResourceGroupsDTO).isNotNull();
    assertThat(azureResourceGroupsDTO.getResourceGroups().size()).isEqualTo(3);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(azureResourceGroupsDTO.getResourceGroups().get(0).getResourceGroup()).isEqualTo("rg1");
    assertThat(azureResourceGroupsDTO.getResourceGroups().get(1).getResourceGroup()).isEqualTo("rg2");
    assertThat(azureResourceGroupsDTO.getResourceGroups().get(2).getResourceGroup()).isEqualTo("rg3");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetClustersSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    List<String> clusters = new LinkedList<>();
    clusters.add("aks1");
    clusters.add("aks2");
    clusters.add("aks3");
    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureClustersResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .clusters(clusters)
                        .build());

    AzureClustersDTO azureClustersDTO = azureResourceService.getClusters(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID, ACR_RESOURCE_GROUP);
    assertThat(azureClustersDTO).isNotNull();
    assertThat(azureClustersDTO.getClusters().size()).isEqualTo(3);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(azureClustersDTO.getClusters().get(0).getCluster()).isEqualTo("aks1");
    assertThat(azureClustersDTO.getClusters().get(1).getCluster()).isEqualTo("aks2");
    assertThat(azureClustersDTO.getClusters().get(2).getCluster()).isEqualTo("aks3");
  }

  private AzureConnectorDTO getSPConnector(AzureSecretType azureSecretType) {
    if (azureSecretType == AzureSecretType.SECRET_KEY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSPWithSecret())
          .build();
    }

    if (azureSecretType == AzureSecretType.KEY_CERT) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSPWithCert())
          .build();
    }

    return null;
  }

  private AzureConnectorDTO getMSIConnector(AzureManagedIdentityType azureManagedIdentityType) {
    if (azureManagedIdentityType == AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForUserAssignedMSI())
          .build();
    }

    if (azureManagedIdentityType == AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSystemAssignedMSI())
          .build();
    }

    return null;
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithSecret() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.SECRET_KEY)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_KEY.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithCert() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.KEY_CERT)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_CERT.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForUserAssignedMSI() {
    AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
        AzureUserAssignedMSIAuthDTO.builder().clientId(ACR_CLIENT_ID).build();

    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
            .credentials(azureUserAssignedMSIAuthDTO)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSystemAssignedMSI() {
    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }
}
