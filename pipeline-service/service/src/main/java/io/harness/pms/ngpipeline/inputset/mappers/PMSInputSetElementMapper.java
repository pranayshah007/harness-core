/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.merger.helpers.InputSetYamlHelper.getPipelineComponent;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetReference;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.ngpipeline.inputset.api.InputSetRequestInfoDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;

import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PMSInputSetElementMapper {
  public InputSetEntity toInputSetEntity(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String yaml) {
    String identifier = InputSetYamlHelper.getStringField(yaml, "identifier", "inputSet");
    if (EmptyPredicate.isEmpty(identifier) || NGExpressionUtils.isRuntimeOrExpressionField(identifier)) {
      throw new InvalidRequestException("Input Set Identifier cannot be empty or a runtime input");
    }
    String name = InputSetYamlHelper.getStringField(yaml, "name", "inputSet");
    if (EmptyPredicate.isEmpty(name) || NGExpressionUtils.isRuntimeOrExpressionField(name)) {
      throw new InvalidRequestException("Input Set Name cannot be empty or a runtime input");
    }
    return InputSetEntity.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .pipelineIdentifier(pipelineIdentifier)
        .identifier(identifier)
        .name(name)
        .description(InputSetYamlHelper.getStringField(yaml, "description", "inputSet"))
        .tags(TagMapper.convertToList(InputSetYamlHelper.getTags(yaml, "inputSet")))
        .inputSetEntityType(InputSetEntityType.INPUT_SET)
        .yaml(yaml)
        .build();
  }

  public InputSetEntity toInputSetEntity(InputSetRequestInfoDTO requestInfoDTO, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String yaml) {
    String identifier = requestInfoDTO.getIdentifier();
    if (EmptyPredicate.isEmpty(identifier) || NGExpressionUtils.isRuntimeOrExpressionField(identifier)) {
      throw new InvalidRequestException("Input Set Identifier cannot be empty or a runtime input");
    }
    String name = requestInfoDTO.getName();
    if (NGExpressionUtils.isRuntimeOrExpressionField(name)) {
      throw new InvalidRequestException("Input Set Name cannot a runtime input");
    }
    String topKey = InputSetYamlHelper.getRootNodeOfInputSetYaml(yaml);
    String yamlIdentifier = InputSetYamlHelper.getStringField(yaml, "identifier", topKey);
    if (isNotEmpty(yamlIdentifier) && !yamlIdentifier.equals(identifier)) {
      throw new InvalidRequestException(
          String.format("Expected Input Set identifier in YAML to be [%s], but was [%s]", identifier, yamlIdentifier));
    }
    String yamlName = InputSetYamlHelper.getStringField(yaml, "name", topKey);
    if (isNotEmpty(yamlName) && !yamlName.equals(name)) {
      throw new InvalidRequestException(
          String.format("Expected Input Set name in YAML to be [%s], but was [%s]", name, yamlName));
    }
    String yamlDescription = InputSetYamlHelper.getStringField(yaml, "description", topKey);
    if (isNotEmpty(yamlDescription) && isNotEmpty(requestInfoDTO.getDescription())
        && !yamlDescription.equals(requestInfoDTO.getDescription())) {
      throw new InvalidRequestException(String.format("Expected Input Set description in YAML to be [%s], but was [%s]",
          requestInfoDTO.getDescription(), yamlDescription));
    }
    Map<String, String> yamlTags = InputSetYamlHelper.getTags(yaml, topKey);
    if (isNotEmpty(yamlTags) && isNotEmpty(requestInfoDTO.getTags()) && !yamlTags.equals(requestInfoDTO.getTags())) {
      throw new InvalidRequestException(String.format(
          "Expected Input Set tags in YAML to be [%s], but was [%s]", requestInfoDTO.getTags(), yamlTags));
    }
    return InputSetEntity.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .pipelineIdentifier(pipelineIdentifier)
        .identifier(identifier)
        .name(name)
        .description(requestInfoDTO.getDescription())
        .tags(TagMapper.convertToList(requestInfoDTO.getTags()))
        .inputSetEntityType(InputSetEntityType.INPUT_SET)
        .yaml(yaml)
        .build();
  }

  public InputSetEntity toInputSetEntity(String accountId, String yaml) {
    String topKey = InputSetYamlHelper.getRootNodeOfInputSetYaml(yaml);
    String orgIdentifier = InputSetYamlHelper.getStringField(yaml, "orgIdentifier", topKey);
    String projectIdentifier = InputSetYamlHelper.getStringField(yaml, "projectIdentifier", topKey);
    if (topKey.equals("inputSet")) {
      String pipelineComponent = getPipelineComponent(yaml);
      String pipelineIdentifier = InputSetYamlHelper.getStringField(pipelineComponent, "identifier", "pipeline");
      return toInputSetEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    } else {
      String pipelineIdentifier = InputSetYamlHelper.getStringField(yaml, "pipelineIdentifier", topKey);
      return toInputSetEntityForOverlay(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml);
    }
  }

  public InputSetEntity toInputSetEntityForOverlay(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String yaml) {
    return InputSetEntity.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .pipelineIdentifier(pipelineIdentifier)
        .identifier(InputSetYamlHelper.getStringField(yaml, "identifier", "overlayInputSet"))
        .name(InputSetYamlHelper.getStringField(yaml, "name", "overlayInputSet"))
        .description(InputSetYamlHelper.getStringField(yaml, "description", "overlayInputSet"))
        .tags(TagMapper.convertToList(InputSetYamlHelper.getTags(yaml, "overlayInputSet")))
        .inputSetEntityType(InputSetEntityType.OVERLAY_INPUT_SET)
        .inputSetReferences(InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(yaml))
        .yaml(yaml)
        .build();
  }

  public EntityGitDetails getEntityGitDetails(InputSetEntity entity) {
    return entity.getStoreType() == null            ? EntityGitDetailsMapper.mapEntityGitDetails(entity)
        : entity.getStoreType() == StoreType.REMOTE ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
                                                    : null;
  }

  public InputSetResponseDTOPMS toInputSetResponseDTOPMS(InputSetEntity entity) {
    return InputSetResponseDTOPMS.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .identifier(entity.getIdentifier())
        .inputSetYaml(entity.getYaml())
        .name(entity.getName())
        .description(entity.getDescription())
        .tags(TagMapper.convertToMap(entity.getTags()))
        .version(entity.getVersion())
        .gitDetails(getEntityGitDetails(entity))
        .isOutdated(entity.getIsInvalid())
        .entityValidityDetails(entity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(entity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(entity.getStoreType())
        .connectorRef(entity.getConnectorRef())
        .build();
  }

  public InputSetResponseDTOPMS toInputSetResponseDTOPMSWithErrors(
      InputSetEntity entity, InputSetErrorWrapperDTOPMS errorWrapperDTO) {
    return InputSetResponseDTOPMS.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .identifier(entity.getIdentifier())
        .inputSetYaml(entity.getYaml())
        .name(entity.getName())
        .description(entity.getDescription())
        .tags(TagMapper.convertToMap(entity.getTags()))
        .version(entity.getVersion())
        .gitDetails(getEntityGitDetails(entity))
        .isOutdated(entity.getIsInvalid())
        .entityValidityDetails(EntityValidityDetails.builder().valid(false).invalidYaml(entity.getYaml()).build())
        .inputSetErrorWrapper(errorWrapperDTO)
        .isErrorResponse(true)
        .storeType(entity.getStoreType())
        .connectorRef(entity.getConnectorRef())
        .build();
  }

  public OverlayInputSetResponseDTOPMS toOverlayInputSetResponseDTOPMS(InputSetEntity entity) {
    return toOverlayInputSetResponseDTOPMS(entity, false, null);
  }

  public OverlayInputSetResponseDTOPMS toOverlayInputSetResponseDTOPMS(
      InputSetEntity entity, boolean isError, Map<String, String> invalidReferences) {
    return OverlayInputSetResponseDTOPMS.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .overlayInputSetYaml(entity.getYaml())
        .identifier(entity.getIdentifier())
        .name(entity.getName())
        .description(entity.getDescription())
        .tags(TagMapper.convertToMap(entity.getTags()))
        .inputSetReferences(entity.getInputSetReferences())
        .version(entity.getVersion())
        .isErrorResponse(isError)
        .invalidInputSetReferences(invalidReferences)
        .gitDetails(getEntityGitDetails(entity))
        .isOutdated(entity.getIsInvalid())
        .entityValidityDetails(entity.isEntityInvalid() || isNotEmpty(invalidReferences)
                ? EntityValidityDetails.builder().valid(false).invalidYaml(entity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(entity.getStoreType())
        .connectorRef(entity.getConnectorRef())
        .build();
  }

  public InputSetSummaryResponseDTOPMS toInputSetSummaryResponseDTOPMS(InputSetEntity entity,
      InputSetErrorWrapperDTOPMS inputSetErrorDetails, Map<String, String> overlaySetErrorDetails) {
    // For List View, getEntityGitDetails(...) method cant be used because for REMOTE input sets. That is because
    // GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata() cannot be used, because there won't be any
    // SCM Context set in the List call.
    EntityGitDetails entityGitDetails = entity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(entity)
        : entity.getStoreType() == StoreType.REMOTE ? GitAwareContextHelper.getEntityGitDetails(entity)
                                                    : null;
    return InputSetSummaryResponseDTOPMS.builder()
        .identifier(entity.getIdentifier())
        .name(entity.getName())
        .description(entity.getDescription())
        .pipelineIdentifier(entity.getPipelineIdentifier())
        .inputSetType(entity.getInputSetEntityType())
        .tags(TagMapper.convertToMap(entity.getTags()))
        .version(entity.getVersion())
        .gitDetails(entityGitDetails)
        .createdAt(entity.getCreatedAt())
        .lastUpdatedAt(entity.getLastUpdatedAt())
        .isOutdated(entity.getIsInvalid())
        .inputSetErrorDetails(inputSetErrorDetails)
        .overlaySetErrorDetails(overlaySetErrorDetails)
        .entityValidityDetails(entity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(entity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(entity.getStoreType())
        .connectorRef(entity.getConnectorRef())
        .build();
  }

  public InputSetSummaryResponseDTOPMS toInputSetSummaryResponseDTOPMS(InputSetEntity entity) {
    return toInputSetSummaryResponseDTOPMS(entity, null, null);
  }

  public EntityDetail toEntityDetail(InputSetEntity entity) {
    return EntityDetail.builder()
        .name(entity.getName())
        .type(EntityType.INPUT_SETS)
        .entityRef(InputSetReference.builder()
                       .accountIdentifier(entity.getAccountIdentifier())
                       .orgIdentifier(entity.getOrgIdentifier())
                       .projectIdentifier(entity.getProjectIdentifier())
                       .pipelineIdentifier(entity.getPipelineIdentifier())
                       .identifier(entity.getIdentifier())
                       .build())
        .build();
  }
}
