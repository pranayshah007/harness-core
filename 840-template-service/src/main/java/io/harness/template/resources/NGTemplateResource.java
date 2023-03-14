/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.NGCommonEntityConstants.FORCE_DELETE_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.TemplateMoveConfigRequestDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitx.USER_FLOW;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateRetainVariablesRequestDTO;
import io.harness.ng.core.template.TemplateRetainVariablesResponse;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.pms.mappers.VariablesResponseDtoMapper;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.FilterParamsDTO;
import io.harness.template.beans.NGTemplateConstants;
import io.harness.template.beans.PageParamsDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.TemplateDeleteListRequestDTO;
import io.harness.template.beans.TemplateFilterProperties;
import io.harness.template.beans.TemplateImportRequestDTO;
import io.harness.template.beans.TemplateImportSaveResponse;
import io.harness.template.beans.TemplateListRepoResponse;
import io.harness.template.beans.TemplateMoveConfigResponse;
import io.harness.template.beans.TemplateWrapperResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.CustomDeploymentVariablesUtils;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.helpers.TemplateYamlConversionHelper;
import io.harness.template.helpers.YamlVariablesUtils;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateMergeService;
import io.harness.template.services.TemplateVariableCreatorFactory;
import io.harness.template.services.TemplateVariableCreatorService;
import io.harness.template.utils.TemplateUtils;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.http.Body;

@OwnedBy(CDC)
@Api("templates")
@Path("templates")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Templates", description = "This contains a list of APIs specific to the Templates")
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
public class NGTemplateResource {
  public static final String TEMPLATE = "TEMPLATE";
  private static final String INCLUDE_ALL_TEMPLATES_ACCESSIBLE = "includeAllTemplatesAvailableAtScope";
  private final NGTemplateService templateService;
  private final NGTemplateServiceHelper templateServiceHelper;
  private final AccessControlClient accessControlClient;
  private final TemplateMergeService templateMergeService;
  private final VariablesServiceBlockingStub variablesServiceBlockingStub;
  private final TemplateYamlConversionHelper templateYamlConversionHelper;
  private final TemplateReferenceHelper templateReferenceHelper;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;

  public static final String TEMPLATE_PARAM_MESSAGE = "Template Identifier for the entity";

