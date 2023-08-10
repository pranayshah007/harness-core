/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateVariableCreatorFactory;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGGlobalTemplateResourceImpl implements NGGlobalTemplateResource {
  public static final String TEMPLATE = "TEMPLATE";
  private final NGTemplateService templateService;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;
  private final NGTemplateServiceHelper templateServiceHelper;
  private final AccessControlClient accessControlClient;
  @Override
  public ResponseDTO<List<TemplateWrapperResponseDTO>> crud(@NotNull String accountId, @OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, String connectorRef, String targetBranch,
      GitEntityCreateInfoDTO gitEntityCreateInfo, @NotNull String webhookEvent, boolean setDefaultTemplate,
      String comments, boolean isNewTemplate) throws IOException {
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = templateService.createUpdateGlobalTemplate(
        accountId, orgId, projectId, setDefaultTemplate, comments, isNewTemplate, connectorRef, webhookEvent);
    return ResponseDTO.newResponse(templateWrapperResponseDTOS);
  }

  @Override
  public ResponseDTO<Page<TemplateSummaryResponseDTO>> listGlobalTemplates(@NotNull @AccountIdentifier String accountId,
      @OrgIdentifier String orgId, @ProjectIdentifier String projectId, int page, int size, List<String> sort,
      String searchTerm, String filterIdentifier, @NotNull TemplateListType templateListType,
      Boolean includeAllTemplatesAccessibleAtScope, GitEntityFindInfoDTO gitEntityBasicInfo,
      TemplateFilterPropertiesDTO filterProperties, Boolean getDistinctFromBranches) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(String.format("Get List of templates in project: %s, org: %s, account: %s", projectId, orgId, accountId));
    Criteria criteria = new Criteria();

    // Adding criteria needed for ui homepage
    criteria = templateServiceHelper.formCriteria(criteria, templateListType);
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<TemplateSummaryResponseDTO> templateSummaryResponseDTOS =
        templateService.getAllGlobalTemplate(criteria, accountId, pageRequest, false)
            .map(NGTemplateDtoMapper::prepareTemplateSummaryResponseDto);

    return ResponseDTO.newResponse(templateSummaryResponseDTOS);
  }

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
    Optional<GlobalTemplateEntity> templateEntity =
        templateService.getGlobalTemplateByIdentifier(templateIdentifier, versionLabel, deleted, accountId);

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
}
