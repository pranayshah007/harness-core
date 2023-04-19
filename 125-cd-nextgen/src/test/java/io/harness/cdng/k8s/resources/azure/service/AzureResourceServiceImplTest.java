/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.azure.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLICA;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureImageGalleriesDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureLocationsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureManagementGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureImageGalleriesResponse;
import io.harness.delegate.beans.azure.response.AzureLocationsResponse;
import io.harness.delegate.beans.azure.response.AzureMngGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureTagsResponse;
import io.harness.delegate.beans.azure.response.AzureWebAppNamesResponse;
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

import software.wings.beans.AzureImageGallery;

import java.util.ArrayList;
import java.util.Arrays;
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
  private final String WEB_APP_NAME = "test-web-app";
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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetWebAppNamesSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    List<String> webAppNames = Arrays.asList("test-web-app-1", "test-web-app-2", "test-web-app-3");
    when(azureHelperService.executeSyncTask(any(), any(), anyString(), any()))
        .thenReturn(AzureWebAppNamesResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .webAppNames(webAppNames)
                        .build());

    AzureWebAppNamesDTO azureWebAppNamesDTO = azureResourceService.getWebAppNames(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID, ACR_RESOURCE_GROUP);

    assertThat(azureWebAppNamesDTO).isNotNull();
    assertThat(azureWebAppNamesDTO.getWebAppNames().size()).isEqualTo(3);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString(), any());

    assertThat(azureWebAppNamesDTO.getWebAppNames().get(0)).isEqualTo("test-web-app-1");
    assertThat(azureWebAppNamesDTO.getWebAppNames().get(1)).isEqualTo("test-web-app-2");
    assertThat(azureWebAppNamesDTO.getWebAppNames().get(2)).isEqualTo("test-web-app-3");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetWebAppDeploymentSlotsSuccess() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    List<AzureDeploymentSlotResponse> deploymentSlots =
        Arrays.asList(AzureDeploymentSlotResponse.builder().name("depSlot-1-name").type("depSlot-1-type").build(),
            AzureDeploymentSlotResponse.builder().name("depSlot-2-name").type("depSlot-2-type").build());
    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureDeploymentSlotsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .deploymentSlots(deploymentSlots)
                        .build());

    AzureDeploymentSlotsDTO azureDeploymentSlotsResponse = azureResourceService.getAppServiceDeploymentSlots(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID, ACR_RESOURCE_GROUP, WEB_APP_NAME);

    assertThat(azureDeploymentSlotsResponse).isNotNull();
    assertThat(azureDeploymentSlotsResponse.getDeploymentSlots().size()).isEqualTo(2);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(azureDeploymentSlotsResponse.getDeploymentSlots().get(0).getName()).isEqualTo("depSlot-1-name");
    assertThat(azureDeploymentSlotsResponse.getDeploymentSlots().get(0).getType()).isEqualTo("depSlot-1-type");
    assertThat(azureDeploymentSlotsResponse.getDeploymentSlots().get(1).getName()).isEqualTo("depSlot-2-name");
    assertThat(azureDeploymentSlotsResponse.getDeploymentSlots().get(1).getType()).isEqualTo("depSlot-2-type");
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
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetImageGallery() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    AzureImageGallery azureImageGallery = new AzureImageGallery();
    azureImageGallery.setName("name");
    azureImageGallery.setRegionName("region");
    azureImageGallery.setResourceGroupName("resourceGroupName");
    azureImageGallery.setSubscriptionId("subscriptionId");
    List<AzureImageGallery> imageGalleryList = new ArrayList<>();
    imageGalleryList.add(azureImageGallery);
    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureImageGalleriesResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .azureImageGalleries(imageGalleryList)
                        .build());

    AzureImageGalleriesDTO azureImageGalleriesDTO = azureResourceService.getImageGallery(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID, "resourceGroupName");
    assertThat(azureImageGalleriesDTO).isNotNull();
    assertThat(azureImageGalleriesDTO.getAzureImageGalleries().size()).isEqualTo(1);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(azureImageGalleriesDTO.getAzureImageGalleries()).isEqualTo(imageGalleryList);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetTags() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureTagsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .tags(Arrays.asList("tag1", "tag2"))
                        .build());

    AzureTagsDTO result =
        azureResourceService.getTags(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID);

    assertThat(result).isNotNull();
    assertThat(result.getTags().size()).isEqualTo(2);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(result.getTags().get(0).getTag()).isEqualTo("tag1");
    assertThat(result.getTags().get(1).getTag()).isEqualTo("tag2");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetAzureManagementGroups() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureMngGroupsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .managementGroups(Arrays.asList(ManagementGroupData.builder().name("name").build()))
                        .build());

    AzureManagementGroupsDTO result =
        azureResourceService.getAzureManagementGroups(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.getManagementGroups().size()).isEqualTo(1);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(result.getManagementGroups().get(0).getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetLocations() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);

    when(azureHelperService.getConnector(identifierRef)).thenReturn(azureConnectorDTO);

    when(azureHelperService.executeSyncTask(any(), any(), anyString()))
        .thenReturn(AzureLocationsResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .locations(Arrays.asList("loc1"))
                        .build());

    AzureLocationsDTO result =
        azureResourceService.getLocations(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ACR_SUBSCRIPTION_ID);

    assertThat(result.getLocations().size()).isEqualTo(1);

    verify(azureHelperService, times(1)).executeSyncTask(any(), any(), anyString());

    assertThat(result.getLocations().get(0)).isEqualTo("loc1");
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
