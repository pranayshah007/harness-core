/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import static software.wings.beans.Service.ServiceKeys;

import static java.lang.String.format;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanupInfraMappingsForDeletedServices implements Migration {
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;

  private static final String DEBUG_LINE = "LOG_ORPHAN_IM_FOR_DELETED_SERVICES";

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + " Beginning of migration");

    final List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);

    Set<String> orphanedInfraMappings = new HashSet<>();
    for (Account account : allAccounts) {
      final String accountId = account.getUuid();
      log.info(DEBUG_LINE + " Beginning of migration for account ID: " + accountId);

      try {
        Query<InfrastructureMapping> infrastructureMappingQuery =
            wingsPersistence.createQuery(InfrastructureMapping.class)
                .field(InfrastructureMappingKeys.accountId)
                .equal(accountId);
        final Set<String> serviceIds = getServiceIdsForAccount(accountId);

        try (HIterator<InfrastructureMapping> iterator = new HIterator<>(infrastructureMappingQuery.fetch())) {
          for (InfrastructureMapping infrastructureMapping : iterator) {
            if (shouldDeleteInfraMapping(serviceIds, infrastructureMapping)) {
              log.info(format("%s Cleaning up infra mapping %s for account ID: %s", DEBUG_LINE,
                  infrastructureMapping.getUuid(), accountId));
              orphanedInfraMappings.add(infrastructureMapping.getUuid());
            }
          }
        }

        log.info(DEBUG_LINE + " End of migration for account ID : " + accountId);

      } catch (Exception e) {
        log.error(DEBUG_LINE + " Exception in marking orphaned infra mappings for account: " + accountId);
      }
    }

    log.info(format("The following orphaned infra mappings need to be deleted: %s", orphanedInfraMappings));

    log.info(DEBUG_LINE + " End of migration");
  }

  private Set<String> getServiceIdsForAccount(String accountId) {
    return wingsPersistence.createQuery(Service.class)
        .field(ServiceKeys.accountId)
        .equal(accountId)
        .project("_id", true)
        .asList()
        .stream()
        .map(Service::getUuid)
        .collect(Collectors.toSet());
  }

  private boolean shouldDeleteInfraMapping(Set<String> serviceIds, InfrastructureMapping infrastructureMapping) {
    return !serviceIds.contains(infrastructureMapping.getServiceId());
  }
}
