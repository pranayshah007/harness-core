package io.harness.template.resources;
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.Long.parseLong;
import static org.apache.commons.lang3.StringUtils.isNumeric;

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
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.CustomDeploymentVariablesUtils;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.helpers.TemplateYamlConversionHelper;
import io.harness.template.helpers.YamlVariablesUtils;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.resources.beans.TemplateDeleteListRequestDTO;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateImportRequestDTO;
import io.harness.template.resources.beans.TemplateImportSaveResponse;
import io.harness.template.resources.beans.TemplateListRepoResponse;
import io.harness.template.resources.beans.TemplateMoveConfigResponse;
import io.harness.template.resources.beans.TemplateUpdateGitMetadataRequest;
import io.harness.template.resources.beans.TemplateUpdateGitMetadataResponse;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.resources.beans.UpdateGitDetailsParams;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateMergeService;
import io.harness.template.services.TemplateVariableCreatorFactory;
import io.harness.template.services.TemplateVariableCreatorService;
import io.harness.template.utils.TemplateUtils;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import io.swagger.annotations.ApiParam;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGTemplateResourceImpl implements NGTemplateResource {
  public static final String TEMPLATE = "TEMPLATE";
  private final NGTemplateService templateService;
  private final NGTemplateServiceHelper templateServiceHelper;
  private final AccessControlClient accessControlClient;
  private final TemplateMergeService templateMergeService;
  private final VariablesServiceBlockingStub variablesServiceBlockingStub;
  private final TemplateYamlConversionHelper templateYamlConversionHelper;
  private final TemplateReferenceHelper templateReferenceHelper;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;

  @Override
  public ResponseDTO<TemplateResponseDTO> get(@NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, String templateIdentifier, String versionLabel, boolean deleted,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, boolean loadFromFallbackBranch) {
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

  @Override
  public ResponseDTO<TemplateWrapperResponseDTO> create(@NotNull String accountId, @OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, GitEntityCreateInfoDTO gitEntityCreateInfo, @NotNull String templateYaml,
      boolean setDefaultTemplate, String comments, boolean isNewTemplate) {
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

  @Override
  public ResponseDTO<String> updateStableTemplate(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, String templateIdentifier, String versionLabel,
      GitEntityFindInfoDTO gitEntityBasicInfo, String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    log.info(String.format(
        "Updating Stable Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, projectId, orgId, accountId));

    TemplateEntity templateEntity = templateService.updateStableTemplateVersion(
        accountId, orgId, projectId, templateIdentifier, versionLabel, comments);
    return ResponseDTO.newResponse(templateEntity.getVersion().toString(), templateEntity.getVersionLabel());
  }

  @Override
  public ResponseDTO<TemplateWrapperResponseDTO> updateExistingTemplateLabel(String ifMatch,
      @NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      @ResourceIdentifier String templateIdentifier, String versionLabel, GitEntityUpdateInfoDTO gitEntityInfo,
      @NotNull String templateYaml, boolean setDefaultTemplate, String comments) {
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

  @Override
  public ResponseDTO<Boolean> deleteTemplate(String ifMatch, @NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @ResourceIdentifier String templateIdentifier,
      @NotNull String versionLabel, GitEntityDeleteInfoDTO entityDeleteInfo, String comments, boolean forceDelete) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(String.format("Deleting Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateService.delete(accountId, orgId, projectId, templateIdentifier, versionLabel,
        isNumeric(ifMatch) ? parseLong(ifMatch) : null, comments, forceDelete));
  }

  @Override
  public ResponseDTO<Boolean> deleteTemplateVersionsOfParticularIdentifier(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @ResourceIdentifier String templateIdentifier,
      TemplateDeleteListRequestDTO templateDeleteListRequestDTO, GitEntityDeleteInfoDTO entityDeleteInfo,
      String comments, boolean forceDelete) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(
        String.format("Deleting Template with identifier %s and versionLabel list %s in project %s, org %s, account %s",
            templateIdentifier, templateDeleteListRequestDTO.toString(), projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateService.deleteTemplates(accountId, orgId, projectId, templateIdentifier,
        new HashSet<>(templateDeleteListRequestDTO.getTemplateVersionLabels()), comments, forceDelete));
  }

  @Override
  public ResponseDTO<Page<TemplateSummaryResponseDTO>> listTemplates(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, int page, int size, List<String> sort,
      String searchTerm, String filterIdentifier, @NotNull TemplateListType templateListType,
      Boolean includeAllTemplatesAccessibleAtScope, GitEntityFindInfoDTO gitEntityBasicInfo,
      TemplateFilterPropertiesDTO filterProperties, Boolean getDistinctFromBranches) {
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

  @Override
  public ResponseDTO<Page<TemplateMetadataSummaryResponseDTO>> listTemplateMetadata(
      @NotNull @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, int page, int size, List<String> sort, String searchTerm,
      String filterIdentifier, @NotNull TemplateListType templateListType, boolean includeAllTemplatesAccessibleAtScope,
      TemplateFilterPropertiesDTO filterProperties, boolean getDistinctFromBranches) {
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

  @Override
  public ResponseDTO<Boolean> updateTemplateSettings(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @ResourceIdentifier String templateIdentifier,
      String updateStableTemplateVersion, Scope currentScope, Scope updateScope,
      GitEntityFindInfoDTO gitEntityBasicInfo, Boolean getDistinctFromBranches) {
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

  @Override
  public ResponseDTO<String> getTemplateInputsYaml(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @ResourceIdentifier String templateIdentifier,
      @NotNull String templateLabel, String loadFromCache, GitEntityFindInfoDTO gitEntityBasicInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    // if label not given, then consider stable template label
    // returns templateInputs yaml
    log.info(String.format("Get Template inputs for template with identifier %s in project %s, org %s, account %s",
        templateIdentifier, projectId, orgId, accountId));
    return ResponseDTO.newResponse(templateMergeService.getTemplateInputs(accountId, orgId, projectId,
        templateIdentifier, templateLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  @Override
  public ResponseDTO<PageResponse<EntitySetupUsageDTO>> listTemplateEntityUsage(int page, int size,
      @NotNull String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @ResourceIdentifier String templateIdentifier, String versionLabel,
      boolean isStableTemplate, String searchTerm) {
    return ResponseDTO.newResponse(templateService.listTemplateReferences(page, size, accountIdentifier, orgIdentifier,
        projectIdentifier, templateIdentifier, versionLabel, searchTerm, isStableTemplate));
  }

  @Override
  public ResponseDTO<TemplateWithInputsResponseDTO> getTemplateAlongWithInputsYaml(
      @NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      @ResourceIdentifier String templateIdentifier, @NotNull String templateLabel,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache) {
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

  @Override
  public ResponseDTO<TemplateRetainVariablesResponse> getMergedTemplateInputsYaml(
      @NotNull @AccountIdentifier String accountId,
      @NotNull TemplateRetainVariablesRequestDTO templateRetainVariablesRequestDTO) {
    log.info("Gets Merged Template Input yaml");
    return ResponseDTO.newResponse(
        templateMergeService.mergeTemplateInputs(templateRetainVariablesRequestDTO.getNewTemplateInputs(),
            templateRetainVariablesRequestDTO.getOldTemplateInputs()));
  }

  @Override
  public ResponseDTO<TemplateMergeResponseDTO> applyTemplates(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull TemplateApplyRequestDTO templateApplyRequestDTO, String loadFromCache, boolean appendInputSetValidator) {
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

  @Override
  public ResponseDTO<TemplateMergeResponseDTO> applyTemplatesV2(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull TemplateApplyRequestDTO templateApplyRequestDTO, String loadFromCache, boolean appendInputSetValidator) {
    log.info("Applying templates V2 to pipeline yaml in project {}, org {}, account {}", projectId, orgId, accountId);
    long start = System.currentTimeMillis();
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

  @Override
  public ResponseDTO<NGTemplateConfig> dummyApiForSwaggerSchemaCheck() {
    log.info("Get Template Config schema");
    return ResponseDTO.newResponse(NGTemplateConfig.builder().build());
  }

  @Override
  public ResponseDTO<VariableMergeServiceResponse> createVariables(
      @NotNull String accountId, String orgId, String projectId, @NotNull @ApiParam(hidden = true) String yaml) {
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

  @Override
  public ResponseDTO<VariableMergeServiceResponse> createVariablesV2(
      @NotNull String accountId, String orgId, String projectId, @NotNull @ApiParam(hidden = true) String yaml) {
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

  @Override
  public ResponseDTO<Boolean> validateTheIdentifierIsUnique(@NotBlank String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String templateIdentifier, String versionLabel) {
    return ResponseDTO.newResponse(templateService.validateIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel));
  }

  @Override
  public ResponseDTO<List<EntityDetailProtoDTO>> getTemplateReferences(@NotNull String accountId, String orgId,
      String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull TemplateReferenceRequestDTO templateReferenceRequestDTO) {
    return ResponseDTO.newResponse(templateReferenceHelper.getNestedTemplateReferences(
        accountId, orgId, projectId, templateReferenceRequestDTO.getYaml(), false));
  }

  @Override
  public ResponseDTO<TemplateImportSaveResponse> importTemplateFromGit(@NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier, @ResourceIdentifier String templateIdentifier,
      GitImportInfoDTO gitImportInfoDTO, TemplateImportRequestDTO templateImportRequestDTO) {
    TemplateEntity importedTemplateFromRemote =
        templateService.importTemplateFromRemote(accountIdentifier, orgIdentifier, projectIdentifier,
            templateIdentifier, templateImportRequestDTO, gitImportInfoDTO.getIsForceImport());
    return ResponseDTO.newResponse(TemplateImportSaveResponse.builder()
                                       .templateIdentifier(importedTemplateFromRemote.getIdentifier())
                                       .templateVersion(importedTemplateFromRemote.getVersionLabel())
                                       .build());
  }

  @Override
  public ResponseDTO<TemplateListRepoResponse> listRepos(@NotNull @AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      boolean includeAllTemplatesAccessibleAtScope) {
    TemplateListRepoResponse templateListRepoResponse = templateService.getListOfRepos(
        accountIdentifier, orgIdentifier, projectIdentifier, includeAllTemplatesAccessibleAtScope);
    return ResponseDTO.newResponse(templateListRepoResponse);
  }

  @Override
  public ResponseDTO<TemplateMoveConfigResponse> moveConfig(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @ResourceIdentifier String templateIdentifier,
      TemplateMoveConfigRequestDTO templateMoveConfigRequestDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateMoveConfigResponse templateMoveConfigResponse = templateService.moveTemplateStoreTypeConfig(
        accountId, orgId, projectId, templateIdentifier, templateMoveConfigRequestDTO);
    return ResponseDTO.newResponse(templateMoveConfigResponse);
  }

  @Override
  public ResponseDTO<TemplateUpdateGitMetadataResponse> updateGitMetadataDetails(
      @NotNull @AccountIdentifier String accountIdentifier, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @ResourceIdentifier String templateIdentifier, String versionLabel,
      TemplateUpdateGitMetadataRequest request) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    templateService.updateGitDetails(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier,
        versionLabel,
        UpdateGitDetailsParams.builder().filePath(request.getFilepath()).repoName(request.getRepoName()).build());
    return ResponseDTO.newResponse(TemplateUpdateGitMetadataResponse.builder().status(true).build());
  }
}
