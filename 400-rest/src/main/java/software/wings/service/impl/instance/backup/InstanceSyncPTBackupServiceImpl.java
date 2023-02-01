/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.backup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class InstanceSyncPTBackupServiceImpl implements InstanceSyncPTBackupService {
  @Inject private InstanceSyncPTInfoBackupDao instanceSyncPTInfoBackupDao;

  @Override
  public void save(String accountId, String infrastructureMappingId, PerpetualTaskRecord perpetualTaskRecord) {
    Optional<InstanceSyncPTInfoBackup> instanceSyncPTBackup =
        instanceSyncPTInfoBackupDao.findByAccountIdAndPerpetualTaskId(accountId, perpetualTaskRecord.getUuid());
    if (instanceSyncPTBackup.isEmpty()) {
      InstanceSyncPTInfoBackup instanceSyncPTBackupToSave =
          InstanceSyncPTInfoBackup.builder()
              .accountId(accountId)
              .infrastructureMappingId(infrastructureMappingId)
              .perpetualTaskRecordId(isNotEmpty(perpetualTaskRecord.getUuid()) ? perpetualTaskRecord.getUuid()
                                                                               : UUIDGenerator.generateUuid())
              .perpetualTaskRecord(perpetualTaskRecord)
              .build();

      instanceSyncPTInfoBackupDao.save(instanceSyncPTBackupToSave);
    }
  }

  @Override
  public void restore(String accountId, String infrastructureMappingId, Consumer<PerpetualTaskRecord> consumer) {
    List<InstanceSyncPTInfoBackup> instanceSyncPTBackupList =
        instanceSyncPTInfoBackupDao.findAllByAccountIdAndInfraMappingId(accountId, infrastructureMappingId);
    if (instanceSyncPTBackupList.isEmpty()) {
      log.warn("Unable to find any instance sync PT backup for account {} and infra mapping {}", accountId,
          infrastructureMappingId);
      return;
    }
    for (InstanceSyncPTInfoBackup instanceSyncPTBackup : instanceSyncPTBackupList) {
      log.info("Restore perpetual task {} with old uuid {} of type {}",
          instanceSyncPTBackup.getPerpetualTaskRecord().getTaskDescription(),
          instanceSyncPTBackup.getPerpetualTaskRecord().getUuid(),
          instanceSyncPTBackup.getPerpetualTaskRecord().getPerpetualTaskType());
      try {
        consumer.accept(instanceSyncPTBackup.getPerpetualTaskRecord());
      } catch (Exception e) {
        log.warn("Unable to restore perpetual task {}", instanceSyncPTBackup.getPerpetualTaskRecord().getUuid());
      }
      instanceSyncPTInfoBackupDao.delete(instanceSyncPTBackup);
    }
  }
}
