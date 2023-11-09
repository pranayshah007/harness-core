/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
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
    Query<CheckEntity> checkEntityQuery =
        persistence.createQuery(CheckEntity.class, excludeAuthority).filter(CheckEntity.CheckKeys.isCustom, true);
    try (HIterator<CheckEntity> checks = new HIterator<>(checkEntityQuery.fetch())) {
      while (checks.hasNext()) {
        migrateCheck(checks.next());
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
                .filter(DataPointEntity.DataPointKeys.accountIdentifier, "__GLOBAL_ACCOUNT_ID__");
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

      Query<CheckEntity> query = persistence.createQuery(CheckEntity.class)
                                     .filter(CheckEntity.CheckKeys.accountIdentifier, check.getAccountIdentifier())
                                     .filter(CheckEntity.CheckKeys.identifier, check.getIdentifier());

      UpdateResults updateOperationResult = persistence.update(query, updateOperations);
      if (updateOperationResult.getUpdatedCount() == 1) {
        log.info(String.format(
            "Added input values field for check %s, account %s", check.getIdentifier(), check.getAccountIdentifier()));
      } else {
        log.warn(String.format("Could not add input values field for check %s, account %s", check.getIdentifier(),
            check.getAccountIdentifier()));
      }
    } catch (Exception ex) {
      log.error(String.format("Unexpected error occurred while migrating check %s, account %s", check.getIdentifier(),
                    check.getAccountIdentifier()),
          ex);
    }
  }
}
