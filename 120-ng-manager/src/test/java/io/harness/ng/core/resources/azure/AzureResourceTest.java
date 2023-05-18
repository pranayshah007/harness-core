/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.resources.azure;

import static io.harness.ng.core.Status.SUCCESS;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.azure.resources.dtos.AzureTagDTO;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
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
import io.harness.cdng.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.validations.helper.OrgAndProjectValidationHelper;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureResourceTest extends CategoryTest {
  @Mock AzureResourceService azureResourceService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock AccessControlClient accessControlClient;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @InjectMocks AzureResource azureResource;

  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  private static final String ENV_ID = "envId";
  private static final String INFRA_DEFINITION_ID = "infraDefinitionId";

  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String RESOURCE_GROUP = "resourceGroup";
  private static final String WEB_APP_NAME = "AzureWebAppName";

  private final IdentifierRef identifierRef = IdentifierRefHelper.getConnectorIdentifierRef(
      CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAzureSubscriptionsTest() {
    when(azureResourceService.getSubscriptions(eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(
            AzureSubscriptionsDTO.builder()
                .subscriptions(Arrays.asList(AzureSubscriptionDTO.builder().subscriptionId("subscriptionId").build()))
                .build());
    ResponseDTO<AzureSubscriptionsDTO> result = azureResource.getAzureSubscriptions(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getSubscriptions().get(0).getSubscriptionId()).isEqualTo("subscriptionId");
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void getAzureSubscriptionsIdTestError() {
    assertThatThrownBy(()
                           -> azureResource.getAzureSubscriptions(
                               null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, INFRA_DEFINITION_ID))
        .isInstanceOf(InvalidIdentifierRefException.class)
        .hasMessage("Unable to resolve empty connector identifier reference");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAppServiceNamesTest() {
    when(azureResourceService.getWebAppNames(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureWebAppNamesDTO.builder().webAppNames(Arrays.asList("name")).build());
    ResponseDTO<AzureWebAppNamesDTO> result = azureResource.getAppServiceNames(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getWebAppNames().get(0)).isEqualTo("name");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void getAppServiceNamesV2Test() {
    when(azureResourceService.getWebAppNames(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureWebAppNamesDTO.builder().webAppNames(Arrays.asList("name")).build());
    ResponseDTO<AzureWebAppNamesDTO> result = azureResource.getAppServiceNamesV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getWebAppNames().get(0)).isEqualTo("name");
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
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getDeploymentSlots().get(0).getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void getAppServiceDeploymentSlotNamesV2Test() {
    when(azureResourceService.getAppServiceDeploymentSlots(eq(identifierRef), eq(ORG_IDENTIFIER),
             eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP), anyString()))
        .thenReturn(AzureDeploymentSlotsDTO.builder()
                        .deploymentSlots(Arrays.asList(AzureDeploymentSlotDTO.builder().name("name").build()))
                        .build());
    ResponseDTO<AzureDeploymentSlotsDTO> result =
        azureResource.getAppServiceDeploymentSlotNamesV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP, ENV_ID, INFRA_DEFINITION_ID, "webApp");
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getDeploymentSlots().get(0).getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getResourceGroupsBySubscriptionTest() {
    when(azureResourceService.getResourceGroups(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(
            AzureResourceGroupsDTO.builder()
                .resourceGroups(Arrays.asList(AzureResourceGroupDTO.builder().resourceGroup("resourceGroup").build()))
                .build());
    ResponseDTO<AzureResourceGroupsDTO> result = azureResource.getResourceGroupsBySubscription(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getResourceGroups().get(0).getResourceGroup()).isEqualTo("resourceGroup");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getResourceGroupsV2Test() {
    when(azureResourceService.getResourceGroups(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(
            AzureResourceGroupsDTO.builder()
                .resourceGroups(Arrays.asList(AzureResourceGroupDTO.builder().resourceGroup("resourceGroup").build()))
                .build());
    ResponseDTO<AzureResourceGroupsDTO> result = azureResource.getResourceGroupsV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getResourceGroups().get(0).getResourceGroup()).isEqualTo("resourceGroup");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getClustersTest() {
    when(azureResourceService.getClusters(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureClustersDTO.builder()
                        .clusters(Arrays.asList(AzureClusterDTO.builder().cluster("cluster").build()))
                        .build());
    ResponseDTO<AzureClustersDTO> result = azureResource.getClusters(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getClusters().get(0).getCluster()).isEqualTo("cluster");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAzureClustersV2Test() {
    when(azureResourceService.getClusters(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID), eq(RESOURCE_GROUP)))
        .thenReturn(AzureClustersDTO.builder()
                        .clusters(Arrays.asList(AzureClusterDTO.builder().cluster("cluster").build()))
                        .build());
    ResponseDTO<AzureClustersDTO> result = azureResource.getAzureClustersV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, RESOURCE_GROUP, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getClusters().get(0).getCluster()).isEqualTo("cluster");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getSubscriptionTagsTest() {
    when(azureResourceService.getTags(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureTagsDTO.builder().tags(Arrays.asList(AzureTagDTO.builder().tag("tag").build())).build());
    ResponseDTO<AzureTagsDTO> result = azureResource.getSubscriptionTags(
        CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getTags().get(0).getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getSubscriptionTagsV2Test() {
    when(azureResourceService.getTags(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureTagsDTO.builder().tags(Arrays.asList(AzureTagDTO.builder().tag("tag").build())).build());
    ResponseDTO<AzureTagsDTO> result = azureResource.getSubscriptionTagsV2(CONNECTOR_REF, ACCOUNT_IDENTIFIER,
        ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION_ID, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getTags().get(0).getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getManagementGroupsTest() {
    when(azureResourceService.getAzureManagementGroups(eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER)))
        .thenReturn(AzureManagementGroupsDTO.builder()
                        .managementGroups(Arrays.asList(ManagementGroupData.builder().name("name").build()))
                        .build());
    ResponseDTO<AzureManagementGroupsDTO> result =
        azureResource.getManagementGroups(CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getManagementGroups().get(0).getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getLocationsTest() {
    when(azureResourceService.getLocations(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(SUBSCRIPTION_ID)))
        .thenReturn(AzureLocationsDTO.builder().locations(Arrays.asList("location")).build());
    ResponseDTO<AzureLocationsDTO> result = azureResource.getLocations(
        CONNECTOR_REF, SUBSCRIPTION_ID, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getLocations().get(0)).isEqualTo("location");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void getAppServiceNamesForInfraTest() {
    InfrastructureConfig azureInfra =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJECT_IDENTIFIER)
                    .identifier(INFRA_DEFINITION_ID)
                    .type(InfrastructureType.AZURE_WEB_APP)
                    .spec(AzureWebAppInfrastructure.builder()
                              .connectorRef(ParameterField.createValueField(identifierRef.getIdentifier()))
                              .build())
                    .build())
            .build();
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                                    .identifier(INFRA_DEFINITION_ID)
                                                    .envIdentifier(ENV_ID)
                                                    .type(azureInfra.getInfrastructureDefinitionConfig().getType())
                                                    .yaml(YamlPipelineUtils.writeYamlString(azureInfra))
                                                    .build();
    when(infrastructureEntityService.get(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_ID, INFRA_DEFINITION_ID))
        .thenReturn(Optional.of(infrastructureEntity));
    when(azureResourceService.getWebAppNames(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), any(), any()))
        .thenReturn(AzureWebAppNamesDTO.builder().webAppNames(Arrays.asList(WEB_APP_NAME)).build());

    ResponseDTO<AzureWebAppNamesDTO> result = azureResource.getAppServiceNamesV2(
        null, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null, ENV_ID, INFRA_DEFINITION_ID);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getWebAppNames().get(0)).isEqualTo(WEB_APP_NAME);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void getAppServiceDeploymenSlotsForInfraTest() {
    String slotName = "slotName";
    InfrastructureConfig azureInfra =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJECT_IDENTIFIER)
                    .identifier(INFRA_DEFINITION_ID)
                    .type(InfrastructureType.AZURE_WEB_APP)
                    .spec(AzureWebAppInfrastructure.builder()
                              .connectorRef(ParameterField.createValueField(identifierRef.getIdentifier()))
                              .build())
                    .build())
            .build();
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                                    .identifier(INFRA_DEFINITION_ID)
                                                    .envIdentifier(ENV_ID)
                                                    .type(azureInfra.getInfrastructureDefinitionConfig().getType())
                                                    .yaml(YamlPipelineUtils.writeYamlString(azureInfra))
                                                    .build();
    when(infrastructureEntityService.get(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ENV_ID, INFRA_DEFINITION_ID))
        .thenReturn(Optional.of(infrastructureEntity));
    when(azureResourceService.getAppServiceDeploymentSlots(
             eq(identifierRef), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), any(), any(), eq(WEB_APP_NAME)))
        .thenReturn(AzureDeploymentSlotsDTO.builder()
                        .deploymentSlots(Arrays.asList(AzureDeploymentSlotDTO.builder().name(slotName).build()))
                        .build());
    ResponseDTO<AzureDeploymentSlotsDTO> result = azureResource.getAppServiceDeploymentSlotNamesV2(null,
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null, ENV_ID, INFRA_DEFINITION_ID, WEB_APP_NAME);
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
    assertThat(result.getData().getDeploymentSlots().get(0).getName()).isEqualTo(slotName);
  }
}
