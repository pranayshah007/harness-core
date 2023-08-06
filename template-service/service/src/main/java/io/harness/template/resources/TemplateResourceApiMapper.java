/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.spec.server.template.v1.model.EntityGitDetails;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitFindDetails;
import io.harness.spec.server.template.v1.model.GitUpdateDetails;
import io.harness.spec.server.template.v1.model.TemplateMetadataSummaryResponse;
import io.harness.spec.server.template.v1.model.TemplateResponse;
import io.harness.spec.server.template.v1.model.TemplateWithInputsResponse;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
public class TemplateResourceApiMapper {
  public static final String TEMPLATE = "TEMPLATE";
  private final Validator validator;

  @Inject
  public TemplateResourceApiMapper(Validator validator) {
    this.validator = validator;
  }

  public TemplateWithInputsResponse toTemplateWithInputResponse(TemplateWithInputsResponseDTO templateInput) {
    String templateInputYaml = templateInput.getTemplateInputs();
    TemplateResponseDTO templateResponse = templateInput.getTemplateResponseDTO();
    TemplateWithInputsResponse templateWithInputsResponse = new TemplateWithInputsResponse();
    templateWithInputsResponse.setInputs(templateInputYaml);
    templateWithInputsResponse.setTemplate(toTemplateResponse(templateResponse));
    Set<ConstraintViolation<TemplateResponseDTO>> violations = validator.validate(templateResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return templateWithInputsResponse;
  }
  public TemplateWithInputsResponse toTemplateResponseDefault(TemplateResponseDTO templateResponse) {
    TemplateWithInputsResponse templateWithInputsResponse = new TemplateWithInputsResponse();
    templateWithInputsResponse.setInputs("Input YAML not requested");
    templateWithInputsResponse.setTemplate(toTemplateResponse(templateResponse));
    Set<ConstraintViolation<TemplateWithInputsResponse>> violations = validator.validate(templateWithInputsResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return templateWithInputsResponse;
  }

  public TemplateResponse toTemplateResponse(TemplateResponseDTO templateResponseDTO) {
    TemplateResponse templateResponse = new TemplateResponse();
    templateResponse.setAccount(templateResponseDTO.getAccountId());
    templateResponse.setOrg(templateResponseDTO.getOrgIdentifier());
    templateResponse.setProject(templateResponseDTO.getProjectIdentifier());
    templateResponse.setIdentifier(templateResponseDTO.getIdentifier());
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
        TemplateResponse.ScopeEnum.fromValue(templateResponseDTO.getTemplateScope().getYamlRepresentation());
    templateResponse.setScope(scopeEnum);
    templateResponse.setVersion(templateResponseDTO.getVersion());
    templateResponse.setGitDetails(toEntityGitDetails(templateResponseDTO.getGitDetails()));
    templateResponse.setUpdated(templateResponseDTO.getLastUpdatedAt());
    TemplateResponse.StoreTypeEnum storeTypeEnum =
        TemplateResponse.StoreTypeEnum.fromValue(templateResponseDTO.getStoreType().toString());
    templateResponse.setStoreType(storeTypeEnum);
    templateResponse.setConnectorRef(templateResponseDTO.getConnectorRef());
    templateResponse.setStableTemplate(templateResponseDTO.isStableTemplate());
    Set<ConstraintViolation<TemplateResponse>> violations = validator.validate(templateResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return templateResponse;
  }

  public TemplateMetadataSummaryResponse mapToTemplateMetadataResponse(
      TemplateMetadataSummaryResponseDTO templateMetadataSummaryResponseDTO) {
    TemplateMetadataSummaryResponse templateMetadataSummaryResponse = new TemplateMetadataSummaryResponse();
    templateMetadataSummaryResponse.setAccount(templateMetadataSummaryResponseDTO.getAccountId());
    templateMetadataSummaryResponse.setOrg(templateMetadataSummaryResponseDTO.getOrgIdentifier());
    templateMetadataSummaryResponse.setProject(templateMetadataSummaryResponseDTO.getProjectIdentifier());
    templateMetadataSummaryResponse.setIdentifier(templateMetadataSummaryResponseDTO.getIdentifier());
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
        templateMetadataSummaryResponseDTO.getTemplateScope() != null
            ? templateMetadataSummaryResponseDTO.getTemplateScope().getYamlRepresentation()
            : Scope.UNKNOWN.toString());
    templateMetadataSummaryResponse.setScope(scopeEnum);
    templateMetadataSummaryResponse.setVersion(templateMetadataSummaryResponseDTO.getVersion());
    templateMetadataSummaryResponse.setGitDetails(
        toEntityGitDetails(templateMetadataSummaryResponseDTO.getGitDetails()));
    templateMetadataSummaryResponse.setUpdated(templateMetadataSummaryResponseDTO.getLastUpdatedAt());
    TemplateMetadataSummaryResponse.StoreTypeEnum storeTypeEnum =
        TemplateMetadataSummaryResponse.StoreTypeEnum.fromValue(
            templateMetadataSummaryResponseDTO.getStoreType() != null
                ? templateMetadataSummaryResponseDTO.getStoreType().toString()
                : null);
    templateMetadataSummaryResponse.setStoreType(storeTypeEnum);
    templateMetadataSummaryResponse.setConnectorRef(templateMetadataSummaryResponseDTO.getConnectorRef());
    templateMetadataSummaryResponse.setStableTemplate(templateMetadataSummaryResponseDTO.getStableTemplate());
    Set<ConstraintViolation<TemplateMetadataSummaryResponseDTO>> violations =
        validator.validate(templateMetadataSummaryResponseDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return templateMetadataSummaryResponse;
  }

  public EntityGitDetails toEntityGitDetails(io.harness.gitsync.sdk.EntityGitDetails entityGitDetails) {
    EntityGitDetails responseGitDetails = new EntityGitDetails();
    responseGitDetails.setObjectId(entityGitDetails.getObjectId());
    responseGitDetails.setBranchName(entityGitDetails.getBranch());
    responseGitDetails.setFilePath(entityGitDetails.getFilePath());
    responseGitDetails.setRepoName(entityGitDetails.getRepoName());
    responseGitDetails.setCommitId(entityGitDetails.getCommitId());
    responseGitDetails.setFileUrl(entityGitDetails.getFileUrl());
    responseGitDetails.setRepoUrl(entityGitDetails.getRepoUrl());
    Set<ConstraintViolation<EntityGitDetails>> violations = validator.validate(responseGitDetails);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return responseGitDetails;
  }

  public String mapSort(String sort, String order) {
    String property;
    if (isEmpty(sort) && isEmpty(order)) {
      property = TemplateEntityKeys.lastUpdatedAt;
      order = "DESC";
      return property + ',' + order;
    } else if (isEmpty(sort)) {
      property = TemplateEntityKeys.lastUpdatedAt;
      return property + ',' + order;
    } else if (isEmpty(order)) {
      order = "DESC";
    }
    switch (sort) {
      case "identifier":
        property = TemplateEntityKeys.identifier;
        break;
      case "name":
        property = sort;
        break;
      case "updated":
        property = TemplateEntityKeys.lastUpdatedAt;
        break;
      default:
        throw new InvalidRequestException(
            "Field provided for sorting unidentified. Accepted values: identifier / name / updated");
    }
    return property + ',' + order;
  }

  public GitEntityInfo populateGitCreateDetails(GitCreateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    StoreType storeType = StoreType.INLINE;
    if (gitDetails.getStoreType() != null) {
      storeType = StoreType.getFromStringOrNull(gitDetails.getStoreType().toString());
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .filePath(gitDetails.getFilePath())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .connectorRef(gitDetails.getConnectorRef())
        .storeType(storeType)
        .repoName(gitDetails.getRepoName())
        .build();
  }

  public GitEntityInfo populateGitUpdateDetails(GitUpdateDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    StoreType storeType = StoreType.INLINE;
    if (gitDetails.getStoreType() != null) {
      storeType = StoreType.getFromStringOrNull(gitDetails.getStoreType().toString());
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .commitMsg(gitDetails.getCommitMessage())
        .isNewBranch(isNotEmpty(gitDetails.getBranchName()) && isNotEmpty(gitDetails.getBaseBranch()))
        .baseBranch(gitDetails.getBaseBranch())
        .lastCommitId(gitDetails.getLastCommitId())
        .lastObjectId(gitDetails.getLastObjectId())
        .connectorRef(gitDetails.getConnectorRef())
        .filePath(gitDetails.getFilePath())
        .repoName(gitDetails.getRepoName())
        .storeType(storeType)
        .build();
  }
  public GitEntityInfo populateGitFindDetails(GitFindDetails gitDetails) {
    if (gitDetails == null) {
      return GitEntityInfo.builder().build();
    }
    return GitEntityInfo.builder()
        .branch(gitDetails.getBranchName())
        .parentEntityProjectIdentifier(gitDetails.getParentEntityProjectId())
        .parentEntityOrgIdentifier(gitDetails.getParentEntityOrgId())
        .parentEntityAccountIdentifier(gitDetails.getParentEntityAccountId())
        .parentEntityRepoName(gitDetails.getParentEntityRepoName())
        .parentEntityConnectorRef(gitDetails.getParentEntityConnectorRef())
        .build();
  }
}
