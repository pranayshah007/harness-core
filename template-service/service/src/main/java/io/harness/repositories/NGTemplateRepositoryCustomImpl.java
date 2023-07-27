/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateCreateEvent;
import io.harness.template.events.TemplateDeleteEvent;
import io.harness.template.events.TemplateForceDeleteEvent;
import io.harness.template.events.TemplateUpdateEvent;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.services.TemplateGitXService;
import io.harness.template.utils.TemplateUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDC)
public class NGTemplateRepositoryCustomImpl implements NGTemplateRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final MongoTemplate mongoTemplate;
  private final TemplateGitXService templateGitXService;
  OutboxService outboxService;

  @Override
  public TemplateEntity saveForOldGitSync(TemplateEntity templateToSave, String comments) {
    Supplier<OutboxEvent> supplier = null;
    if (shouldLogAudits(
            templateToSave.getAccountId(), templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier())) {
      supplier = ()
          -> outboxService.save(new TemplateCreateEvent(templateToSave.getAccountIdentifier(),
              templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier(), templateToSave, comments));
    }
    return gitAwarePersistence.save(
        templateToSave, templateToSave.getYaml(), ChangeType.ADD, TemplateEntity.class, supplier);
  }

  @Override
  public TemplateEntity save(TemplateEntity templateToSave, String comments) throws InvalidRequestException {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo == null || TemplateUtils.isInlineEntity(gitEntityInfo)) {
      templateToSave.setStoreType(StoreType.INLINE);
      TemplateEntity savedTemplateEntity = mongoTemplate.save(templateToSave);
      if (shouldLogAudits(templateToSave.getAccountId(), templateToSave.getOrgIdentifier(),
              templateToSave.getProjectIdentifier())) {
        outboxService.save(new TemplateCreateEvent(templateToSave.getAccountIdentifier(),
            templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier(), templateToSave, comments));
      }
      return savedTemplateEntity;
    }
    if (templateGitXService.isNewGitXEnabledAndIsRemoteEntity(templateToSave, gitEntityInfo)) {
      Scope scope = TemplateUtils.buildScope(templateToSave);
      String yamlToPush = templateToSave.getYaml();
      addGitParamsToTemplateEntity(templateToSave, gitEntityInfo);

      gitAwareEntityHelper.createEntityOnGit(templateToSave, yamlToPush, scope);
    } else {
      if (templateToSave.getProjectIdentifier() != null) {
        throw new InvalidRequestException(
            format("Remote git simplification was not enabled for Project [%s] in Organisation [%s] in Account [%s]",
                templateToSave.getProjectIdentifier(), templateToSave.getOrgIdentifier(),
                templateToSave.getAccountIdentifier()));
      } else {
        throw new InvalidRequestException(
            format("Remote git simplification or feature flag was not enabled for Organisation [%s] or Account [%s]",
                templateToSave.getOrgIdentifier(), templateToSave.getAccountIdentifier()));
      }
    }
    TemplateEntity savedTemplateEntity = mongoTemplate.save(templateToSave);
    if (shouldLogAudits(
            templateToSave.getAccountId(), templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier())) {
      outboxService.save(new TemplateCreateEvent(templateToSave.getAccountIdentifier(),
          templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier(), templateToSave, comments));
    }
    return savedTemplateEntity;
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, notDeleted);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted, boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, notDeleted);
    return getTemplateEntity(
        criteria, accountId, orgIdentifier, projectIdentifier, getMetadataOnly, loadFromCache, loadFromFallbackBranch);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, notDeleted);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted,
      boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, notDeleted);
    return getTemplateEntity(
        criteria, accountId, orgIdentifier, projectIdentifier, getMetadataOnly, loadFromCache, loadFromFallbackBranch);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, notDeleted);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted,
      boolean getMetadataOnly) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, notDeleted);
    return getTemplateEntity(criteria, accountId, orgIdentifier, projectIdentifier, getMetadataOnly, false, false);
  }

  private Criteria
  buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted) {
    return Criteria.where(TemplateEntityKeys.deleted)
        .is(!notDeleted)
        .and(TemplateEntityKeys.versionLabel)
        .is(versionLabel)
        .and(TemplateEntityKeys.identifier)
        .is(templateIdentifier)
        .and(TemplateEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(TemplateEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(TemplateEntityKeys.accountId)
        .is(accountId);
  }

  private Criteria
  buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    return Criteria.where(TemplateEntityKeys.deleted)
        .is(!notDeleted)
        .and(TemplateEntityKeys.isStableTemplate)
        .is(true)
        .and(TemplateEntityKeys.identifier)
        .is(templateIdentifier)
        .and(TemplateEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(TemplateEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(TemplateEntityKeys.accountId)
        .is(accountId);
  }

  private Criteria
  buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    return Criteria.where(TemplateEntityKeys.deleted)
        .is(!notDeleted)
        .and(TemplateEntityKeys.isLastUpdatedTemplate)
        .is(true)
        .and(TemplateEntityKeys.identifier)
        .is(templateIdentifier)
        .and(TemplateEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(TemplateEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(TemplateEntityKeys.accountId)
        .is(accountId);
  }

  private Optional<TemplateEntity> getTemplateEntity(Criteria criteria, String accountId, String orgIdentifier,
      String projectIdentifier, boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    Query query = new Query(criteria);
    TemplateEntity savedEntity = mongoTemplate.findOne(query, TemplateEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      // fetch yaml from git
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(
            accountId, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      } else {
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      }
    }

    return Optional.of(savedEntity);
  }

  TemplateEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      TemplateEntity savedEntity, String branchName, boolean loadFromCache) {
    return (TemplateEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .entityType(EntityType.TEMPLATE)
            .loadFromCache(loadFromCache)
            .getOnlyFileContent(TemplateUtils.isExecutionFlow())
            .build(),
        Collections.emptyMap());
  }

  TemplateEntity fetchRemoteEntityWithFallBackBranch(String accountId, String orgIdentifier, String projectIdentifier,
      TemplateEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity = fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      String fallBackBranch = savedEntity.getFallBackBranch();
      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(savedEntity.getFallBackBranch());
      if (shouldRetryWithFallBackBranch(TemplateUtils.getScmException(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving template [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  @VisibleForTesting
  boolean shouldRetryWithFallBackBranch(ScmException scmException, String branchTried, String templateFallBackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(templateFallBackBranch) && !branchTried.equals(templateFallBackBranch));
  }

  @Override
  public TemplateEntity updateTemplateYamlForOldGitSync(TemplateEntity templateToUpdate,
      TemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    Supplier<OutboxEvent> supplier = null;
    if (isAuditEnabled(templateToUpdate, skipAudits)) {
      supplier =
          () -> generateUpdateOutboxEvent(templateToUpdate, oldTemplateEntity, comments, templateUpdateEventType);
    }
    return gitAwarePersistence.save(
        templateToUpdate, templateToUpdate.getYaml(), changeType, TemplateEntity.class, supplier);
  }

  @Override
  public TemplateEntity updateTemplateInDb(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    // This works considering that the templateToUpdate has the same _id as the oldTemplate
    TemplateEntity templateEntity = mongoTemplate.save(templateToUpdate);

    if (isAuditEnabled(templateToUpdate, skipAudits)) {
      generateUpdateOutboxEvent(templateToUpdate, oldTemplateEntity, comments, templateUpdateEventType);
    }
    return templateEntity;
  }

  @Override
  public TemplateEntity updateTemplateYaml(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (templateGitXService.isNewGitXEnabledAndIsRemoteEntity(templateToUpdate, gitEntityInfo)) {
      Scope scope = TemplateUtils.buildScope(templateToUpdate);
      gitAwareEntityHelper.updateEntityOnGit(templateToUpdate, templateToUpdate.getYaml(), scope);
    } else if (templateToUpdate.getStoreType() == StoreType.REMOTE) {
      throw new InvalidRequestException(format(
          "Template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] could not be updated.",
          templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(), templateToUpdate.getProjectIdentifier(),
          templateToUpdate.getOrgIdentifier()));
    }
    return updateTemplateInDb(
        templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
  }

  @Override
  public void hardDeleteTemplateForOldGitSync(TemplateEntity templateToDelete, String comments, boolean forceDelete) {
    String accountId = templateToDelete.getAccountId();
    String orgIdentifier = templateToDelete.getOrgIdentifier();
    String projectIdentifier = templateToDelete.getProjectIdentifier();
    gitAwarePersistence.delete(templateToDelete, ChangeType.DELETE, TemplateEntity.class);
    if (!forceDelete) {
      outboxService.save(
          new TemplateDeleteEvent(accountId, orgIdentifier, projectIdentifier, templateToDelete, comments));
    } else {
      outboxService.save(
          new TemplateForceDeleteEvent(accountId, orgIdentifier, projectIdentifier, templateToDelete, comments));
    }
  }

  @Override
  public void deleteTemplate(TemplateEntity templateToDelete, String comments, boolean forceDelete) {
    Criteria criteria = buildCriteriaForDelete(templateToDelete);
    Query query = new Query(criteria);
    TemplateEntity deletedTemplateEntity = mongoTemplate.findAndRemove(query, TemplateEntity.class);
    if (shouldLogAudits(templateToDelete.getAccountId(), templateToDelete.getOrgIdentifier(),
            templateToDelete.getProjectIdentifier())) {
      if (forceDelete) {
        outboxService.save(
            new TemplateForceDeleteEvent(templateToDelete.getAccountIdentifier(), templateToDelete.getOrgIdentifier(),
                templateToDelete.getProjectIdentifier(), deletedTemplateEntity, comments));
      } else {
        outboxService.save(
            new TemplateDeleteEvent(templateToDelete.getAccountIdentifier(), templateToDelete.getOrgIdentifier(),
                templateToDelete.getProjectIdentifier(), deletedTemplateEntity, comments));
      }
    }
  }

  private Criteria buildCriteriaForDelete(TemplateEntity templateEntity) {
    return Criteria.where(TemplateEntityKeys.deleted)
        .is(false)
        .where(TemplateEntityKeys.accountId)
        .is(templateEntity.getAccountId())
        .and(TemplateEntityKeys.orgIdentifier)
        .is(templateEntity.getOrgIdentifier())
        .and(TemplateEntityKeys.projectIdentifier)
        .is(templateEntity.getProjectIdentifier())
        .and(TemplateEntityKeys.identifier)
        .is(templateEntity.getIdentifier())
        .and(TemplateEntityKeys.versionLabel)
        .is(templateEntity.getVersionLabel());
  }

  @Override
  public Page<TemplateEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, boolean getDistinctFromBranches) {
    if (getDistinctFromBranches) {
      return EntityDistinctElementHelper.getDistinctElementPage(mongoTemplate, criteria, pageable, TemplateEntity.class,
          TemplateEntityKeys.accountId, TemplateEntityKeys.orgIdentifier, TemplateEntityKeys.projectIdentifier,
          TemplateEntityKeys.identifier, TemplateEntityKeys.versionLabel);
    }
    List<TemplateEntity> templateEntities = gitAwarePersistence.find(
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, TemplateEntity.class, false);
    return PageableExecutionUtils.getPage(templateEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, TemplateEntity.class));
  }

  @Override
  public Page<TemplateEntity> findAll(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<TemplateEntity> templateEntities = mongoTemplate.find(query, TemplateEntity.class);
    return PageableExecutionUtils.getPage(templateEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TemplateEntity.class));
  }

  @Override
  public boolean existsByAccountIdAndOrgIdAndProjectIdAndIdentifierAndVersionLabel(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel) {
    return gitAwarePersistence.exists(Criteria.where(TemplateEntityKeys.identifier)
                                          .is(templateIdentifier)
                                          .and(TemplateEntityKeys.projectIdentifier)
                                          .is(projectIdentifier)
                                          .and(TemplateEntityKeys.orgIdentifier)
                                          .is(orgIdentifier)
                                          .and(TemplateEntityKeys.accountId)
                                          .is(accountId)
                                          .and(TemplateEntityKeys.versionLabel)
                                          .is(versionLabel),
        projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public boolean existsByAccountIdAndOrgIdAndProjectIdAndIdentifierWithoutVersionLabel(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier) {
    Optional<TemplateEntity> template = gitAwarePersistence.findOne(Criteria.where(TemplateEntityKeys.identifier)
                                                                        .is(templateIdentifier)
                                                                        .and(TemplateEntityKeys.projectIdentifier)
                                                                        .is(projectIdentifier)
                                                                        .and(TemplateEntityKeys.orgIdentifier)
                                                                        .is(orgIdentifier)
                                                                        .and(TemplateEntityKeys.accountId)
                                                                        .is(accountId),
        projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
    return template.isPresent();
  }

  @Override
  public TemplateEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    criteria = gitAwarePersistence.makeCriteriaGitAware(
        accountIdentifier, orgIdentifier, projectIdentifier, TemplateEntity.class, criteria);
    return mongoTemplate.findAndModify(
        query(criteria), update, FindAndModifyOptions.options().returnNew(true), TemplateEntity.class);
  }

  @Override
  public TemplateEntity updateV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    return mongoTemplate.findAndModify(
        query(criteria), update, FindAndModifyOptions.options().returnNew(true), TemplateEntity.class);
  }

  @Override
  public TemplateEntity updateIsStableTemplate(TemplateEntity templateEntity, boolean value) {
    Update update = new Update().set(TemplateEntityKeys.isStableTemplate, value);
    return mongoTemplate.findAndModify(query(buildCriteria(templateEntity)), update,
        FindAndModifyOptions.options().returnNew(true), TemplateEntity.class);
  }

  @Override
  public TemplateEntity updateIsLastUpdatedTemplate(TemplateEntity templateEntity, boolean value) {
    Update update = new Update().set(TemplateEntityKeys.isLastUpdatedTemplate, value);
    return mongoTemplate.findAndModify(query(buildCriteria(templateEntity)), update,
        FindAndModifyOptions.options().returnNew(true), TemplateEntity.class);
  }

  @Override
  public boolean deleteAllTemplatesInAProject(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = Criteria.where(TemplateEntityKeys.accountId)
                            .is(accountId)
                            .and(TemplateEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(TemplateEntityKeys.projectIdentifier)
                            .is(projectIdentifier);
    Query query = new Query(criteria);
    try {
      List<TemplateEntity> entities = mongoTemplate.findAllAndRemove(query, TemplateEntity.class);
      entities.stream().forEach(deletedTemplateEntity -> {
        outboxService.save(
            new TemplateDeleteEvent(accountId, orgIdentifier, projectIdentifier, deletedTemplateEntity, ""));
      });
      return true;
    } catch (Exception e) {
      String errorMessage = format("Error while deleting Templates in Project [%s], in Org [%s] for Account [%s] : %s",
          projectIdentifier, orgIdentifier, accountId, e.getMessage());
      log.error(errorMessage, e);
      return false;
    }
  }

  @Override
  public boolean deleteAllOrgLevelTemplates(String accountId, String orgId) {
    Criteria criteria = Criteria.where(TemplateEntityKeys.accountId)
                            .is(accountId)
                            .and(TemplateEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(TemplateEntityKeys.projectIdentifier)
                            .exists(false);
    Query query = new Query(criteria);
    try {
      List<TemplateEntity> entities = mongoTemplate.findAllAndRemove(query, TemplateEntity.class);
      entities.stream().forEach(deletedTemplateEntity -> {
        outboxService.save(new TemplateDeleteEvent(accountId, orgId, null, deletedTemplateEntity, ""));
      });
      return true;
    } catch (Exception e) {
      String errorMessage =
          format("Error while deleting Templates in Org [%s] for Account [%s] : %s", orgId, accountId, e.getMessage());
      log.error(errorMessage, e);
      return false;
    }
  }

  @Override
  public Long countFileInstances(String accountIdentifier, String repoURL, String filePath) {
    return countTemplates(Criteria.where(TemplateEntityKeys.accountId)
                              .is(accountIdentifier)
                              .and(TemplateEntityKeys.repoURL)
                              .is(repoURL)
                              .and(TemplateEntityKeys.filePath)
                              .is(filePath));
  }

  @Override
  public TemplateEntity importFlowSaveTemplate(TemplateEntity templateEntity, String comments) {
    TemplateEntity savedTemplateEntity = mongoTemplate.save(templateEntity);
    if (shouldLogAudits(
            templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier())) {
      outboxService.save(new TemplateCreateEvent(templateEntity.getAccountIdentifier(),
          templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier(), templateEntity, comments));
    }
    return savedTemplateEntity;
  }

  @Override
  public List<String> getListOfRepos(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, TemplateEntityKeys.repo, TemplateEntity.class, String.class);
  }

  private Long countTemplates(Criteria criteria) {
    Query query = new Query().addCriteria(criteria);
    return mongoTemplate.count(query, TemplateEntity.class);
  }

  private Criteria buildCriteria(TemplateEntity templateEntity) {
    return Criteria.where(TemplateEntityKeys.accountId)
        .is(templateEntity.getAccountId())
        .and(TemplateEntityKeys.orgIdentifier)
        .is(templateEntity.getOrgIdentifier())
        .and(TemplateEntityKeys.projectIdentifier)
        .is(templateEntity.getProjectIdentifier())
        .and(TemplateEntityKeys.identifier)
        .is(templateEntity.getIdentifier())
        .and(TemplateEntityKeys.versionLabel)
        .is(templateEntity.getVersionLabel())
        .and(TemplateEntityKeys.branch)
        .is(templateEntity.getBranch())
        .and(TemplateEntityKeys.yamlGitConfigRef)
        .is(templateEntity.getYamlGitConfigRef());
  }

  boolean shouldLogAudits(String accountId, String orgIdentifier, String projectIdentifier) {
    // if git sync is disabled or if git sync is enabled (only for default branch)
    return !gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
  }

  private void addGitParamsToTemplateEntity(TemplateEntity templateEntity, GitEntityInfo gitEntityInfo) {
    templateEntity.setStoreType(StoreType.REMOTE);
    if (EmptyPredicate.isEmpty(templateEntity.getRepoURL())) {
      templateEntity.setRepoURL(gitAwareEntityHelper.getRepoUrl(
          templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier()));
    }
    templateEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    templateEntity.setRepo(gitEntityInfo.getRepoName());
    templateEntity.setFilePath(gitEntityInfo.getFilePath());
    templateEntity.setFallBackBranch(gitEntityInfo.getBranch());
  }

  private boolean isAuditEnabled(TemplateEntity templateEntity, boolean skipAudits) {
    return shouldLogAudits(
               templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier())
        && !skipAudits;
  }

  private OutboxEvent generateUpdateOutboxEvent(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      String comments, TemplateUpdateEventType templateUpdateEventType) {
    return outboxService.save(
        new TemplateUpdateEvent(templateToUpdate.getAccountIdentifier(), templateToUpdate.getOrgIdentifier(),
            templateToUpdate.getProjectIdentifier(), templateToUpdate, oldTemplateEntity, comments,
            templateUpdateEventType != null ? templateUpdateEventType : TemplateUpdateEventType.OTHERS_EVENT));
  }
}
