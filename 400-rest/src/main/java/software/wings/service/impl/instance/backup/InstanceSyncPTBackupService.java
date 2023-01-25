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

import java.util.function.Consumer;

@OwnedBy(CDP)
public interface InstanceSyncPTBackupService {
  void save(String accountId, String infrastructureMappingId, PerpetualTaskRecord perpetualTaskRecord);

  void restore(String accountId, String infrastructureMappingId, Consumer<PerpetualTaskRecord> consumer);
}
