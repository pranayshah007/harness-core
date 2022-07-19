/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
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
import io.harness.template.events.TemplateUpdateEvent;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.utils.TemplateUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
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

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDC)
public class NGTemplateRepositoryCustomImpl implements NGTemplateRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final MongoTemplate mongoTemplate;
  private final NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;
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
    if (isNewGitXEnabled(templateToSave, gitEntityInfo)) {
      Scope scope = TemplateUtils.buildScope(templateToSave);
      String yamlToPush = templateToSave.getYaml();
      addGitParamsToTemplateEntity(templateToSave, gitEntityInfo);

      gitAwareEntityHelper.createEntityOnGit(templateToSave, yamlToPush, scope);
    } else {
      if (templateToSave.getProjectIdentifier() != null) {
        throw new InvalidRequestException(String.format(
            "Remote git simplification was not enabled for Project [%s] in Organisation [%s] in Account [%s]",
            templateToSave.getProjectIdentifier(), templateToSave.getOrgIdentifier(),
            templateToSave.getAccountIdentifier()));
      } else {
        throw new InvalidRequestException(String.format(
            "Remote git simplification or feature flag was not enabled for Organisation [%s] or Account [%s]",
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
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(String accountId,
      String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted) {
    return gitAwarePersistence.findOne(Criteria.where(TemplateEntityKeys.deleted)
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
                                           .is(accountId),
        projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    return gitAwarePersistence.findOne(Criteria.where(TemplateEntityKeys.deleted)
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
                                           .is(accountId),
        projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public Optional<TemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, boolean notDeleted) {
    return gitAwarePersistence.findOne(Criteria.where(TemplateEntityKeys.deleted)
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
                                           .is(accountId),
        projectIdentifier, orgIdentifier, accountId, TemplateEntity.class);
  }

  @Override
  public TemplateEntity updateTemplateYaml(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    Supplier<OutboxEvent> supplier = null;
    if (shouldLogAudits(templateToUpdate.getAccountId(), templateToUpdate.getOrgIdentifier(),
            templateToUpdate.getProjectIdentifier())
        && !skipAudits) {
      supplier = ()
          -> outboxService.save(
              new TemplateUpdateEvent(templateToUpdate.getAccountIdentifier(), templateToUpdate.getOrgIdentifier(),
                  templateToUpdate.getProjectIdentifier(), templateToUpdate, oldTemplateEntity, comments,
                  templateUpdateEventType != null ? templateUpdateEventType : TemplateUpdateEventType.OTHERS_EVENT));
    }
    return gitAwarePersistence.save(
        templateToUpdate, templateToUpdate.getYaml(), changeType, TemplateEntity.class, supplier);
  }

  @Override
  public TemplateEntity deleteTemplate(TemplateEntity templateToDelete, String comments) {
    Supplier<OutboxEvent> supplier = null;
    if (shouldLogAudits(templateToDelete.getAccountId(), templateToDelete.getOrgIdentifier(),
            templateToDelete.getProjectIdentifier())) {
      supplier = ()
          -> outboxService.save(
              new TemplateDeleteEvent(templateToDelete.getAccountIdentifier(), templateToDelete.getOrgIdentifier(),
                  templateToDelete.getProjectIdentifier(), templateToDelete, comments));
    }
    return gitAwarePersistence.save(
        templateToDelete, templateToDelete.getYaml(), ChangeType.DELETE, TemplateEntity.class, supplier);
  }

  @Override
  public void hardDeleteTemplate(TemplateEntity templateToDelete, String comments) {
    String accountId = templateToDelete.getAccountId();
    String orgIdentifier = templateToDelete.getOrgIdentifier();
    String projectIdentifier = templateToDelete.getProjectIdentifier();
    gitAwarePersistence.delete(templateToDelete, ChangeType.DELETE, TemplateEntity.class);
    outboxService.save(
        new TemplateDeleteEvent(accountId, orgIdentifier, projectIdentifier, templateToDelete, comments));
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
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, TemplateEntity.class);
    return PageableExecutionUtils.getPage(templateEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, TemplateEntity.class));
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
  public TemplateEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    criteria = gitAwarePersistence.makeCriteriaGitAware(
        accountIdentifier, orgIdentifier, projectIdentifier, TemplateEntity.class, criteria);
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
    templateEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    templateEntity.setRepo(gitEntityInfo.getRepoName());
    templateEntity.setFilePath(gitEntityInfo.getFilePath());
  }

  @VisibleForTesting
  boolean isNewGitXEnabled(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    if (templateToSave.getProjectIdentifier() != null) {
      return isGitSimplificationEnabled(templateToSave, gitEntityInfo);
    } else {
      return ngTemplateFeatureFlagHelperService.isEnabled(
                 templateToSave.getAccountId(), FeatureName.FF_TEMPLATE_GITSYNC)
          && TemplateUtils.isRemoteEntity(gitEntityInfo);
    }
  }

  private boolean isGitSimplificationEnabled(TemplateEntity templateToSave, GitEntityInfo gitEntityInfo) {
    return gitSyncSdkService.isGitSimplificationEnabled(templateToSave.getAccountIdentifier(),
               templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier())
        && TemplateUtils.isRemoteEntity(gitEntityInfo);
  }
}
