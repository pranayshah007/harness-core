/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import java.util.List;
import java.util.Objects;
import com.google.inject.Inject;

import static io.harness.persistence.HQuery.excludeAuthority;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class GovernanceSavingsMigration implements NGMigration{
    @Inject private HPersistence hPersistence;
    @Inject private RuleExecutionDAO ceRuleExecutionRecordDao;

    @Override
    public void migrate() {

        try {
            log.info("Starting migration (updates) of all CE Governance Rule Executions");
            final List<RuleExecution> ruleExecutionRecords =
                    hPersistence.createQuery(RuleExecution.class, excludeAuthority).asList();
            for (final RuleExecution ruleExecution : ruleExecutionRecords) {
                try {
                    if (Objects.isNull(ruleExecution.getPotentialSavings()) || Objects.isNull(ruleExecution.getRealizedSavings())) {
                        ruleExecution.setPotentialSavings(0);
                        ruleExecution.setRealizedSavings(0);
                        ceRuleExecutionRecordDao.update(ruleExecution);
                        log.info("Updated the governance rule executions record with savings for account {}, GovernanceExecutionId {}", ruleExecution.getAccountId(), ruleExecution.getUuid());
                    }
                } catch (final Exception e) {
                    log.error("Migration Failed for Account {}, GovernanceExecutionId {}", ruleExecution.getAccountId(),
                            ruleExecution.getUuid(), e);
                }
            }
            log.info("Governance Savings Migration finished!");
        } catch (final Exception e) {
            log.error("Failure occurred in GovernanceSavingsMigration", e);
        }
    }
}

