/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.migration.NGMigration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.spec.server.idp.v1.model.InputDetails;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.Rule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class MultipleInputValuesMigration implements NGMigration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding input values field in checks collection.");
    Query<CheckEntity> checkEntityQuery = persistence.createQuery(CheckEntity.class)
                                              .filter(CheckEntity.CheckKeys.isCustom, true)
                                              .filter(CheckEntity.CheckKeys.isDeleted, false);

    try (HIterator<CheckEntity> checks = new HIterator<>(checkEntityQuery.fetch())) {
      for (CheckEntity check : checks) {
        migrateCheck(check);
      }
    }

    log.info("Migration complete for adding input values field in checks collection.");
  }

  private void migrateCheck(CheckEntity check) {
    try {
      List<Rule> rules = new ArrayList<>();
      for (Rule rule : check.getRules()) {
        Query<DataPointEntity> dataPointEntityQuery =
            persistence.createQuery(DataPointEntity.class)
                .filter(DataPointEntity.DataPointKeys.identifier, rule.getDataPointIdentifier())
                .filter(DataPointEntity.DataPointKeys.dataSourceIdentifier, rule.getDataSourceIdentifier())
                .filter(DataPointEntity.DataPointKeys.accountIdentifier, check.getAccountIdentifier());
        DataPointEntity dataPoint = dataPointEntityQuery.asList().get(0);

        Rule updatedRule = new Rule();
        updatedRule.setDataPointIdentifier(rule.getDataPointIdentifier());
        updatedRule.setDataSourceIdentifier(rule.getDataSourceIdentifier());
        updatedRule.setOperator(rule.getOperator());
        updatedRule.setValue(rule.getValue());
        updatedRule.setConditionalInputValue(rule.getConditionalInputValue());

        List<InputDetails> inputsDetails = dataPoint.getInputDetails();
        if (inputsDetails.size() == 1) {
          InputValue inputValue = new InputValue();
          inputValue.setKey(inputsDetails.get(0).getKey());
          inputValue.setValue(rule.getConditionalInputValue());
          updatedRule.setInputValues(Collections.singletonList(inputValue));
        }
        rules.add(updatedRule);
      }

      UpdateOperations<CheckEntity> updateOperations = persistence.createUpdateOperations(CheckEntity.class);
      updateOperations.set(CheckEntity.CheckKeys.rules, rules);
      UpdateResults updateOperationResult = persistence.update(check, updateOperations);
      if (updateOperationResult.getUpdatedCount() > 0) {
        log.info("Added input values field successfully for {} records", updateOperationResult.getUpdatedCount());
      } else {
        log.warn("Could not add input values field in checks collection to any record");
      }
    } catch (Exception ex) {
      log.error(
          "DeletedEnvironmentInstancesCleanupMigration: Unexpected error occurred while migrating Instances for deleted environment",
          ex);
    }
  }
}
