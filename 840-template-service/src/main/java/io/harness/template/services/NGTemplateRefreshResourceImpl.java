/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.NGTemplateRefreshResource;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGTemplateRefreshResourceImpl implements NGTemplateRefreshResource {
  private static final String TEMPLATE_PARAM_MESSAGE = "Template Identifier for the entity";
  private static final String TEMPLATE = "TEMPLATE";
  private final TemplateRefreshService templateRefreshService;
  private final AccessControlClient accessControlClient;

  public ResponseDTO<Boolean>
  refreshAndUpdateTemplate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @QueryParam(
          "templateIdentifier") @NotNull String templateIdentifier,
      @Parameter(description = "Template version") @NotNull @QueryParam(
          NGCommonEntityConstants.VERSION_LABEL_KEY) String versionLabel,
      String templateLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @BeanParam GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    templateRefreshService.refreshAndUpdateTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel,
        NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    return ResponseDTO.newResponse(true);
  }

  public ResponseDTO<RefreshResponseDTO> getRefreshedYaml(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "YAML") @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(
        RefreshResponseDTO.builder()
            .refreshedYaml(templateRefreshService.refreshLinkedTemplateInputs(accountId, orgId, projectId,
                refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)))
            .build());
  }

  public ResponseDTO<ValidateTemplateInputsResponseDTO> validateTemplateInputsForTemplate(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @QueryParam(
          "templateIdentifier") @NotNull String templateIdentifier,
      @Parameter(description = "Template version") @NotNull @QueryParam(NGCommonEntityConstants.VERSION_LABEL_KEY)
      String versionLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(templateRefreshService.validateTemplateInputsInTemplate(accountId, orgId, projectId,
        templateIdentifier, versionLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  public ResponseDTO<ValidateTemplateInputsResponseDTO>
  validateTemplateInputsForYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "YAML") @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(templateRefreshService.validateTemplateInputsForYaml(accountId, orgId, projectId,
        refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  public ResponseDTO<YamlDiffResponseDTO> getYamlDiff(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @QueryParam(
          "templateIdentifier") @NotNull String templateIdentifier,
      @Parameter(description = "Template version") @NotNull @QueryParam(NGCommonEntityConstants.VERSION_LABEL_KEY)
      String versionLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(templateRefreshService.getYamlDiffOnRefreshingTemplate(accountId, orgId, projectId,
        templateIdentifier, versionLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  public ResponseDTO<Boolean> refreshAllTemplates(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = TEMPLATE_PARAM_MESSAGE) @QueryParam(
          "templateIdentifier") @NotNull String templateIdentifier,
      @Parameter(description = "Template version") @NotNull @QueryParam(NGCommonEntityConstants.VERSION_LABEL_KEY)
      String versionLabel, @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @BeanParam GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO) {
    templateRefreshService.recursivelyRefreshTemplates(accountId, orgId, projectId, templateIdentifier, versionLabel,
        NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    return ResponseDTO.newResponse(true);
  }

  public ResponseDTO<YamlFullRefreshResponseDTO>
  refreshAllTemplatesForYaml(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull
                             @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @Parameter(description = "YAML") @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(templateRefreshService.recursivelyRefreshTemplatesForYaml(accountId, orgId,
        projectId, refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }
}
