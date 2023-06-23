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
import static io.harness.gitaware.helper.TemplateMoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.gitaware.helper.TemplateMoveConfigOperationType.getMoveConfigType;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;
import static io.harness.template.resources.beans.NGTemplateConstants.STABLE_VERSION;
import static io.harness.template.resources.beans.PermissionTypes.TEMPLATE_VIEW_PERMISSION;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.engine.GovernanceService;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eraro.ErrorMessageConstants;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.TemplateAlreadyExistsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitaware.helper.TemplateMoveConfigOperationDTO;
import io.harness.gitaware.helper.TemplateMoveConfigOperationType;
import io.harness.gitaware.helper.TemplateMoveConfigRequestDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.governance.GovernanceMetadata;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.NGTemplateRepository;
import io.harness.springdata.TransactionHelper;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.gitsync.TemplateGitSyncBranchContextGuard;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateImportRequestDTO;
import io.harness.template.resources.beans.TemplateListRepoResponse;
import io.harness.template.resources.beans.TemplateMoveConfigResponse;
import io.harness.template.resources.beans.UpdateGitDetailsParams;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.utils.TemplateUtils;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagService;

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
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
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
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;

  @Inject private TemplateGitXService templateGitXService;

  @Inject private TemplateAsyncSetupUsageService templateAsyncSetupUsageService;

  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private AccountClient accountClient;
  @Inject NGSettingsClient settingsClient;
  @Inject GitXSettingsHelper gitXSettingsHelper;
  @Inject private TemplateRbacHelper templateRbacHelper;

  @Inject private GovernanceService governanceService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Template [%s] of versionLabel [%s] under Project[%s], Organization [%s] already exists";

  private static final int MAX_LIST_SIZE = 1000;

  private static final String TEMPLATE = "TEMPLATE";

  private static final String REPO_LIST_SIZE_EXCEPTION = "The size of unique repository list is greater than [%d]";

  @Override
  public TemplateEntity create(
      TemplateEntity templateEntity, boolean setStableTemplate, String comments, boolean isNewTemplate) {
    enforcementClientService.checkAvailability(
        FeatureRestrictionName.TEMPLATE_SERVICE, templateEntity.getAccountIdentifier());

    NGTemplateServiceHelper.validatePresenceOfRequiredFields(
        templateEntity.getAccountId(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());
    assureThatTheProjectAndOrgExists(
        templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier());
    if (TemplateUtils.remoteEnabledTemplateTypes.contains(templateEntity.getTemplateEntityType())) {
      applyGitXSettingsIfApplicable(
          templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier());
    }

    if (TemplateRefHelper.hasTemplateRef(templateEntity.getYaml())) {
      TemplateUtils.setupGitParentEntityDetails(templateEntity.getAccountIdentifier(),
          templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(), templateEntity.getRepo(),
          templateEntity.getConnectorRef());
    }

    if (!validateIdentifierIsUnique(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
            templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getVersionLabel())) {
      throw new InvalidRequestException(String.format(
          "The template with identifier %s and version label %s already exists in the account %s, org %s, project %s",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getAccountId(),
          templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier()));
    }

    if (isNewTemplate
        && validateIsNewTemplateIdentifier(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
            templateEntity.getProjectIdentifier(), templateEntity.getIdentifier())) {
      throw new TemplateAlreadyExistsException(String.format(
          "The template with identifier %s already exists in account %s, org %s, project %s, if you want to create a new version %s of this template then use save as new version option from the given template or if you want to create a new Template then use a different identifier.",
          templateEntity.getIdentifier(), templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), templateEntity.getVersionLabel()));
    }

    if (!isRemoteTemplateAndGitEntity(templateEntity)) {
      if (null != templateEntity.getOrgIdentifier() && null != templateEntity.getProjectIdentifier()) {
        throw new InvalidRequestException(format(
            "Remote template entity cannot be created for template type [%s] on git simplification enabled for Project [%s] in Organisation [%s] in Account [%s]",
            templateEntity.getTemplateEntityType(), templateEntity.getProjectIdentifier(),
            templateEntity.getOrgIdentifier(), templateEntity.getAccountIdentifier()));
      } else if (null != templateEntity.getOrgIdentifier()) {
        throw new InvalidRequestException(format(
            "Remote template entity cannot be created for template type [%s] on git simplification enabled in Organisation [%s] in Account [%s]",
            templateEntity.getTemplateEntityType(), templateEntity.getOrgIdentifier(),
            templateEntity.getAccountIdentifier()));
      } else {
        throw new InvalidRequestException(format(
            "Remote template entity cannot be created for template type [%s] on git simplification enabled in Account [%s]",
            templateEntity.getTemplateEntityType(), templateEntity.getAccountIdentifier()));
      }
    }

    checkForChildTypesInTemplates(templateEntity, "create");

    // apply templates to template yaml for validation and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);

    List<EntityDetailProtoDTO> referredEntities = templateReferenceHelper.calculateTemplateReferences(templateEntity);

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

      TemplateEntity template = null;

      if (!firstVersionEntry && setStableTemplate) {
        String finalComments = comments;
        template = transactionHelper.performTransaction(() -> {
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
        template = transactionHelper.performTransaction(() -> {
          makePreviousLastUpdatedTemplateFalse(finalTemplateEntity.getAccountIdentifier(),
              finalTemplateEntity.getOrgIdentifier(), finalTemplateEntity.getProjectIdentifier(),
              finalTemplateEntity.getIdentifier(), finalTemplateEntity.getVersionLabel());
          return saveTemplate(finalTemplateEntity, finalComments1);
        });
      }

      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfo();
      if (doPublishSetupUsages(template)) {
        templateReferenceHelper.publishTemplateReferences(
            SetupUsageParams.builder().templateEntity(templateEntity).build(), referredEntities);
      }

      return template;

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

  private boolean doPublishSetupUsages(TemplateEntity templateEntity) {
    boolean defaultBranchCheckForGitX = GitAwareContextHelper.getIsDefaultBranchFromGitEntityInfo();

    if (templateEntity.getStoreType() == null || templateEntity.getStoreType().equals(StoreType.INLINE)
        || (templateEntity.getStoreType() == StoreType.REMOTE && defaultBranchCheckForGitX)) {
      return true;
    }

    return false;
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
    TemplateMergeResponseDTO templateMergeResponseDTO = null;
    templateMergeResponseDTO = templateMergeService.applyTemplatesToYamlV2(templateEntity.getAccountId(),
        templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
        YamlUtils.readAsJsonNode(templateEntity.getYaml()), false, false, false);
    populateLinkedTemplatesModules(templateEntity, templateMergeResponseDTO);
    checkLinkedTemplateAccess(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier(), templateMergeResponseDTO);

    // validate schema on resolved yaml to validate template inputs value as well.
    ngTemplateSchemaService.validateYamlSchemaInternal(
        templateEntity.withYaml(templateMergeResponseDTO.getMergedPipelineYaml()));
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
    TemplateUtils.setupGitParentEntityDetails(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier(), templateEntity.getRepo(), templateEntity.getConnectorRef());
    // apply templates to template yaml for validations and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);
    // calculate the references, returns error if any errors occur while fetching references
    List<EntityDetailProtoDTO> referredEntities = templateReferenceHelper.calculateTemplateReferences(templateEntity);

    TemplateEntity template = null;

    template = transactionHelper.performTransaction(() -> {
      makePreviousLastUpdatedTemplateFalse(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());
      return updateTemplateHelper(templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
          templateEntity, changeType, setDefaultTemplate, true, comments, null);
    });

    GitAwareContextHelper.setIsDefaultBranchInGitEntityInfo();
    if (doPublishSetupUsages(template)) {
      templateReferenceHelper.publishTemplateReferences(
          SetupUsageParams.builder().templateEntity(templateEntity).build(), referredEntities);
    }

    return template;
  }

  @Override
  public TemplateEntity updateTemplateEntity(TemplateEntity templateEntity, ChangeType changeType,
      boolean setDefaultTemplate, String comments, TemplateResponseDTO templateResponse) {
    enforcementClientService.checkAvailability(
        FeatureRestrictionName.TEMPLATE_SERVICE, templateEntity.getAccountIdentifier());
    // apply templates to template yaml for validations and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);
    // calculate the references, returns error if any errors occur while fetching references
    List<EntityDetailProtoDTO> referredEntities = templateReferenceHelper.calculateTemplateReferences(templateEntity);

    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (gitEntityInfo != null) {
      if (templateResponse.getGitDetails() != null) {
        if (templateResponse.getGitDetails().getCommitId() != null) {
          gitEntityInfo.setLastCommitId(templateResponse.getGitDetails().getCommitId());
        }
        if (templateResponse.getGitDetails().getObjectId() != null) {
          gitEntityInfo.setLastObjectId(templateResponse.getGitDetails().getObjectId());
        }
        if (templateResponse.getGitDetails().getFilePath() != null) {
          gitEntityInfo.setFilePath(templateResponse.getGitDetails().getFilePath());
        }
        if (templateResponse.getGitDetails().getBranch() != null) {
          gitEntityInfo.setBranch(templateResponse.getGitDetails().getBranch());
        }
      }
    }

    TemplateEntity template = null;

    template = transactionHelper.performTransaction(() -> {
      makePreviousLastUpdatedTemplateFalse(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getVersionLabel());
      return updateTemplateHelper(templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
          templateEntity, changeType, setDefaultTemplate, true, comments, null);
    });

    GitAwareContextHelper.setIsDefaultBranchInGitEntityInfo();
    if (doPublishSetupUsages(template)) {
      templateReferenceHelper.publishTemplateReferences(
          SetupUsageParams.builder().templateEntity(templateEntity).build(), referredEntities);
    }

    return template;
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
                                            .withIcon(templateEntity.getIcon())
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
      String templateIdentifier, String versionLabel, boolean deleted, boolean loadFromCache) {
    return get(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, deleted, loadFromCache, false);
  }

  @Override
  public Optional<TemplateEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted, boolean loadFromCache,
      boolean loadFromFallbackBranch) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    try {
      Optional<TemplateEntity> templateOptional = templateServiceHelper.getTemplate(accountId, orgIdentifier,
          projectIdentifier, templateIdentifier, versionLabel, deleted, false, loadFromCache, loadFromFallbackBranch);
      if (templateOptional.isPresent() && StoreType.REMOTE.equals(templateOptional.get().getStoreType())) {
        TemplateEntity templateEntity = templateOptional.get();
        validateTemplateVersion(versionLabel, templateEntity);
      }

      return templateOptional;

    } catch (ExplanationException | HintException | ScmException e) {
      String errorMessage = getErrorMessage(templateIdentifier, versionLabel);
      log.error(errorMessage, e);
      throw e;
    } catch (Exception e) {
      String errorMessage = getErrorMessage(templateIdentifier, versionLabel);
      log.error(errorMessage, e);
      throw new InvalidRequestException(String.format("[%s]: %s", errorMessage, ExceptionUtils.getMessage(e)));
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
      String deleteVersionLabel, Long version, String comments, boolean forceDelete) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountId);
    List<TemplateEntity> templateEntities =
        getAllTemplatesForGivenIdentifier(accountId, orgIdentifier, projectIdentifier, templateIdentifier, false);

    TemplateEntity templateToDelete = null;
    TemplateEntity stableTemplate = null;
    if (forceDelete && !isForceDeleteEnabled(accountId)) {
      throw new InvalidRequestException(ErrorMessageConstants.FORCE_DELETE_SETTING_NOT_ENABLED, USER);
    }
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
        Collections.singletonList(templateToDelete), version, comments, templateEntities.size() == 1, stableTemplate,
        forceDelete);
  }

  @Override
  public boolean deleteTemplates(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, Set<String> deleteTemplateVersions, String comments, boolean forceDelete) {
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
        comments, canDeleteStableTemplate, stableTemplate, forceDelete);
  }

  private boolean isForceDeleteEnabled(String accountIdentifier) {
    return isForceDeleteFFEnabledViaSettings(accountIdentifier);
  }

  @VisibleForTesting
  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
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
      TemplateEntity stableTemplate, boolean forceDelete) {
    boolean lastUpdatedTemplateDeleted = false;
    boolean foundStableTemplate = false;
    for (TemplateEntity templateEntity : templateToDeleteList) {
      try (TemplateGitSyncBranchContextGuard ignored = templateServiceHelper.getTemplateGitContextForGivenTemplate(
               templateEntity, GitContextHelper.getGitEntityInfo(),
               format("Deleting template with identifier [%s] and versionLabel [%s].", templateEntity.getIdentifier(),
                   templateEntity.getVersionLabel()))) {
        // If it is stable template then we will delete it at the last
        if (templateEntity.isStableTemplate()) {
          foundStableTemplate = true;
          continue;
        }
        deleteSingleTemplateHelper(accountId, orgIdentifier, projectIdentifier, templateEntity.getIdentifier(),
            templateEntity, version, canDeleteStableTemplate, comments, forceDelete);
        if (templateEntity.isLastUpdatedTemplate()) {
          lastUpdatedTemplateDeleted = true;
        }
      } catch (Exception exception) {
        // if template to delete contains stable template along with all other versions with one template having
        // references, therefore removed !canDeleteStableTemplate from if condition.
        if (lastUpdatedTemplateDeleted) {
          makeGivenTemplateLastUpdatedTemplateTrue(stableTemplate);
        }
        throw exception;
      }
    }

    // Update stable Template as last updated template if the earlier lastUpdatedTemplate is Deleted.
    if (lastUpdatedTemplateDeleted) {
      makeGivenTemplateLastUpdatedTemplateTrue(stableTemplate);
    }

    // Delete Stable Template
    if (canDeleteStableTemplate && foundStableTemplate) {
      deleteSingleTemplateHelper(accountId, orgIdentifier, projectIdentifier, stableTemplate.getIdentifier(),
          stableTemplate, version, canDeleteStableTemplate, comments, forceDelete);
    }

    return true;
  }

  protected boolean deleteSingleTemplateHelper(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, TemplateEntity templateToDelete, Long version, boolean canDeleteStableTemplate,
      String comments, boolean forceDelete) {
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
    if (!forceDelete) {
      checkThatTheTemplateIsNotUsedByOthers(templateToDelete);
    }

    try {
      return templateServiceHelper.deleteTemplate(accountId, orgIdentifier, projectIdentifier, templateIdentifier,
          templateToDelete, versionLabel, comments, forceDelete);
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
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(templateToDelete.getAccountIdentifier())
                                      .orgIdentifier(templateToDelete.getOrgIdentifier())
                                      .projectIdentifier(templateToDelete.getProjectIdentifier())
                                      .identifier(templateToDelete.getIdentifier())
                                      .build();

    if (isTemplateEntityReferenced(identifierRef, templateToDelete.getAccountIdentifier(),
            templateToDelete.getIdentifier(), templateToDelete.getVersionLabel())) {
      throw new ReferencedEntityException(String.format(
          "Could not delete the template %s as it is referenced by other entities", templateToDelete.getIdentifier()));
    }
    if (templateToDelete.isStableTemplate()
        && isTemplateEntityReferenced(
            identifierRef, templateToDelete.getAccountIdentifier(), templateToDelete.getIdentifier(), STABLE_VERSION)) {
      throw new ReferencedEntityException(String.format(
          "Could not delete the template %s as it is referenced by other entities", templateToDelete.getIdentifier()));
    }
  }

  private boolean isTemplateEntityReferenced(
      IdentifierRef identifierRef, String accountId, String templateId, String versionLabel) {
    String referredEntityFQN = identifierRef.getFullyQualifiedName() + "/" + versionLabel + "/";
    boolean isEntityReferenced;
    try {
      isEntityReferenced = NGRestUtils.getResponse(
          entitySetupUsageClient.isEntityReferenced(accountId, referredEntityFQN, EntityType.TEMPLATE));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception.",
          templateId, ex);
      throw new UnexpectedException(
          String.format("Error while checking references for template %s with version label: %s : %s", templateId,
              versionLabel, ex.getMessage()));
    }
    return isEntityReferenced;
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

    return getRBACFilteredTemplates(
        accountIdentifier, orgIdentifier, projectIdentifier, criteria, pageable, filterParamsDTO);
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
        get(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, false);
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

      unSyncedTemplate.ifPresent(templateEntity
          -> templateReferenceHelper.populateTemplateReferences(
              SetupUsageParams.builder().templateEntity(templateEntity).build()));
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
  public boolean validateIsNewTemplateIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String templateIdentifier) {
    return templateRepository.existsByAccountIdAndOrgIdAndProjectIdAndIdentifierWithoutVersionLabel(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier);
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
      for (TemplateReferenceSummary templateReferenceSummary :
          templateMergeResponseDTO.getTemplateReferenceSummaries()) {
        String templateIdentifier = templateReferenceSummary.getTemplateIdentifier();
        Scope scope = templateReferenceSummary.getScope();
        String templateOrgIdentifier = null;
        String templateProjIdentifier = null;
        if (scope.equals(Scope.ORG)) {
          templateOrgIdentifier = orgId;
        } else if (scope.equals(Scope.PROJECT)) {
          templateOrgIdentifier = orgId;
          templateProjIdentifier = projectId;
        }
        accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, templateOrgIdentifier, templateProjIdentifier),
            Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_ACCESS_PERMISSION);
      }
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
          templateRepository.hardDeleteTemplateForOldGitSync(templateEntity, "", false);
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

  @Override
  public TemplateEntity importTemplateFromRemote(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, TemplateImportRequestDTO templateImportRequest,
      boolean isForceImport) {
    checkGitXEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
    String repoUrl = templateGitXService.checkForFileUniquenessAndGetRepoURL(
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, isForceImport);
    String importedTemplateYAML =
        templateGitXService.importTemplateFromRemote(accountIdentifier, orgIdentifier, projectIdentifier);
    templateGitXService.performImportFlowYamlValidations(
        orgIdentifier, projectIdentifier, templateIdentifier, templateImportRequest, importedTemplateYAML);
    TemplateEntity templateEntity =
        NGTemplateDtoMapper.toTemplateEntity(accountIdentifier, orgIdentifier, projectIdentifier, importedTemplateYAML);

    checkForChildTypesInTemplates(templateEntity, "import");

    TemplateEntity templateEntityToSave = prepareTemplateEntity(templateEntity, repoUrl);

    try {
      return transactionHelper.performTransaction(() -> {
        makePreviousLastUpdatedTemplateFalse(templateEntityToSave.getAccountIdentifier(),
            templateEntityToSave.getOrgIdentifier(), templateEntityToSave.getProjectIdentifier(),
            templateEntityToSave.getIdentifier(), templateEntityToSave.getVersionLabel());
        return templateRepository.importFlowSaveTemplate(templateEntityToSave, "");
      });

    } catch (DuplicateKeyException ex) {
      log.error(
          format(DUP_KEY_EXP_FORMAT_STRING, templateEntity.getIdentifier(), templateImportRequest.getTemplateVersion(),
              templateEntity.getProjectIdentifier(), templateEntity.getOrgIdentifier()),
          ex);
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, templateEntity.getIdentifier(), templateImportRequest.getTemplateVersion(),
              templateEntity.getProjectIdentifier(), templateEntity.getOrgIdentifier()),
          USER_SRE, ex);
    }
  }

  private void checkForChildTypesInTemplates(TemplateEntity templateEntity, String action) {
    Set<TemplateEntityType> templatesWithChildTypes = new HashSet<>();
    templatesWithChildTypes.add(TemplateEntityType.STAGE_TEMPLATE);
    templatesWithChildTypes.add(TemplateEntityType.STEP_TEMPLATE);
    templatesWithChildTypes.add(TemplateEntityType.STEPGROUP_TEMPLATE);
    String error = "";
    String actionType = action.equals("create") ? "save" : "import";
    if (templatesWithChildTypes.contains(templateEntity.getTemplateEntityType())
        && EmptyPredicate.isEmpty(templateEntity.getChildType())) {
      if (templateEntity.getTemplateEntityType() == TemplateEntityType.STEPGROUP_TEMPLATE) {
        error = "Unable to " + actionType + " the template. Missing property [stageType].";
      } else {
        error = "Unable to " + actionType + " the template. Missing property [type] for "
            + templateEntity.getTemplateEntityType().toString() + " template";
      }
      throw new InvalidRequestException(error);
    }
  }

  @Override
  public TemplateListRepoResponse getListOfRepos(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, boolean includeAllTemplatesAccessibleAtScope) {
    Criteria criteria = templateServiceHelper.formCriteriaForRepoListing(
        accountIdentifier, orgIdentifier, projectIdentifier, includeAllTemplatesAccessibleAtScope);
    List<String> uniqueRepos = templateRepository.getListOfRepos(criteria);
    CollectionUtils.filter(uniqueRepos, PredicateUtils.notNullPredicate());
    if (uniqueRepos.size() > MAX_LIST_SIZE) {
      log.error(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
      throw new InternalServerErrorException(String.format(REPO_LIST_SIZE_EXCEPTION, MAX_LIST_SIZE));
    }
    return TemplateListRepoResponse.builder().repositories(uniqueRepos).build();
  }

  private void checkGitXEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (!templateGitXService.isNewGitXEnabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
      if (projectIdentifier != null) {
        throw new InvalidRequestException(
            format("Remote git simplification was not enabled for Project [%s] in Organisation [%s] in Account [%s]",
                projectIdentifier, orgIdentifier, accountIdentifier));
      } else {
        throw new InvalidRequestException(
            format("Remote git simplification or feature flag was not enabled for Organisation [%s] or Account [%s]",
                orgIdentifier, accountIdentifier));
      }
    }
  }

  private TemplateEntity prepareTemplateEntity(TemplateEntity templateEntity, String repoUrl) {
    templateEntity.setRepoURL(repoUrl);
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    templateEntity.setStoreType(StoreType.REMOTE);
    templateEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    templateEntity.setRepo(gitEntityInfo.getRepoName());
    templateEntity.setFilePath(gitEntityInfo.getFilePath());
    templateEntity.setFallBackBranch(gitEntityInfo.getBranch());
    List<TemplateEntity> templates =
        getAllTemplatesForGivenIdentifier(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
            templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), false);
    boolean firstVersionEntry = EmptyPredicate.isEmpty(templates);
    if (firstVersionEntry) {
      templateEntity = templateEntity.withStableTemplate(true);
    } else {
      templateEntity = templateEntity.withStableTemplate(false);
    }
    templateEntity = templateEntity.withLastUpdatedTemplate(true);
    return templateEntity;
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
        accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, false, true, false, false);
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

  public PageResponse<EntitySetupUsageDTO> listTemplateReferences(int page, int size, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel, String searchTerm,
      boolean isStableTemplate) {
    PageResponse<EntitySetupUsageDTO> referredEntities;
    String referredEntityFQN =
        createFqnForTemplate(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel);
    if (isStableTemplate) {
      String referredEntityFQNForStableTemplate =
          createFqnForTemplate(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, "");
      referredEntities = NGRestUtils.getResponse(entitySetupUsageClient.listAllEntityUsageWith2Fqns(page, size,
          accountIdentifier, referredEntityFQN, referredEntityFQNForStableTemplate, EntityType.TEMPLATE, searchTerm));
    } else {
      referredEntities = NGRestUtils.getResponse(entitySetupUsageClient.listAllEntityUsage(
          page, size, accountIdentifier, referredEntityFQN, EntityType.TEMPLATE, searchTerm));
    }
    return referredEntities;
  }

  private String createFqnForTemplate(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel) {
    return String.format("%s/%s",
        FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier),
        EmptyPredicate.isNotEmpty(versionLabel) ? versionLabel + "/" : STABLE_VERSION + "/");
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
          accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, false, false, false);
      if (!optionalTemplate.isPresent()) {
        throw new InvalidRequestException(format(
            "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] doesn't exist.",
            accountId, versionLabel, projectIdentifier, orgIdentifier));
      }
      return optionalTemplate;
    }
  }

  TemplateEntity saveTemplate(TemplateEntity templateEntity, String comments) throws InvalidRequestException {
    if (templateServiceHelper.isOldGitSync(templateEntity)) {
      return templateRepository.saveForOldGitSync(templateEntity, comments);
    } else {
      return templateRepository.save(templateEntity, comments);
    }
  }

  private TemplateEntity getAndValidateOldTemplateEntity(
      TemplateEntity templateEntity, String oldOrgIdentifier, String oldProjectIdentifier) {
    TemplateUtils.setupGitParentEntityDetails(
        templateEntity.getAccountIdentifier(), oldOrgIdentifier, oldProjectIdentifier, null, null);
    Optional<TemplateEntity> optionalTemplate = templateServiceHelper.getTemplateWithVersionLabel(
        templateEntity.getAccountId(), oldOrgIdentifier, oldProjectIdentifier, templateEntity.getIdentifier(),
        templateEntity.getVersionLabel(), false, false, false, false);

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

    if (EmptyPredicate.isEmpty(oldTemplateEntity.getChildType())
        && EmptyPredicate.isNotEmpty(templateEntity.getChildType())) {
      return oldTemplateEntity.withChildType(templateEntity.getChildType());
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

  @Override
  public TemplateWithInputsResponseDTO getTemplateWithInputs(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean loadFromCache) {
    Optional<TemplateEntity> templateEntity =
        get(accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, loadFromCache);
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity.orElseThrow(
        ()
            -> new InvalidRequestException(String.format(

                "Template with the given Identifier: %s and %s does not exist or has been deleted", templateIdentifier,
                EmptyPredicate.isEmpty(versionLabel) ? "stable versionLabel" : "versionLabel: " + versionLabel))));
    String templateInputs = templateMergeServiceHelper.createTemplateInputsFromTemplate(templateEntity.get().getYaml());
    return TemplateWithInputsResponseDTO.builder()
        .templateInputs(templateInputs)
        .templateResponseDTO(templateResponseDTO)
        .build();
  }

  private boolean isRemoteTemplateAndGitEntity(TemplateEntity templateEntity) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null && TemplateUtils.isRemoteEntity(gitEntityInfo)) {
      return templateEntity.getTemplateEntityType().isGitEntity();
    } else {
      return true;
    }
  }

  private void validateTemplateVersion(String versionLabel, TemplateEntity templateEntity) {
    if (isNotBlank(templateEntity.getYaml())) {
      YamlField templateYamlField = TemplateUtils.getTemplateYamlFieldElseThrow(templateEntity.getOrgIdentifier(),
          templateEntity.getProjectIdentifier(), templateEntity.getIdentifier(), templateEntity.getYaml());

      String templateVersionFromGit =
          templateYamlField.getNode().getStringValue(YAMLFieldNameConstants.TEMPLATE_VERSION);
      if (EmptyPredicate.isNotEmpty(versionLabel) && EmptyPredicate.isNotEmpty(templateVersionFromGit)
          && !versionLabel.equals(templateVersionFromGit)) {
        throw new InvalidRequestException(format(
            "Template version from remote template file [%s] does not match with template version in request [%s]. Each template version maps to a unique file on Git. Create a new version through harness or import a new version if the file is already created on Git",
            templateVersionFromGit, versionLabel));
      }
    }
  }

  @Override
  public TemplateMoveConfigResponse moveTemplateStoreTypeConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, TemplateMoveConfigRequestDTO templateMoveConfigRequestDTO) {
    String versionLabel = templateMoveConfigRequestDTO.getVersionLabel();
    TemplateMoveConfigOperationDTO moveConfigOperationDTO =
        TemplateMoveConfigOperationDTO.builder()
            .repoName(templateMoveConfigRequestDTO.getRepoName())
            .branch(templateMoveConfigRequestDTO.getBranch())
            .moveConfigOperationType(getMoveConfigType(templateMoveConfigRequestDTO.getMoveConfigOperationType()))
            .connectorRef(templateMoveConfigRequestDTO.getConnectorRef())
            .baseBranch(templateMoveConfigRequestDTO.getBaseBranch())
            .commitMessage(templateMoveConfigRequestDTO.getCommitMsg())
            .isNewBranch(templateMoveConfigRequestDTO.getIsNewBranch())
            .filePath(templateMoveConfigRequestDTO.getFilePath())
            .build();

    Optional<TemplateEntity> templateEntityOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, false, false);

    if (templateEntityOptional.isPresent()) {
      if (!templateEntityOptional.get().getTemplateEntityType().isGitEntity()) {
        throw new InvalidRequestException(String.format(
            "Template with the given Identifier: %s and versionLabel %s cannot be moved to Git as it is not a Git Supported Template Type",
            templateIdentifier, versionLabel));
      }

      TemplateEntity movedTemplateEntity = moveTemplateEntity(accountIdentifier, orgIdentifier, projectIdentifier,
          templateIdentifier, versionLabel, moveConfigOperationDTO, templateEntityOptional.get());

      return TemplateMoveConfigResponse.builder()
          .templateIdentifier(movedTemplateEntity.getIdentifier())
          .versionLabel(movedTemplateEntity.getVersionLabel())
          .build();
    } else {
      throw new NotFoundException(
          String.format("Template with the given Identifier: %s and versionLabel %s does not exist or has been deleted",
              templateIdentifier, versionLabel));
    }
  }

  @Override
  public void updateGitDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, UpdateGitDetailsParams updateGitDetailsParams) {
    Criteria templateCriteria = Criteria.where(TemplateEntityKeys.accountId)
                                    .is(accountIdentifier)
                                    .and(TemplateEntityKeys.orgIdentifier)
                                    .is(orgIdentifier)
                                    .and(TemplateEntityKeys.projectIdentifier)
                                    .is(projectIdentifier)
                                    .and(TemplateEntityKeys.identifier)
                                    .is(templateIdentifier)
                                    .and(TemplateEntityKeys.versionLabel)
                                    .is(versionLabel)
                                    .and(TemplateEntityKeys.storeType)
                                    .is(StoreType.REMOTE);

    Update update = templateServiceHelper.getGitDetailsUpdate(updateGitDetailsParams);
    TemplateEntity templateEntity =
        templateRepository.updateV2(accountIdentifier, orgIdentifier, projectIdentifier, templateCriteria, update);
    if (templateEntity == null) {
      String scope =
          String.format("account %s%s%s", accountIdentifier, isNotEmpty(orgIdentifier) ? ", org " + orgIdentifier : "",
              isNotEmpty(projectIdentifier) ? ", project " + projectIdentifier : "");
      throw new EntityNotFoundException(
          String.format("Template not found for template identifier [%s] and version label [%s] in %s",
              templateIdentifier, versionLabel, scope));
    }
  }

  @Override
  public void populateSetupUsageAsync(TemplateEntity templateEntity) {
    if (templateEntity.getStoreType() == StoreType.REMOTE) {
      SetupUsageParams setupUsageParams = SetupUsageParams.builder().templateEntity(templateEntity).build();
      templateAsyncSetupUsageService.populateAsyncSetupUsage(setupUsageParams);
    }
  }

  @VisibleForTesting
  protected TemplateEntity moveTemplateEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, TemplateMoveConfigOperationDTO moveConfigOperationDTO,
      TemplateEntity templateEntity) {
    Criteria templateCriteria = Criteria.where(TemplateEntityKeys.accountId)
                                    .is(accountIdentifier)
                                    .and(TemplateEntityKeys.orgIdentifier)
                                    .is(orgIdentifier)
                                    .and(TemplateEntityKeys.projectIdentifier)
                                    .is(projectIdentifier)
                                    .and(TemplateEntityKeys.identifier)
                                    .is(templateIdentifier)
                                    .and(TemplateEntityKeys.versionLabel)
                                    .is(versionLabel)
                                    .and(TemplateEntityKeys.deleted)
                                    .is(false);

    Update templateUpdate;

    if (INLINE_TO_REMOTE.equals(moveConfigOperationDTO.getMoveConfigOperationType())) {
      setupGitContext(moveConfigOperationDTO);
      templateUpdate = templateServiceHelper.getTemplateUpdateForInlineToRemote(
          accountIdentifier, orgIdentifier, projectIdentifier, moveConfigOperationDTO);
    } else {
      throw new InvalidRequestException(String.format(
          "Invalid move config operation specified [%s].", moveConfigOperationDTO.getMoveConfigOperationType().name()));
    }
    return updateMoveConfigForTemplateEntity(
        templateEntity, templateUpdate, templateCriteria, moveConfigOperationDTO.getMoveConfigOperationType());
  }

  TemplateEntity updateMoveConfigForTemplateEntity(TemplateEntity templateEntity, Update templateUpdate,
      Criteria templateCriteria, TemplateMoveConfigOperationType moveConfigOperationType) {
    return transactionHelper.performTransaction(
        () -> moveConfigOperations(templateEntity, templateUpdate, templateCriteria, moveConfigOperationType));
  }

  TemplateEntity moveConfigOperations(TemplateEntity templateEntityToMove, Update templateUpdate,
      Criteria templateCriteria, TemplateMoveConfigOperationType moveConfigOperationType) {
    //   create file if inline to remote
    if (INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      createRemoteEntity(templateEntityToMove);
    }
    //    update the mongo db
    return updateTemplateConfig(templateEntityToMove.getAccountId(), templateEntityToMove.getOrgIdentifier(),
        templateEntityToMove.getProjectIdentifier(), templateCriteria, templateUpdate);
  }

  private TemplateEntity updateTemplateConfig(String accountId, String orgIdentifier, String projectIdentifier,
      Criteria templateCriteria, Update templateUpdate) {
    return templateRepository.updateV2(accountId, orgIdentifier, projectIdentifier, templateCriteria, templateUpdate);
  }

  private ScmCreateFileGitResponse createRemoteEntity(TemplateEntity templateEntityToMove) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    io.harness.beans.Scope scope = io.harness.beans.Scope.of(templateEntityToMove.getAccountIdentifier(),
        templateEntityToMove.getOrgIdentifier(), templateEntityToMove.getProjectIdentifier());
    String yamlToPush = templateEntityToMove.getYaml();
    addGitParamsToTemplateEntity(templateEntityToMove, gitEntityInfo);

    return gitAwareEntityHelper.createEntityOnGit(templateEntityToMove, yamlToPush, scope);
  }

  private void addGitParamsToTemplateEntity(TemplateEntity templateEntityToMove, GitEntityInfo gitEntityInfo) {
    templateEntityToMove.setStoreType(StoreType.REMOTE);
    if (EmptyPredicate.isEmpty(templateEntityToMove.getRepoURL())) {
      templateEntityToMove.setRepoURL(gitAwareEntityHelper.getRepoUrl(templateEntityToMove.getAccountId(),
          templateEntityToMove.getOrgIdentifier(), templateEntityToMove.getProjectIdentifier()));
    }
    templateEntityToMove.setConnectorRef(gitEntityInfo.getConnectorRef());
    templateEntityToMove.setRepo(gitEntityInfo.getRepoName());
    templateEntityToMove.setFilePath(gitEntityInfo.getFilePath());
    templateEntityToMove.setFallBackBranch(gitEntityInfo.getBranch());
  }

  private void setupGitContext(TemplateMoveConfigOperationDTO moveConfigDTO) {
    GitAwareContextHelper.populateGitDetails(
        GitEntityInfo.builder()
            .branch(moveConfigDTO.getBranch())
            .filePath(moveConfigDTO.getFilePath())
            .commitMsg(moveConfigDTO.getCommitMessage())
            .isNewBranch(isNotEmpty(moveConfigDTO.getBranch()) && isNotEmpty(moveConfigDTO.getBaseBranch()))
            .baseBranch(moveConfigDTO.getBaseBranch())
            .connectorRef(moveConfigDTO.getConnectorRef())
            .storeType(StoreType.REMOTE)
            .repoName(moveConfigDTO.getRepoName())
            .build());
  }
  private boolean hasViewPermissionForAll(String accountId, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of("TEMPLATE", null), TEMPLATE_VIEW_PERMISSION);
  }
  private Page<TemplateEntity> getRBACFilteredTemplates(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Criteria criteria, Pageable pageable, FilterParamsDTO filterParamsDTO) {
    Page<TemplateEntity> templateEntities;
    if (hasViewPermissionForAll(accountIdentifier, orgIdentifier, projectIdentifier)) {
      templateEntities = templateServiceHelper.listTemplate(accountIdentifier, orgIdentifier, projectIdentifier,
          criteria, pageable, filterParamsDTO.isGetDistinctFromBranches());

    } else {
      Page<TemplateEntity> templateEntityPage = templateServiceHelper.listTemplate(accountIdentifier, orgIdentifier,
          projectIdentifier, criteria, Pageable.unpaged(), filterParamsDTO.isGetDistinctFromBranches());
      if (templateEntityPage == null) {
        return Page.empty();
      }
      List<TemplateEntity> templateEntityList = templateEntityPage.getContent();
      templateEntityList = templateRbacHelper.getPermittedTemplateList(templateEntityList);
      if (isEmpty(templateEntityList)) {
        return Page.empty();
      }
      populateInFilter(criteria, TemplateEntityKeys.identifier,
          templateEntityList.stream().map(TemplateEntity::getIdentifier).collect(toList()));

      templateEntities = templateServiceHelper.listTemplate(accountIdentifier, orgIdentifier, projectIdentifier,
          criteria, Pageable.unpaged(), filterParamsDTO.isGetDistinctFromBranches());
    }
    return templateEntities;
  }

  @VisibleForTesting
  void applyGitXSettingsIfApplicable(String accountIdentifier, String orgIdentifier, String projIdentifier) {
    gitXSettingsHelper.enforceGitExperienceIfApplicable(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setConnectorRefForRemoteEntity(accountIdentifier, orgIdentifier, projIdentifier);
    gitXSettingsHelper.setDefaultStoreTypeForEntities(
        accountIdentifier, orgIdentifier, projIdentifier, EntityType.TEMPLATE);
  }

  @Override
  public GovernanceMetadata validateGovernanceRules(TemplateEntity templateEntity) {
    if (!pmsFeatureFlagService.isEnabled(templateEntity.getAccountId(), FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE)) {
      return GovernanceMetadata.newBuilder()
          .setDeny(false)
          .setMessage(String.format("FF: [%s] is disabled for account: [%s]", FeatureName.CDS_OPA_TEMPLATE_GOVERNANCE,
              templateEntity.getAccountId()))
          .build();
    }
    return governanceService.evaluateGovernancePoliciesForTemplate(templateEntity.getYaml(),
        templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
        OpaConstants.OPA_EVALUATION_ACTION_SAVE, OpaConstants.OPA_EVALUATION_TYPE_TEMPLATE);
  }
}