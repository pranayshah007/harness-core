/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.jira.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.jira.resources.service.JiraResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueTransitionNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;
import io.harness.jira.JiraUserData;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Api("jira")
@Path("/jira")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraResource {
  private final JiraResourceService jiraResourceService;

  @GET
  @Path("validate")
  @ApiOperation(value = "Validate jira credentials", nickname = "validateJiraCredentials")
  public ResponseDTO<Boolean> validateCredentials(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    boolean isValid = jiraResourceService.validateCredentials(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(isValid);
  }

  @GET
  @Path("projects")
  @ApiOperation(value = "Get jira projects", nickname = "getJiraProjects")
  public ResponseDTO<List<JiraProjectBasicNG>> getProjects(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    List<JiraProjectBasicNG> projects = jiraResourceService.getProjects(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(projects);
  }

  @GET
  @Path("statuses")
  @ApiOperation(value = "Get jira statuses", nickname = "getJiraStatuses")
  public ResponseDTO<List<JiraStatusNG>> getStatuses(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("projectKey") String projectKey,
      @QueryParam("issueType") String issueType, @QueryParam("issueKey") String issueKey,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    List<JiraStatusNG> statuses =
        jiraResourceService.getStatuses(connectorRef, orgId, projectId, projectKey, issueType, issueKey);
    return ResponseDTO.newResponse(statuses);
  }

  @GET
  @Path("createMetadata")
  @ApiOperation(value = "Get jira issue create metadata", nickname = "getJiraIssueCreateMetadata")
  public ResponseDTO<JiraIssueCreateMetadataNG> getIssueCreateMetadata(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("projectKey") String projectKey,
      @QueryParam("issueType") String issueType, @QueryParam("expand") String expand,
      @QueryParam("fetchStatus") boolean fetchStatus, @QueryParam("ignoreComment") boolean ignoreComment,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    JiraIssueCreateMetadataNG createMetadata = jiraResourceService.getIssueCreateMetadata(
        connectorRef, orgId, projectId, projectKey, issueType, expand, fetchStatus, ignoreComment);
    return ResponseDTO.newResponse(createMetadata);
  }

  @GET
  @Path("searchUser")
  @ApiOperation(value = "Get jira usernames for the jira ConnectorDisconnectHandler", nickname = "jiraUserSearch")
  public ResponseDTO<List<JiraUserData>> getUserSearch(
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountId,
      @QueryParam(NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorId,
      @QueryParam("userQuery") String userQuery, @QueryParam("offset") String offset) {
    return ResponseDTO.newResponse(jiraResourceService
                                       .searchUser(accountId, orgIdentifier, projectIdentifier, connectorId,
                                           DEFAULT_SYNC_CALL_TIMEOUT, userQuery, offset)
                                       .getJiraUserDataList());
  }

  @GET
  @Path("updateMetadata")
  @ApiOperation(value = "Get jira issue update metadata", nickname = "getJiraIssueUpdateMetadata")
  public ResponseDTO<JiraIssueUpdateMetadataNG> getIssueUpdateMetadata(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("issueKey") String issueKey,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    JiraIssueUpdateMetadataNG updateMetadata =
        jiraResourceService.getIssueUpdateMetadata(connectorRef, orgId, projectId, issueKey);
    return ResponseDTO.newResponse(updateMetadata);
  }

  @GET
  @Path("transitions")
  @ApiOperation(value = "Get issue transitions", nickname = "getIssueTransitions")
  public ResponseDTO<List<JiraIssueTransitionNG>> getIssueTransitions(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @QueryParam("issueKey") String issueKey, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    return ResponseDTO.newResponse(jiraResourceService.getTransitions(connectorRef, orgId, projectId, issueKey));
  }
}
