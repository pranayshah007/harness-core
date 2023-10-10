/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.mappers.NGGlobalTemplateDtoMapper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGGlobalTemplateService;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.TemplateVariableCreatorFactory;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class NGGlobalTemplateResourceImpl implements NGGlobalTemplateResource {
  public static final String TEMPLATE = "TEMPLATE";
  private final NGGlobalTemplateService ngGlobalTemplateService;
  private final NGTemplateService templateService;
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;
  @Override
  public ResponseDTO<List<TemplateWrapperResponseDTO>> createAndUpdate(@NotNull String accountId, String OrgIdentifier,
      String projectIdentifier, String connectorRef, @NotNull Map<String, Object> webhookEvent, String comments) {
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = ngGlobalTemplateService.createUpdateGlobalTemplate(
        accountId, connectorRef, webhookEvent, comments, OrgIdentifier, projectIdentifier);
    if (templateWrapperResponseDTOS.isEmpty()) {
      throw new InvalidRequestException("Unable to fetch the template from Git");
    }
    return ResponseDTO.newResponse(templateWrapperResponseDTOS);
  }

  @Override
  public ResponseDTO<String> getTemplateInputsYaml(@NotNull @AccountIdentifier String accountId,
      @ResourceIdentifier String globalTemplateIdentifier, @NotNull String templateLabel, String loadFromCache) {
    // if label not given, then consider stable template label
    // returns templateInputs yaml
    log.info(String.format("Get Template inputs for template with identifier %s ", globalTemplateIdentifier));
    Optional<GlobalTemplateEntity> optionalGlobalTemplate =
        ngGlobalTemplateService.getGlobalTemplateWithVersionLabel(globalTemplateIdentifier, templateLabel, false, false,
            NGTemplateDtoMapper.parseLoadFromCacheHeaderParam(loadFromCache), false);
    if (!optionalGlobalTemplate.isPresent()) {
      throw new InvalidRequestException(format("Template with identifier [%s] and versionLabel [%s] doesn't exist.",
          globalTemplateIdentifier, templateLabel));
    }
    return ResponseDTO.newResponse(
        templateMergeServiceHelper.createTemplateInputsFromTemplate(optionalGlobalTemplate.get().getYaml()));
  }

  @Override
  public ResponseDTO<Page<TemplateSummaryResponseDTO>> listGlobalTemplates(int page, int size, List<String> sort,
      String searchTerm, String filterIdentifier, @NotNull TemplateListType templateListType,
      TemplateFilterPropertiesDTO filterProperties) {
    Pageable pageRequest;
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<TemplateSummaryResponseDTO> templateSummaryResponseDTOS =
        ngGlobalTemplateService.getAllGlobalTemplate(true, false, pageRequest)
            .map(NGGlobalTemplateDtoMapper::prepareTemplateSummaryResponseDto);

    return ResponseDTO.newResponse(templateSummaryResponseDTOS);
  }

  @Override
  public ResponseDTO<TemplateWrapperResponseDTO> importTemplate(@NotNull String accountId, @OrgIdentifier String orgId,
      @ProjectIdentifier String projectId, @NotNull String templateYaml, boolean setDefaultTemplate, String comments) {
    templateYaml =
        ngGlobalTemplateService.importTemplateFromGlobalTemplateMarketPlace(accountId, orgId, projectId, templateYaml);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(accountId, orgId, projectId, templateYaml);
    log.info(String.format(
        "Importing Template from global template list with identifier %s with label %s in project %s, org %s, account %s",
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), projectId, orgId, accountId));

    GovernanceMetadata governanceMetadata = templateService.validateGovernanceRules(templateEntity);
    if (governanceMetadata.getDeny()) {
      TemplateWrapperResponseDTO templateWrapperResponseDTO =
          TemplateWrapperResponseDTO.builder().isValid(true).governanceMetadata(governanceMetadata).build();
      return ResponseDTO.newResponse(templateWrapperResponseDTO);
    }

    TemplateEntity createdTemplate = templateService.create(templateEntity, setDefaultTemplate, comments, true);
    TemplateWrapperResponseDTO templateWrapperResponseDTO =
        TemplateWrapperResponseDTO.builder()
            .isValid(true)
            .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate))
            .governanceMetadata(governanceMetadata)
            .build();
    return ResponseDTO.newResponse(createdTemplate.getVersion().toString(), templateWrapperResponseDTO);
  }
}