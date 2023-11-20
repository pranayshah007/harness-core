/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.UnexpectedException;
import io.harness.idp.events.producers.IdpEntityCrudStreamProducer;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity.AsyncScoreComputationKeys;
import io.harness.idp.scorecard.scores.mappers.AsyncScoreComputationMapper;
import io.harness.idp.scorecard.scores.repositories.AsyncAsyncScoreComputationRepository;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;

import java.util.Collections;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AsyncScoreComputationServiceImpl implements AsyncScoreComputationService {
  private AsyncAsyncScoreComputationRepository asyncScoreComputationRepository;
  private IdpEntityCrudStreamProducer idpEntityCrudStreamProducer;
  private ScoreComputerService scoreComputerService;

  @Override
  public ScorecardRecalibrateInfo getStartTimeOfInProgressScoreComputation(
      String harnessAccount, String scorecardIdentifier, String entityIdentifier) {
    // Check if score computation was started due to a re-run request
    Optional<AsyncScoreComputationEntity> scoreComputationEntityOpt =
        asyncScoreComputationRepository.findByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
            harnessAccount, scorecardIdentifier, entityIdentifier);

    if (scoreComputationEntityOpt.isEmpty() && entityIdentifier != null) {
      // Check if score computation was started due to scorecard update
      scoreComputationEntityOpt =
          asyncScoreComputationRepository.findByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
              harnessAccount, scorecardIdentifier, null);
    }

    if (scorecardIdentifier != null && scoreComputationEntityOpt.isEmpty()) {
      // Check if score computation was started by the iterator
      scoreComputationEntityOpt =
          asyncScoreComputationRepository.findByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
              harnessAccount, null, null);
    }

    return scoreComputationEntityOpt.map(AsyncScoreComputationMapper::toDTO).orElse(null);
  }

  @Override
  public ScorecardRecalibrateInfo logScoreComputationRequestAndPublishEvent(
      String harnessAccount, String scorecardIdentifier, String entityIdentifier) {
    AsyncScoreComputationEntity entity = AsyncScoreComputationEntity.builder()
                                             .accountIdentifier(harnessAccount)
                                             .scorecardIdentifier(scorecardIdentifier)
                                             .entityIdentifier(entityIdentifier)
                                             .startTime(System.currentTimeMillis())
                                             .build();
    AsyncScoreComputationEntity createdEntity = asyncScoreComputationRepository.save(entity);
    boolean producerResult = idpEntityCrudStreamProducer.publishAsyncScoreComputationChangeEventToRedis(
        harnessAccount, scorecardIdentifier, entityIdentifier);
    if (!producerResult) {
      throw new UnexpectedException("Error in producing event for async score computation.");
    }
    return AsyncScoreComputationMapper.toDTO(createdEntity);
  }

  @Override
  public void deleteScoreComputationRequest(
      String harnessAccount, String scorecardIdentifier, String entityIdentifier) {
    asyncScoreComputationRepository.deleteByAccountIdentifierAndScorecardIdentifierAndEntityIdentifier(
        harnessAccount, scorecardIdentifier, entityIdentifier);
  }

  @Override
  public void triggerScoreComputation(EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();

    String scorecardIdentifier = entityChangeDTO.getMetadataMap().get(AsyncScoreComputationKeys.scorecardIdentifier);
    scorecardIdentifier = !scorecardIdentifier.isBlank() ? scorecardIdentifier : null;

    String entityIdentifier = entityChangeDTO.getMetadataMap().get(AsyncScoreComputationKeys.entityIdentifier);
    entityIdentifier = !entityIdentifier.isBlank() ? entityIdentifier : null;

    try {
      scoreComputerService.computeScores(accountIdentifier,
          scorecardIdentifier == null ? Collections.emptyList() : Collections.singletonList(scorecardIdentifier),
          entityIdentifier == null ? Collections.emptyList() : Collections.singletonList(entityIdentifier));
    } catch (Exception ex) {
      log.error("Could not compute score for account {}, scorecard {}, entity {}", accountIdentifier,
          scorecardIdentifier, entityIdentifier);
    }
    this.deleteScoreComputationRequest(accountIdentifier, scorecardIdentifier, entityIdentifier);
  }
}
