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
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.mappers.NGGlobalTemplateDtoMapper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.services.NGGlobalTemplateService;
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
}