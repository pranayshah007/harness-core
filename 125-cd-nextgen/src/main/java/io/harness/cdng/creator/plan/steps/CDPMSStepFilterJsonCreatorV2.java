/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileReference;
import io.harness.cdng.creator.CDCreatorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.filestore.service.FileStoreService;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.filters.WithFileRefs;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class CDPMSStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Inject private FileStoreService fileStoreService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return CDCreatorUtils.getSupportedStepsV2();
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    FilterCreationResponse response = super.handleNode(filterCreationContext, yamlField);
    List<EntityDetailProtoDTO> result = response.getReferredEntities();

    // add file reference for step if step implements with file ref interface
    if (WithFileRefs.class.isAssignableFrom(yamlField.getStepSpecType().getClass())) {
      Map<String, ParameterField<List<String>>> fileRefs =
          ((WithFileRefs) yamlField.getStepSpecType()).extractFileRefs();
      result.addAll(getAllFileReferredEntities(filterCreationContext, fileRefs));
    }

    response.setReferredEntities(result);
    return response;
  }

  private List<EntityDetailProtoDTO> getAllFileReferredEntities(
      FilterCreationContext filterCreationContext, Map<String, ParameterField<List<String>>> fileRefs) {
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
    List<EntityDetailProtoDTO> result = new ArrayList<>();

    for (Map.Entry<String, ParameterField<List<String>>> ref : fileRefs.entrySet()) {
      ParameterField<List<String>> refValue = ref.getValue();
      List<String> files =
          (isNull(refValue) || isNull(refValue.getValue())) ? Collections.emptyList() : refValue.getValue();
      files.stream().filter(EmptyPredicate::isNotEmpty).forEach(scopedFilePath -> {
        if (!EngineExpressionEvaluator.hasExpressions(scopedFilePath)) {
          try {
            FileReference fileReference =
                FileReference.of(scopedFilePath, accountIdentifier, orgIdentifier, projectIdentifier);
            Optional<FileDTO> fileDTO;
            fileDTO = fileStoreService.getByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
                fileReference.getProjectIdentifier(), fileReference.getPath());
            if (fileDTO.isPresent()) {
              String fullQualifiedDomainName =
                  YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
                  + ref.getKey() + PATH_CONNECTOR + YAMLFieldNameConstants.FILES;
              String fileScopedIdentifier =
                  FileReference.getScopedFileIdentifier(fileReference.getScope(), fileDTO.get().getIdentifier());

              result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier,
                  projectIdentifier, fullQualifiedDomainName, ParameterField.createValueField(fileScopedIdentifier),
                  EntityTypeProtoEnum.FILES));
            }
          } catch (Exception e) {
            log.error("Not able to add file reference", e);
          }
        }
      });
    }
    return result;
  }
}
