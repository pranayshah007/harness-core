/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.utils.NGUtils.validate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGTemplateReference;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.FilterParamsDTO;
import io.harness.template.beans.PageParamsDTO;
import io.harness.template.beans.TemplateFilterProperties;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class NGTemplateDtoMapper {
  public TemplateResponseDTO writeTemplateResponseDto(TemplateEntity templateEntity) {
    return TemplateResponseDTO.builder()
        .accountId(templateEntity.getAccountId())
        .orgIdentifier(templateEntity.getOrgIdentifier())
        .projectIdentifier(templateEntity.getProjectIdentifier())
        .yaml(templateEntity.getYaml())
        .identifier(templateEntity.getIdentifier())
        .description(templateEntity.getDescription())
        .name(templateEntity.getName())
        .isStableTemplate(templateEntity.isStableTemplate())
        .childType(templateEntity.getChildType())
        .templateEntityType(templateEntity.getTemplateEntityType())
        .templateScope(templateEntity.getTemplateScope())
        .versionLabel(templateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(templateEntity.getTags()))
        .version(templateEntity.getVersion())
        .gitDetails(getEntityGitDetails(templateEntity))
        .lastUpdatedAt(templateEntity.getLastUpdatedAt())
        .entityValidityDetails(templateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(templateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(templateEntity.getStoreType())
        .connectorRef(templateEntity.getConnectorRef())
        .build();
  }

  public EntityGitDetails getEntityGitDetails(TemplateEntity templateEntity) {
    return templateEntity.getStoreType() == null ? EntityGitDetailsMapper.mapEntityGitDetails(templateEntity)
        : templateEntity.getStoreType() == StoreType.REMOTE
        ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
        : EntityGitDetails.builder().build();
  }

  public TemplateSummaryResponseDTO prepareTemplateSummaryResponseDto(TemplateEntity templateEntity) {
    return TemplateSummaryResponseDTO.builder()
        .accountId(templateEntity.getAccountId())
        .orgIdentifier(templateEntity.getOrgIdentifier())
        .projectIdentifier(templateEntity.getProjectIdentifier())
        .yaml(templateEntity.getYaml())
        .identifier(templateEntity.getIdentifier())
        .description(templateEntity.getDescription())
        .name(templateEntity.getName())
        .isStableTemplate(templateEntity.isStableTemplate())
        .childType(templateEntity.getChildType())
        .templateEntityType(templateEntity.getTemplateEntityType())
        .templateScope(templateEntity.getTemplateScope())
        .versionLabel(templateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(templateEntity.getTags()))
        .version(templateEntity.getVersion())
        .gitDetails(getEntityGitDetails(templateEntity))
        .lastUpdatedAt(templateEntity.getLastUpdatedAt())
        .entityValidityDetails(templateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(templateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .createdAt(templateEntity.getCreatedAt())
        .build();
  }

  public TemplateMetadataSummaryResponseDTO prepareTemplateMetaDataSummaryResponseDto(TemplateEntity templateEntity) {
    return TemplateMetadataSummaryResponseDTO.builder()
        .accountIdentifier(templateEntity.getAccountId())
        .orgIdentifier(templateEntity.getOrgIdentifier())
        .projectIdentifier(templateEntity.getProjectIdentifier())
        .identifier(templateEntity.getIdentifier())
        .description(templateEntity.getDescription())
        .name(templateEntity.getName())
        .isStableTemplate(templateEntity.isStableTemplate())
        .childType(templateEntity.getChildType())
        .templateEntityType(templateEntity.getTemplateEntityType())
        .templateScope(templateEntity.getTemplateScope())
        .versionLabel(templateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(templateEntity.getTags()))
        .version(templateEntity.getVersion())
        .gitDetails(getEntityGitDetails(templateEntity))
        .lastUpdatedAt(templateEntity.getLastUpdatedAt())
        .createdAt(templateEntity.getCreatedAt())
        .storeType(templateEntity.getStoreType())
        .connectorRef(templateEntity.getConnectorRef())
        .build();
  }

  public TemplateEntity toTemplateEntityResponse(
      String accountId, String orgId, String projectId, NGTemplateConfig templateConfig, String yaml) {
    validateTemplateYaml(templateConfig, orgId, projectId);
    NGTemplateReference templateReference =
        NGTemplateReference.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(templateConfig.getTemplateInfoConfig().getOrgIdentifier())
            .projectIdentifier(templateConfig.getTemplateInfoConfig().getProjectIdentifier())
            .identifier(templateConfig.getTemplateInfoConfig().getIdentifier())
            .versionLabel(templateConfig.getTemplateInfoConfig().getVersionLabel())
            .build();
    String description = (String) templateConfig.getTemplateInfoConfig().getDescription().fetchFinalValue();
    description = description == null ? "" : description;
    return TemplateEntity.builder()
        .yaml(yaml)
        .identifier(templateConfig.getTemplateInfoConfig().getIdentifier())
        .versionLabel(templateConfig.getTemplateInfoConfig().getVersionLabel())
        .accountId(accountId)
        .orgIdentifier(templateConfig.getTemplateInfoConfig().getOrgIdentifier())
        .projectIdentifier(templateConfig.getTemplateInfoConfig().getProjectIdentifier())
        .name(templateConfig.getTemplateInfoConfig().getName())
        .description(description)
        .tags(TagMapper.convertToList(templateConfig.getTemplateInfoConfig().getTags()))
        .templateEntityType(templateConfig.getTemplateInfoConfig().getType())
        .templateScope(getScopeFromTemplateDto(templateConfig.getTemplateInfoConfig()))
        .fullyQualifiedIdentifier(templateReference.getFullyQualifiedName())
        .childType(templateConfig.getTemplateInfoConfig().getSpec() != null
                ? JsonNodeUtils.getString(templateConfig.getTemplateInfoConfig().getSpec(), "type")
                : null)
        .build();
  }

  public FilterParamsDTO prepareFilterParamsDTO(String searchTerm, String filterIdentifier,
      TemplateListType templateListType, TemplateFilterProperties templateFilterProperties,
      boolean includeAllTemplatesAccessibleAtScope, boolean getDistinctFromBranches) {
    return FilterParamsDTO.builder()
        .searchTerm(searchTerm)
        .filterIdentifier(filterIdentifier)
        .templateListType(templateListType)
        .templateFilterProperties(templateFilterProperties)
        .includeAllTemplatesAccessibleAtScope(includeAllTemplatesAccessibleAtScope)
        .getDistinctFromBranches(getDistinctFromBranches)
        .build();
  }

  public PageParamsDTO preparePageParamsDTO(int page, int size, List<String> sort) {
    return PageParamsDTO.builder().page(page).size(size).sort(sort).build();
  }

  public TemplateFilterProperties toTemplateFilterProperties(TemplateFilterPropertiesDTO filterProperties) {
    if (filterProperties == null) {
      return TemplateFilterProperties.builder().build();
    } else {
      return TemplateFilterProperties.builder()
          .templateNames(filterProperties.getTemplateNames())
          .templateIdentifiers(filterProperties.getTemplateIdentifiers())
          .templateEntityTypes(filterProperties.getTemplateEntityTypes())
          .childTypes(filterProperties.getChildTypes())
          .description(filterProperties.getDescription())
          .build();
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, NGTemplateConfig templateConfig) {
    try {
      // Set this value so that description parameterField is not serialised as null during yaml creation from template
      // config
      if (templateConfig.getTemplateInfoConfig().getDescription().fetchFinalValue() == null) {
        templateConfig.getTemplateInfoConfig().setDescription(ParameterField.createValueField(""));
      }
      return toTemplateEntityResponse(accountId, templateConfig.getTemplateInfoConfig().getOrgIdentifier(),
          templateConfig.getTemplateInfoConfig().getProjectIdentifier(), templateConfig,
          YamlPipelineUtils.getYamlString(templateConfig));
    } catch (Exception e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      return toTemplateEntityResponse(accountId, templateConfig.getTemplateInfoConfig().getOrgIdentifier(),
          templateConfig.getTemplateInfoConfig().getProjectIdentifier(), templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String orgId, String projectId, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      return toTemplateEntityResponse(accountId, orgId, projectId, templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String orgId, String projectId, String templateIdentifier,
      String versionLabel, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      validateTemplateYaml(templateConfig, orgId, projectId, templateIdentifier, versionLabel);
      return toTemplateEntityResponse(accountId, orgId, projectId, templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public NGTemplateConfig toDTO(String yaml) {
    try {
      return YamlPipelineUtils.read(yaml, NGTemplateConfig.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create template yaml: " + ex.getMessage(), ex);
    }
  }

  public NGTemplateConfig toDTO(TemplateEntity templateEntity) {
    try {
      return YamlPipelineUtils.read(templateEntity.getYaml(), NGTemplateConfig.class);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create template yaml: " + ex.getMessage(), ex);
    }
  }

  private void validateTemplateYaml(NGTemplateConfig templateConfig, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel) {
    if (templateConfig.getTemplateInfoConfig().getVersionLabel() == null) {
      throw new InvalidRequestException("Template VersionLabel is Not Present");
    }
    if (!templateConfig.getTemplateInfoConfig().getIdentifier().equals(templateIdentifier)) {
      throw new InvalidRequestException("Template Identifier for template is not matching as in template yaml.");
    }
    if (!templateConfig.getTemplateInfoConfig().getVersionLabel().equals(versionLabel)) {
      throw new InvalidRequestException("VersionLabel for template is not matching as in template yaml.");
    }
    validateTemplateYaml(templateConfig, orgIdentifier, projectIdentifier);
  }

  private void validateTemplateYaml(NGTemplateConfig templateConfig, String orgIdentifier, String projectIdentifier) {
    validate(templateConfig.getTemplateInfoConfig());
    if (EmptyPredicate.isNotEmpty(templateConfig.getTemplateInfoConfig().getProjectIdentifier())
        && !templateConfig.getTemplateInfoConfig().getProjectIdentifier().equals(projectIdentifier)) {
      throw new InvalidRequestException("ProjectIdentifier for template is not matching as in template yaml.");
    }
    if (EmptyPredicate.isNotEmpty(templateConfig.getTemplateInfoConfig().getOrgIdentifier())
        && !templateConfig.getTemplateInfoConfig().getOrgIdentifier().equals(orgIdentifier)) {
      throw new InvalidRequestException("OrgIdentifier for template is not matching as in template yaml.");
    }

    if (EmptyPredicate.isEmpty(templateConfig.getTemplateInfoConfig().getVersionLabel())) {
      throw new InvalidRequestException("Template version label cannot be empty.");
    }
    if (NGExpressionUtils.matchesInputSetPattern(templateConfig.getTemplateInfoConfig().getIdentifier())) {
      throw new InvalidRequestException("Template identifier cannot be runtime input");
    }
  }

  public Scope getScopeFromTemplateDto(NGTemplateInfoConfig templateInfoConfig) {
    if (EmptyPredicate.isNotEmpty(templateInfoConfig.getProjectIdentifier())) {
      return Scope.PROJECT;
    }
    if (EmptyPredicate.isNotEmpty(templateInfoConfig.getOrgIdentifier())) {
      return Scope.ORG;
    }
    return Scope.ACCOUNT;
  }
}
