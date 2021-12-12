package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.clients.Resource;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.TemplateDeleteListRequestDTO;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.beans.TemplateWrapperResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateMergeHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final TemplateMergeHelper templateMergeHelper;

  public static final String TEMPLATE_PARAM_MESSAGE = "Template Identifier for the entity";

  @GET
  @Path("{templateIdentifier}")
  @ApiOperation(value = "Gets Template", nickname = "getTemplate")
  @Operation(operationId = "getTemplate", summary = "Gets Template",
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
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // if label is not given, return stable template
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(
        String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
            templateIdentifier, versionLabel, projectId, orgId, accountId));
    Optional<TemplateEntity> templateEntity =
        templateService.get(accountId, orgId, projectId, templateIdentifier, versionLabel, deleted);

    String version = "0";
    if (templateEntity.isPresent()) {
      version = templateEntity.get().getVersion().toString();
    }
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity.orElseThrow(
        ()
            -> new InvalidRequestException(String.format(
                "Template with the given Identifier: %s and %s does not exist or has been deleted", templateIdentifier,
                EmptyPredicate.isEmpty(versionLabel) ? "stable versionLabel" : "versionLabel: " + versionLabel))));
    return ResponseDTO.newResponse(version, templateResponseDTO);
  }

  @POST
  @ApiOperation(value = "Creates a Template", nickname = "createTemplate")
  @Operation(operationId = "createTemplate", summary = "Creates a Template",
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
      @Parameter(description = "Template YAML") @NotNull String templateYaml,
      @Parameter(description = "Specify true if Default Template is to be set") @QueryParam(
          "setDefaultTemplate") @DefaultValue("false") boolean setDefaultTemplate,
      @Parameter(description = "Comments") @QueryParam("comments") String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountId, orgId, projectId, templateYaml);
    log.info(String.format("Creating Template with identifier %s with label %s in project %s, org %s, account %s",
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), projectId, orgId, accountId));

    // TODO(archit): Add schema validations
    TemplateEntity createdTemplate = templateService.create(templateEntity, setDefaultTemplate, comments);
    TemplateWrapperResponseDTO templateWrapperResponseDTO =
        TemplateWrapperResponseDTO.builder()
            .isValid(true)
            .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate))
            .build();
    return ResponseDTO.newResponse(createdTemplate.getVersion().toString(), templateWrapperResponseDTO);
  }

  @PUT
  @Path("/updateStableTemplate/{templateIdentifier}/{versionLabel}")
  @ApiOperation(value = "Updating stable template label", nickname = "updateStableTemplate")
  @Operation(operationId = "updateStableTemplate", summary = "Updating stable Template Label",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Template Label")
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
  @ApiOperation(value = "Updating existing template label", nickname = "updateExistingTemplateLabel")
  @Operation(operationId = "updateExistingTemplateLabel", summary = "Updating the existing Template Label",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Template Label")
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
      @Parameter(description = "Template YAML") @NotNull String templateYaml,
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

    // TODO(archit): Add schema validations
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
  @ApiOperation(value = "Deletes template versionLabel", nickname = "deleteTemplateVersionLabel")
  @Operation(operationId = "deleteTemplateVersionLabel", summary = "Deletes Template VersionLabel",
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
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo, @QueryParam("comments") String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(String.format("Deleting Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, projectId, orgId, accountId));

    return ResponseDTO.newResponse(templateService.delete(accountId, orgId, projectId, templateIdentifier, versionLabel,
        isNumeric(ifMatch) ? parseLong(ifMatch) : null, comments));
  }

  @DELETE
  @Path("/{templateIdentifier}")
  @ApiOperation(value = "Deletes multiple template versionLabels of a particular template identifier",
      nickname = "deleteTemplateVersionsOfIdentifier")
  @Operation(operationId = "deleteTemplateVersionsOfIdentifier",
      summary = "Deletes multiple Template VersionLabels of a Template Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns true if the Template VersionLabels of a Template Identifier are deleted")
      })
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
      @Parameter(description = "Comments") @QueryParam("comments") String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(
        String.format("Deleting Template with identifier %s and versionLabel list %s in project %s, org %s, account %s",
            templateIdentifier, templateDeleteListRequestDTO.toString(), projectId, orgId, accountId));

    return ResponseDTO.newResponse(templateService.deleteTemplates(accountId, orgId, projectId, templateIdentifier,
        new HashSet<>(templateDeleteListRequestDTO.getTemplateVersionLabels()), comments));
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets all template list", nickname = "getTemplateList")
  @Operation(operationId = "getTemplateList", summary = "Gets all Template list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the Templates")
      })
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
    Criteria criteria = templateServiceHelper.formCriteria(
        accountId, orgId, projectId, filterIdentifier, filterProperties, false, searchTerm, false);

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
      @Parameter(description = "Template Label") @NotNull @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String templateLabel) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    // if label not given, then consider stable template label
    // returns templateInputs yaml
    log.info(String.format("Get Template inputs for template with identifier %s in project %s, org %s, account %s",
        templateIdentifier, projectId, orgId, accountId));
    return ResponseDTO.newResponse(
        templateMergeHelper.getTemplateInputs(accountId, orgId, projectId, templateIdentifier, templateLabel));
  }

  @POST
  @Path("/applyTemplates")
  @ApiOperation(value = "Gets complete yaml with templateRefs resolved", nickname = "getYamlWithTemplateRefsResolved")
  @Hidden
  public ResponseDTO<TemplateMergeResponseDTO> applyTemplates(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull TemplateApplyRequestDTO templateApplyRequestDTO) {
    TemplateMergeResponseDTO templateMergeResponseDTO = templateMergeHelper.mergeTemplateSpecToPipelineYaml(
        accountId, orgId, projectId, templateApplyRequestDTO.getOriginalEntityYaml());
    if (templateApplyRequestDTO.isCheckForAccess() && templateMergeResponseDTO != null
        && EmptyPredicate.isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      Set<String> templateIdentifiers = templateMergeResponseDTO.getTemplateReferenceSummaries()
                                            .stream()
                                            .map(TemplateReferenceSummary::getTemplateIdentifier)
                                            .collect(Collectors.toSet());
      templateIdentifiers.forEach(templateIdentifier
          -> accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
              Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_ACCESS_PERMISSION));
    }
    return ResponseDTO.newResponse(templateMergeResponseDTO);
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
}
