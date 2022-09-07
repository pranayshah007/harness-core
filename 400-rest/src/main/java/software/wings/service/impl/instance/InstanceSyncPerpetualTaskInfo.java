/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAccess;
import io.harness.persistence.UuidAware;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "InstanceSyncPerpetualTaskInfoKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "instanceSyncPerpetualTasksInfo", noClassnameStored = true)
public class InstanceSyncPerpetualTaskInfo implements PersistentEntity, UuidAware, UuidAccess, AccountAccess,
                                                      CreatedAtAccess, CreatedAtAware, UpdatedAtAware, UpdatedAtAccess {
  @Id String uuid;
  @FdIndex String accountId;
  @FdIndex String infrastructureMappingId;
  @Singular List<String> perpetualTaskIds;
  long createdAt;
  long lastUpdatedAt;
}
