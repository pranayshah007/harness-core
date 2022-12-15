/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.resources.azure;

import static io.harness.ng.core.Status.SUCCESS;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.azure.resources.dtos.AzureTagDTO;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClusterDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureLocationsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureManagementGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class AzureResourceTest extends CategoryTest {
  @InjectMocks AzureResource azureResource;

  @Mock AzureResourceService azureResourceService;

  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  private static final String ENV_ID = "envId";
  private static final String INFRA_DEFINITION_ID = "infraDefinitionId";

  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String RESOURCE_GROUP = "resourceGroup";

  private final IdentifierRef identifierRef = IdentifierRef.builder()
                                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                                  .build();

  @Rule public ExpectedException expected = ExpectedException.none();

  @Before
  public void setUp() {
    MockedStatic<IdentifierRefHelper> identifierRefHelper = mockStatic(IdentifierRefHelper.class);
    identifierRefHelper
        .when(()
                  -> IdentifierRefHelper.getIdentifierRef(
                      eq(CONNECTOR_REF), eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(identifierRef);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAzureSubscriptionsTest() {
    when(azureResourceService.getSubscriptions(same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(
            AzureSubscriptionsDTO.builder()
                .subscriptions(Arrays.asList(AzureSubscriptionDTO.builder().subscriptionId("subscriptionId").build()))
                .build());
    ResponseDTO<AzureSubscriptionsDTO> result = azureResource.getAzureSubscriptions(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getSubscriptions().get(0).getSubscriptionId().equals("subscriptionId"));
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void getAzureSubscriptionsIdTestError() {
    expected.expect(InvalidRequestException.class);
    expected.expectMessage("subscriptionId must be provided");
    azureResource.getAzureSubscriptions(
        null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, INFRA_DEFINITION_ID);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAppServiceNamesTest() {
    when(azureResourceService.getWebAppNames(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureWebAppNamesDTO.builder().webAppNames(Arrays.asList("name")).build());
    ResponseDTO<AzureWebAppNamesDTO> result = azureResource.getAppServiceNames(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getWebAppNames().get(0).equals("name"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAppServiceDeploymentSlotNamesTest() {
    when(azureResourceService.getAppServiceDeploymentSlots(eq(identifierRef), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP), anyString()))
        .thenReturn(AzureDeploymentSlotsDTO.builder()
                        .deploymentSlots(Arrays.asList(AzureDeploymentSlotDTO.builder().name("name").build()))
                        .build());
    ResponseDTO<AzureDeploymentSlotsDTO> result = azureResource.getAppServiceDeploymentSlotNames(CONNECTOR_REF,
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP, "webApp");
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getDeploymentSlots().get(0).getName().equals("name"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getResourceGroupsBySubscriptionTest() {
    when(azureResourceService.getResourceGroups(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(
            AzureResourceGroupsDTO.builder()
                .resourceGroups(Arrays.asList(AzureResourceGroupDTO.builder().resourceGroup("resourceGroup").build()))
                .build());
    ResponseDTO<AzureResourceGroupsDTO> result = azureResource.getResourceGroupsBySubscription(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getResourceGroups().get(0).getResourceGroup().equals("resourceGroup"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getResourceGroupsV2Test() {
    when(azureResourceService.getResourceGroups(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(
            AzureResourceGroupsDTO.builder()
                .resourceGroups(Arrays.asList(AzureResourceGroupDTO.builder().resourceGroup("resourceGroup").build()))
                .build());
    ResponseDTO<AzureResourceGroupsDTO> result = azureResource.getResourceGroupsV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getResourceGroups().get(0).getResourceGroup().equals("resourceGroup"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getClustersTest() {
    when(azureResourceService.getClusters(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureClustersDTO.builder()
                        .clusters(Arrays.asList(AzureClusterDTO.builder().cluster("cluster").build()))
                        .build());
    ResponseDTO<AzureClustersDTO> result = azureResource.getClusters(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getClusters().get(0).getCluster().equals("cluster"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAzureClustersV2Test() {
    when(azureResourceService.getClusters(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureClustersDTO.builder()
                        .clusters(Arrays.asList(AzureClusterDTO.builder().cluster("cluster").build()))
                        .build());
    ResponseDTO<AzureClustersDTO> result = azureResource.getAzureClustersV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getClusters().get(0).getCluster().equals("cluster"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getSubscriptionTagsTest() {
    when(azureResourceService.getTags(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureTagsDTO.builder().tags(Arrays.asList(AzureTagDTO.builder().tag("tag").build())).build());
    ResponseDTO<AzureTagsDTO> result = azureResource.getSubscriptionTags(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getTags().get(0).getTag().equals("tag"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getSubscriptionTagsV2Test() {
    when(azureResourceService.getTags(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureTagsDTO.builder().tags(Arrays.asList(AzureTagDTO.builder().tag("tag").build())).build());
    ResponseDTO<AzureTagsDTO> result = azureResource.getSubscriptionTagsV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getTags().get(0).getTag().equals("tag"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getManagementGroupsTest() {
    when(azureResourceService.getAzureManagementGroups(same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(AzureManagementGroupsDTO.builder()
                        .managementGroups(Arrays.asList(ManagementGroupData.builder().name("name").build()))
                        .build());
    ResponseDTO<AzureManagementGroupsDTO> result =
        azureResource.getManagementGroups(CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getManagementGroups().get(0).getName().equals("name"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getLocationsTest() {
    when(azureResourceService.getLocations(
             same(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureLocationsDTO.builder().locations(Arrays.asList("location")).build());
    ResponseDTO<AzureLocationsDTO> result = azureResource.getLocations(
        CONNECTOR_REF, SUBSCRIPTION_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(result.getStatus() == SUCCESS);
    assertThat(result.getData().getLocations().get(0).equals("location"));
  }
}
