/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicenow.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.servicenow.resources.service.ServiceNowResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketTypeDTO;
import io.harness.servicenow.ServiceNowTicketTypeNG;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@OwnedBy(CDC)
@Api("servicenow")
@Path("/servicenow")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class ServiceNowResource {
  private final ServiceNowResourceService serviceNowResourceService;
  @GET
  @Path("ticketTypes")
  @ApiOperation(value = "Get serviceNow ticket types", nickname = "getServiceNowTicketTypes")
  public ResponseDTO<List<ServiceNowTicketTypeDTO>> getTicketTypes(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(
        Arrays.stream(ServiceNowTicketTypeNG.values())
            .map(ticketType -> new ServiceNowTicketTypeDTO(ticketType.name(), ticketType.getDisplayName()))
            .collect(Collectors.toList()));
  }

  @GET
  @Path("stagingTable")
  @ApiOperation(value = "Get serviceNow staging tables", nickname = "getServiceNowStagingTables")
  public ResponseDTO<List<ServiceNowStagingTable>> getStagingTables(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(serviceNowConnectorRef, accountId, orgId, projectId);
    List<ServiceNowStagingTable> stagingTableList =
        serviceNowResourceService.getStagingTableList(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(stagingTableList);
  }

  @GET
  @Path("ticketTypesV2")
  @ApiOperation(value = "Get serviceNow ticket types V2", nickname = "getServiceNowTicketTypesV2")
  public ResponseDTO<List<ServiceNowTicketTypeDTO>> getTicketTypesV2(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(serviceNowConnectorRef, accountId, orgId, projectId);
    List<ServiceNowTicketTypeDTO> ticketTypesList =
        serviceNowResourceService.getTicketTypesV2(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(ticketTypesList);
  }

  @GET
  @Path("createMetadata")
  @ApiOperation(value = "Get ServiceNow issue create metadata", nickname = "getServiceNowIssueCreateMetadata")
  public ResponseDTO<List<ServiceNowFieldNG>> getIssueCreateMetadata(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("ticketType") String ticketType,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(serviceNowConnectorRef, accountId, orgId, projectId);
    List<ServiceNowFieldNG> issueCreateMetadataResponse =
        serviceNowResourceService.getIssueCreateMetadata(connectorRef, orgId, projectId, ticketType);
    return ResponseDTO.newResponse(issueCreateMetadataResponse);
  }

  @GET
  @Path("metadata")
  @ApiOperation(value = "Get ServiceNow metadata for ticketType", nickname = "getServiceNowIssueMetadata")
  public ResponseDTO<List<ServiceNowFieldNG>> getMetadata(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("ticketType") String ticketType,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(serviceNowConnectorRef, accountId, orgId, projectId);
    List<ServiceNowFieldNG> metadataResponse =
        serviceNowResourceService.getMetadata(connectorRef, orgId, projectId, ticketType);
    return ResponseDTO.newResponse(metadataResponse);
  }

  @GET
  @Path("getTemplate")
  @ApiOperation(value = "Get ServiceNow template metadata", nickname = "getServiceNowTemplateMetadata")
  public ResponseDTO<List<ServiceNowTemplate>> getTemplateMetadata(
      @NotNull @QueryParam("connectorRef") String serviceNowConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("ticketType") String ticketType,
      @QueryParam("templateName") String templateName, @QueryParam("limit") int limit, @QueryParam("offset") int offset,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(serviceNowConnectorRef, accountId, orgId, projectId);
    List<ServiceNowTemplate> metadataResponse = serviceNowResourceService.getTemplateList(
        connectorRef, orgId, projectId, limit, offset, templateName, ticketType);
    return ResponseDTO.newResponse(metadataResponse);
  }
}
