/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

@Slf4j
public class CleanupOrphanInfraMappings implements Migration {
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;

  private final String DEBUG_LINE = "CLEANUP_ORPHAN_IM";

  public void migrate() {
    logInfo("The Beginning");
    final List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      final String accountId = account.getUuid();
      logInfo("The Beginning for accountId " + accountId);
      try {
        final Set<String> infraDefinitions = wingsPersistence.createQuery(InfrastructureDefinition.class)
                                                 .field(InfrastructureDefinitionKeys.accountId)
                                                 .equal(accountId)
                                                 .asKeyList()
                                                 .stream()
                                                 .map(Key::getId)
                                                 .map(String.class ::cast)
                                                 .collect(toSet());

        final Query<InfrastructureMapping> infraMappingQuery = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                                   .field(InfrastructureMappingKeys.accountId)
                                                                   .equal(accountId);
        final Set<String> infraMappingsToDeleteForAccount = new HashSet<>();
        try (HIterator<InfrastructureMapping> iterator = new HIterator<>(infraMappingQuery.fetch())) {
          for (InfrastructureMapping infrastructureMapping : iterator) {
            if (deservesCleanup(infraDefinitions, infrastructureMapping)) {
              logInfo(
                  format("Cleaning up InfraMapping %s for accountId: %s", infrastructureMapping.getUuid(), accountId));
              infraMappingsToDeleteForAccount.add(infrastructureMapping.getUuid());
            }
          }
        }
        if (isNotEmpty(infraMappingsToDeleteForAccount)) {
          deleteInfraMapping(accountId, infraMappingsToDeleteForAccount);
        }
        logInfo("The End for accountId " + accountId);
      } catch (Exception e) {
        logError(format("Exception in deleting Orphan InfraMappings for account %s", accountId), e);
      }
    }
    logInfo("The End");
  }

  private void deleteInfraMapping(String accountId, Set<String> infraMappingsToDeleteForAccount) {
    final boolean done = wingsPersistence.delete(wingsPersistence.createQuery(InfrastructureMapping.class)
                                                     .field(InfrastructureMappingKeys.accountId)
                                                     .equal(accountId)
                                                     .field("_id")
                                                     .in(infraMappingsToDeleteForAccount));
    if (done) {
      logInfo(
          format("Deleted %d Orphan InfraMappings for account %s", infraMappingsToDeleteForAccount.size(), accountId));
    } else {
      logError(format("DB error in deleting Orphan InfraMappings for account %s", accountId));
    }
  }

  private boolean deservesCleanup(Set<String> infraDefinitions, InfrastructureMapping infrastructureMapping) {
    return isBlank(infrastructureMapping.getInfrastructureDefinitionId())
        || !infraDefinitions.contains(infrastructureMapping.getInfrastructureDefinitionId());
  }

  private void logError(String str) {
    log.error(DEBUG_LINE + SPACE + str);
  }

  private void logError(String str, Throwable t) {
    log.error(DEBUG_LINE + SPACE + str, t);
  }

  private void logInfo(String str) {
    log.info(DEBUG_LINE + SPACE + str);
  }

  private void logWarn(String str) {
    log.info(DEBUG_LINE + SPACE + str);
  }
}
