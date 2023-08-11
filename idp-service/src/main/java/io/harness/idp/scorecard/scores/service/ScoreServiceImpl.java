/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasources.providers.DataSourceProvider;
import io.harness.idp.scorecard.datasources.providers.DataSourceProviderFactory;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entities.ScoreEntity;
import io.harness.idp.scorecard.scores.mappers.ScorecardGraphSummaryInfoMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardScoreMapper;
import io.harness.idp.scorecard.scores.mappers.ScorecardSummaryInfoMapper;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;
import io.harness.spec.server.idp.v1.model.ScorecardScore;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
public class ScoreServiceImpl implements ScoreService {
  ScorecardService scorecardService;
  DataSourceProviderFactory dataSourceProviderFactory;

  ScoreRepository scoreRepository;

  @Override
  public void computeScores(String accountIdentifier) {
    List<ScorecardEntity> scorecards = scorecardService.getAllScorecards(accountIdentifier);
    List<String> filters = getAllFilters(scorecards);
    List<BackstageCatalogEntity> entities = getAllEntities(accountIdentifier, filters);
    List<DataPointEntity> dataPoints = getDataPointsToCollect(scorecards);

    for (BackstageCatalogEntity entity : entities) {
      Map<String, Map<String, Object>> data = fetch(accountIdentifier, entity, dataPoints);
      compute(data, scorecards);
    }
  }

  @Override
  public List<ScorecardSummaryInfo> getScoresSummaryForAnEntity(String accountIdentifier, String entityIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifier(accountIdentifier, entityIdentifier);
    Map<String, String> scoreCardIdentifierNameMapping =
        scorecardService.getAllScorecards(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(ScorecardEntity::getIdentifier, ScorecardEntity::getName));
    return scoreEntities.stream()
        .map(scoreEntity
            -> ScorecardSummaryInfoMapper.toDTO(
                scoreEntity, scoreCardIdentifierNameMapping.get(scoreEntity.getScorecardIdentifier())))
        .collect(Collectors.toList());
  }

  @Override
  public List<ScorecardGraphSummaryInfo> getScoresGraphSummaryForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifierAndScorecardIdentifier(
            accountIdentifier, entityIdentifier, scorecardIdentifier);
    return scoreEntities.stream()
        .map(scoreEntity -> ScorecardGraphSummaryInfoMapper.toDTO(scoreEntity))
        .collect(Collectors.toList());
  }

  @Override
  public List<ScorecardScore> getScorecardScoreOverviewForAnEntity(String accountIdentifier, String entityIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifier(accountIdentifier, entityIdentifier);
    Map<String, ScorecardEntity> scorecardIdentifierEntityMapping =
        scorecardService.getAllScorecards(accountIdentifier)
            .stream()
            .collect(Collectors.toMap(ScorecardEntity::getIdentifier, Function.identity()));
    return scoreEntities.stream()
        .map(scoreEntity
            -> ScorecardScoreMapper.toDTO(scoreEntity,
                scorecardIdentifierEntityMapping.get(scoreEntity.getScorecardIdentifier()).getName(),
                scorecardIdentifierEntityMapping.get(scoreEntity.getScorecardIdentifier()).getDescription()))
        .collect(Collectors.toList());
  }

  @Override
  public ScorecardSummaryInfo getScorecardRecalibratedScoreInfoForAnEntityAndScorecard(
      String accountIdentifier, String entityIdentifier, String scorecardIdentifier) {
    List<ScoreEntity> scoreEntities =
        scoreRepository.findAllByAccountIdentifierAndEntityIdentifierAndScorecardIdentifier(
            accountIdentifier, entityIdentifier, scorecardIdentifier);
    if (scoreEntities.size() > 0) {
      scoreEntities.sort(Comparator.comparing(ScoreEntity::getLastComputedTimestamp));
      ScoreEntity scoreEntity = scoreEntities.get(scoreEntities.size() - 1);
      return ScorecardSummaryInfoMapper.toDTO(scoreEntity,
          scorecardService.getScorecardDetails(accountIdentifier, scoreEntity.getScorecardIdentifier())
              .getScorecard()
              .getName());
    }
    return new ScorecardSummaryInfo();
  }

  private List<String> getAllFilters(List<ScorecardEntity> scorecards) {
    // get all filters ["Component:Service"]
    // kind=Component&type=Service
    return Collections.emptyList();
  }

  private List<BackstageCatalogEntity> getAllEntities(String accountIdentifier, List<String> filters) {
    return Collections.emptyList();
  }

  private List<DataPointEntity> getDataPointsToCollect(List<ScorecardEntity> scorecards) {
    // ======= prepare =======

    // for each scorecards {
    // get all checks
    // get all datapoints
    // DP -> DSL (DS)

    // github - readme (DSL) -> [DP1, DP2]
    // github - issues (DSL) -> [DP5, DP6]
    // harness - dashboard (DSL) -> [DP3, DP4]

    // ======= prepare =======
    return null;
  }

  private Map<String, Map<String, Object>> fetch(
      String accountIdentifier, BackstageCatalogEntity entity, List<DataPointEntity> dataPoints) {
    Map<String, Map<String, Object>> data = new HashMap<>();
    for (DataSourceProvider provider : dataSourceProviderFactory.getProviders()) {
      data.putAll(provider.fetchData(accountIdentifier, entity, dataPoints));
    }
    return data;
  }

  private void compute(Map<String, Map<String, Object>> data, List<ScorecardEntity> scorecards) {
    // ======= compute =======

    // catalog - API -> [DP4, DP3, ....]

    // for each DSL
    //   Map<String, String>

    // compute score
    // scores collection {scorecard -> score... }
    // }

    // ======= compute =======
  }
}
