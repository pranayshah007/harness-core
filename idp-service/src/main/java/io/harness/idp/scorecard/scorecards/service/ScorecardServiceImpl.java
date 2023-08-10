/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.beans.ScorecardCheckFullDetails;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardCheckFullDetailsMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardDetailsMapper;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardMapper;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepository;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ScorecardServiceImpl implements ScorecardService {
  private final ScorecardRepository scorecardRepository;
  private final CheckService checkService;
  private final SetupUsageProducer setupUsageProducer;

  @Inject
  public ScorecardServiceImpl(
      ScorecardRepository scorecardRepository, CheckService checkService, SetupUsageProducer setupUsageProducer) {
    this.scorecardRepository = scorecardRepository;
    this.checkService = checkService;
    this.setupUsageProducer = setupUsageProducer;
  }

  @Override
  public List<Scorecard> getAllScorecardsAndChecksDetails(String accountIdentifier) {
    List<Scorecard> scorecards = new ArrayList<>();
    List<ScorecardEntity> scorecardEntities = scorecardRepository.findByAccountIdentifier(accountIdentifier);

    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      List<String> checkIds =
          scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toList());
      List<CheckEntity> checkEntities = checkService.getChecksByAccountIdAndIdentifiers(accountIdentifier, checkIds);
      scorecards.add(ScorecardMapper.toDTO(scorecardEntity, checkEntities));
    }
    return scorecards;
  }

  @Override
  public List<ScorecardCheckFullDetails> getAllScorecardCheckFullDetails(
      String accountIdentifier, List<String> scorecardIdentifiers) {
    List<ScorecardEntity> scorecardEntities;
    if (scorecardIdentifiers.isEmpty()) {
      scorecardEntities = scorecardRepository.findByAccountIdentifierAndPublished(accountIdentifier, true);
    } else {
      scorecardEntities =
          scorecardRepository.findByAccountIdentifierAndIdentifierIn(accountIdentifier, scorecardIdentifiers);
    }
    List<String> checkIdentifiers = scorecardEntities.stream()
                                        .flatMap(scorecardEntity -> scorecardEntity.getChecks().stream())
                                        .map(ScorecardEntity.Check::getIdentifier)
                                        .collect(Collectors.toList());
    Map<String, CheckEntity> checkEntityMap =
        checkService.getActiveChecks(accountIdentifier, checkIdentifiers)
            .stream()
            .collect(Collectors.toMap(CheckEntity::getIdentifier, Function.identity()));
    List<ScorecardCheckFullDetails> scorecardDetailsList = new ArrayList<>();
    for (ScorecardEntity scorecardEntity : scorecardEntities) {
      List<CheckEntity> checksList = scorecardEntity.getChecks()
                                         .stream()
                                         .map(check -> checkEntityMap.get(check.getIdentifier()))
                                         .collect(Collectors.toList());
      scorecardDetailsList.add(ScorecardCheckFullDetailsMapper.toDTO(scorecardEntity, checksList));
    }
    return scorecardDetailsList;
  }

  @Override
  public void saveScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    scorecardRepository.save(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));
    setupUsageProducer.publishScorecardSetupUsage(scorecardDetailsRequest, accountIdentifier);
  }

  @Override
  public void updateScorecard(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    scorecardRepository.update(ScorecardDetailsMapper.fromDTO(scorecardDetailsRequest, accountIdentifier));
    setupUsageProducer.deleteScorecardSetupUsage(
        accountIdentifier, scorecardDetailsRequest.getScorecard().getIdentifier());
    setupUsageProducer.publishScorecardSetupUsage(scorecardDetailsRequest, accountIdentifier);
  }

  @Override
  public ScorecardDetailsResponse getScorecardDetails(String accountIdentifier, String identifier) {
    ScorecardEntity scorecardEntity =
        scorecardRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    List<String> checkIds =
        scorecardEntity.getChecks().stream().map(ScorecardEntity.Check::getIdentifier).collect(Collectors.toList());
    Map<String, CheckEntity> checkEntityMap =
        checkService.getChecksByAccountIdAndIdentifiers(accountIdentifier, checkIds)
            .stream()
            .collect(Collectors.toMap(CheckEntity::getIdentifier, Function.identity()));
    return ScorecardDetailsMapper.toDTO(scorecardEntity, checkEntityMap);
  }

  @Override
  public void deleteScorecard(String accountIdentifier, String identifier) {
    DeleteResult deleteResult = scorecardRepository.delete(accountIdentifier, identifier);
    if (deleteResult.getDeletedCount() == 0) {
      throw new InvalidRequestException("Could not delete scorecard");
    }
    setupUsageProducer.deleteScorecardSetupUsage(accountIdentifier, identifier);
  }
}
