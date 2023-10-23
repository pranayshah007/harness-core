/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.services.NGHostService;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.beans.HostValidationParams;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.domain.Page;
import retrofit2.http.Body;

@Api("/hosts")
@Path("/hosts")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Tag(name = "Hosts", description = "This contains APIs related to Hosts as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDP)
public class NGHostResource {
  private final NGHostService hostService;
  private final NGHostValidationService hostValidationService;
  private final AccessControlClient accessControlClient;

  @POST
  @Path("filter")
  @ApiOperation(value = "Get hosts filtered by ConnectorDisconnectHandler", nickname = "filterHostsByConnector")
  @Operation(operationId = "filterHostsByConnector",
      summary = "Gets the list of hosts filtered by accountIdentifier and connectorIdentifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of hosts filtered by accountIdentifier and connectorIdentifier")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  public ResponseDTO<PageResponse<HostDTO>>
  filterHostsByConnector(
      @RequestBody(description = "Details of Page including: size, index, sort") @BeanParam PageRequest pageRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Connector Identifier") @QueryParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String scopedConnectorIdentifier,
      @RequestBody(description = "Details of the filters applied") @Body HostFilterDTO filter) {
    Page<HostDTO> connectorResponseDTO = hostService.filterHostsByConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, scopedConnectorIdentifier, filter, pageRequest);
    return ResponseDTO.newResponse(getNGPageResponse(connectorResponseDTO));
  }

  @POST
  @Path("validate")
  @Consumes({"application/json"})
  @ApiOperation(value = "Validate hosts connectivity", nickname = "validateHosts")
  @Operation(operationId = "validateHosts", summary = "Validates hosts connectivity credentials",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns validation response")
      })
  public ResponseDTO<List<HostValidationDTO>>
  validateHost(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Secret Identifier") @QueryParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String secretIdentifier,
      @RequestBody(
          required = true, description = "List of SSH or WinRm hosts to validate, and Delegate tags (optional)")
      @NotNull @Valid HostValidationParams hostValidationParams) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(SECRET_RESOURCE_TYPE, secretIdentifier), SECRET_ACCESS_PERMISSION, "Unauthorized to view secrets.");

    return ResponseDTO.newResponse(hostValidationService.validateHosts(hostValidationParams.getHosts(),
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier,
        hostValidationParams.getTags() != null ? new HashSet<>(hostValidationParams.getTags())
                                               : Collections.emptySet()));
  }
}
