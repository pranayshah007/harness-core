/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.template.resources.beans.NGTemplateConstants.DEFAULT_BRANCH;
import static io.harness.template.resources.beans.NGTemplateConstants.FILE_ADDED;
import static io.harness.template.resources.beans.NGTemplateConstants.FILE_MODIFIED;
import static io.harness.template.resources.beans.NGTemplateConstants.HEAD_COMMITS;
import static io.harness.template.resources.beans.NGTemplateConstants.LIST_PARTITION;
import static io.harness.template.resources.beans.NGTemplateConstants.NAME;
import static io.harness.template.resources.beans.NGTemplateConstants.READ_ME_EXTENSION;
import static io.harness.template.resources.beans.NGTemplateConstants.REPOSITORY;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.FetchRemoteEntityRequest;
import io.harness.gitaware.dto.GetFileGitContextRequestParams;
import io.harness.gitaware.dto.GetFileGitContextRequestParams.GetFileGitContextRequestParamsBuilder;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmGetBatchFilesResponse;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.yaml.YamlUtils;
import io.harness.project.remote.ProjectClient;
import io.harness.repositories.NGGlobalTemplateRepository;
import io.harness.springdata.TransactionHelper;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.GlobalTemplateEntity.GlobalTemplateEntityKeys;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.helpers.TemplateMergeServiceHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.FilterParamsDTO;
import io.harness.template.resources.beans.PageParamsDTO;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.utils.TemplateUtils;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY, HarnessModuleComponent.CDS_GITX,
        HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGGlobalTemplateServiceImpl implements NGGlobalTemplateService {
  @Inject private NGGlobalTemplateRepository ngGlobalTemplateRepository;
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

  @Inject private GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private AccountClient accountClient;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "Template [%s] of versionLabel [%s] under Project[%s], Organization [%s] already exists";

  private static final String TEMPLATE = "TEMPLATE";

  /*
  This method will parse the webhook event and create a list of file path which is added and list of file path which
  is modifies in the commit.
  createGlobalTemplate method will read the file content of newly created filePaths and create template.
  updateGlobalTemplate method will read the file content of modified filePaths and update template.
   */
  @Override
  public List<TemplateWrapperResponseDTO> createUpdateGlobalTemplate(String accountId, String connectorRef,
      Map<String, Object> webhookEvent, String comments, String orgIdentifier, String projectIdentifier) {
    HashMap<String, Object> repository = (HashMap<String, Object>) webhookEvent.get(REPOSITORY);
    String repoName = repository.get(NAME).toString();
    String branch = repository.get(DEFAULT_BRANCH).toString();
    ArrayList<String> filePaths = fetchFilesDetails(webhookEvent, FILE_ADDED);
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = Collections.emptyList();
    if (EmptyPredicate.isNotEmpty(filePaths)) {
      templateWrapperResponseDTOS = createGlobalTemplate(
          accountId, orgIdentifier, projectIdentifier, repoName, branch, connectorRef, filePaths, comments);
    }
    filePaths = fetchFilesDetails(webhookEvent, FILE_MODIFIED);
    if (EmptyPredicate.isNotEmpty(filePaths)) {
      templateWrapperResponseDTOS = updateGlobalTemplate(
          accountId, orgIdentifier, projectIdentifier, repoName, branch, filePaths, comments, connectorRef);
    }
    return templateWrapperResponseDTOS;
  }

  /*
  Get the git content of modified file Path and call update entity.
  Update the existing Global template
   */
  private List<TemplateWrapperResponseDTO> updateGlobalTemplate(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, String branch, ArrayList<String> filePaths, String comments,
      String connectorRef) {
    Map<String, GlobalTemplateEntity> globalTemplateEntities = getGitContentForGlobalTemplates(
        accountId, filePaths, repoName, branch, connectorRef, false, orgIdentifier, projectIdentifier);
    return updateGlobalTemplate(accountId, globalTemplateEntities, comments);
  }

  private List<TemplateWrapperResponseDTO> updateGlobalTemplate(
      String accountId, Map<String, GlobalTemplateEntity> globalTemplateEntities, String comments) {
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = new ArrayList<>();
    globalTemplateEntities.forEach((filePath, globalTemplateEntity) -> {
      if (validateFileIsYamlType(filePath)) {
        globalTemplateEntity = NGTemplateDtoMapper.toGlobalTemplateEntity(accountId, globalTemplateEntity.getYaml(),
            globalTemplateEntity.getReadMe(), globalTemplateEntity.getFilePath());
        templateWrapperResponseDTOS.add(updateGlobalTemplateEntityDto(globalTemplateEntity, comments));
      }
      if (validateReadMeFile(filePath)) {
        String[] path = filePath.split("/");
        Optional<GlobalTemplateEntity> optionalTemplateEntity =
            ngGlobalTemplateRepository.findByFilePath(filePath.replace(path[path.length - 1], ""));
        if (optionalTemplateEntity.isPresent()) {
          globalTemplateEntity =
              NGTemplateDtoMapper.toGlobalTemplateEntity(accountId, optionalTemplateEntity.get().getYaml(),
                  globalTemplateEntity.getReadMe(), optionalTemplateEntity.get().getFilePath());
          templateWrapperResponseDTOS.add(updateGlobalTemplateEntityDto(globalTemplateEntity, comments));
        }
      }
    });
    return templateWrapperResponseDTOS;
  }

  private TemplateWrapperResponseDTO updateGlobalTemplateEntityDto(
      GlobalTemplateEntity globalTemplateEntity, String comments) {
    GlobalTemplateEntity updateTemplate = updateTemplateEntity(globalTemplateEntity, ChangeType.MODIFY, true, comments);
    return TemplateWrapperResponseDTO.builder()
        .isValid(true)
        .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(updateTemplate))
        .build();
  }

  /*
  Validating the Yaml schema and performing update call.
   */
  public GlobalTemplateEntity updateTemplateEntity(
      GlobalTemplateEntity templateEntity, ChangeType changeType, boolean setDefaultTemplate, String comments) {
    // apply templates to template yaml for validations and populating module info
    applyTemplatesToYamlAndValidateSchema(templateEntity);
    // calculate the references, returns error if any errors occur while fetching references

    GlobalTemplateEntity template;

    template = transactionHelper.performTransaction(
        () -> updateTemplateHelper(templateEntity, changeType, setDefaultTemplate, comments, null));

    GitAwareContextHelper.setIsDefaultBranchInGitEntityInfo();
    return template;
  }

  /*
  Update the existing Global template
   */
  private GlobalTemplateEntity updateTemplateHelper(GlobalTemplateEntity globalTemplateEntity, ChangeType changeType,
      boolean updateLastUpdatedTemplateFlag, String comments, TemplateUpdateEventType eventType) {
    try {
      NGTemplateServiceHelper.validatePresenceOfRequiredFields(globalTemplateEntity.getAccountId(),
          globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel());
      GlobalTemplateEntity oldTemplateEntity = getAndValidateOldTemplateEntity(globalTemplateEntity);

      GlobalTemplateEntity templateToUpdate =
          oldTemplateEntity.withYaml(globalTemplateEntity.getYaml())
              .withTemplateScope(globalTemplateEntity.getTemplateScope())
              .withName(globalTemplateEntity.getName())
              .withDescription(globalTemplateEntity.getDescription())
              .withTags(globalTemplateEntity.getTags())
              .withIcon(globalTemplateEntity.getIcon())
              .withFullyQualifiedIdentifier(globalTemplateEntity.getFullyQualifiedIdentifier())
              .withLastUpdatedTemplate(updateLastUpdatedTemplateFlag)
              .withReadMe(globalTemplateEntity.getReadMe())
              .withIsEntityInvalid(false);

      return makeTemplateUpdateCall(templateToUpdate, oldTemplateEntity, changeType, comments,
          eventType != null ? eventType : TemplateUpdateEventType.OTHERS_EVENT, false);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format("Template [%s] of versionLabel [%s]", globalTemplateEntity.getIdentifier(),
              globalTemplateEntity.getVersionLabel()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while updating template [%s] of versionLabel [%s]",
                    globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving template [%s] of versionLabel [%s]",
                    globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel()),
          e);
      throw new InvalidRequestException(
          String.format("Error while saving template [%s] of versionLabel [%s] : [%s]",
              globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel(), e.getMessage()),
          e);
    }
  }

  /*
  Validating the Template with given identifier or label exist
  Validating the Template with given identifier and new version is of same template Entity type
   */
  private GlobalTemplateEntity getAndValidateOldTemplateEntity(GlobalTemplateEntity templateEntity) {
    Optional<GlobalTemplateEntity> optionalTemplate = getGlobalTemplateWithVersionLabel(
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), false, false, false, false);

    if (!optionalTemplate.isPresent()) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s] under Project[%s], Organization [%s] doesn't exist.",
          templateEntity.getIdentifier(), templateEntity.getVersionLabel(), templateEntity.getProjectIdentifier(),
          templateEntity.getOrgIdentifier()));
    }
    GlobalTemplateEntity oldTemplateEntity = optionalTemplate.get();
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

  public Optional<GlobalTemplateEntity> getGlobalTemplateWithVersionLabel(String templateIdentifier,
      String versionLabel, boolean deleted, boolean getMetadataOnly, boolean loadFromCache,
      boolean loadFromFallbackBranch) {
    return ngGlobalTemplateRepository.getGlobalEntityUsingVersionLabel(null, null, null, templateIdentifier,
        versionLabel, !deleted, getMetadataOnly, loadFromCache, loadFromFallbackBranch);
  }

  /*
  Read the list contains newly added filePath
  Get the Git content and call create method.
   */
  private List<TemplateWrapperResponseDTO> createGlobalTemplate(String accountId, String orgIdentifier,
      String projectIdentifier, String repoName, String branch, String connectorRef, ArrayList<String> filePaths,
      String comments) {
    List<TemplateWrapperResponseDTO> templateWrapperResponseDTOS = new ArrayList<>();
    Map<String, GlobalTemplateEntity> globalTemplateEntities = getGitContentForGlobalTemplates(
        accountId, filePaths, repoName, branch, connectorRef, false, orgIdentifier, projectIdentifier);
    globalTemplateEntities.forEach((filePath, globalTemplateEntity) -> {
      if (validateFileIsYamlType(filePath)) {
        globalTemplateEntity = NGTemplateDtoMapper.toGlobalTemplateEntity(accountId, globalTemplateEntity.getYaml(),
            globalTemplateEntity.getReadMe(), globalTemplateEntity.getFilePath());
        GlobalTemplateEntity createdTemplate = create(globalTemplateEntity, true, comments);
        TemplateWrapperResponseDTO templateWrapperResponseDTO =
            TemplateWrapperResponseDTO.builder()
                .isValid(true)
                .templateResponseDTO(NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate))
                .build();
        templateWrapperResponseDTOS.add(templateWrapperResponseDTO);
      }
    });
    return templateWrapperResponseDTOS;
  }

  public GlobalTemplateEntity makeTemplateUpdateCall(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    return makeUpdateCall(
        templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits, true);
  }

  /*
  Update call to db
   */
  private GlobalTemplateEntity makeUpdateCall(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits, boolean makeOnlyDbUpdate) {
    try {
      GlobalTemplateEntity updatedTemplate = null;

      if (makeOnlyDbUpdate) {
        updatedTemplate = ngGlobalTemplateRepository.updateTemplateInDb(
            templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
      }

      if (updatedTemplate == null) {
        throw new InvalidRequestException(format(
            "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] could not be updated.",
            templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
            templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()));
      }

      return updatedTemplate;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw new InvalidRequestException(String.format(
          "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] : %s",
          templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(), templateToUpdate.getProjectIdentifier(),
          templateToUpdate.getOrgIdentifier(), e.getMessage()));
    }
  }

  /*
  Fetching the Git Content in batches for file Paths and storing into Map<String, FetchRemoteEntityRequest>.
  Map Fetch entities to Map<String, GlobalTemplateEntity>. Map key will be the filePath and corresponding value will be
  the file content.
   */

  public Map<String, GlobalTemplateEntity> getGitContentForGlobalTemplates(String accountId, List<String> filePathList,
      String repoName, String branch, String connectorRef, boolean loadFromCache, String orgIdentifier,
      String projectIdentifier) {
    Map<String, GlobalTemplateEntity> batchTemplateEntities = new HashMap<>();
    GetFileGitContextRequestParamsBuilder getFileGitContextRequestParams =
        GetFileGitContextRequestParams.builder()
            .connectorRef(connectorRef)
            .repoName(repoName)
            .getOnlyFileContent(TemplateUtils.isExecutionFlow())
            .entityType(EntityType.TEMPLATE)
            .loadFromCache(loadFromCache)
            .branchName(branch);
    for (List<String> filePaths : Lists.partition(filePathList, LIST_PARTITION)) {
      Map<String, FetchRemoteEntityRequest> fetchRemoteEntityRequestMap = new HashMap<>();
      filePaths.forEach(filePath
          -> fetchRemoteEntityRequestMap.put(filePath,
              FetchRemoteEntityRequest.builder()
                  .contextMap(Collections.emptyMap())
                  .entity(GlobalTemplateEntity.builder().accountId(accountId).build())
                  .scope(io.harness.beans.Scope.of(accountId, orgIdentifier, projectIdentifier))
                  .getFileGitContextRequestParams(getFileGitContextRequestParams.filePath(filePath).build())
                  .build()));
      ScmGetBatchFilesResponse scmGetBatchFilesResponse =
          gitAwareEntityHelper.fetchEntitiesFromRemoteIncludingReadMeFile(accountId, fetchRemoteEntityRequestMap);
      batchTemplateEntities.putAll(processScmGetBatchFilesWithReadMe(
          scmGetBatchFilesResponse.getBatchFilesResponse(), fetchRemoteEntityRequestMap));
    }
    return batchTemplateEntities;
  }

  /*
  Here we will be processing the BatchFilesResponse Map and create GlobalTemplateEntity.
   */
  private Map<String, GlobalTemplateEntity> processScmGetBatchFilesWithReadMe(
      Map<String, ScmGetFileResponse> getBatchFilesResponse,
      Map<String, FetchRemoteEntityRequest> remoteTemplatesList) {
    Map<String, GlobalTemplateEntity> batchFilesResponse = new HashMap<>();

    // Processing the Template yaml and adding corresponding readMe to GlobalTemplate Entity
    getBatchFilesResponse.forEach((identifier, scmGetFileResponse) -> {
      GlobalTemplateEntity globalTemplateEntity =
          (GlobalTemplateEntity) remoteTemplatesList.get(identifier).getEntity();
      String filePath = scmGetFileResponse.getGitMetaData().getFilePath();
      if (filePath.endsWith(".yaml") || filePath.endsWith(".yml")) {
        globalTemplateEntity.setData(scmGetFileResponse.getFileContent());
        String[] path = filePath.split("/");
        String readMeFile = filePath.replace(path[path.length - 1], READ_ME_EXTENSION);
        globalTemplateEntity.setFilePath(filePath.replace(path[path.length - 1], ""));
        if (getBatchFilesResponse.containsKey(readMeFile)) {
          globalTemplateEntity.setReadMe(getBatchFilesResponse.get(readMeFile).getFileContent());
        }
        batchFilesResponse.put(identifier, globalTemplateEntity);
      }

      if (filePath.endsWith("README.md")) {
        globalTemplateEntity.setReadMe(scmGetFileResponse.getFileContent());
        batchFilesResponse.put(identifier, globalTemplateEntity);
      }
    });
    return batchFilesResponse;
  }

  /*
  Create Global Template Entity
   */

  public GlobalTemplateEntity create(
      GlobalTemplateEntity globalTemplateEntity, boolean setStableTemplate, String comments) {
    NGTemplateServiceHelper.validatePresenceOfRequiredFields(globalTemplateEntity.getAccountId(),
        globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel());
    assureThatTheProjectAndOrgExists(globalTemplateEntity.getAccountId(), globalTemplateEntity.getOrgIdentifier(),
        globalTemplateEntity.getProjectIdentifier());

    if (TemplateRefHelper.hasTemplateRef(globalTemplateEntity.getYaml())) {
      TemplateUtils.setupGitParentEntityDetails(globalTemplateEntity.getAccountIdentifier(),
          globalTemplateEntity.getOrgIdentifier(), globalTemplateEntity.getProjectIdentifier(),
          globalTemplateEntity.getRepo(), globalTemplateEntity.getConnectorRef());
    }

    if (!validateGlobalIdentifierIsUnique(
            globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel())) {
      throw new InvalidRequestException(
          String.format("The template with identifier %s and version label %s already exists.",
              globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel()));
    }

    checkForChildTypesInTemplates(globalTemplateEntity, "create");

    // apply templates to template yaml for validation and populating module info
    applyTemplatesToYamlAndValidateSchema(globalTemplateEntity);

    try {
      // Check if this is template identifier first entry, for marking it as stable template.
      List<GlobalTemplateEntity> templates = getAllGlobalTemplatesForGivenIdentifier(
          globalTemplateEntity.getAccountId(), globalTemplateEntity.getIdentifier(), false);
      boolean firstVersionEntry = EmptyPredicate.isEmpty(templates);
      validateTemplateTypeAndChildTypeOfTemplate(globalTemplateEntity, templates);
      if (firstVersionEntry || setStableTemplate) {
        globalTemplateEntity = globalTemplateEntity.withStableTemplate(true);
      }

      // a new template creation always means this is now the lastUpdated template.
      globalTemplateEntity = globalTemplateEntity.withLastUpdatedTemplate(true);

      // check to make previous template stable as false
      GlobalTemplateEntity finalTemplateEntity = globalTemplateEntity;

      GlobalTemplateEntity template;

      if (!firstVersionEntry && setStableTemplate) {
        String finalComments = comments;
        template = transactionHelper.performTransaction(() -> {
          makePreviousStableGlobalTemplateFalse(finalTemplateEntity.getAccountIdentifier(),
              finalTemplateEntity.getIdentifier(), finalTemplateEntity.getVersionLabel());
          return saveTemplate(finalTemplateEntity, finalComments);
        });
      } else {
        return saveTemplate(finalTemplateEntity, comments);
      }

      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfo();
      return template;

    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, globalTemplateEntity.getIdentifier(),
              globalTemplateEntity.getVersionLabel(), globalTemplateEntity.getProjectIdentifier(),
              globalTemplateEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while creating template [%s] of versionLabel [%s]",
                    globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while saving template [%s] of versionLabel [%s]",
                    globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel()),
          e);
      throw new InvalidRequestException(String.format("Error while saving template [%s] of versionLabel [%s]: %s",
          globalTemplateEntity.getIdentifier(), globalTemplateEntity.getVersionLabel(), e.getMessage()));
    }
  }

  private void validateTemplateTypeAndChildTypeOfTemplate(
      GlobalTemplateEntity newTemplateEntity, List<GlobalTemplateEntity> templates) {
    if (EmptyPredicate.isNotEmpty(templates)) {
      templates.forEach(existingTemplateEntity -> {
        if (!Objects.equals(
                existingTemplateEntity.getTemplateEntityType(), newTemplateEntity.getTemplateEntityType())) {
          throw NestedExceptionUtils.hintWithExplanationException(
              String.format(
                  "Failed to save the template [%s] because an existing template of different type has the same identifier",
                  newTemplateEntity.getIdentifier(), newTemplateEntity.getVersionLabel()),
              String.format(
                  "Template identifier [%s] exists. You cannot save a template of different type with the same identifier.",
                  newTemplateEntity.getIdentifier(), newTemplateEntity.getName()),
              new InvalidRequestException("Failed to save the template."));
        }
        if (!Objects.equals(existingTemplateEntity.getChildType(), newTemplateEntity.getChildType())) {
          throw new InvalidRequestException(
              String.format("Template should have same child type %s as other template versions",
                  existingTemplateEntity.getChildType()));
        }
      });
    }
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

  private void applyTemplatesToYamlAndValidateSchema(GlobalTemplateEntity templateEntity) {
    TemplateMergeResponseDTO templateMergeResponseDTO = null;
    String yamlVersion = templateEntity.getHarnessVersion();
    templateMergeResponseDTO = templateMergeService.applyTemplatesToYamlV2(templateEntity.getAccountId(),
        templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(),
        YamlUtils.readAsJsonNode(templateEntity.getYaml()), false, false, false, yamlVersion);
    populateLinkedTemplatesModules(templateEntity, templateMergeResponseDTO);
    checkLinkedTemplateAccess(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier(), templateMergeResponseDTO);

    // validate schema on resolved yaml to validate template inputs value as well.
    ngTemplateSchemaService.validateYamlSchemaInternal(
        templateEntity.withYaml(templateMergeResponseDTO.getMergedPipelineYaml()));
  }

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

  private void populateLinkedTemplatesModules(
      GlobalTemplateEntity globalTemplateEntity, TemplateMergeResponseDTO templateMergeResponseDTO) {
    if (EmptyPredicate.isNotEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      Set<String> templateModules = EmptyPredicate.isNotEmpty(globalTemplateEntity.getModules())
          ? globalTemplateEntity.getModules()
          : new HashSet<>();
      templateMergeResponseDTO.getTemplateReferenceSummaries().forEach(templateReferenceSummary -> {
        if (EmptyPredicate.isNotEmpty(templateReferenceSummary.getModuleInfo())) {
          templateModules.addAll(templateReferenceSummary.getModuleInfo());
        }
      });
      globalTemplateEntity.setModules(templateModules);
    }
  }

  GlobalTemplateEntity saveTemplate(GlobalTemplateEntity templateEntity, String comments)
      throws InvalidRequestException {
    return ngGlobalTemplateRepository.save(templateEntity, comments);
  }

  private List<GlobalTemplateEntity> getAllGlobalTemplatesForGivenIdentifier(
      String accountId, String templateIdentifier, Boolean getDistinctFromBranches) {
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO("", "", null,
        NGTemplateDtoMapper.toTemplateFilterProperties(
            TemplateFilterPropertiesDTO.builder()
                .templateIdentifiers(Collections.singletonList(templateIdentifier))
                .build()),
        false, getDistinctFromBranches);
    PageParamsDTO pageParamsDTO = NGTemplateDtoMapper.preparePageParamsDTO(0, 1000, new ArrayList<>());

    return listTemplateMetadata(accountId, filterParamsDTO, pageParamsDTO).getContent();
  }

  public Page<GlobalTemplateEntity> listTemplateMetadata(
      String accountIdentifier, FilterParamsDTO filterParamsDTO, PageParamsDTO pageParamsDTO) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.TEMPLATE_SERVICE, accountIdentifier);
    Criteria criteria = new Criteria();
    criteria.and(GlobalTemplateEntityKeys.identifier).is(filterParamsDTO.getFilterIdentifier());

    // Adding criteria needed for ui homepage
    if (filterParamsDTO.getTemplateListType() != null) {
      criteria = formCriteria(criteria, filterParamsDTO.getTemplateListType());
    }
    Pageable pageable;
    if (EmptyPredicate.isEmpty(pageParamsDTO.getSort())) {
      pageable = PageRequest.of(pageParamsDTO.getPage(), pageParamsDTO.getSize(),
          Sort.by(Sort.Direction.DESC, TemplateEntityKeys.lastUpdatedAt));
    } else {
      pageable = PageUtils.getPageRequest(pageParamsDTO.getPage(), pageParamsDTO.getSize(), pageParamsDTO.getSort());
    }

    return ngGlobalTemplateRepository.findAll(accountIdentifier, criteria, pageable);
  }

  public Criteria formCriteria(Criteria criteria, TemplateListType templateListType) {
    if (templateListType.equals(TemplateListType.LAST_UPDATED_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    } else if (templateListType.equals(TemplateListType.STABLE_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isStableTemplate).is(true);
    }
    return criteria;
  }
  private void makePreviousStableGlobalTemplateFalse(
      String accountIdentifier, String templateIdentifier, String updatedStableTemplateVersion) {
    NGTemplateServiceHelper.validatePresenceOfRequiredFields(accountIdentifier, templateIdentifier);
    Optional<GlobalTemplateEntity> optionalTemplateEntity = getStableTemplate(templateIdentifier, false, false);
    if (optionalTemplateEntity.isPresent()) {
      // make previous stable template as false.
      GlobalTemplateEntity oldTemplate = optionalTemplateEntity.get();
      if (updatedStableTemplateVersion.equals(oldTemplate.getVersionLabel())) {
        log.info(
            "Ignoring marking previous stable template as false, as new versionLabel given is same as already existing one.");
        return;
      }
      ngGlobalTemplateRepository.updateIsStableTemplate(oldTemplate, false);

      // Update the git context with details of the template on which the operation is going to run.
    } else {
      log.info(format(
          "Requested template entity with identifier [%s] not found in account [%s], hence the update call is ignored",
          templateIdentifier, accountIdentifier));
    }
  }

  public Optional<GlobalTemplateEntity> getStableTemplate(
      String templateIdentifier, boolean deleted, boolean getMetadataOnly) {
    return ngGlobalTemplateRepository.findGlobalTemplateByIdentifierAndIsStableAndDeletedNot(
        templateIdentifier, !deleted, getMetadataOnly);
  }

  private void checkForChildTypesInTemplates(GlobalTemplateEntity templateEntity, String action) {
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

  public boolean validateIsNewGlobalTemplateIdentifier(String templateIdentifier) {
    return ngGlobalTemplateRepository.globalTemplateExistByIdentifierWithoutVersionLabel(templateIdentifier);
  }

  public boolean validateGlobalIdentifierIsUnique(String templateIdentifier, String versionLabel) {
    return !ngGlobalTemplateRepository.globalTemplateExistByIdentifierAndVersionLabel(templateIdentifier, versionLabel);
  }

  private ArrayList<String> fetchFilesDetails(Map<String, Object> webhookEvent, String actionType) {
    HashMap<String, Object> map = (HashMap<String, Object>) webhookEvent.get(HEAD_COMMITS);
    return (ArrayList) map.get(actionType);
  }

  public boolean validateFileIsYamlType(String filePath) {
    if (filePath.endsWith(".yaml") || filePath.endsWith(".yml")) {
      return true;
    }
    return false;
  }

  public boolean validateReadMeFile(String filePath) {
    if (filePath.endsWith(".md")) {
      return true;
    }
    return false;
  }
}
