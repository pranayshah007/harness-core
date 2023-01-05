package software.wings.service.impl.instance.backup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "InstanceSyncPTBackupKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceSyncPerpetualTasksInfoBackup", noClassnameStored = true)
public class InstanceSyncPTInfoBackup implements PersistentEntity, UuidAware, UuidAccess, AccountAccess,
                                                 CreatedAtAccess, CreatedAtAware, UpdatedAtAware, UpdatedAtAccess {
  @Id String uuid;
  @FdIndex String accountId;
  @FdIndex String infrastructureMappingId;
  @Singular List<PerpetualTaskRecord> perpetualTaskRecords;
  Set<String> perpetualTaskRecordIds;

  long createdAt;
  long lastUpdatedAt;
}
