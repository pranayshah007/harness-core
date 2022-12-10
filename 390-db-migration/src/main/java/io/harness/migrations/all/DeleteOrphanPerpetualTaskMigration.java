/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static java.lang.String.format;
import static org.reflections.Reflections.log;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.perpetualtask.PerpetualTaskService;

import software.wings.beans.Account;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskInfo;
import software.wings.service.impl.instance.InstanceSyncPerpetualTaskService;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

@OwnedBy(HarnessTeam.CDP)
public class DeleteOrphanPerpetualTaskMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;

  private final String DEBUG_LINE = "DELETE_ORPHAN_PTASK_MIGRATION: ";

  @Override
  public void migrate() {
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      Set<String> pTasksToBeDeleted = new HashSet<>();
      Set<String> instanceSyncPTaskInfoToBeDeleted = new HashSet<>();
      String accountId = account.getUuid();
      log.info(
          StringUtils.join(DEBUG_LINE, "Starting delete orphaned perpetual task migration for accountId:", accountId));

      try {
        List<Key<InfrastructureMapping>> infraKeyList = wingsPersistence.createQuery(InfrastructureMapping.class)
                                                            .field(InfrastructureMappingKeys.accountId)
                                                            .equal(accountId)
                                                            .asKeyList();
        Set<String> infraMappingsForAccount =
            infraKeyList.stream().map(key -> (String) key.getId()).collect(Collectors.toCollection(HashSet::new));

        List<InstanceSyncPerpetualTaskInfo> instanceSyncPerpetualTaskInfos =
            wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class)
                .field(InstanceSyncPerpetualTaskInfo.ACCOUNT_ID_KEY)
                .equal(accountId)
                .asList();

        Map<String, List<String>> infraMappingsPTasksMapForAccount = instanceSyncPerpetualTaskInfos.stream().collect(
            Collectors.toMap(InstanceSyncPerpetualTaskInfo::getInfrastructureMappingId,
                InstanceSyncPerpetualTaskInfo::getPerpetualTaskIds));

        Map<String, String> infraMappingsInstanceSyncPTaskInfoMap =
            instanceSyncPerpetualTaskInfos.stream().collect(Collectors.toMap(
                InstanceSyncPerpetualTaskInfo::getInfrastructureMappingId, InstanceSyncPerpetualTaskInfo::getUuid));

        infraMappingsPTasksMapForAccount.forEach((infraMappingId, pTasks) -> {
          if (!infraMappingsForAccount.contains(infraMappingId)
              && StringUtils.isNotEmpty(infraMappingsInstanceSyncPTaskInfoMap.get(infraMappingId))) {
            pTasksToBeDeleted.addAll(pTasks);
            instanceSyncPTaskInfoToBeDeleted.add(infraMappingsInstanceSyncPTaskInfoMap.get(infraMappingId));
          }
        });

        log.info(format("Perpetual tasks to be deleted %s", pTasksToBeDeleted));

        pTasksToBeDeleted.forEach(pTaskId -> {
          try {
            perpetualTaskService.deleteTask(accountId, pTaskId);
            log.info(StringUtils.join(DEBUG_LINE,
                format("Successfully deleted orphaned perpetualTask %s for acountId %s", pTaskId, accountId)));
          } catch (Exception ex) {
            log.error(StringUtils.join(DEBUG_LINE,
                format("Error deleting orphaned perpetualTask %s for accountId %s", pTaskId, accountId),
                ex.getMessage()));
          }
        });

        try {
          Query<InstanceSyncPerpetualTaskInfo> query = wingsPersistence.createQuery(InstanceSyncPerpetualTaskInfo.class)
                                                           .field(InstanceSyncPerpetualTaskInfo.ACCOUNT_ID_KEY)
                                                           .equal(accountId)
                                                           .field(InstanceSyncPerpetualTaskInfo.UUID_KEY)
                                                           .in(instanceSyncPTaskInfoToBeDeleted);

          wingsPersistence.delete(query);

          log.info(StringUtils.join(DEBUG_LINE,
              format(
                  "Successfully deleted all InstanceSyncPerpetualTaskInfo corresponds to orphaned perpetualTasks for accountId %s",
                  accountId)));

        } catch (Exception ex) {
          log.error(StringUtils.join(
              DEBUG_LINE, format("Error deleting InstanceSyncPTaskInfo for accountId %s", accountId), ex.getMessage()));
        }

      } catch (Exception ex) {
        log.error(StringUtils.join(
            DEBUG_LINE, format("Error deleting orphaned perpetualTasks for accountId %s", accountId), ex.getMessage()));
      }
      log.info(StringUtils.join(DEBUG_LINE,
          format("Successfully deleted all InstanceSyncPerpetualTaskInfo and orphaned perpetualTasks for accountId %s",
              accountId)));
    }
  }
}
