/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.migration.NGMigration;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScorecardMigration implements NGMigration {
  static final String SCORECARD_MIGRATIONS_FOLDER_PATH = "migrations/scorecard/";
  @Inject ScoreService scoreService;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding data to scorecard related collections.");

    String checkEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "checks.json");
    String datapointEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "dataPoints.json");
    String datasourceEntities = loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "dataSources.json");
    String datasourceLocationEntities =
        loadResourceFileAsString(SCORECARD_MIGRATIONS_FOLDER_PATH + "datasourceLocations.json");

    log.info("Loaded scorecard entities json as string");

    scoreService.populateData(checkEntities, datapointEntities, datasourceEntities, datasourceLocationEntities);

    log.info("Migration complete for adding data to scorecard related collections.");
  }

  public String loadResourceFileAsString(String resourcePath) {
    try {
      return Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading resource {} as string. Error = {}", resourcePath, e.getMessage(), e);
      throw new UnexpectedException(
          "Error in loading resource " + resourcePath + " as string. Error = " + e.getMessage());
    }
  }
}
