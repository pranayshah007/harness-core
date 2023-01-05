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
