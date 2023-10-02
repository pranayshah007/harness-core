/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.Rule.RuleId;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class GovernanceRuleCloudProviderMigration implements NGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all Rules, adding default cloud provider as AWS");
      final List<Rule> ruleList =
          hPersistence.createQuery(Rule.class, excludeAuthority).filter(RuleId.cloudProvider, null).asList();
      for (final Rule rule : ruleList) {
        // For every rule where cloud provider is null, we are defaulting it to AWS
        try {
          migrateCloudProviderForRule(rule.getAccountId(), rule.getUuid());
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ruleId {}, {}", rule.getAccountId(), rule.getUuid(), e);
        }
      }
      log.info("GovernanceRuleCloudProviderMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in GovernanceRuleCloudProviderMigration", e);
    }
  }

  private void migrateCloudProviderForRule(final String accountId, final String ruleUuid) {
    Query query = hPersistence.createQuery(Rule.class)
                      .field(RuleId.accountId)
                      .equal(accountId)
                      .field(RuleId.uuid)
                      .equal(ruleUuid);
    UpdateOperations<Rule> updateOperations = hPersistence.createUpdateOperations(Rule.class);

    updateOperations.set(RuleId.cloudProvider, RuleCloudProviderType.AWS);
    hPersistence.update(query, updateOperations);
  }
}
