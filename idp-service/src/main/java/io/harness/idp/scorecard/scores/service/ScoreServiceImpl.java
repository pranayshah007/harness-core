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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class ScoreServiceImpl implements ScoreService {
  ScorecardService scorecardService;
  DataSourceProviderFactory dataSourceProviderFactory;

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
