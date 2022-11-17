package io.harness.ng.core.artifacts.resources.azuremachineimage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.AzureMachineImage.dtos.AzureBuildsDTO;
import io.harness.cdng.artifact.resources.AzureMachineImage.service.AzureMachineImageResourceService;
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

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/azuremachineimage")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AzureMachineImageResource {
  private final AzureMachineImageResourceService azureMachineImageResourceService;
  //  @GET
  //  @Path("/subscriptions/{subscriptionId}/resourceGroups")
  //  @ApiOperation(value = "Gets azure machine image resource groups", nickname = "getresourcegroups")
  //  public ResponseDTO<List<AzureMachineImageResourceGroupDto>> listResourceGroups(
  //      @QueryParam("connectorRef") String AzureConnectorIdentifier,
  //      @PathParam(value = "subscriptionId") String subscriptionId,
  //      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
  //      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
  //      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
  //      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
  //    IdentifierRef connectorRef =
  //        IdentifierRefHelper.getIdentifierRef(AzureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
  //    List<AzureMachineImageResourceGroupDto> resourceGroups = azureMachineImageResourceService.listResourceGroups(
  //        connectorRef, subscriptionId, orgIdentifier, projectIdentifier);
  //
  //    return ResponseDTO.newResponse(resourceGroups);
  //  }

  @GET
  @Path(
      "subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/galleryName/{galleryName}/imageDefinition/{imageDefinition}/getBuilds")
  @ApiOperation(value = "Gets azure machine image builds", nickname = "Getsazuremachineimagebuilds")
  public ResponseDTO<AzureBuildsDTO>
  getBuilds(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @PathParam("resourceGroup") String resourceGroup, @PathParam("galleryName") String galleryName,
      @PathParam("imageDefinition") String imageDefinition) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(azureMachineImageResourceService.getBuilds(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, galleryName, imageDefinition));
  }
}
