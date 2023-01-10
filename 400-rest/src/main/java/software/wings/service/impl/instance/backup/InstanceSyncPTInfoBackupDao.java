/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.backup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.Optional;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncPTInfoBackupDao {
  @Inject private HPersistence persistence;

  void save(InstanceSyncPTInfoBackup instanceSyncPTInfoBackup) {
    persistence.save(instanceSyncPTInfoBackup);
  }

  void delete(InstanceSyncPTInfoBackup instanceSyncPTInfoBackup) {
    persistence.delete(instanceSyncPTInfoBackup);
  }

  Optional<InstanceSyncPTInfoBackup> findByAccountIdAndInfraMappingId(String accountId, String infraMappingId) {
    Query<InstanceSyncPTInfoBackup> query =
        persistence.createQuery(InstanceSyncPTInfoBackup.class)
            .filter(InstanceSyncPTInfoBackup.ACCOUNT_ID_KEY, accountId)
            .filter(InstanceSyncPTInfoBackup.InstanceSyncPTBackupKeys.infrastructureMappingId, infraMappingId);
    return Optional.ofNullable(query.get());
  }
}
