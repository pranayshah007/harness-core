/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.refresh.RefreshRequestDTO;
import io.harness.template.beans.refresh.RefreshRequestType;
import io.harness.template.beans.refresh.TemplateRefreshRequestDTO;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.refresh.YamlDiffResponseDTO;
import io.harness.template.services.TemplateRefreshService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("refresh-template")
@Path("refresh-template")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Templates", description = "This contains a list of APIs specific to the Templates Validations and Refresh")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@NextGenManagerAuth
@Slf4j
public class NGTemplateRefreshResource {
  private static final String TEMPLATE = "TEMPLATE";
  private final TemplateRefreshService templateRefreshService;
  private final AccessControlClient accessControlClient;

  @POST
  @ApiOperation(value = "This refreshes and update template inputs in template/pipeline",
      nickname = "refreshAndUpdateTemplateInputs")
  @Hidden
  public ResponseDTO<Boolean>
  refreshAndUpdateTemplate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull RefreshRequestDTO refreshRequestDTO) {
    if (RefreshRequestType.TEMPLATE.equals(refreshRequestDTO.getType())) {
      TemplateRefreshRequestDTO templateRefreshRequest = (TemplateRefreshRequestDTO) refreshRequestDTO;
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of(TEMPLATE, templateRefreshRequest.getTemplateIdentifier()),
          PermissionTypes.TEMPLATE_EDIT_PERMISSION);
      return ResponseDTO.newResponse(templateRefreshService.refreshAndUpdateTemplate(accountId, orgId, projectId,
          templateRefreshRequest.getTemplateIdentifier(), templateRefreshRequest.getVersionLabel()));
    }

    // TODO: refresh pipeline
    return ResponseDTO.newResponse(true);
  }

  @POST
  @Path("validate-template-inputs")
  @ApiOperation(
      value = "This validates whether yaml of template/pipeline is valid or not", nickname = "validateTemplateInputs")
  @Hidden
  public ResponseDTO<ValidateTemplateInputsResponseDTO>
  validateTemplateInputs(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull RefreshRequestDTO refreshRequestDTO) {
    if (RefreshRequestType.TEMPLATE.equals(refreshRequestDTO.getType())) {
      TemplateRefreshRequestDTO templateRefreshRequestDTO = (TemplateRefreshRequestDTO) refreshRequestDTO;
      ResponseDTO.newResponse(templateRefreshService.validateTemplateInputsInTemplate(accountId, orgId, projectId,
          templateRefreshRequestDTO.getTemplateIdentifier(), templateRefreshRequestDTO.getVersionLabel()));
    }
    return null;
  }

  @POST
  @Path("show-diff")
  @ApiOperation(value = "This returns original yaml and refresh yaml of template/pipeline", nickname = "getYamlDiff")
  @Hidden
  public ResponseDTO<YamlDiffResponseDTO> getYamlDiff(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull RefreshRequestDTO refreshRequestDTO) {
    return null;
  }

  @POST
  @Path("refresh-all")
  @ApiOperation(
      value = "This does recursive refresh and update template inputs in template/pipeline", nickname = "refreshAll")
  @Hidden
  public ResponseDTO<Boolean>
  refreshAll(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull RefreshRequestDTO refreshRequestDTO) {
    return null;
  }
}
