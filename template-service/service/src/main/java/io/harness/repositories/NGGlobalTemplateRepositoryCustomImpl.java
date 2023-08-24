/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.template.entity.GlobalTemplateEntity;
import io.harness.template.entity.GlobalTemplateEntity.GlobalTemplateEntityKeys;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.GlobalTemplateCreateEvent;
import io.harness.template.events.GlobalTemplateUpdateEvent;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.services.TemplateGitXService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
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
public class NGGlobalTemplateRepositoryCustomImpl implements NGGlobalTemplateRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final MongoTemplate mongoTemplate;
  private final TemplateGitXService templateGitXService;
  OutboxService outboxService;

  @Override
  public boolean globalTemplateExistByIdentifierWithoutVersionLabel(String templateIdentifier) {
    Optional<GlobalTemplateEntity> template =
        getGlobalTemplateEntity(Criteria.where(GlobalTemplateEntityKeys.identifier).is(templateIdentifier), false);
    return template.isPresent();
  }

  @Override
  public Optional<GlobalTemplateEntity> findGlobalTemplateByIdentifierAndIsStableAndDeletedNot(
      String templateIdentifier, boolean notDeleted, boolean getMetadataOnly) {
    Criteria criteria =
        buildCriteriaForGlobalTemplateFindByIdentifierAndIsStableAndDeletedNot(templateIdentifier, notDeleted);
    return getGlobalTemplateEntity(criteria, getMetadataOnly);
  }

  @Override
  public Optional<GlobalTemplateEntity> findGlobalTemplateByIdentifierAndVersionLabelAndDeletedNot(
      String templateIdentifier, String versionLabel, boolean notDeleted, boolean getMetadataOnly) {
    Criteria criteria = buildCriteriaForGlobalTemplateFindByIdentifierAndVersionLabelAndDeletedNot(
        templateIdentifier, versionLabel, notDeleted);
    return getGlobalTemplateEntity(criteria, getMetadataOnly);
  }

  @Override
  public Page<GlobalTemplateEntity> findALLGlobalTemplateAndDeletedNot(
      boolean notDeleted, boolean getMetadataOnly, Pageable pageable, Criteria criteria) {
    criteria = buildCriteriaForGlobalTemplateFindByDeletedNot(notDeleted);
    return getAllGlobalTemplateEntity(criteria, getMetadataOnly, pageable);
  }

  @Override
  public Optional<GlobalTemplateEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForGlobalTemplate(
      String accountId, String orgIdentifier, String projectIdentifier, String templateIdentifier, String versionLabel,
      boolean notDeleted, boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    Criteria criteria =
        buildCriteriaForFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(
            accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, notDeleted);
    return getGlobalTemplateEntity(criteria, getMetadataOnly);
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

  private Criteria buildCriteriaForGlobalTemplateFindByDeletedNot(boolean notDeleted) {
    return Criteria.where(GlobalTemplateEntityKeys.deleted).is(!notDeleted);
  }

  private Optional<GlobalTemplateEntity> getGlobalTemplateEntity(Criteria criteria, boolean getMetadataOnly) {
    Query query = new Query(criteria);
    GlobalTemplateEntity savedEntity = mongoTemplate.findOne(query, GlobalTemplateEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    return Optional.of(savedEntity);
  }

  @Override
  public GlobalTemplateEntity updateTemplateInDb(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, ChangeType changeType, String comments,
      TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    // This works considering that the templateToUpdate has the same _id as the oldTemplate
    GlobalTemplateEntity templateEntity = mongoTemplate.save(templateToUpdate);

    if (isAuditEnabled(templateToUpdate, skipAudits)) {
      generateUpdateOutboxEvent(templateToUpdate, oldTemplateEntity, comments, templateUpdateEventType);
    }
    return templateEntity;
  }

  @Override
  public Page<GlobalTemplateEntity> findAll(String accountIdentifier, Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<GlobalTemplateEntity> globalTemplateEntities = mongoTemplate.find(query, GlobalTemplateEntity.class);
    return PageableExecutionUtils.getPage(globalTemplateEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), GlobalTemplateEntity.class));
  }

  private OutboxEvent generateUpdateOutboxEvent(GlobalTemplateEntity templateToUpdate,
      GlobalTemplateEntity oldTemplateEntity, String comments, TemplateUpdateEventType templateUpdateEventType) {
    return outboxService.save(new GlobalTemplateUpdateEvent(templateToUpdate.getAccountIdentifier(), templateToUpdate,
        oldTemplateEntity, comments,
        templateUpdateEventType != null ? templateUpdateEventType : TemplateUpdateEventType.OTHERS_EVENT));
  }

  @Override
  public GlobalTemplateEntity updateIsStableTemplate(GlobalTemplateEntity globalTemplateEntity, boolean value) {
    Update update = new Update().set(GlobalTemplateEntityKeys.isStableTemplate, value);
    return mongoTemplate.findAndModify(query(buildCriteria(globalTemplateEntity)), update,
        FindAndModifyOptions.options().returnNew(true), GlobalTemplateEntity.class);
  }

  @Override
  public boolean globalTemplateExistByIdentifierAndVersionLabel(String templateIdentifier, String versionLabel) {
    Optional<GlobalTemplateEntity> template =
        getGlobalTemplateEntity(Criteria.where(GlobalTemplateEntityKeys.identifier)
                                    .is(templateIdentifier)
                                    .and(GlobalTemplateEntityKeys.versionLabel)
                                    .is(versionLabel),
            false);
    return template.isPresent();
  }

  @Override
  public GlobalTemplateEntity save(GlobalTemplateEntity templateToSave, String comments)
      throws InvalidRequestException {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GlobalTemplateEntity savedTemplateEntity = mongoTemplate.save(templateToSave);
    if (shouldLogAudits(
            templateToSave.getAccountId(), templateToSave.getOrgIdentifier(), templateToSave.getProjectIdentifier())) {
      outboxService.save(
          new GlobalTemplateCreateEvent(templateToSave.getAccountIdentifier(), templateToSave, comments));
    }
    return savedTemplateEntity;
  }

  boolean shouldLogAudits(String accountId, String orgIdentifier, String projectIdentifier) {
    // if git sync is disabled or if git sync is enabled (only for default branch)
    return !gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier);
  }

  private Criteria buildCriteriaForGlobalTemplateFindByIdentifierAndIsStableAndDeletedNot(
      String templateIdentifier, boolean notDeleted) {
    return Criteria.where(GlobalTemplateEntityKeys.deleted)
        .is(!notDeleted)
        .and(GlobalTemplateEntityKeys.isStableTemplate)
        .is(true)
        .and(GlobalTemplateEntityKeys.identifier)
        .is(templateIdentifier);
  }
  private Criteria buildCriteria(GlobalTemplateEntity globalTemplateEntity) {
    return Criteria.where(TemplateEntityKeys.identifier)
        .is(globalTemplateEntity.getIdentifier())
        .and(TemplateEntityKeys.versionLabel)
        .is(globalTemplateEntity.getVersionLabel());
  }

  private boolean isAuditEnabled(GlobalTemplateEntity globalTemplateEntity, boolean skipAudits) {
    return shouldLogAudits(globalTemplateEntity.getAccountId(), globalTemplateEntity.getOrgIdentifier(),
               globalTemplateEntity.getProjectIdentifier())
        && !skipAudits;
  }

  private Page<GlobalTemplateEntity> getAllGlobalTemplateEntity(
      Criteria criteria, boolean getMetadataOnly, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<GlobalTemplateEntity> templateEntities = mongoTemplate.find(query, GlobalTemplateEntity.class);
    return PageableExecutionUtils.getPage(templateEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), GlobalTemplateEntity.class));
  }

  private Criteria buildCriteriaForGlobalTemplateFindByIdentifierAndVersionLabelAndDeletedNot(
      String templateIdentifier, String versionLabel, boolean notDeleted) {
    return Criteria.where(GlobalTemplateEntityKeys.deleted)
        .is(!notDeleted)
        .and(GlobalTemplateEntityKeys.versionLabel)
        .is(versionLabel)
        .and(GlobalTemplateEntityKeys.identifier)
        .is(templateIdentifier);
  }
}
