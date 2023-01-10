/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.backup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class InstanceSyncPTBackupServiceImpl implements InstanceSyncPTBackupService {
  @Inject private InstanceSyncPTInfoBackupDao instanceSyncPTInfoBackupDao;

  @Override
  public void save(String accountId, String infrastructureMappingId, PerpetualTaskRecord perpetualTaskRecord) {
    InstanceSyncPTInfoBackup instanceSyncPTBackup =
        instanceSyncPTInfoBackupDao.findByAccountIdAndInfraMappingId(accountId, infrastructureMappingId)
            .orElseGet(()
                           -> InstanceSyncPTInfoBackup.builder()
                                  .accountId(accountId)
                                  .infrastructureMappingId(infrastructureMappingId)
                                  .perpetualTaskRecordIds(new HashSet<>())
                                  .build());

    if (instanceSyncPTBackup.getPerpetualTaskRecordIds().contains(perpetualTaskRecord.getUuid())) {
      return;
    }
    instanceSyncPTBackup.getPerpetualTaskRecordIds().add(perpetualTaskRecord.getUuid());
    List<PerpetualTaskRecord> records = new ArrayList<>(instanceSyncPTBackup.getPerpetualTaskRecords());
    records.add(perpetualTaskRecord);
    instanceSyncPTBackup.setPerpetualTaskRecords(records);
    instanceSyncPTInfoBackupDao.save(instanceSyncPTBackup);
  }

  @Override
  public void restore(String accountId, String infrastructureMappingId, Consumer<PerpetualTaskRecord> consumer) {
    Optional<InstanceSyncPTInfoBackup> instanceSyncPTBackupOptional =
        instanceSyncPTInfoBackupDao.findByAccountIdAndInfraMappingId(accountId, infrastructureMappingId);
    if (instanceSyncPTBackupOptional.isEmpty()) {
      log.warn("Unable to find any instance sync PT backup for account {} and infra mapping {}", accountId,
          infrastructureMappingId);
      return;
    }

    InstanceSyncPTInfoBackup instanceSyncPTBackup = instanceSyncPTBackupOptional.get();
    Set<String> perpetualTasksRecreated = new HashSet<>();
    for (PerpetualTaskRecord perpetualTaskRecord : instanceSyncPTBackup.getPerpetualTaskRecords()) {
      log.info("Restore perpetual task {} with old uuid {} of type {}", perpetualTaskRecord.getTaskDescription(),
          perpetualTaskRecord.getUuid(), perpetualTaskRecord.getPerpetualTaskType());
      try {
        consumer.accept(perpetualTaskRecord);
      } catch (Exception e) {
        log.warn("Unable to restore perpetual task {}", perpetualTaskRecord.getUuid());
        continue;
      }

      perpetualTasksRecreated.add(perpetualTaskRecord.getUuid());
    }

    instanceSyncPTBackup.getPerpetualTaskRecordIds().removeAll(perpetualTasksRecreated);
    if (instanceSyncPTBackup.getPerpetualTaskRecordIds().isEmpty()) {
      instanceSyncPTInfoBackupDao.delete(instanceSyncPTBackup);
    } else {
      log.warn("Unable to restore all perpetual tasks. PTs left: {}", instanceSyncPTBackup.getPerpetualTaskRecordIds());
      instanceSyncPTInfoBackupDao.save(instanceSyncPTBackup);
    }
  }
}
