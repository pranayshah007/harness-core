/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static javax.ws.rs.core.UriBuilder.fromPath;
import static org.apache.commons.lang3.StringUtils.isEmpty;
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
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;
import io.harness.spec.server.template.model.EntityGitDetails;
import io.harness.spec.server.template.model.GitCreateDetails;
import io.harness.spec.server.template.model.GitFindDetails;
import io.harness.spec.server.template.model.GitUpdateDetails;
import io.harness.spec.server.template.model.TemplateMetadataSummaryResponse;
import io.harness.spec.server.template.model.TemplateResponse;
import io.harness.spec.server.template.model.TemplateUpdateStableResponse;
import io.harness.spec.server.template.model.TemplateWithInputsResponse;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.FilterParamsDTO;
import io.harness.template.beans.PageParamsDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.TemplateFilterProperties;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.helpers.TemplateYamlConversionHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.services.TemplateMergeService;
import io.harness.template.services.TemplateVariableCreatorFactory;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class TemplateResourceApiUtils {
  public static final String TEMPLATE = "TEMPLATE";
  public static final int FIRST_PAGE = 1;
  private static final String INCLUDE_ALL_TEMPLATES_ACCESSIBLE = "includeAllTemplatesAvailableAtScope";
  private final NGTemplateService templateService;
  private final NGTemplateServiceHelper templateServiceHelper;
  private final AccessControlClient accessControlClient;
  private final TemplateMergeService templateMergeService;
  private final VariablesServiceBlockingStub variablesServiceBlockingStub;
  private final TemplateYamlConversionHelper templateYamlConversionHelper;
  private final TemplateReferenceHelper templateReferenceHelper;
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;
  @Inject TemplateVariableCreatorFactory templateVariableCreatorFactory;

  public static final String TEMPLATE_PARAM_MESSAGE = "Template Identifier for the entity";

  public Response getTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      boolean deleted, GitFindDetails gitFindDetails, Boolean getInputYaml) {
    // if label is not given, return stable template
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    if (getInputYaml) {
      // if label not given, then consider stable template label
      // returns template along with templateInputs yaml
      log.info(String.format(
          "Gets Template along with Template inputs for template with identifier %s in project %s, org %s, account %s",
          templateIdentifier, project, org, account));
      TemplateWithInputsResponseDTO templateWithInputs =
          templateService.getTemplateWithInputs(account, org, project, templateIdentifier, versionLabel);
      String version = "0";
      if (templateWithInputs != null && templateWithInputs.getTemplateResponseDTO() != null
          && templateWithInputs.getTemplateResponseDTO().getVersion() != null) {
        version = String.valueOf(templateWithInputs.getTemplateResponseDTO().getVersion());
      }
      return Response.ok().entity(toTemplateWithInputResponse(templateWithInputs)).tag(version).build();
    } else {
      log.info(
          String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
              templateIdentifier, versionLabel, project, org, account));
      Optional<TemplateEntity> templateEntity =
          templateService.get(account, org, project, templateIdentifier, versionLabel, deleted);

      String version = "0";
      if (templateEntity.isPresent()) {
        version = templateEntity.get().getVersion().toString();
      }
      TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity.orElseThrow(
          ()
              -> new InvalidRequestException(String.format(
                  "Template with the given Identifier: %s and %s does not exist or has been deleted",
                  templateIdentifier,
                  EmptyPredicate.isEmpty(versionLabel) ? "stable versionLabel" : "versionLabel: " + versionLabel))));
      return Response.ok().entity(toTemplateWithInputResponse(templateResponseDTO)).tag(version).build();
    }
  }

  public Response createTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, GitCreateDetails gitEntityCreateInfo, String templateYaml,
      boolean setDefaultTemplate, String comments) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(account, org, project, templateYaml);
    log.info(String.format("Creating Template with identifier %s with label %s in project %s, org %s, account %s",
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), project, org, account));
    TemplateEntity createdTemplate = templateService.create(templateEntity, setDefaultTemplate, comments);
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate);
    return Response.status(Response.Status.CREATED)
        .entity(toTemplateResponse(templateResponseDTO))
        .tag(createdTemplate.getVersion().toString())
        .build();
  }

  public Response updateStableTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      GitFindDetails gitEntityBasicInfo, String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    log.info(String.format(
        "Updating Stable Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, project, org, account));

    TemplateEntity templateEntity =
        templateService.updateStableTemplateVersion(account, org, project, templateIdentifier, versionLabel, comments);
    TemplateUpdateStableResponse templateUpdateStableResponse = new TemplateUpdateStableResponse();
    templateUpdateStableResponse.setStableVersion(templateEntity.getVersionLabel());
    return Response.ok().entity(templateUpdateStableResponse).tag(templateEntity.getVersion().toString()).build();
  }

  public Response updateTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      GitUpdateDetails gitEntityInfo, String templateYaml, boolean setDefaultTemplate, String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    TemplateEntity templateEntity =
        NGTemplateDtoMapper.toTemplateEntity(account, org, project, templateIdentifier, versionLabel, templateYaml);
    log.info(
        String.format("Updating Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
            templateEntity.getIdentifier(), templateEntity.getVersionLabel(), project, org, account));
    templateEntity = templateEntity.withVersion(isNumeric("ifMatch") ? parseLong("ifMatch") : null);
    TemplateEntity updatedTemplate =
        templateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, setDefaultTemplate, comments);
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(updatedTemplate);
    return Response.ok()
        .entity(toTemplateResponse(templateResponseDTO))
        .tag(updatedTemplate.getVersion().toString())
        .build();
  }

  public Response deleteTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(String.format("Deleting Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, project, org, account));

    templateService.delete(account, org, project, templateIdentifier, versionLabel,
        isNumeric("ifMatch") ? parseLong("ifMatch") : null, comments);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  public Response getTemplates(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, int page, int limit, String sort, String order, String searchTerm,
      String listType, boolean recursive,
      io.harness.spec.server.template.model.TemplateFilterProperties templatefilterProperties) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(String.format("Get List of templates in project: %s, org: %s, account: %s", project, org, account));
    TemplateFilterPropertiesDTO filterProperties = toFilterProperties(templatefilterProperties);
    TemplateFilterProperties templateFilterProperties =
        NGTemplateDtoMapper.toTemplateFilterProperties(filterProperties);
    TemplateListType templateListType = TemplateListType.getTemplateType(listType);
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO(
        searchTerm, "", templateListType, templateFilterProperties, recursive, false);

    String sortQuery = mapSort(sort, order);
    PageParamsDTO pageParamsDTO =
        NGTemplateDtoMapper.preparePageParamsDTO(page, limit, Collections.singletonList(sortQuery));

    Page<TemplateMetadataSummaryResponseDTO> templateMetadataSummaryResponseDTOS =
        templateService.listTemplateMetadata(account, org, project, filterParamsDTO, pageParamsDTO)
            .map(NGTemplateDtoMapper::prepareTemplateMetaDataSummaryResponseDto);

    Page<TemplateMetadataSummaryResponse> templateMetadataSummaryResponses =
        templateMetadataSummaryResponseDTOS.map(TemplateResourceApiUtils::mapTotemplateMetadataResponse);
    List<TemplateMetadataSummaryResponse> templateList = templateMetadataSummaryResponses.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    if (isEmpty(org)) {
      ResponseBuilder responseBuilderWithLinks =
          addLinksHeader(responseBuilder, "/v1/templates)", templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    } else if (isEmpty(project)) {
      ResponseBuilder responseBuilderWithLinks =
          addLinksHeader(responseBuilder, format("/v1/orgs/%s/templates", org), templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    } else {
      ResponseBuilder responseBuilderWithLinks = addLinksHeader(
          responseBuilder, format("/v1/orgs/%s/projects/%s/templates", org, project), templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    }
  }

  private TemplateWithInputsResponse toTemplateWithInputResponse(TemplateWithInputsResponseDTO templateInput) {
    String templateInputYaml = templateInput.getTemplateInputs();
    TemplateResponseDTO templateResponse = templateInput.getTemplateResponseDTO();
    TemplateWithInputsResponse templateWithInputsResponse = new TemplateWithInputsResponse();
    templateWithInputsResponse.setInputYaml(templateInputYaml);
    templateWithInputsResponse.setTemplateResponse(toTemplateResponse(templateResponse));
    return templateWithInputsResponse;
  }
  private TemplateWithInputsResponse toTemplateWithInputResponse(TemplateResponseDTO templateResponse) {
    TemplateWithInputsResponse templateWithInputsResponse = new TemplateWithInputsResponse();
    templateWithInputsResponse.setInputYaml("Input YAML not requested");
    templateWithInputsResponse.setTemplateResponse(toTemplateResponse(templateResponse));
    return templateWithInputsResponse;
  }

  private TemplateResponse toTemplateResponse(TemplateResponseDTO templateResponseDTO) {
    TemplateResponse templateResponse = new TemplateResponse();
    templateResponse.setAccount(templateResponseDTO.getAccountId());
    templateResponse.setOrg(templateResponseDTO.getOrgIdentifier());
    templateResponse.setProject(templateResponseDTO.getProjectIdentifier());
    templateResponse.setSlug(templateResponseDTO.getIdentifier());
    templateResponse.setName(templateResponseDTO.getName());
    templateResponse.setDescription(templateResponseDTO.getDescription());
    templateResponse.setTags(templateResponseDTO.getTags());
    templateResponse.setYaml(templateResponseDTO.getYaml());
    templateResponse.setVersionLabel(templateResponseDTO.getVersionLabel());
    TemplateResponse.EntityTypeEnum templateEntityType =
        TemplateResponse.EntityTypeEnum.fromValue(templateResponseDTO.getTemplateEntityType().toString());
    templateResponse.setEntityType(templateEntityType);
    templateResponse.setChildType(templateResponseDTO.getChildType());
    TemplateResponse.ScopeEnum scopeEnum =
        TemplateResponse.ScopeEnum.fromValue(templateResponseDTO.getTemplateScope().toString());
    templateResponse.setScope(scopeEnum);
    templateResponse.setVersion(templateResponseDTO.getVersion());
    templateResponse.setGitDetails(toEntityGitDetails(templateResponseDTO.getGitDetails()));
    templateResponse.setUpdated(templateResponseDTO.getLastUpdatedAt());
    TemplateResponse.StoreTypeEnum storeTypeEnum =
        TemplateResponse.StoreTypeEnum.fromValue(templateResponseDTO.getStoreType().toString());
    templateResponse.setStoreType(storeTypeEnum);
    templateResponse.setConnectorRef(templateResponseDTO.getConnectorRef());
    templateResponse.setStableTemplate(templateResponseDTO.isStableTemplate());
    return templateResponse;
  }

  private static TemplateMetadataSummaryResponse mapTotemplateMetadataResponse(
      TemplateMetadataSummaryResponseDTO templateMetadataSummaryResponseDTO) {
    TemplateMetadataSummaryResponse templateMetadataSummaryResponse = new TemplateMetadataSummaryResponse();
    templateMetadataSummaryResponse.setAccount(templateMetadataSummaryResponseDTO.getAccountId());
    templateMetadataSummaryResponse.setOrg(templateMetadataSummaryResponseDTO.getOrgIdentifier());
    templateMetadataSummaryResponse.setProject(templateMetadataSummaryResponseDTO.getProjectIdentifier());
    templateMetadataSummaryResponse.setSlug(templateMetadataSummaryResponseDTO.getIdentifier());
    templateMetadataSummaryResponse.setName(templateMetadataSummaryResponseDTO.getName());
    templateMetadataSummaryResponse.setDescription(templateMetadataSummaryResponseDTO.getDescription());
    templateMetadataSummaryResponse.setTags(templateMetadataSummaryResponseDTO.getTags());
    templateMetadataSummaryResponse.setVersionLabel(templateMetadataSummaryResponseDTO.getVersionLabel());
    TemplateMetadataSummaryResponse.EntityTypeEnum templateEntityType =
        TemplateMetadataSummaryResponse.EntityTypeEnum.fromValue(
            templateMetadataSummaryResponseDTO.getTemplateEntityType().toString());
    templateMetadataSummaryResponse.setEntityType(templateEntityType);
    templateMetadataSummaryResponse.setChildType(templateMetadataSummaryResponseDTO.getChildType());
    TemplateMetadataSummaryResponse.ScopeEnum scopeEnum = TemplateMetadataSummaryResponse.ScopeEnum.fromValue(
        templateMetadataSummaryResponseDTO.getTemplateScope().toString());
    templateMetadataSummaryResponse.setScope(scopeEnum);
    templateMetadataSummaryResponse.setVersion(templateMetadataSummaryResponseDTO.getVersion());
    templateMetadataSummaryResponse.setGitDetails(
        toEntityGitDetails(templateMetadataSummaryResponseDTO.getGitDetails()));
    templateMetadataSummaryResponse.setUpdated(templateMetadataSummaryResponseDTO.getLastUpdatedAt());
    TemplateMetadataSummaryResponse.StoreTypeEnum storeTypeEnum =
        TemplateMetadataSummaryResponse.StoreTypeEnum.fromValue(
            templateMetadataSummaryResponseDTO.getStoreType().toString());
    templateMetadataSummaryResponse.setStoreType(storeTypeEnum);
    templateMetadataSummaryResponse.setConnectorRef(templateMetadataSummaryResponseDTO.getConnectorRef());
    templateMetadataSummaryResponse.setStableTemplate(templateMetadataSummaryResponseDTO.getStableTemplate());
    return templateMetadataSummaryResponse;
  }

  private static EntityGitDetails toEntityGitDetails(io.harness.gitsync.sdk.EntityGitDetails entityGitDetails) {
    EntityGitDetails responseGitDetails = new EntityGitDetails();
    responseGitDetails.setEntityIdentifier(entityGitDetails.getObjectId());
    responseGitDetails.setBranchName(entityGitDetails.getBranch());
    responseGitDetails.setFilePath(entityGitDetails.getFilePath());
    responseGitDetails.setRepoName(entityGitDetails.getRepoName());
    responseGitDetails.setCommitId(entityGitDetails.getCommitId());
    responseGitDetails.setFileUrl(entityGitDetails.getFileUrl());
    responseGitDetails.setRepoUrl(entityGitDetails.getRepoUrl());
    return responseGitDetails;
  }

  private TemplateFilterPropertiesDTO toFilterProperties(
      io.harness.spec.server.template.model.TemplateFilterProperties templateFilterProperties) {
    TemplateFilterPropertiesDTO filterProperties = new TemplateFilterPropertiesDTO();
    filterProperties.setTemplateNames(templateFilterProperties.getNames());
    filterProperties.setTemplateIdentifiers(templateFilterProperties.getIdentifiers());
    filterProperties.setDescription(templateFilterProperties.getDescription());
    filterProperties.setChildTypes(templateFilterProperties.getChildTypes());
    List<TemplateEntityType> templateEntityTypes = templateFilterProperties.getEntityTypes()
                                                       .stream()
                                                       .map(TemplateResourceApiUtils::mapToTemplateEntity)
                                                       .collect(Collectors.toList());
    filterProperties.setTemplateEntityTypes(templateEntityTypes);
    return filterProperties;
  }

  public static TemplateEntityType mapToTemplateEntity(
      io.harness.spec.server.template.model.TemplateFilterProperties.EntityTypesEnum entityTypesEnum) {
    TemplateEntityType templateEntityType = TemplateEntityType.getTemplateType(entityTypesEnum.toString());
    return templateEntityType;
  }

  public String mapSort(String sort, String order) {
    String property;
    switch (sort) {
      case "slug":
        property = TemplateEntityKeys.identifier;
        break;
      case "harness_account":
        property = TemplateEntityKeys.accountId;
        break;
      case "org":
        property = TemplateEntityKeys.orgIdentifier;
        break;
      case "project":
        property = TemplateEntityKeys.projectIdentifier;
        break;
      case "created":
        property = TemplateEntityKeys.createdAt;
        break;
      case "updated":
        property = TemplateEntityKeys.lastUpdatedAt;
        break;
      default:
        property = sort;
    }
    return property + ',' + order;
  }

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= FIRST_PAGE) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
}
