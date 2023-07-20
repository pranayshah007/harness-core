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
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class GovernanceRuleResourceTypeMigration implements NGMigration {
  // This resource type would be used in governance enforcement telemetry for existing rules
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all Rules, adding resourceType to rules");
      final List<Rule> ruleList =
          hPersistence.createQuery(Rule.class, excludeAuthority).filter(RuleId.resourceType, null).asList();
      for (final Rule rule : ruleList) {
        // For every rule where resource Type is null
        try {
          migrateResourceTypeForRule(rule.getAccountId(), rule.getUuid(), rule.getRulesYaml());
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ruleId {}, {}", rule.getAccountId(), rule.getUuid(), e);
        }
      }
      log.info("GovernanceRuleResourceTypeMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in GovernanceRuleResourceTypeMigration", e);
    }
  }

  private void migrateResourceTypeForRule(final String accountId, final String ruleUuid, final String ruleYaml) {
    Query query = hPersistence.createQuery(Rule.class)
                      .field(RuleId.accountId)
                      .equal(accountId)
                      .field(RuleId.uuid)
                      .equal(ruleUuid);
    UpdateOperations<Rule> updateOperations = hPersistence.createUpdateOperations(Rule.class);

    updateOperations.set(RuleId.resourceType, getResourceType(ruleYaml));
    hPersistence.update(query, updateOperations);
  }

  private String getResourceType(String ruleYaml) {
    Yaml yaml = new Yaml();
    Map<String, Object> ruleYamlMap = yaml.load(ruleYaml);
    ArrayList<Object> policies = (ArrayList<Object>) ruleYamlMap.get("policies");
    if (policies != null && policies.size() >= 1) {
      Map<String, Object> policyMap = (Map) policies.get(0);
      return (String) policyMap.get("resource");
    }
    return null;
  }
}
