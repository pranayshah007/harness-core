/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDC)
public class NGTemplateRepositoryCustomImpl implements NGTemplateRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final GitSyncSdkService gitSyncSdkService;
  private final MongoTemplate mongoTemplate;
  OutboxService outboxService;

  @Override
  public TemplateEntity save(TemplateEntity templateToSave, String comments) {
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

  boolean shouldLogAudits(String accountId, String orgIdentifier, String projectIdentifier) {
    // if git sync is disabled or if git sync is enabled (only for default branch)
    return !gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
  }
}
