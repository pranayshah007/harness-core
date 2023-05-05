/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.resources.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AzureInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureImageGalleriesDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureLocationsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureManagementGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.validations.helper.OrgAndProjectValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Api("azure")
@Path("/azure")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AzureResource {
  private final AzureResourceService azureResourceService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final AccessControlClient accessControlClient;

  @GET
  @Path("subscriptions")
  @ApiOperation(value = "Gets azure subscriptions ", nickname = "getAzureSubscriptions")
  public ResponseDTO<AzureSubscriptionsDTO> getAzureSubscriptions(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    if (isEmpty(azureConnectorIdentifier) && isNotEmpty(envId) && isNotEmpty(infraDefinitionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      azureConnectorIdentifier = infrastructureDefinitionConfig.getSpec().getConnectorReference().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getSubscriptions(connectorRef, orgIdentifier, projectIdentifier));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/app-services-names")
  @ApiOperation(
      value = "Gets azure app services names by subscriptionId and resourceGroup", nickname = "getAzureWebAppNames")
  public ResponseDTO<AzureWebAppNamesDTO>
  getAppServiceNames(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @NotEmpty @PathParam("subscriptionId") String subscriptionId,
      @NotNull @NotEmpty @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getWebAppNames(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }

  @GET
  @Path("v2/app-services-names")
  @ApiOperation(value = "Gets azure app services names V2", nickname = "getAzureWebAppNamesV2")
  public ResponseDTO<AzureWebAppNamesDTO> getAppServiceNamesV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }
    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }
    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getWebAppNames(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/app-services/{webAppName}/slots")
  @ApiOperation(value = "Gets azure webApp deployment slots", nickname = "getAzureWebAppDeploymentSlots")
  public ResponseDTO<AzureDeploymentSlotsDTO> getAppServiceDeploymentSlotNames(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @NotEmpty @PathParam("subscriptionId") String subscriptionId,
      @NotNull @NotEmpty @PathParam("resourceGroup") String resourceGroup,
      @NotNull @NotEmpty @PathParam("webAppName") String webAppName) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getAppServiceDeploymentSlots(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, webAppName));
  }

  @GET
  @Path("v2/app-services/{webAppName}/slots")
  @ApiOperation(value = "Gets azure webApp deployment slots V2", nickname = "getAzureWebAppDeploymentSlotsV2")
  public ResponseDTO<AzureDeploymentSlotsDTO> getAppServiceDeploymentSlotNamesV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId,
      @NotNull @PathParam("webAppName") String webAppName) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }
    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }
    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getAppServiceDeploymentSlots(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, webAppName));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups")
  @ApiOperation(
      value = "Gets azure resource groups by subscription ", nickname = "getAzureResourceGroupsBySubscription")
  public ResponseDTO<AzureResourceGroupsDTO>
  getResourceGroupsBySubscription(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getResourceGroups(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }
  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/imageGalleries")
  @ApiOperation(
      value = "Gets azure image Galleries by resource group", nickname = "GetsazureimageGalleriesbyresourcegroup")
  public ResponseDTO<AzureImageGalleriesDTO>
  getImageGalleries(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getImageGallery(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }
  @GET
  @Path("v2/resourceGroups")
  @ApiOperation(value = "Gets azure resource groups V2", nickname = "getAzureResourceGroupsV2")
  public ResponseDTO<AzureResourceGroupsDTO> getResourceGroupsV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getResourceGroups(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/clusters")
  @ApiOperation(value = "Gets azure k8s clusters by subscription ", nickname = "getAzureClusters")
  public ResponseDTO<AzureClustersDTO> getClusters(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getClusters(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }

  @GET
  @Path("v2/clusters")
  @ApiOperation(value = "Gets azure k8s clusters ", nickname = "getAzureClustersV2")
  public ResponseDTO<AzureClustersDTO> getAzureClustersV2(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getClusters(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/tags")
  @ApiOperation(value = "Gets azure tags by subscription ", nickname = "getSubscriptionTags")
  public ResponseDTO<AzureTagsDTO> getSubscriptionTags(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getTags(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  @GET
  @Path("v2/tags")
  @ApiOperation(value = "Gets azure tags by subscription ", nickname = "getSubscriptionTagsV2")
  public ResponseDTO<AzureTagsDTO> getSubscriptionTagsV2(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getTags(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  private InfrastructureDefinitionConfig getInfrastructureDefinitionConfig(
      String accountId, String orgIdentifier, String projectIdentifier, String envId, String infraDefinitionId) {
    if (isEmpty(envId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.ENVIRONMENT_KEY)));
    }

    if (isEmpty(infraDefinitionId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.INFRA_DEFINITION_KEY)));
    }

    InfrastructureEntity infrastructureEntity =
        infrastructureEntityService.get(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId)
            .orElseThrow(() -> {
              throw new NotFoundException(String.format(
                  "Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
                  infraDefinitionId, projectIdentifier, orgIdentifier, envId));
            });

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity)
        .getInfrastructureDefinitionConfig();
  }

  @GET
  @Path("management-groups")
  @ApiOperation(value = "Gets azure management groups", nickname = "getManagementGroups")
  public ResponseDTO<AzureManagementGroupsDTO> getManagementGroups(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getAzureManagementGroups(connectorRef, orgIdentifier, projectIdentifier));
  }

  @GET
  @Path("locations")
  @ApiOperation(value = "Gets azure locations defined for a subscription", nickname = "getLocationsBySubscription")
  public ResponseDTO<AzureLocationsDTO> getLocations(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getLocations(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }
}
