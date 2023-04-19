/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.IDENTIFIER;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.SERVICEACCOUNT;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.ng.accesscontrol.PlatformPermissions;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.service.ServiceAccountDTOMapper;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.security.annotations.InternalApi;
import io.harness.serviceaccount.ServiceAccountDTO;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Optional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@Api("serviceaccount")
@Path("serviceaccount")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Service Account", description = "This has all the APIs specific to the Service Accounts in Harness.")
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
@Slf4j
@OwnedBy(PL)
public class ServiceAccountResource {
  @Inject private final ServiceAccountService serviceAccountService;
  @Inject private final AccessControlClient accessControlClient;

  @POST
  @ApiOperation(value = "Create service account", nickname = "createServiceAccount")
  @Operation(operationId = "createServiceAccount", summary = "Create a Service Account",
      description = "Creates a new Service Account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns details of the created Service Account")
      })
  @NGAccessControlCheck(resourceType = SERVICEACCOUNT, permission = PlatformPermissions.EDIT_SERVICEACCOUNT_PERMISSION)
  public ResponseDTO<ServiceAccountDTO>
  createServiceAccount(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @RequestBody(required = true, description = "Details required to create Service Account")
      @Valid ServiceAccountDTO serviceAccountRequestDTO) {
    ServiceAccountDTO serviceAccountDTO = serviceAccountService.createServiceAccount(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceAccountRequestDTO);
    return ResponseDTO.newResponse(serviceAccountDTO);
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update service account", nickname = "updateServiceAccount")
  @Operation(operationId = "updateServiceAccount", summary = "Update a Service Account",
      description = "Updates details of the Service Account for the given Service Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Service Account details.")
      })
  @NGAccessControlCheck(resourceType = SERVICEACCOUNT, permission = PlatformPermissions.EDIT_SERVICEACCOUNT_PERMISSION)
  public ResponseDTO<ServiceAccountDTO>
  updateServiceAccount(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Service Account ID") @NotNull @PathParam(
          IDENTIFIER) @ResourceIdentifier String identifier,
      @RequestBody(required = true,
          description = "Details of the updated Service Account") @Valid ServiceAccountDTO serviceAccountRequestDTO) {
    ServiceAccountDTO serviceAccountDTO = serviceAccountService.updateServiceAccount(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, serviceAccountRequestDTO);
    return ResponseDTO.newResponse(serviceAccountDTO);
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete service account", nickname = "deleteServiceAccount")
  @Operation(operationId = "deleteServiceAccount", summary = "Delete a Service Account",
      description = "Deletes a Service Account corresponding to the given Service Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "It returns true if the Service Account is deleted successfully and false if the Service Account is not deleted.")
      })
  @NGAccessControlCheck(
      resourceType = SERVICEACCOUNT, permission = PlatformPermissions.DELETE_SERVICEACCOUNT_PERMISSION)
  public ResponseDTO<Boolean>
  deleteServiceAccount(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Service Account ID") @NotNull @PathParam(
          IDENTIFIER) @ResourceIdentifier String identifier) {
    boolean deleted =
        serviceAccountService.deleteServiceAccount(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return ResponseDTO.newResponse(deleted);
  }

  @GET
  @ApiOperation(value = "List service account", nickname = "listServiceAccount")
  @Operation(operationId = "listServiceAccount", summary = "Get Service Accounts",
      description = "Fetches list of Service Accounts for the given filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Service Accounts.")
      })
  public ResponseDTO<List<ServiceAccountDTO>>
  listServiceAccounts(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(
          description = "This is the list of Service Account IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers) {
    List<ServiceAccount> serviceAccounts =
        serviceAccountService.listServiceAccounts(accountIdentifier, orgIdentifier, projectIdentifier, identifiers);
    if (!accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(SERVICEACCOUNT, null), VIEW_SERVICEACCOUNT_PERMISSION)) {
      serviceAccounts = serviceAccountService.getPermittedServiceAccounts(serviceAccounts);
    }
    List<ServiceAccountDTO> serviceAccountDTOS =
        serviceAccounts.stream().map(ServiceAccountDTOMapper::getDTOFromServiceAccount).collect(Collectors.toList());
    return ResponseDTO.newResponse(serviceAccountDTOS);
  }

  @GET
  @Path("aggregate")
  @ApiOperation(value = "List service account", nickname = "listAggregatedServiceAccounts")
  @Operation(operationId = "listAggregatedServiceAccounts", summary = "List aggregated Service Accounts",
      description = "Fetches the list of Aggregated Service Accounts corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns the paginated list of Aggregated Service Accounts.")
      })
  public ResponseDTO<PageResponse<ServiceAccountAggregateDTO>>
  listAggregatedServiceAccounts(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(
          description = "This is the list of Service Account IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers,
      @BeanParam PageRequest pageRequest,
      @Parameter(
          description =
              "This would be used to filter Service Accounts. Any Service Account having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    ServiceAccountFilterDTO filterDTO = ServiceAccountFilterDTO.builder()
                                            .accountIdentifier(accountIdentifier)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .searchTerm(searchTerm)
                                            .identifiers(identifiers)
                                            .build();
    PageResponse<ServiceAccountAggregateDTO> requestDTOS = serviceAccountService.listAggregateServiceAccounts(
        accountIdentifier, orgIdentifier, projectIdentifier, identifiers, getPageRequest(pageRequest), filterDTO);
    return ResponseDTO.newResponse(requestDTOS);
  }

  @GET
  @Path("aggregate/{identifier}")
  @ApiOperation(value = "Get service account", nickname = "getAggregatedServiceAccount")
  @Operation(operationId = "getAggregatedServiceAccount", summary = "Get Service Account In Scope",
      description = "Gets the list of Service Accounts in the given scope.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns the Service Account details corresponding to the specified Account Identifier and Service Account Identifier")
      })
  @NGAccessControlCheck(resourceType = SERVICEACCOUNT, permission = PlatformPermissions.VIEW_SERVICEACCOUNT_PERMISSION)
  public ResponseDTO<ServiceAccountAggregateDTO>
  getAggregatedServiceAccount(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Service Account IDr") @NotNull @PathParam(
          IDENTIFIER) @ResourceIdentifier String identifier) {
    ServiceAccountAggregateDTO aggregateDTO = serviceAccountService.getServiceAccountAggregateDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return ResponseDTO.newResponse(aggregateDTO);
  }

  @GET
  @Path("internal/{identifier}")
  @Hidden
  @InternalApi
  @ApiOperation(value = "Get service account by id", nickname = "getServiceAccountInternal")
  public ResponseDTO<ServiceAccountDTO> getServiceAccountInternal(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @PathParam(IDENTIFIER) @NotNull String identifier) {
    ServiceAccountDTO requestDTOS = serviceAccountService.getServiceAccountDTO(accountIdentifier, identifier);
    return ResponseDTO.newResponse(requestDTOS);
  }
}
