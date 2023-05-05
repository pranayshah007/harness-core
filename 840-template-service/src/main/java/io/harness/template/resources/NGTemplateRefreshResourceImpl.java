package io.harness.template.resources;

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CDC;

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
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.services.TemplateRefreshService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGTemplateRefreshResourceImpl implements NGTemplateRefreshResource {
  private static final String TEMPLATE = "TEMPLATE";
  private final TemplateRefreshService templateRefreshService;
  private final AccessControlClient accessControlClient;

  @Override
  public ResponseDTO<Boolean> refreshAndUpdateTemplate(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @NotNull String templateIdentifier,
      @NotNull String versionLabel, String templateLabel, String loadFromCache,
      GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    templateRefreshService.refreshAndUpdateTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel,
        NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    return ResponseDTO.newResponse(true);
  }

  @Override
  public ResponseDTO<RefreshResponseDTO> getRefreshedYaml(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, GitEntityFindInfoDTO gitEntityBasicInfo,
      String loadFromCache, @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(
        RefreshResponseDTO.builder()
            .refreshedYaml(templateRefreshService.refreshLinkedTemplateInputs(accountId, orgId, projectId,
                refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)))
            .build());
  }

  @Override
  public ResponseDTO<ValidateTemplateInputsResponseDTO> validateTemplateInputsForTemplate(
      @NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      @NotNull String templateIdentifier, @NotNull String versionLabel, String loadFromCache,
      GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(templateRefreshService.validateTemplateInputsInTemplate(accountId, orgId, projectId,
        templateIdentifier, versionLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  @Override
  public ResponseDTO<ValidateTemplateInputsResponseDTO> validateTemplateInputsForYaml(
      @NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache,
      @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(templateRefreshService.validateTemplateInputsForYaml(accountId, orgId, projectId,
        refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  @Override
  public ResponseDTO<YamlDiffResponseDTO> getYamlDiff(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @NotNull String templateIdentifier,
      @NotNull String versionLabel, String loadFromCache, GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(templateRefreshService.getYamlDiffOnRefreshingTemplate(accountId, orgId, projectId,
        templateIdentifier, versionLabel, NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }

  @Override
  public ResponseDTO<Boolean> refreshAllTemplates(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, @NotNull String templateIdentifier,
      @NotNull String versionLabel, String loadFromCache, GitEntityUpdateInfoDTO gitEntityUpdateInfoDTO) {
    templateRefreshService.recursivelyRefreshTemplates(accountId, orgId, projectId, templateIdentifier, versionLabel,
        NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    return ResponseDTO.newResponse(true);
  }

  @Override
  public ResponseDTO<YamlFullRefreshResponseDTO> refreshAllTemplatesForYaml(
      @NotNull @AccountIdentifier String accountId, @OrgIdentifier String orgId, @ProjectIdentifier String projectId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache,
      @NotNull @Body RefreshRequestDTO refreshRequestDTO) {
    return ResponseDTO.newResponse(templateRefreshService.recursivelyRefreshTemplatesForYaml(accountId, orgId,
        projectId, refreshRequestDTO.getYaml(), NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache)));
  }
}
