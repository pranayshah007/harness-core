package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@GitSyncableHarnessRepo
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineRepositoryCustomImpl implements PMSPipelineRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final GitAwarePersistence gitAwarePersistence;
  private final GitSyncSdkService gitSyncSdkService;
  OutboxService outboxService;

  @Override
  public Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, boolean getDistinctFromBranches) {
    if (getDistinctFromBranches) {
      return EntityDistinctElementHelper.getDistinctElementPage(mongoTemplate, criteria, pageable, PipelineEntity.class,
          PipelineEntityKeys.accountId, PipelineEntityKeys.orgIdentifier, PipelineEntityKeys.projectIdentifier,
          PipelineEntityKeys.identifier);
    }
    List<PipelineEntity> pipelineEntities = gitAwarePersistence.find(
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, PipelineEntity.class);
    return PageableExecutionUtils.getPage(pipelineEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, PipelineEntity.class));
  }

  @Override
  public PipelineEntity findFirstPipeline(Criteria criteria) {
    Query query = new Query().addCriteria(criteria);
    return mongoTemplate.findOne(query, PipelineEntity.class);
  }

  @Override
  public Long countAllPipelines(Criteria criteria) {
    Query query = new Query().addCriteria(criteria);
    return mongoTemplate.count(query, PipelineEntity.class);
  }

  @Override
  public PipelineEntity save(PipelineEntity pipelineToSave, PipelineConfig yamlDTO) {
    Supplier<OutboxEvent> supplier = ()
        -> outboxService.save(new PipelineCreateEvent(pipelineToSave.getAccountIdentifier(),
            pipelineToSave.getOrgIdentifier(), pipelineToSave.getProjectIdentifier(), pipelineToSave));
    return gitAwarePersistence.save(
        pipelineToSave, pipelineToSave.getYaml(), ChangeType.ADD, PipelineEntity.class, supplier);
  }

  @Override
  public Optional<PipelineEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted) {
    return gitAwarePersistence.findOne(Criteria.where(PipelineEntityKeys.deleted)
                                           .is(!notDeleted)
                                           .and(PipelineEntityKeys.identifier)
                                           .is(pipelineIdentifier)
                                           .and(PipelineEntityKeys.projectIdentifier)
                                           .is(projectIdentifier)
                                           .and(PipelineEntityKeys.orgIdentifier)
                                           .is(orgIdentifier)
                                           .and(PipelineEntityKeys.accountId)
                                           .is(accountId),
        projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
  }

  @Override
  public Optional<PipelineEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return gitAwarePersistence.findOne(Criteria.where(PipelineEntityKeys.identifier)
                                           .is(pipelineIdentifier)
                                           .and(PipelineEntityKeys.projectIdentifier)
                                           .is(projectIdentifier)
                                           .and(PipelineEntityKeys.orgIdentifier)
                                           .is(orgIdentifier)
                                           .and(PipelineEntityKeys.accountId)
                                           .is(accountId),
        projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
  }

  @Override
  public PipelineEntity updatePipelineYaml(PipelineEntity pipelineToUpdate, PipelineEntity oldPipelineEntity,
      PipelineConfig yamlDTO, ChangeType changeType) {
    Supplier<OutboxEvent> supplier = null;
    if (!gitSyncSdkService.isGitSyncEnabled(pipelineToUpdate.getAccountId(), pipelineToUpdate.getOrgIdentifier(),
            pipelineToUpdate.getProjectIdentifier())) {
      supplier = ()
          -> outboxService.save(
              new PipelineUpdateEvent(pipelineToUpdate.getAccountIdentifier(), pipelineToUpdate.getOrgIdentifier(),
                  pipelineToUpdate.getProjectIdentifier(), pipelineToUpdate, oldPipelineEntity));
    }
    return gitAwarePersistence.save(
        pipelineToUpdate, pipelineToUpdate.getYaml(), changeType, PipelineEntity.class, supplier);
  }

  @Override
  public PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    Criteria gitSyncCriteria =
        gitAwarePersistence.getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
    if (gitSyncCriteria != null) {
      criteria = new Criteria().andOperator(criteria, gitSyncCriteria);
    }

    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicyForPipelineUpdate();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineEntity.class));
  }

  @Override
  public PipelineEntity deletePipeline(PipelineEntity pipelineToUpdate, PipelineConfig yamlDTO) {
    Optional<PipelineEntity> pipelineEntityOptional =
        findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(pipelineToUpdate.getAccountId(),
            pipelineToUpdate.getOrgIdentifier(), pipelineToUpdate.getProjectIdentifier(),
            pipelineToUpdate.getIdentifier(), true);
    if (pipelineEntityOptional.isPresent()) {
      Supplier<OutboxEvent> supplier = ()
          -> outboxService.save(new PipelineDeleteEvent(pipelineToUpdate.getAccountIdentifier(),
              pipelineToUpdate.getOrgIdentifier(), pipelineToUpdate.getProjectIdentifier(), pipelineToUpdate));
      return gitAwarePersistence.save(
          pipelineToUpdate, pipelineToUpdate.getYaml(), ChangeType.DELETE, PipelineEntity.class, supplier);
    }
    throw new InvalidRequestException("No such pipeline exists");
  }

  private RetryPolicy<Object> getRetryPolicyForPipelineUpdate() {
    int MAX_ATTEMPTS = 3;
    Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .handle(DuplicateKeyException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.info(
                "[Retrying]: Failed updating Pipeline; attempt: {}", event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event
            -> log.error(
                "[Failed]: Failed updating Pipeline; attempt: {}", event.getAttemptCount(), event.getFailure()));
  }
}
