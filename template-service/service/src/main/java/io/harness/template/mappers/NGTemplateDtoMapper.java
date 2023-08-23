/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.NGUtils.validate;

import static java.lang.Double.compare;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.NGTemplateReference;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.CacheResponseMetadataDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.resources.beans.yaml.NGTemplateInfoConfig;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class NGTemplateDtoMapper {
  public static final String BOOLEAN_TRUE_VALUE = "true";

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
        .icon(templateEntity.getIcon())
        .gitDetails(getEntityGitDetails(templateEntity))
        .lastUpdatedAt(templateEntity.getLastUpdatedAt())
        .entityValidityDetails(templateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(templateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(templateEntity.getStoreType())
        .connectorRef(templateEntity.getConnectorRef())
        .cacheResponseMetadata(getCacheResponse(templateEntity))
        .build();
  }

  public TemplateResponseDTO writeTemplateResponseDto(TemplateEntityGetResponse templateEntityGetResponse) {
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
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
        .gitDetails(templateEntityGetResponse.getEntityGitDetails())
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
  public EntityGitDetails getEntityGitDetailsForListTemplates(TemplateEntity templateEntity) {
    return templateEntity.getStoreType() == null            ? EntityGitDetailsMapper.mapEntityGitDetails(templateEntity)
        : templateEntity.getStoreType() == StoreType.REMOTE ? GitAwareContextHelper.getEntityGitDetails(templateEntity)
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
        .gitDetails(getEntityGitDetailsForListTemplates(templateEntity))
        .lastUpdatedAt(templateEntity.getLastUpdatedAt())
        .icon(templateEntity.getIcon())
        .entityValidityDetails(templateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(templateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .createdAt(templateEntity.getCreatedAt())
        .build();
  }

  public TemplateMetadataSummaryResponseDTO prepareTemplateMetaDataSummaryResponseDto(TemplateEntity templateEntity) {
    return TemplateMetadataSummaryResponseDTO.builder()
        .accountId(templateEntity.getAccountId())
        .orgIdentifier(templateEntity.getOrgIdentifier())
        .projectIdentifier(templateEntity.getProjectIdentifier())
        .identifier(templateEntity.getIdentifier())
        .description(templateEntity.getDescription())
        .name(templateEntity.getName())
        .stableTemplate(templateEntity.isStableTemplate())
        .childType(templateEntity.getChildType())
        .templateEntityType(templateEntity.getTemplateEntityType())
        .templateScope(templateEntity.getTemplateScope())
        .versionLabel(templateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(templateEntity.getTags()))
        .icon(templateEntity.getIcon())
        .version(templateEntity.getVersion())
        .gitDetails(getEntityGitDetailsForListTemplates(templateEntity))
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
        .icon(templateConfig.getTemplateInfoConfig().getIcon())
        .templateEntityType(templateConfig.getTemplateInfoConfig().getType())
        .templateScope(getScopeFromTemplateDto(templateConfig.getTemplateInfoConfig()))
        .fullyQualifiedIdentifier(templateReference.getFullyQualifiedName())
        .childType(templateConfig.getTemplateInfoConfig().fetchChildType())
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

  public CacheResponseMetadataDTO getCacheResponse(TemplateEntity templateEntity) {
    if (templateEntity.getStoreType() == StoreType.REMOTE) {
      return getCacheResponse();
    }
    return null;
  }

  public CacheResponseMetadataDTO getCacheResponse() {
    CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();
    if (cacheResponse != null) {
      return CacheResponseMetadataDTO.builder()
          .cacheState(cacheResponse.getCacheState())
          .ttlLeft(cacheResponse.getTtlLeft())
          .lastUpdatedAt(cacheResponse.getLastUpdatedAt())
          .build();
    }
    return null;
  }

  public PageParamsDTO preparePageParamsDTO(int page, int size, List<String> sort) {
    return PageParamsDTO.builder().page(page).size(size).sort(sort).build();
  }

  public TemplateFilterProperties toTemplateFilterProperties(TemplateFilterPropertiesDTO filterProperties) {
    if (filterProperties == null) {
      return null;
    } else {
      ModelMapper modelMapper = new ModelMapper();
      TemplateFilterProperties filterPropertiesWithTags =
          modelMapper.map(TemplateFilterProperties.builder()
                              .templateNames(filterProperties.getTemplateNames())
                              .templateIdentifiers(filterProperties.getTemplateIdentifiers())
                              .templateEntityTypes(filterProperties.getTemplateEntityTypes())
                              .childTypes(filterProperties.getChildTypes())
                              .description(filterProperties.getDescription())
                              .repoName(filterProperties.getRepoName())
                              .build(),
              TemplateFilterProperties.class);
      if (isNotEmpty(filterProperties.getTags())) {
        filterPropertiesWithTags.setTags(TagMapper.convertToList(filterProperties.getTags()));
      }
      return filterPropertiesWithTags;
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
          YamlUtils.writeYamlString(templateConfig));
    } catch (Exception e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = getTemplateConfigOrThrow(templateYaml);
      return toTemplateEntityResponse(accountId, templateConfig.getTemplateInfoConfig().getOrgIdentifier(),
          templateConfig.getTemplateInfoConfig().getProjectIdentifier(), templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String orgId, String projectId, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = getTemplateConfigOrThrow(templateYaml);
      return toTemplateEntityResponse(accountId, orgId, projectId, templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public TemplateEntity toTemplateEntity(String accountId, String orgId, String projectId, String templateIdentifier,
      String versionLabel, String templateYaml) {
    try {
      NGTemplateConfig templateConfig = getTemplateConfigOrThrow(templateYaml);
      validateTemplateYaml(templateConfig, orgId, projectId, templateIdentifier, versionLabel);
      return toTemplateEntityResponse(accountId, orgId, projectId, templateConfig, templateYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  private NGTemplateConfig getTemplateConfigOrThrow(String templateYaml) throws IOException {
    NGTemplateConfig config = YamlUtils.read(templateYaml, NGTemplateConfig.class);
    if (config.getTemplateInfoConfig() == null) {
      throw new InvalidRequestException(
          "The provided template yaml does not contain the \"template\" keyword at the root level");
    }
    return config;
  }

  public NGTemplateConfig toDTO(String yaml) {
    try {
      return getTemplateConfigOrThrow(yaml);
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot create template yaml: " + ex.getMessage(), ex);
    }
  }

  public NGTemplateConfig toDTO(TemplateEntity templateEntity) {
    try {
      return YamlUtils.read(templateEntity.getYaml(), NGTemplateConfig.class);
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

    if (isEmpty(templateConfig.getTemplateInfoConfig().getVersionLabel())) {
      throw new InvalidRequestException("Template version label cannot be empty.");
    }
    if (NGExpressionUtils.matchesInputSetPattern(templateConfig.getTemplateInfoConfig().getIdentifier())) {
      throw new InvalidRequestException("Template identifier cannot be runtime input");
    }
    validateIconForTemplate(templateConfig.getTemplateInfoConfig().getIcon());
  }
  @VisibleForTesting
  protected void validateIconForTemplate(String iconWithFormat) {
    if (iconWithFormat == null || iconWithFormat.length() == 0) {
      return;
    }
    String format;
    String icon;
    try {
      String[] strings = iconWithFormat.split(",");
      format = strings[0];
      icon = strings[1];
    } catch (Exception e) {
      throw new InvalidRequestException("Icon string is invalid");
    }
    int iconLength = icon.length();
    checkForTheCorrectFormat(format);
    checkForTheCorrectSize(icon, iconLength);
  }

  private void checkForTheCorrectSize(String icon, int iconLength) {
    int padding = getPadding(icon, iconLength);
    double fileSizeInBytes = Math.ceil((double) iconLength / 4) * 3;
    fileSizeInBytes = fileSizeInBytes - padding;
    double fileSizeInKB = fileSizeInBytes / 1024;
    if (compare(fileSizeInKB, 30.00) > 0) {
      throw new InvalidRequestException("Icon Size can not be more than 30KB");
    }
  }

  private void checkForTheCorrectFormat(String format) {
    switch (format) { // check image's extension
      case "data:image/jpeg;base64":
      case "data:image/png;base64":
      case "data:image/svg+xml;base64":
      case "data:image/jpg;base64":
        break;
      default: // Icon should be of above-mentioned format
        throw new InvalidRequestException("Invalid format for Icon Image");
    }
  }

  private int getPadding(String icon, int iconLength) {
    int pad = 0;
    if (iconLength >= 1 && icon.charAt(iconLength - 1) == '=') {
      pad++;
    }
    if (iconLength >= 2 && icon.charAt(iconLength - 2) == '=') {
      pad++;
    }
    return pad;
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

  public boolean parseLoadFromCacheHeaderParam(String loadFromCache) {
    if (isEmpty(loadFromCache)) {
      return false;
    } else {
      return BOOLEAN_TRUE_VALUE.equalsIgnoreCase(loadFromCache);
    }
  }

  public TemplateResponseDTO writeTemplateResponseDto(GlobalTemplateEntity globalTemplateEntity) {
    return TemplateResponseDTO.builder()
        .accountId(globalTemplateEntity.getAccountId())
        .orgIdentifier(globalTemplateEntity.getOrgIdentifier())
        .projectIdentifier(globalTemplateEntity.getProjectIdentifier())
        .yaml(globalTemplateEntity.getYaml())
        .identifier(globalTemplateEntity.getIdentifier())
        .description(globalTemplateEntity.getDescription())
        .name(globalTemplateEntity.getName())
        .isStableTemplate(globalTemplateEntity.isStableTemplate())
        .childType(globalTemplateEntity.getChildType())
        .templateEntityType(globalTemplateEntity.getTemplateEntityType())
        .templateScope(globalTemplateEntity.getTemplateScope())
        .versionLabel(globalTemplateEntity.getVersionLabel())
        .tags(TagMapper.convertToMap(globalTemplateEntity.getTags()))
        .version(globalTemplateEntity.getVersion())
        .icon(globalTemplateEntity.getIcon())
        .gitDetails(getEntityGitDetails(globalTemplateEntity))
        .lastUpdatedAt(globalTemplateEntity.getLastUpdatedAt())
        .entityValidityDetails(globalTemplateEntity.isEntityInvalid()
                ? EntityValidityDetails.builder().valid(false).invalidYaml(globalTemplateEntity.getYaml()).build()
                : EntityValidityDetails.builder().valid(true).build())
        .storeType(globalTemplateEntity.getStoreType())
        .connectorRef(globalTemplateEntity.getConnectorRef())
        .cacheResponseMetadata(getCacheResponse(globalTemplateEntity))
        .build();
  }

  public EntityGitDetails getEntityGitDetails(GlobalTemplateEntity globalTemplateEntity) {
    return globalTemplateEntity.getStoreType() == null
        ? EntityGitDetailsMapper.mapEntityGitDetails(globalTemplateEntity)
        : globalTemplateEntity.getStoreType() == StoreType.REMOTE
        ? GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata()
        : EntityGitDetails.builder().build();
  }

  public CacheResponseMetadataDTO getCacheResponse(GlobalTemplateEntity templateEntity) {
    if (templateEntity.getStoreType() == StoreType.REMOTE) {
      return getCacheResponse();
    }
    return null;
  }
}