  @GET
  @Path("{templateIdentifier}")
  @ApiOperation(value = "Gets Template", nickname = "getTemplate")
  @Operation(operationId = "getTemplate", summary = "Get Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the saved Template")
      })
  public ResponseDTO<TemplateResponseDTO>
  get(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "Specifies whether Template is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted,
      @Parameter(description = "This contains details of Git Entity like Git Branch info")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch) {
    // if label is not given, return stable template
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(
        String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
            templateIdentifier, versionLabel, projectId, orgId, accountId));
    Optional<TemplateEntity> templateEntity =
        templateService.get(accountId, orgId, projectId, templateIdentifier, versionLabel, deleted,
            NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache), loadFromFallbackBranch);

    String version = "0";
    if (templateEntity.isPresent()) {
      version = templateEntity.get().getVersion().toString();
    }
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity.orElseThrow(
        ()
            -> new NotFoundException(String.format(
                "Template with the given Identifier: %s and %s does not exist or has been deleted", templateIdentifier,
                EmptyPredicate.isEmpty(versionLabel) ? "stable versionLabel" : "versionLabel: " + versionLabel))));
    return ResponseDTO.newResponse(version, templateResponseDTO);
  }

  @POST
  @ApiOperation(value = "Creates a Template", nickname = "createTemplate")
  @Operation(operationId = "createTemplate", summary = "Create a Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Template")
      })
  public ResponseDTO<TemplateWrapperResponseDTO>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "This contains details of Git Entity like Git Branch, Git Repository to be created")
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true, description = "Template YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Create Template YAML",
                         value = NGTemplateConstants.API_SAMPLE_TEMPLATE_YAML, description = "Sample Template YAML"))
          }) @NotNull String templateYaml,
      @Parameter(description = "Specify true if Default Template is to be set") @QueryParam(
          "setDefaultTemplate") @DefaultValue("false") boolean setDefaultTemplate,
      @Parameter(description = "Comments") @QueryParam("comments") String comments,
      @Parameter(
          description =
              "When isNewTemplate flag is set user will not be able to create a new version for an existing template")
      @QueryParam("isNewTemplate") @DefaultValue("false") @ApiParam(hidden = true) boolean isNewTemplate) {
    /*
      isNewTemplate flag is used to restrict users from creating new versions for an existing template from UI
      As we dont want to allow creation of new versions from create template flow
      Default value is false as we use same api for creation for different versions of template
      Jira - CDS-47301
     */

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountId, orgId, projectId, templateYaml);
    log.info(String.format("Creating Template with identifier %s with label %s in project %s, org %s, account %s",
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), projectId, orgId, accountId));
    if (gitEntityCreateInfo != null && StoreType.REMOTE.equals(gitEntityCreateInfo.getStoreType())) {
      comments = templateServiceHelper.getComment(
          "created", templateEntity.getIdentifier(), gitEntityCreateInfo.getCommitMsg());
    }

    TemplateEntity createdTemplate =
        templateService.create(templateEntity, setDefaultTemplate, comments, isNewTemplate);
    TemplateWrapperResponseDTO templateWrapperResponseDTO =
        TemplateWrapperResponseDTO.builder()
            .isValid(true)
            .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate))
            .build();
    return ResponseDTO.newResponse(createdTemplate.getVersion().toString(), templateWrapperResponseDTO);
  }

  @PUT
  @Path("/updateStableTemplate/{templateIdentifier}/{versionLabel}")
  @ApiOperation(value = "Updating stable template version", nickname = "updateStableTemplate")
  @Operation(operationId = "updateStableTemplate", summary = "Update Stable Template Version",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Template Version")
      })
  public ResponseDTO<String>
  updateStableTemplate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @PathParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "This contains details of Git Entity like Git Branch info to be updated")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "Comments") @QueryParam("comments") String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    log.info(String.format(
        "Updating Stable Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, projectId, orgId, accountId));

    TemplateEntity templateEntity = templateService.updateStableTemplateVersion(
        accountId, orgId, projectId, templateIdentifier, versionLabel, comments);
    return ResponseDTO.newResponse(templateEntity.getVersion().toString(), templateEntity.getVersionLabel());
  }

  @PUT
  @Path("/update/{templateIdentifier}/{versionLabel}")
  @ApiOperation(value = "Updating existing template version", nickname = "updateExistingTemplateVersion")
  @Operation(operationId = "updateExistingTemplateVersion", summary = "Update Template Version",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Template Version")
      })
  public ResponseDTO<TemplateWrapperResponseDTO>
  updateExistingTemplateLabel(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @PathParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be updated")
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true, description = "Template YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Update", summary = "Sample Update Template YAML",
                         value = NGTemplateConstants.API_SAMPLE_TEMPLATE_YAML, description = "Sample Template YAML"))
          }) @NotNull String templateYaml,
      @Parameter(description = "Specify true if Default Template is to be set") @QueryParam(
          "setDefaultTemplate") @DefaultValue("false") boolean setDefaultTemplate,
      @Parameter(description = "Comments") @QueryParam("comments") String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(
        accountId, orgId, projectId, templateIdentifier, versionLabel, templateYaml);
    log.info(
        String.format("Updating Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
            templateEntity.getIdentifier(), templateEntity.getVersionLabel(), projectId, orgId, accountId));
    templateEntity = templateEntity.withVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);

    if (gitEntityInfo != null && StoreType.REMOTE.equals(gitEntityInfo.getStoreType())) {
      comments =
          templateServiceHelper.getComment("updated", templateEntity.getIdentifier(), gitEntityInfo.getCommitMsg());
    }
    TemplateEntity createdTemplate =
        templateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, setDefaultTemplate, comments);
    TemplateWrapperResponseDTO templateWrapperResponseDTO =
        TemplateWrapperResponseDTO.builder()
            .isValid(true)
            .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate))
            .build();
    return ResponseDTO.newResponse(createdTemplate.getVersion().toString(), templateWrapperResponseDTO);
  }

  @DELETE
  @Path("/{templateIdentifier}/{versionLabel}")
  @ApiOperation(value = "Deletes template version", nickname = "deleteTemplateVersion")
  @Operation(operationId = "deleteTemplateVersion", summary = "Delete Template Version",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the Template is deleted")
      })
  public ResponseDTO<Boolean>
  deleteTemplate(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @NotNull @PathParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be deleted")
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo, @QueryParam("comments") String comments,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(String.format("Deleting Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateService.delete(accountId, orgId, projectId, templateIdentifier, versionLabel,
        isNumeric(ifMatch) ? parseLong(ifMatch) : null, comments, forceDelete));
  }

  @DELETE
  @Path("/{templateIdentifier}")
  @ApiOperation(value = "Delete Template Versions", nickname = "deleteTemplateVersionsOfIdentifier")
  @Operation(operationId = "deleteTemplateVersionsOfIdentifier", summary = "Delete Template Versions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns true if the Template VersionLabels of a Template Identifier are deleted")
      })
  @Hidden
  public ResponseDTO<Boolean>
  deleteTemplateVersionsOfParticularIdentifier(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "List of Template Version Labels to be deleted")
      @Body TemplateDeleteListRequestDTO templateDeleteListRequestDTO,
      @Parameter(description = "This contains details of Git Entity like Git Branch information to be deleted")
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo,
      @Parameter(description = "Comments") @QueryParam("comments") String comments,
      @Parameter(description = FORCE_DELETE_MESSAGE) @QueryParam(NGCommonEntityConstants.FORCE_DELETE) @DefaultValue(
          "false") boolean forceDelete) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(
        String.format("Deleting Template with identifier %s and versionLabel list %s in project %s, org %s, account %s",
            templateIdentifier, templateDeleteListRequestDTO.toString(), projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateService.deleteTemplates(accountId, orgId, projectId, templateIdentifier,
        new HashSet<>(templateDeleteListRequestDTO.getTemplateVersionLabels()), comments, forceDelete));
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets all template list", nickname = "getTemplateList")
  @Operation(operationId = "getTemplateList", summary = "Get Templates",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the Templates")
      })
  @Hidden
  // will return non deleted templates only
  public ResponseDTO<Page<TemplateSummaryResponseDTO>>
  listTemplates(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("25") int size,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Filter Identifier") @QueryParam("filterIdentifier") String filterIdentifier,
      @Parameter(description = "Template List Type") @NotNull @QueryParam(
          "templateListType") TemplateListType templateListType,
      @Parameter(description = "Specify true if all accessible Templates are to be included") @QueryParam(
          INCLUDE_ALL_TEMPLATES_ACCESSIBLE) Boolean includeAllTemplatesAccessibleAtScope,
      @Parameter(description = "This contains details of Git Entity like Git Branch info")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "This contains details of Template filters based on Template Types and Template Names ")
      @Body TemplateFilterPropertiesDTO filterProperties,
      @QueryParam("getDistinctFromBranches") Boolean getDistinctFromBranches) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(String.format("Get List of templates in project: %s, org: %s, account: %s", projectId, orgId, accountId));
    Criteria criteria = templateServiceHelper.formCriteria(accountId, orgId, projectId, filterIdentifier,
        filterProperties, false, searchTerm, includeAllTemplatesAccessibleAtScope);

    // Adding criteria needed for ui homepage
    criteria = templateServiceHelper.formCriteria(criteria, templateListType);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    Page<TemplateSummaryResponseDTO> templateSummaryResponseDTOS =
        templateService.list(criteria, pageRequest, accountId, orgId, projectId, getDistinctFromBranches)
            .map(NGTemplateDtoMapper::prepareTemplateSummaryResponseDto);
    return ResponseDTO.newResponse(templateSummaryResponseDTOS);
  }

  @POST
  @Path("/list-metadata")
  @Hidden
  @ApiOperation(value = "Gets all template list", nickname = "getTemplateMetadataList")
  @Operation(operationId = "getTemplateMetadataList", summary = "Gets all metadata of template list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the metadata of all Templates")
      })

  public ResponseDTO<Page<TemplateMetadataSummaryResponseDTO>>
  listTemplateMetadata(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("25") int size,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Filter Identifier") @QueryParam("filterIdentifier") String filterIdentifier,
      @Parameter(description = "Template List Type") @NotNull @QueryParam(
          "templateListType") TemplateListType templateListType,
      @Parameter(description = "Specify true if all accessible Templates are to be included") @QueryParam(
          INCLUDE_ALL_TEMPLATES_ACCESSIBLE) boolean includeAllTemplatesAccessibleAtScope,
      @Parameter(description = "This contains details of Template filters based on Template Types and Template Names ")
      @Body TemplateFilterPropertiesDTO filterProperties,
      @QueryParam("getDistinctFromBranches") boolean getDistinctFromBranches) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(String.format("Get List of templates in project: %s, org: %s, account: %s", projectIdentifier,
        orgIdentifier, accountIdentifier));

    TemplateFilterProperties templateFilterProperties =
        NGTemplateDtoMapper.toTemplateFilterProperties(filterProperties);
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO(searchTerm, filterIdentifier,
        templateListType, templateFilterProperties, includeAllTemplatesAccessibleAtScope, getDistinctFromBranches);
    PageParamsDTO pageParamsDTO = NGTemplateDtoMapper.preparePageParamsDTO(page, size, sort);
    Page<TemplateMetadataSummaryResponseDTO> templateMetadataSummaryResponseDTOS =
        templateService
            .listTemplateMetadata(accountIdentifier, orgIdentifier, projectIdentifier, filterParamsDTO, pageParamsDTO)
            .map(NGTemplateDtoMapper::prepareTemplateMetaDataSummaryResponseDto);
    return ResponseDTO.newResponse(templateMetadataSummaryResponseDTOS);
  }

  @PUT
  @Path("/updateTemplateSettings/{templateIdentifier}")
  @ApiOperation(value = "Updating template settings, template scope and template stable version",
      nickname = "updateTemplateSettings")
  @Operation(operationId = "updateTemplateSettings",
      summary = "Updating Template Settings, Template Scope and Template Stable Version",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns true if Template Settings, Template Scope and Template Stable Version are updated")
      })
  @Hidden
  public ResponseDTO<Boolean>
  updateTemplateSettings(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Update Stable Template Version") @QueryParam(
          "updateStableTemplateVersion") String updateStableTemplateVersion,
      @Parameter(description = "Current Scope") @QueryParam("currentScope") Scope currentScope,
      @Parameter(description = "Update Scope") @QueryParam("updateScope") Scope updateScope,
      @Parameter(description = "This contains details of Git Entity like Git Branch info")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("getDistinctFromBranches") Boolean getDistinctFromBranches) {
    if (updateScope != currentScope) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, Scope.ACCOUNT.equals(currentScope) ? null : orgId,
              Scope.PROJECT.equals(currentScope) ? projectId : null),
          Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountId, Scope.ACCOUNT.equals(updateScope) ? null : orgId,
              Scope.PROJECT.equals(updateScope) ? projectId : null),
          Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    }
    log.info(
        String.format("Updating Template Settings with identifier %s in project %s, org %s, account %s to scope %s",
            templateIdentifier, projectId, orgId, accountId, updateScope));

    return ResponseDTO.newResponse(templateService.updateTemplateSettings(accountId, orgId, projectId,
        templateIdentifier, currentScope, updateScope, updateStableTemplateVersion, getDistinctFromBranches));
  }

  @GET
  @Path("/templateInputs/{templateIdentifier}")
  @ApiOperation(value = "Gets template input set yaml", nickname = "getTemplateInputSetYaml")
  @Operation(operationId = "getTemplateInputSetYaml", summary = "Gets Template Input Set YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Template Input Set YAML")
      })
  public ResponseDTO<String>
  getTemplateInputsYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Template Label") @NotNull @QueryParam(NGCommonEntityConstants.VERSION_LABEL_KEY)
      String templateLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(
          description = "This contains details of Git Entity") @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    // if label not given, then consider stable template label
    // returns templateInputs yaml
    log.info(String.format("Get Template inputs for template with identifier %s in project %s, org %s, account %s",
        templateIdentifier, projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateMergeService.getTemplateInputs(accountId, orgId, projectId,
        templateIdentifier, templateLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  @GET
  @Path("/entitySetupUsage/{templateIdentifier}")
  @ApiOperation(value = "Get Entities referring this template", nickname = "listTemplateUsage")
  @Hidden
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> listTemplateEntityUsage(
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") int size,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      @Parameter(description = "Is Stable Template") @QueryParam(NGCommonEntityConstants.IS_STABLE_TEMPLATE)
      boolean isStableTemplate, @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    return ResponseDTO.newResponse(templateService.listTemplateReferences(page, size, accountIdentifier, orgIdentifier,
        projectIdentifier, templateIdentifier, versionLabel, searchTerm, isStableTemplate));
  }

  @GET
  @Path("/templateWithInputs/{templateIdentifier}")
  @ApiOperation(value = "Get Template along with Input Set YAML", nickname = "getTemplateAlongWithInputSetYaml")
  @Operation(operationId = "getTemplateAlongWithInputSetYaml", summary = "Gets Template along with Input Set YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Template along with Input Set YAML")
      })
  @Hidden
  public ResponseDTO<TemplateWithInputsResponseDTO>
  getTemplateAlongWithInputsYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Template Label") @NotNull @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String templateLabel,
      @Parameter(
          description = "This contains details of Git Entity") @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    // if label not given, then consider stable template label
    // returns template along with templateInputs yaml
    log.info(String.format(
        "Gets Template along with Template inputs for template with identifier %s in project %s, org %s, account %s",
        templateIdentifier, projectId, orgId, accountId));
    TemplateWithInputsResponseDTO templateWithInputs = templateService.getTemplateWithInputs(accountId, orgId,
        projectId, templateIdentifier, templateLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    String version = "0";
    if (templateWithInputs != null && templateWithInputs.getTemplateResponseDTO() != null
        && templateWithInputs.getTemplateResponseDTO().getVersion() != null) {
      version = String.valueOf(templateWithInputs.getTemplateResponseDTO().getVersion());
    }
    return ResponseDTO.newResponse(version, templateWithInputs);
  }

  @POST
  @Path("/mergeTemplateInputs/")
  @ApiOperation(value = "Get merged Template input YAML", nickname = "getsMergedTemplateInputYaml")
  @Operation(operationId = "getsMergedTemplateInputYaml", summary = "Gets merged Template input YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Merged YAML")
      })
  @Hidden
  public ResponseDTO<TemplateRetainVariablesResponse>
  getMergedTemplateInputsYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull TemplateRetainVariablesRequestDTO templateRetainVariablesRequestDTO) {
    log.info("Gets Merged Template Input yaml");
    return ResponseDTO.newResponse(
        templateMergeService.mergeTemplateInputs(templateRetainVariablesRequestDTO.getNewTemplateInputs(),
            templateRetainVariablesRequestDTO.getOldTemplateInputs()));
  }

  @POST
  @Path("/applyTemplates")
  @ApiOperation(value = "Gets complete yaml with templateRefs resolved", nickname = "getYamlWithTemplateRefsResolved")
  @Operation(operationId = "getYamlWithTemplateRefsResolved", summary = "Gets complete yaml with templateRefs resolved",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets complete yaml with templateRefs resolved")
      })
  @Hidden
  public ResponseDTO<TemplateMergeResponseDTO>
  applyTemplates(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull TemplateApplyRequestDTO templateApplyRequestDTO,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @QueryParam("AppendInputSetValidator") @DefaultValue("false") boolean appendInputSetValidator) {
    log.info("Applying templates to pipeline yaml in project {}, org {}, account {}", projectId, orgId, accountId);
    if (templateApplyRequestDTO.isGetOnlyFileContent()) {
      TemplateUtils.setUserFlowContext(USER_FLOW.EXECUTION);
    }
    long start = System.currentTimeMillis();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        templateMergeService.applyTemplatesToYaml(accountId, orgId, projectId,
            templateApplyRequestDTO.getOriginalEntityYaml(), templateApplyRequestDTO.isGetMergedYamlWithTemplateField(),
            NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache), appendInputSetValidator);
    checkLinkedTemplateAccess(accountId, orgId, projectId, templateApplyRequestDTO, templateMergeResponseDTO);
    log.info("[TemplateService] applyTemplates took {}ms ", System.currentTimeMillis() - start);
    return ResponseDTO.newResponse(templateMergeResponseDTO);
  }

  @POST
  @Path("v2/applyTemplates")
  @ApiOperation(value = "Gets complete yaml with templateRefs resolved", nickname = "getYamlWithTemplateRefsResolvedV2")
  @Operation(operationId = "getYamlWithTemplateRefsResolvedV2",
      summary = "Gets complete yaml with templateRefs resolved",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets complete yaml with templateRefs resolved")
      })
  @Hidden
  public ResponseDTO<TemplateMergeResponseDTO>
  applyTemplatesV2(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull TemplateApplyRequestDTO templateApplyRequestDTO,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @QueryParam("AppendInputSetValidator") @DefaultValue("false") boolean appendInputSetValidator) {
    log.info("Applying templates V2 to pipeline yaml in project {}, org {}, account {}", projectId, orgId, accountId);
    long start = System.currentTimeMillis();
    log.info("Principal in the applyTemplate resource layer is {}", SourcePrincipalContextBuilder.getSourcePrincipal());
    if (templateApplyRequestDTO.isGetOnlyFileContent()) {
      TemplateUtils.setUserFlowContext(USER_FLOW.EXECUTION);
    }
    TemplateMergeResponseDTO templateMergeResponseDTO =
        templateMergeService.applyTemplatesToYamlV2(accountId, orgId, projectId,
            templateApplyRequestDTO.getOriginalEntityYaml(), templateApplyRequestDTO.isGetMergedYamlWithTemplateField(),
            NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache), appendInputSetValidator);
    checkLinkedTemplateAccess(accountId, orgId, projectId, templateApplyRequestDTO, templateMergeResponseDTO);
    log.info("[TemplateService] applyTemplatesV2 took {}ms ", System.currentTimeMillis() - start);
    return ResponseDTO.newResponse(templateMergeResponseDTO);
  }

  private void checkLinkedTemplateAccess(String accountId, String orgId, String projectId,
      TemplateApplyRequestDTO templateApplyRequestDTO, TemplateMergeResponseDTO templateMergeResponseDTO) {
    if (templateApplyRequestDTO.isCheckForAccess()) {
      templateService.checkLinkedTemplateAccess(accountId, orgId, projectId, templateMergeResponseDTO);
    }
  }

  @GET
  @ApiOperation(value = "dummy api for checking template schema", nickname = "dummyApiForSwaggerSchemaCheck")
  @Path("/dummyApiForSwaggerSchemaCheck")
  @Hidden
  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<NGTemplateConfig> dummyApiForSwaggerSchemaCheck() {
    log.info("Get Template Config schema");
    return ResponseDTO.newResponse(NGTemplateConfig.builder().build());
  }

  @POST
  @Path("/variables")
  @Operation(operationId = "createVariables", summary = "Get all the Variables",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in template.")
      })
  @ApiOperation(value = "Create variables for Template", nickname = "createVariables")
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse>
  createVariables(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE,
                      required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @RequestBody(required = true, description = "Template YAML") @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for template.");
    String appliedTemplateYaml =
        templateMergeService.applyTemplatesToYaml(accountId, orgId, projectId, yaml, false, false, false)
            .getMergedPipelineYaml();
    TemplateEntity templateEntity =
        NGTemplateDtoMapper.toTemplateEntity(accountId, orgId, projectId, appliedTemplateYaml);
    String entityYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(templateEntity);
    if (templateEntity.getTemplateEntityType().getOwnerTeam().equals(PIPELINE)) {
      VariablesServiceRequest request = VariablesServiceRequest.newBuilder().setYaml(entityYaml).build();
      VariableMergeResponseProto variables = variablesServiceBlockingStub.getVariables(request);
      VariableMergeServiceResponse variableMergeServiceResponse = VariablesResponseDtoMapper.toDto(variables);
      return ResponseDTO.newResponse(variableMergeServiceResponse);
    } else if (templateEntity.getTemplateEntityType().equals(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE)) {
      CustomDeploymentYamlRequestDTO requestDTO =
          CustomDeploymentYamlRequestDTO.builder().entityYaml(entityYaml).build();
      CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
          NGRestUtils.getResponse(customDeploymentResourceClient.getExpressionVariables(requestDTO));
      return ResponseDTO.newResponse(
          CustomDeploymentVariablesUtils.getVariablesFromResponse(customDeploymentVariableResponseDTO));
    } else {
      return ResponseDTO.newResponse(
          YamlVariablesUtils.getVariablesFromYaml(entityYaml, templateEntity.getTemplateEntityType()));
    }
  }

  @POST
  @Path("/v2/variables")
  @Operation(operationId = "createVariablesV2",
      summary = "Get all the Variables which can be used as expression in the Template.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in template.")
      })
  @ApiOperation(value = "Create variables for Template", nickname = "createVariablesV2")
  @Hidden
  public ResponseDTO<VariableMergeServiceResponse>
  createVariablesV2(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE,
                        required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @RequestBody(required = true, description = "Template YAML") @NotNull @ApiParam(hidden = true) String yaml) {
    log.info("Creating variables for template.");
    String appliedTemplateYaml =
        templateMergeService.applyTemplatesToYaml(accountId, orgId, projectId, yaml, false, false, false)
            .getMergedPipelineYaml();
    TemplateEntity templateEntity =
        NGTemplateDtoMapper.toTemplateEntity(accountId, orgId, projectId, appliedTemplateYaml);
    String entityYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(templateEntity);
    TemplateVariableCreatorService ngTemplateVariableService =
        templateVariableCreatorFactory.getVariablesService(templateEntity.getTemplateEntityType());
    return ResponseDTO.newResponse(ngTemplateVariableService.getVariables(
        accountId, orgId, projectId, entityYaml, templateEntity.getTemplateEntityType()));
  }

  @GET
  @Path("validateUniqueIdentifier")
  @ApiOperation(value = "Validate Identifier is unique", nickname = "validateTheIdentifierIsUnique")
  @Operation(operationId = "validateTheIdentifierIsUnique",
      summary =
          "Validate template identifier is unique by Account Identifier, Organization Identifier, Project Identifier, Template Identifier and Version Label",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the Identifier is unique and false if the Identifier is not unique")
      })
  @Hidden
  public ResponseDTO<Boolean>
  validateTheIdentifierIsUnique(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                                @NotBlank @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String templateIdentifier,
      @Parameter(description = "Version Label") @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel) {
    return ResponseDTO.newResponse(templateService.validateIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel));
  }

  @POST
  @Path("templateReferences")
  @ApiOperation(value = "get all Template entity references", nickname = "getTemplateReferences")
  @Operation(operationId = "getTemplateReferences", summary = "get all Template entity references",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "get all Template entity references")
      })
  @Hidden
  public ResponseDTO<List<EntityDetailProtoDTO>>
  getTemplateReferences(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull TemplateReferenceRequestDTO templateReferenceRequestDTO) {
    return ResponseDTO.newResponse(templateReferenceHelper.getNestedTemplateReferences(
        accountId, orgId, projectId, templateReferenceRequestDTO.getYaml(), false));
  }

  @POST
  @Path("/import/{templateIdentifier}")
  @ApiOperation(value = "import template from git", nickname = "importTemplate")
  @Operation(operationId = "importTemplate", summary = "import template from git",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "import template from git")
      })
  @Hidden
  public ResponseDTO<TemplateImportSaveResponse>
  importTemplateFromGit(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @BeanParam GitImportInfoDTO gitImportInfoDTO, TemplateImportRequestDTO templateImportRequestDTO) {
    TemplateEntity importedTemplateFromRemote =
        templateService.importTemplateFromRemote(accountIdentifier, orgIdentifier, projectIdentifier,
            templateIdentifier, templateImportRequestDTO, gitImportInfoDTO.getIsForceImport());
    return ResponseDTO.newResponse(TemplateImportSaveResponse.builder()
                                       .templateIdentifier(importedTemplateFromRemote.getIdentifier())
                                       .templateVersion(importedTemplateFromRemote.getVersionLabel())
                                       .build());
  }

  @GET
  @Path("/list-repo")
  @Hidden
  @ApiOperation(value = "Gets all repo list", nickname = "getRepositoryList")
  @Operation(operationId = "getRepositoryList", summary = "Gets the list of all repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all Templates")
      })

  public ResponseDTO<TemplateListRepoResponse>
  listRepos(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify true if all accessible Templates are to be included") @QueryParam(
          INCLUDE_ALL_TEMPLATES_ACCESSIBLE) boolean includeAllTemplatesAccessibleAtScope) {
    TemplateListRepoResponse templateListRepoResponse = templateService.getListOfRepos(
        accountIdentifier, orgIdentifier, projectIdentifier, includeAllTemplatesAccessibleAtScope);
    return ResponseDTO.newResponse(templateListRepoResponse);
  }

  @POST
  @Path("/move-config/{templateIdentifier}")
  @ApiOperation(value = "Move Template YAML from inline to remote", nickname = "moveTemplateConfigs")
  @Operation(operationId = "moveTemplateConfigs", summary = "Move Template YAML from inline to remote",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Fetches Template YAML from Harness DB and creates a remote entity")
      })
  public ResponseDTO<TemplateMoveConfigResponse>
  moveConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @PathParam(
          "templateIdentifier") @ResourceIdentifier String templateIdentifier,
      @BeanParam TemplateMoveConfigRequestDTO templateMoveConfigRequestDTO) {
    TemplateMoveConfigResponse templateMoveConfigResponse = templateService.moveTemplateStoreTypeConfig(
        accountId, orgId, projectId, templateIdentifier, templateMoveConfigRequestDTO);
    return ResponseDTO.newResponse(templateMoveConfigResponse);
  }
}