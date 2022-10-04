/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.validation.Update;

import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * The search lock bean based on
 * Mongo TTL indexes
 *
 * @author utkarsh
 */
@OwnedBy(PL)
@Value
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "searchDistributedLocks", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "SearchDistributedLockKeys")
public class SearchDistributedLock implements PersistentEntity, CreatedAtAccess {
  @Id @NotNull(groups = {Update.class}) private String name;
  @FdIndex @NotNull private String uuid;
  @FdTtlIndex(70) @NotNull private Date heartbeat;
  @NotNull private long createdAt;
}
