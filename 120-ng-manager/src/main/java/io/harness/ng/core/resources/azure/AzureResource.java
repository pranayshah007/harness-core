/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.resources.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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

  @GET
  @Path("subscriptions")
  @ApiOperation(value = "Gets azure subscriptions ", nickname = "getAzureSubscriptions")
  public ResponseDTO<AzureSubscriptionsDTO> getAzureSubscriptions(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
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
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
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
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
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
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getResourceGroups(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/clusters")
  @ApiOperation(value = "Gets azure k8s clusters by subscription ", nickname = "getAzureClusters")
  public ResponseDTO<AzureClustersDTO> getClusters(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(azureResourceService.getClusters(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/tags")
  @ApiOperation(value = "Gets azure tags by subscription ", nickname = "getSubscriptionTags")
  public ResponseDTO<AzureTagsDTO> getSubscriptionTags(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        azureResourceService.getTags(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }
}
