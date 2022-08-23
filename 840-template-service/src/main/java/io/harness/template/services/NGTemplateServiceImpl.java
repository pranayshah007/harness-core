/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.utils.RestCallToNGManagerClientUtils.execute;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.NGTemplateRepository;
import io.harness.springdata.TransactionHelper;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.FilterParamsDTO;
import io.harness.template.beans.PageParamsDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.gitsync.TemplateGitSyncBranchContextGuard;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.NGTemplateResource;
import io.harness.template.utils.TemplateUtils;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGTemplateServiceImpl implements NGTemplateService {
  @Inject private NGTemplateRepository templateRepository;
  @Inject private NGTemplateServiceHelper templateServiceHelper;
  @Inject private GitSyncSdkService gitSyncSdkService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private EntitySetupUsageClient entitySetupUsageClient;
  @Inject EnforcementClientService enforcementClientService;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject @Named("PRIVILEGED") private OrganizationClient organizationClient;
  @Inject private TemplateReferenceHelper templateReferenceHelper;

  @Inject private NGTemplateSchemaService ngTemplateSchemaService;
  @Inject private TemplateMergeService templateMergeService;
  @Inject private AccessControlClient accessControlClient;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Template [%s] of versionLabel [%s] under Project[%s], Organization [%s] already exists";

  @Override
  public TemplateEntity create(TemplateEntity templateEntity, boolean setStableTemplate, String comments) {
    enforcementClientService.checkAvailability(
        FeatureRestrictionName.TEMPLATE_SERVICE, templateEntity.getAccountIdentifier());

    NGTemplateServiceHelper.validatePresenceOfRequiredFields(
        templateEntity.getAccountId(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());
    assureThatTheProjectAndOrgExists(
        templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier());

    if (!validateIdentifierIsUnique(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
            templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getVersionLabel())) {
      throw new InvalidRequestException(String.format(
          "The template with identifier %s and version label %s already exists in the account %s, org %s, project %s",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getAccountId(),
          templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier()));
    }

    if (!isGitRemoteEntity(templateEntity)) {
      throw new InvalidRequestException(format(
          "Remote template entity cannot for template type [%s] on git simplification enabled for Project [%s] in Organisation [%s] in Account [%s]",
          templateEntity.getTemplateEntityType(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier(), templateEntity.getAccountIdentifier()));
    }

    // apply templates to template yaml for validation and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);
    // populate template references
    templateReferenceHelper.populateTemplateReferences(templateEntity);

    try {
      // Check if this is template identifier first entry, for marking it as stable template.
      List<TemplateEntity> templates =
          getAllTemplatesForGivenIdentifier(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
              templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), false);
      boolean firstVersionEntry = EmptyPredicate.isEmpty(templates);
      validateTemplateTypeAndChildTypeOfTemplate(templateEntity, templates);
      if (firstVersionEntry || setStableTemplate) {
        templateEntity = templateEntity.withStableTemplate(true);
      }

      // a new template creation always means this is now the lastUpdated template.
      templateEntity = templateEntity.withLastUpdatedTemplate(true);

      comments = getActualComments(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), comments);

      // check to make previous template stable as false
      TemplateEntity finalTemplateEntity = templateEntity;
      if (!firstVersionEntry && setStableTemplate) {
        String finalComments = comments;
        return transactionHelper.performTransaction(() -> {
          makePreviousStableTemplateFalse(finalTemplateEntity.getAccountIdentifier(),
              finalTemplateEntity.getOrgIdentifier(), finalTemplateEntity.getProjectIdentifier(),
              finalTemplateEntity.getIdentifier(), finalTemplateEntity.getVersionLabel());
          makePreviousLastUpdatedTemplateFalse(finalTemplateEntity.getAccountIdentifier(),
              finalTemplateEntity.getOrgIdentifier(), finalTemplateEntity.getProjectIdentifier(),
              finalTemplateEntity.getIdentifier(), finalTemplateEntity.getVersionLabel());
          return saveTemplate(finalTemplateEntity, finalComments);
        });
      } else {
        String finalComments1 = comments;
        return transactionHelper.performTransaction(() -> {
          makePreviousLastUpdatedTemplateFalse(finalTemplateEntity.getAccountIdentifier(),
              finalTemplateEntity.getOrgIdentifier(), finalTemplateEntity.getProjectIdentifier(),
              finalTemplateEntity.getIdentifier(), finalTemplateEntity.getVersionLabel());
          return saveTemplate(finalTemplateEntity, finalComments1);
        });
      }

    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, templateEntity.getIdentifier(), templateEntity.getVersionLabel(),
              templateEntity.getProjectIdentifier(), templateEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while creating template [%s] of versionLabel [%s]", templateEntity.getIdentifier(),
                    templateEntity.getVersionLabel()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving template [%s] of versionLabel [%s]", templateEntity.getIdentifier(),
                    templateEntity.getVersionLabel()),
          e);
      throw new InvalidRequestException(String.format("Error while saving template [%s] of versionLabel [%s]: %s",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), e.getMessage()));
    }
  }

  private void validateTemplateTypeAndChildTypeOfTemplate(
      TemplateEntity templateEntity, List<TemplateEntity> templates) {
    if (EmptyPredicate.isNotEmpty(templates)) {
      TemplateEntityType templateEntityType = templates.get(0).getTemplateEntityType();
      String childType = templates.get(0).getChildType();
      if (!Objects.equals(templateEntityType, templateEntity.getTemplateEntityType())) {
        throw new InvalidRequestException(String.format(
            "Template should have same template entity type %s as other template versions", templateEntityType));
      }
      if (!Objects.equals(childType, templateEntity.getChildType())) {
        throw new InvalidRequestException(
            String.format("Template should have same child type %s as other template versions", childType));
      }
    }
  }

  private void applyTemplatesToYamlAndValidateSchema(TemplateEntity templateEntity) {
    try {
      TemplateMergeResponseDTO templateMergeResponseDTO = null;
      if (TemplateRefHelper.hasTemplateRef(templateEntity.getYaml())) {
        templateMergeResponseDTO = templateMergeService.applyTemplatesToYamlV2(templateEntity.getAccountId(),
            templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(), templateEntity.getYaml(), false);
        populateLinkedTemplatesModules(templateEntity, templateMergeResponseDTO);
        checkLinkedTemplateAccess(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
            templateEntity.getProjectIdentifier(), templateMergeResponseDTO);
      }

      // validate schema on resolved yaml to validate template inputs value as well.
      ngTemplateSchemaService.validateYamlSchemaInternal(templateMergeResponseDTO == null
              ? templateEntity
              : templateEntity.withYaml(templateMergeResponseDTO.getMergedPipelineYaml()));
    } catch (NGTemplateResolveExceptionV2 ex) {
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse = ex.getValidateTemplateInputsResponseDTO();
      validateTemplateInputsResponse.getErrorNodeSummary().setTemplateResponse(
          NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity));
      throw new NGTemplateResolveExceptionV2(
          "Exception in resolving template refs in given yaml.", USER, validateTemplateInputsResponse);
    }
  }

  private void populateLinkedTemplatesModules(
      TemplateEntity templateEntity, TemplateMergeResponseDTO templateMergeResponseDTO) {
    if (EmptyPredicate.isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      Set<String> templateModules =
          EmptyPredicate.isNotEmpty(templateEntity.getModules()) ? templateEntity.getModules() : new HashSet<>();
      templateMergeResponseDTO.getTemplateReferenceSummaries().forEach(templateReferenceSummary -> {
        if (EmptyPredicate.isNotEmpty(templateReferenceSummary.getModuleInfo())) {
          templateModules.addAll(templateReferenceSummary.getModuleInfo());
        }
      });
      templateEntity.setModules(templateModules);
    }
  }

  @Override
  public TemplateEntity updateTemplateEntity(
      TemplateEntity templateEntity, ChangeType changeType, boolean setDefaultTemplate, String comments) {
    enforcementClientService.checkAvailability(
        FeatureRestrictionName.TEMPLATE_SERVICE, templateEntity.getAccountIdentifier());
    // apply templates to template yaml for validations and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);
    // update template references
    templateReferenceHelper.populateTemplateReferences(templateEntity);
    return transactionHelper.performTransaction(() -> {
      makePreviousLastUpdatedTemplateFalse(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());
      return updateTemplateHelper(templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
          templateEntity, changeType, setDefaultTemplate, true, comments, null);
    });
  }

  private TemplateEntity updateTemplateHelper(String oldOrgIdentifier, String oldProjectIdentifier,
      TemplateEntity templateEntity, ChangeType changeType, boolean setStableTemplate,
      boolean updateLastUpdatedTemplateFlag, String comments, TemplateUpdateEventType eventType) {
    try {
      NGTemplateServiceHelper.validatePresenceOfRequiredFields(
          templateEntity.getAccountId(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());

      comments = getActualComments(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), comments);
      if (templateServiceHelper.isOldGitSync(templateEntity)) {
        GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
        if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
          // sending old entity as null here because a new mongo entity will be created. If audit trail needs to be
          // added to git synced projects, a get call needs to be added here to the base branch of this template update
          TemplateEntity templateToCreate = templateEntity.withLastUpdatedTemplate(true);
          return templateServiceHelper.makeTemplateUpdateCall(
              templateToCreate, null, changeType, comments, TemplateUpdateEventType.TEMPLATE_CREATE_EVENT, false);
        }
      }

      TemplateEntity oldTemplateEntity =
          getAndValidateOldTemplateEntity(templateEntity, oldOrgIdentifier, oldProjectIdentifier);

      TemplateEntity templateToUpdate = oldTemplateEntity.withYaml(templateEntity.getYaml())
                                            .withTemplateScope(templateEntity.getTemplateScope())
                                            .withName(templateEntity.getName())
                                            .withDescription(templateEntity.getDescription())
                                            .withTags(templateEntity.getTags())
                                            .withOrgIdentifier(templateEntity.getOrgIdentifier())
                                            .withProjectIdentifier(templateEntity.getProjectIdentifier())
                                            .withFullyQualifiedIdentifier(templateEntity.getFullyQualifiedIdentifier())
                                            .withLastUpdatedTemplate(updateLastUpdatedTemplateFlag)
                                            .withIsEntityInvalid(false);

      // Updating the stable template version.
      if (setStableTemplate && !templateToUpdate.isStableTemplate()) {
        TemplateEntity templateToUpdateWithStable = templateToUpdate.withStableTemplate(true);
        String finalComments = comments;
        return transactionHelper.performTransaction(() -> {
          makePreviousStableTemplateFalse(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
              templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(),
              templateToUpdate.getVersionLabel());
          return templateServiceHelper.makeTemplateUpdateCall(templateToUpdateWithStable, oldTemplateEntity, changeType,
              finalComments, TemplateUpdateEventType.TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE_EVENT, false);
        });
      }

      return templateServiceHelper.makeTemplateUpdateCall(templateToUpdate, oldTemplateEntity, changeType, comments,
          eventType != null ? eventType : TemplateUpdateEventType.OTHERS_EVENT, false);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, templateEntity.getIdentifier(), templateEntity.getVersionLabel(),
              templateEntity.getProjectIdentifier(), templateEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while updating template [%s] of versionLabel [%s]", templateEntity.getIdentifier(),
                    templateEntity.getVersionLabel()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving template [%s] of versionLabel [%s]", templateEntity.getIdentifier(),
                    templateEntity.getVersionLabel()),
          e);
      throw new InvalidRequestException(String.format("Error while saving template [%s] of versionLabel [%s]",
                                            templateEntity.getIdentifier(), templateEntity.getVersionLabel()),
          e);
    }
  }

  @Override
  public Optional<TemplateEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    try {
      return templateServiceHelper.getTemplate(
          accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, deleted, false);
    } catch (Exception e) {
      String errorMessage = getErrorMessage(templateIdentifier, versionLabel);
      log.error(errorMessage, e);
      ScmException exception = TemplateUtils.getScmException(e);
      if (null != exception) {
        throw new InvalidRequestException(errorMessage, e);
      } else {
        throw new InvalidRequestException(String.format("[%s]: %s", errorMessage, ExceptionUtils.getMessage(e)));
      }
    }
  }

  @Override
  public Optional<TemplateEntity> getMetadataOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted) {
    return templateServiceHelper.getMetadataOrThrowExceptionIfInvalid(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, deleted);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier,
      String deleteVersionLabel, Long version, String comments) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    List<TemplateEntity> templateEntities =
        getAllTemplatesForGivenIdentifier(accountId, orgIdentifier, projectIdentifier, templateIdentifier, false);

    TemplateEntity templateToDelete = null;
    TemplateEntity stableTemplate = null;

    for (TemplateEntity templateEntity : templateEntities) {
      if (deleteVersionLabel.equals(templateEntity.getVersionLabel())) {
        templateToDelete = templateEntity;
      }
      if (templateEntity.isStableTemplate()) {
        stableTemplate = templateEntity;
      }
    }
    if (templateToDelete == null) {
      throw new InvalidRequestException(format("Template with identifier [%s] and versionLabel [%s] %s does not exist.",
          templateIdentifier, deleteVersionLabel, getMessageHelper(accountId, orgIdentifier, projectIdentifier)));
    }
    if (stableTemplate != null && stableTemplate.getVersionLabel().equals(deleteVersionLabel)
        && templateEntities.size() != 1) {
      throw new InvalidRequestException(
          "You cannot delete the stable version of the template. Please update another version as the stable version before deleting this version");
    }
    return deleteMultipleTemplatesHelper(accountId, orgIdentifier, projectIdentifier,
        Collections.singletonList(templateToDelete), version, comments, templateEntities.size() == 1, stableTemplate);
  }

  @Override
  public boolean deleteTemplates(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, Set<String> deleteTemplateVersions, String comments) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    List<TemplateEntity> templateEntities =
        getAllTemplatesForGivenIdentifier(accountId, orgIdentifier, projectIdentifier, templateIdentifier, false);
    boolean canDeleteStableTemplate = templateEntities.size() == deleteTemplateVersions.size();
    List<TemplateEntity> templateToDeleteList = new LinkedList<>();
    TemplateEntity stableTemplate = null;
    for (TemplateEntity templateEntity : templateEntities) {
      if (deleteTemplateVersions.contains(templateEntity.getVersionLabel())) {
        templateToDeleteList.add(templateEntity);
      }
      if (templateEntity.isStableTemplate()) {
        stableTemplate = templateEntity;
      }
    }
    if (stableTemplate != null && deleteTemplateVersions.contains(stableTemplate.getVersionLabel())
        && !canDeleteStableTemplate) {
      throw new InvalidRequestException(
          "You cannot delete the stable version of the template. Please update another version as the stable version before deleting this version");
    }
    return deleteMultipleTemplatesHelper(accountId, orgIdentifier, projectIdentifier, templateToDeleteList, null,
        comments, canDeleteStableTemplate, stableTemplate);
  }

  private String getMessageHelper(String accountId, String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      return format("under Project[%s], Organization [%s], Account [%s]", projectIdentifier, orgIdentifier, accountId);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      return format("under Organization [%s], Account [%s]", orgIdentifier, accountId);
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return format("under Account [%s]", accountId);
    } else {
      return "";
    }
  }

  private boolean deleteMultipleTemplatesHelper(String accountId, String orgIdentifier, String projectIdentifier,
      List<TemplateEntity> templateToDeleteList, Long version, String comments, boolean canDeleteStableTemplate,
      TemplateEntity stableTemplate) {
    for (TemplateEntity templateEntity : templateToDeleteList) {
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               templateEntity, GitContextHelper.getGitEntityInfo(),
               format("Deleting template with identifier [%s] and versionLabel [%s].", templateEntity.getIdentifier(),
                   templateEntity.getVersionLabel()))) {
        deleteSingleTemplateHelper(accountId, orgIdentifier, projectIdentifier, templateEntity.getIdentifier(),
            templateEntity, version, canDeleteStableTemplate, comments);
      }
    }

    if (!canDeleteStableTemplate) {
      makeGivenTemplateLastUpdatedTemplateTrue(stableTemplate);
    }
    return true;
  }

  protected boolean deleteSingleTemplateHelper(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, TemplateEntity templateToDelete, Long version, boolean canDeleteStableTemplate,
      String comments) {
    String versionLabel = templateToDelete.getVersionLabel();
    comments = getActualComments(accountId, orgIdentifier, projectIdentifier, comments);
    // find the given template version in the list

    if (version != null && !version.equals(templateToDelete.getVersion())) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] is not on the correct version of DB.",
          templateIdentifier, versionLabel, projectIdentifier, orgIdentifier));
    }

    // Check if template is stable whether it can be deleted or not.
    // Can delete stable template only if that's the only template version left.
    if (templateToDelete.isStableTemplate() && !canDeleteStableTemplate) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] is a stable template, thus cannot delete it.",
          templateIdentifier, versionLabel, projectIdentifier, orgIdentifier));
    }
    checkThatTheTemplateIsNotUsedByOthers(templateToDelete);
    // delete template references
    templateReferenceHelper.deleteTemplateReferences(templateToDelete);
    try {
      return templateServiceHelper.deleteTemplate(
          accountId, orgIdentifier, projectIdentifier, templateIdentifier, templateToDelete, versionLabel, comments);
    } catch (Exception e) {
      log.error(String.format("Error while deleting template with identifier [%s] and versionLabel [%s]",
                    templateIdentifier, versionLabel),
          e);
      ScmException exception = TemplateUtils.getScmException(e);
      if (null != exception) {
        throw e;
      }
      throw new InvalidRequestException(
          String.format("Error while deleting template with identifier [%s] and versionLabel [%s]: %s",
              templateIdentifier, versionLabel, e.getMessage()));
    }
  }

  private void checkThatTheTemplateIsNotUsedByOthers(TemplateEntity templateToDelete) {
    boolean isEntityReferenced;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(templateToDelete.getAccountIdentifier())
                                      .orgIdentifier(templateToDelete.getOrgIdentifier())
                                      .projectIdentifier(templateToDelete.getProjectIdentifier())
                                      .identifier(templateToDelete.getIdentifier())
                                      .build();
    String referredEntityFQN = identifierRef.getFullyQualifiedName() + "/" + templateToDelete.getVersionLabel() + "/";
    try {
      isEntityReferenced = execute(entitySetupUsageClient.isEntityReferenced(
          templateToDelete.getAccountIdentifier(), referredEntityFQN, EntityType.TEMPLATE));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          templateToDelete.getIdentifier(), ex);
      throw new UnexpectedException(
          String.format("Error while checking references for template %s with version label: %s : %s",
              templateToDelete.getIdentifier(), templateToDelete.getVersionLabel(), ex.getMessage()));
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(String.format(
          "Could not delete the template %s as it is referenced by other entities", templateToDelete.getIdentifier()));
    }
  }

  @Override
  public Page<TemplateEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    if (Boolean.TRUE.equals(getDistinctFromBranches)
        && gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      return templateRepository.findAll(criteria, pageable, accountId, orgIdentifier, projectIdentifier, true);
    }
    return templateRepository.findAll(criteria, pageable, accountId, orgIdentifier, projectIdentifier, false);
  }

  @Override
  public Page<TemplateEntity> listTemplateMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, FilterParamsDTO filterParamsDTO, PageParamsDTO pageParamsDTO) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountIdentifier);
    Criteria criteria = templateServiceHelper.formCriteria(accountIdentifier, orgIdentifier, projectIdentifier,
        filterParamsDTO.getFilterIdentifier(), false, filterParamsDTO.getTemplateFilterProperties(),
        filterParamsDTO.getSearchTerm(), filterParamsDTO.isIncludeAllTemplatesAccessibleAtScope());

    // Adding criteria needed for ui homepage
    if (filterParamsDTO.getTemplateListType() != null) {
      criteria = templateServiceHelper.formCriteria(criteria, filterParamsDTO.getTemplateListType());
    }
    Pageable pageable;
    if (EmptyPredicate.isEmpty(pageParamsDTO.getSort())) {
      pageable = PageRequest.of(pageParamsDTO.getPage(), pageParamsDTO.getSize(),
          Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    } else {
      pageable = PageUtils.getPageRequest(pageParamsDTO.getPage(), pageParamsDTO.getSize(), pageParamsDTO.getSort());
    }

    return templateServiceHelper.listTemplate(accountIdentifier, orgIdentifier, projectIdentifier, criteria, pageable,
        filterParamsDTO.isGetDistinctFromBranches());
  }

  @Override
  public TemplateEntity updateStableTemplateVersion(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String newStableTemplateVersion, String comments) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountIdentifier);
    return transactionHelper.performTransaction(
        ()
            -> updateStableTemplateVersionHelper(accountIdentifier, orgIdentifier, projectIdentifier,
                templateIdentifier, newStableTemplateVersion, comments));
  }

  @Override
  public boolean updateTemplateSettings(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, Scope currentScope, Scope updateScope, String updateStableTemplateVersion,
      Boolean getDistinctFromBranches) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);

    // if both current and update scope of template are same, check for updating stable template version
    if (currentScope.equals(updateScope)) {
      String orgIdBasedOnScope = currentScope.equals(Scope.ACCOUNT) ? null : orgIdentifier;
      String projectIdBasedOnScope = currentScope.equals(Scope.PROJECT) ? projectIdentifier : null;
      TemplateEntity entity =
          updateStableTemplateVersion(accountId, orgIdBasedOnScope, projectIdBasedOnScope, templateIdentifier,
              updateStableTemplateVersion, "Updating stable template versionLabel to " + updateStableTemplateVersion);
      return entity.isStableTemplate();
    } else {
      return transactionHelper.performTransaction(
          ()
              -> updateTemplateScope(accountId, orgIdentifier, projectIdentifier, templateIdentifier, currentScope,
                  updateScope, updateStableTemplateVersion, getDistinctFromBranches));
    }
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, String invalidYaml) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountIdentifier);
    Optional<TemplateEntity> optionalTemplateEntity =
        get(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false);
    if (!optionalTemplateEntity.isPresent()) {
      log.warn(String.format("Marking template [%s-%s] as invalid failed as it does not exist or has been deleted",
          templateIdentifier, versionLabel));
      return false;
    }

    TemplateEntity existingTemplate = optionalTemplateEntity.get();
    TemplateEntity updatedTemplate =
        existingTemplate.withObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml))
            .withYaml(invalidYaml)
            .withIsEntityInvalid(true);
    log.info(format("The update template for template identifier [%s] yaml happens as this is oldGitSync flow",
        templateIdentifier));
    templateRepository.updateTemplateYamlForOldGitSync(
        updatedTemplate, existingTemplate, ChangeType.NONE, "", TemplateUpdateEventType.OTHERS_EVENT, true);
    return true;
  }

  @Override
  public TemplateEntity fullSyncTemplate(EntityDetailProtoDTO entityDetailProtoDTO) {
    try {
      TemplateReferenceProtoDTO templateRef = entityDetailProtoDTO.getTemplateRef();

      Optional<TemplateEntity> unSyncedTemplate =
          getUnSyncedTemplate(StringValueUtils.getStringFromStringValue(templateRef.getAccountIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getOrgIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getProjectIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel()));

      unSyncedTemplate.ifPresent(templateEntity -> templateReferenceHelper.populateTemplateReferences(templateEntity));
      return templateServiceHelper.makeTemplateUpdateCall(unSyncedTemplate.get(), unSyncedTemplate.get(),
          ChangeType.ADD, "", TemplateUpdateEventType.OTHERS_EVENT, true);
    } catch (DuplicateKeyException ex) {
      TemplateReferenceProtoDTO templateRef = entityDetailProtoDTO.getTemplateRef();
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel()),
              StringValueUtils.getStringFromStringValue(templateRef.getProjectIdentifier()),
              StringValueUtils.getStringFromStringValue(templateRef.getOrgIdentifier())),
          USER_SRE, ex);
    } catch (Exception e) {
      TemplateReferenceProtoDTO templateRef = entityDetailProtoDTO.getTemplateRef();
      log.error(String.format("Error while saving template [%s] of versionLabel [%s]",
                    StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
                    StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel())),
          e);
      throw new InvalidRequestException(String.format("Error while saving template [%s] of versionLabel [%s]: %s",
          StringValueUtils.getStringFromStringValue(templateRef.getIdentifier()),
          StringValueUtils.getStringFromStringValue(templateRef.getVersionLabel()), e.getMessage()));
    }
  }

  @Override
  public boolean validateIdentifierIsUnique(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel) {
    return !templateRepository.existsByAccountIdAndOrgIdAndProjectIdAndIdentifierAndVersionLabel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel);
  }

  @Override
  public TemplateEntity updateGitFilePath(TemplateEntity templateEntity, String newFilePath) {
    Criteria criteria = Criteria.where(TemplateEntityKeys.accountId)
                            .is(templateEntity.getAccountId())
                            .and(TemplateEntityKeys.orgIdentifier)
                            .is(templateEntity.getOrgIdentifier())
                            .and(TemplateEntityKeys.projectIdentifier)
                            .is(templateEntity.getProjectIdentifier())
                            .and(TemplateEntityKeys.identifier)
                            .is(templateEntity.getIdentifier())
                            .and(TemplateEntityKeys.versionLabel)
                            .is(templateEntity.getVersionLabel());

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(TemplateEntityKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(TemplateEntityKeys.rootFolder, gitEntityFilePath.getRootFolder());
    return templateRepository.update(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier(), criteria, update);
  }

  @Override
  public void checkLinkedTemplateAccess(
      String accountId, String orgId, String projectId, TemplateMergeResponseDTO templateMergeResponseDTO) {
    if (EmptyPredicate.isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      Set<String> templateIdentifiers = templateMergeResponseDTO.getTemplateReferenceSummaries()
                                            .stream()
                                            .map(TemplateReferenceSummary::getTemplateIdentifier)
                                            .collect(Collectors.toSet());
      templateIdentifiers.forEach(templateIdentifier
          -> accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
              Resource.of(NGTemplateResource.TEMPLATE, templateIdentifier),
              PermissionTypes.TEMPLATE_ACCESS_PERMISSION));
    }
  }

  @Override
  public boolean deleteAllTemplatesInAProject(String accountId, String orgId, String projectId) {
    boolean isOldGitSyncEnabled = gitSyncSdkService.isGitSyncEnabled(accountId, orgId, projectId);
    if (isOldGitSyncEnabled) {
      Criteria criteria = Criteria.where(TemplateEntityKeys.accountId)
                              .is(accountId)
                              .and(TemplateEntityKeys.orgIdentifier)
                              .is(orgId)
                              .and(TemplateEntityKeys.projectIdentifier)
                              .is(projectId);
      Pageable pageRequest = org.springframework.data.domain.PageRequest.of(
          0, 1000, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));

      Page<TemplateEntity> templateEntities =
          templateRepository.findAll(criteria, pageRequest, accountId, orgId, projectId, false);
      for (TemplateEntity templateEntity : templateEntities) {
        // Update the git context with details of the template on which the operation is going to run.
        try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
                 templateEntity, GitContextHelper.getGitEntityInfo(),
                 format("Template with identifier [%s] and versionLabel [%s] marking stable template as false.",
                     templateEntity.getIdentifier(), templateEntity.getVersionLabel()))) {
          templateRepository.hardDeleteTemplateForOldGitSync(templateEntity, "");
        }
      }
      return true;
    }
    return templateRepository.deleteAllTemplatesInAProject(accountId, orgId, projectId);
  }

  @Override
  public boolean deleteAllOrgLevelTemplates(String accountId, String orgId) {
    // Delete all the org level templates only
    return templateRepository.deleteAllOrgLevelTemplates(accountId, orgId);
  }

  private void assureThatTheProjectAndOrgExists(String accountId, String orgId, String projectId) {
    if (isNotEmpty(projectId)) {
      // it's project level template
      if (isEmpty(orgId)) {
        throw new InvalidRequestException(String.format("Project %s specified without the org Identifier", projectId));
      }
      checkProjectExists(accountId, orgId, projectId);
    } else if (isNotEmpty(orgId)) {
      // its a org level connector
      checkThatTheOrganizationExists(accountId, orgId);
    }
  }

  private void checkProjectExists(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      getResponse(projectClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier),
          String.format("Project with orgIdentifier %s and identifier %s not found", orgIdentifier, projectIdentifier));
    }
  }

  private void checkThatTheOrganizationExists(String accountIdentifier, String orgIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      getResponse(organizationClient.getOrganization(orgIdentifier, accountIdentifier),
          String.format("Organization with orgIdentifier %s not found", orgIdentifier));
    }
  }

  @VisibleForTesting
  String getActualComments(String accountId, String orgIdentifier, String projectIdentifier, String comments) {
    boolean gitSyncEnabled = gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
    if (gitSyncEnabled) {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null && isNotEmpty(gitEntityInfo.getCommitMsg())) {
        return gitEntityInfo.getCommitMsg();
      }
    }
    return comments;
  }

  private TemplateEntity updateStableTemplateVersionHelper(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String newStableTemplateVersion, String comments) {
    try {
      makePreviousStableTemplateFalse(
          accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, newStableTemplateVersion);
      makePreviousLastUpdatedTemplateFalse(
          accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, newStableTemplateVersion);
      Optional<TemplateEntity> optionalTemplateEntity = getMetadataOrThrowExceptionIfInvalid(
          accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, newStableTemplateVersion, false);
      if (!optionalTemplateEntity.isPresent()) {
        throw new InvalidRequestException(format(
            "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] does not exist.",
            templateIdentifier, newStableTemplateVersion, projectIdentifier, orgIdentifier));
      }
      // make given version stable template as true.
      TemplateEntity oldTemplateForGivenVersion = optionalTemplateEntity.get();

      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               oldTemplateForGivenVersion, GitContextHelper.getGitEntityInfo(),
               format("Template with identifier [%s] and versionLabel [%s] marking stable template as true.",
                   templateIdentifier, newStableTemplateVersion))) {
        TemplateEntity templateToUpdateForGivenVersion =
            oldTemplateForGivenVersion.withStableTemplate(true).withLastUpdatedTemplate(true);
        return templateServiceHelper.makeTemplateUpdateInDB(templateToUpdateForGivenVersion, oldTemplateForGivenVersion,
            ChangeType.MODIFY, comments, TemplateUpdateEventType.TEMPLATE_STABLE_TRUE_EVENT, false);
      }
    } catch (Exception e) {
      log.error(
          String.format("Error while updating template with identifier [%s] to stable template of versionLabel [%s]",
              templateIdentifier, newStableTemplateVersion),
          e);
      ScmException exception = TemplateUtils.getScmException(e);
      if (null != exception) {
        throw e;
      }
      throw new InvalidRequestException(String.format(
          "Error while updating template with identifier [%s] to stable template of versionLabel [%s]: %s",
          templateIdentifier, newStableTemplateVersion, ExceptionUtils.getMessage(e)));
    }
  }

  // Current scope is template original scope, updatedScope is new scope.
  // TODO: Change implementation to new requirements. Handle template last updated flag false gracefully.
  // TODO: ListMetadata will not be sending out the yaml in this case
  private boolean updateTemplateScope(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, Scope currentScope, Scope updatedScope, String updateStableTemplateVersion,
      Boolean getDistinctFromBranches) {
    String orgIdBasedOnCurrentScope = currentScope.equals(Scope.ACCOUNT) ? null : orgIdentifier;
    String projectIdBasedOnCurrentScope = currentScope.equals(Scope.PROJECT) ? projectIdentifier : null;
    List<TemplateEntity> templateEntities = getAllTemplatesForGivenIdentifier(
        accountId, orgIdBasedOnCurrentScope, projectIdBasedOnCurrentScope, templateIdentifier, getDistinctFromBranches);

    // Iterating templates to update each entry individually.
    String newOrgIdentifier = null;
    String newProjectIdentifier = null;
    for (int i = 0; i < templateEntities.size(); i++) {
      TemplateEntity templateEntity = templateEntities.get(i);
      NGTemplateConfig templateConfig = NGTemplateDtoMapper.toDTO(templateEntity);
      // set appropriate scope as given by customer
      if (updatedScope.equals(Scope.ORG)) {
        newOrgIdentifier = orgIdentifier;
      } else if (updatedScope.equals(Scope.PROJECT)) {
        newProjectIdentifier = projectIdentifier;
        newOrgIdentifier = orgIdentifier;
      }
      templateConfig.getTemplateInfoConfig().setProjectIdentifier(newProjectIdentifier);
      templateConfig.getTemplateInfoConfig().setOrgIdentifier(newOrgIdentifier);

      TemplateEntity updateEntity = NGTemplateDtoMapper.toTemplateEntity(accountId, templateConfig);
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               updateEntity, GitContextHelper.getGitEntityInfo(),
               format("Template with identifier [%s] and versionLabel [%s] updating the template scope to [%s].",
                   templateIdentifier, updateEntity.getVersionLabel(), updateEntity.getTemplateScope()))) {
        String orgIdBasedOnScope = currentScope.equals(Scope.ACCOUNT) ? null : orgIdentifier;
        String projectIdBasedOnScope = currentScope.equals(Scope.PROJECT) ? projectIdentifier : null;

        // Updating the template
        boolean isLastEntity = i == templateEntities.size() - 1;

        // TODO: @Archit: Check if scope change is possible by checking scope of child entities after referenced by is
        // implemented
        updateTemplateHelper(orgIdBasedOnScope, projectIdBasedOnScope, updateEntity, ChangeType.MODIFY, false,
            isLastEntity, "Changing scope from " + currentScope + " to new scope - " + updatedScope,
            TemplateUpdateEventType.TEMPLATE_CHANGE_SCOPE_EVENT);
      }
    }
    TemplateEntity entity =
        updateStableTemplateVersion(accountId, newOrgIdentifier, newProjectIdentifier, templateIdentifier,
            updateStableTemplateVersion, "Updating stable template versionLabel to " + updateStableTemplateVersion);
    return entity.isStableTemplate();
  }

  private void makePreviousStableTemplateFalse(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String updatedStableTemplateVersion) {
    NGTemplateServiceHelper.validatePresenceOfRequiredFields(accountIdentifier, templateIdentifier);
    Optional<TemplateEntity> optionalTemplateEntity = templateServiceHelper.getStableTemplate(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, false, true);
    if (optionalTemplateEntity.isPresent()) {
      // make previous stable template as false.
      TemplateEntity oldTemplate = optionalTemplateEntity.get();
      if (updatedStableTemplateVersion.equals(oldTemplate.getVersionLabel())) {
        log.info(
            "Ignoring marking previous stable template as false, as new versionLabel given is same as already existing one.");
        return;
      }

      // Update the git context with details of the template on which the operation is going to run.
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               oldTemplate, GitContextHelper.getGitEntityInfo(),
               format("Template with identifier [%s] and versionLabel [%s] marking stable template as false.",
                   templateIdentifier, oldTemplate.getVersionLabel()))) {
        if (templateServiceHelper.isOldGitSync(oldTemplate)) {
          TemplateEntity templateToUpdate = oldTemplate.withStableTemplate(false);
          templateServiceHelper.makeTemplateUpdateCall(templateToUpdate, oldTemplate, ChangeType.MODIFY, "",
              TemplateUpdateEventType.TEMPLATE_STABLE_FALSE_EVENT, true);
        } else {
          templateRepository.updateIsStableTemplate(oldTemplate, false);
        }
      }
    } else {
      log.info(format(
          "Requested template entity with identifier [%s] not found in account [%s] in organisation [%s] and project [%s], hence the update call is ignored",
          templateIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  private void makePreviousLastUpdatedTemplateFalse(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String currentTemplateVersion) {
    NGTemplateServiceHelper.validatePresenceOfRequiredFields(accountIdentifier, templateIdentifier);
    Optional<TemplateEntity> optionalTemplateEntity = templateServiceHelper.getLastUpdatedTemplate(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, true);
    if (optionalTemplateEntity.isPresent()) {
      // make previous last updated template as false.
      TemplateEntity oldTemplate = optionalTemplateEntity.get();

      if (EmptyPredicate.isNotEmpty(currentTemplateVersion)
          && currentTemplateVersion.equals(oldTemplate.getVersionLabel())) {
        log.info(
            "Ignoring marking previous updated template as false, as new versionLabel given is same as already existing one.");
        return;
      }

      // Update the git context with details of the template on which the operation is going to run.
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               oldTemplate, GitContextHelper.getGitEntityInfo(),
               format("Template with identifier [%s] and versionLabel [%s] marking last updated template as false.",
                   templateIdentifier, oldTemplate.getVersionLabel()))) {
        if (templateServiceHelper.isOldGitSync(oldTemplate)) {
          TemplateEntity templateToUpdate = oldTemplate.withLastUpdatedTemplate(false);
          templateServiceHelper.makeTemplateUpdateCall(templateToUpdate, oldTemplate, ChangeType.MODIFY, "",
              TemplateUpdateEventType.TEMPLATE_LAST_UPDATED_FALSE_EVENT, true);
        } else {
          templateRepository.updateIsLastUpdatedTemplate(oldTemplate, false);
        }
      }
    } else {
      log.info(format(
          "Requested template entity with identifier [%s] not found in account [%s] in organisation [%s] and project [%s], hence the update call is ignored",
          templateIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  private void makeGivenTemplateLastUpdatedTemplateTrue(TemplateEntity templateToUpdate) {
    if (templateToUpdate != null) {
      // Update the git context with details of the template on which the operation is going to run.
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               templateToUpdate, GitContextHelper.getGitEntityInfo(),
               format("Template with identifier [%s] and versionLabel [%s] marking last updated template as true.",
                   templateToUpdate, templateToUpdate.getVersionLabel()))) {
        if (templateServiceHelper.isOldGitSync(templateToUpdate)) {
          TemplateEntity withLastUpdatedTemplate = templateToUpdate.withLastUpdatedTemplate(true);
          templateServiceHelper.makeTemplateUpdateCall(withLastUpdatedTemplate, templateToUpdate, ChangeType.MODIFY, "",
              TemplateUpdateEventType.TEMPLATE_LAST_UPDATED_TRUE_EVENT, true);
        } else {
          templateRepository.updateIsLastUpdatedTemplate(templateToUpdate, true);
        }
      }
    }
  }

  private List<TemplateEntity> getAllTemplatesForGivenIdentifier(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, Boolean getDistinctFromBranches) {
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO("", "", null,
        NGTemplateDtoMapper.toTemplateFilterProperties(
            TemplateFilterPropertiesDTO.builder()
                .templateIdentifiers(Collections.singletonList(templateIdentifier))
                .build()),
        false, getDistinctFromBranches);
    PageParamsDTO pageParamsDTO = NGTemplateDtoMapper.preparePageParamsDTO(0, 1000, new ArrayList<>());

    return listTemplateMetadata(accountId, orgIdentifier, projectIdentifier, filterParamsDTO, pageParamsDTO)
        .getContent();
  }

  private Optional<TemplateEntity> getUnSyncedTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel) {
    try (TemplateGitSyncBranchContextGuard ignored =
             templateServiceHelper.getTemplateGitContextForGivenTemplate(null, null, "")) {
      Optional<TemplateEntity> optionalTemplate = templateServiceHelper.getTemplateWithVersionLabel(
          accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, false);
      if (!optionalTemplate.isPresent()) {
        throw new InvalidRequestException(format(
            "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] doesn't exist.",
            accountId, versionLabel, projectIdentifier, orgIdentifier));
      }
      return optionalTemplate;
    }
  }

  private TemplateEntity saveTemplate(TemplateEntity templateEntity, String comments) throws InvalidRequestException {
    if (templateServiceHelper.isOldGitSync(templateEntity)) {
      return templateRepository.saveForOldGitSync(templateEntity, comments);
    } else {
      return templateRepository.save(templateEntity, comments);
    }
  }

  private TemplateEntity getAndValidateOldTemplateEntity(
      TemplateEntity templateEntity, String oldOrgIdentifier, String oldProjectIdentifier) {
    Optional<TemplateEntity> optionalTemplate =
        templateServiceHelper.getTemplateWithVersionLabel(templateEntity.getAccountId(), oldOrgIdentifier,
            oldProjectIdentifier, templateEntity.getIdentifier(), templateEntity.getVersionLabel(), false, false);

    if (!optionalTemplate.isPresent()) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] doesn't exist.",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier()));
    }
    TemplateEntity oldTemplateEntity = optionalTemplate.get();
    if (!oldTemplateEntity.getTemplateEntityType().equals(templateEntity.getTemplateEntityType())) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] cannot update the template type, type is [%s].",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier(), oldTemplateEntity.getTemplateEntityType()));
    }
    if (!((oldTemplateEntity.getChildType() == null && templateEntity.getChildType() == null)
            || oldTemplateEntity.getChildType().equals(templateEntity.getChildType()))) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] cannot update the internal template type, type is [%s].",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier(), oldTemplateEntity.getChildType()));
    }
    return oldTemplateEntity;
  }

  private String getErrorMessage(String templateIdentifier, String versionLabel) {
    if (EmptyPredicate.isEmpty(versionLabel)) {
      return format("Error while retrieving stable template with identifier [%s] ", templateIdentifier);
    } else {
      return format("Error while retrieving template with identifier [%s] and versionLabel [%s]", templateIdentifier,
          versionLabel);
    }
  }

  private boolean isGitRemoteEntity(TemplateEntity templateEntity) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if(gitEntityInfo != null) {
      return TemplateUtils.isRemoteEntity(gitEntityInfo)
              && templateEntity.getTemplateEntityType().isGitEntity();
    } else {
      return true;
    }
  }
}
