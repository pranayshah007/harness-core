/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.capability;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

// The set of capability that is being used
@Data
@Builder
@FieldNameConstants(innerTypeName = "CapabilitySubjectPermissionKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "capabilitySubjectPermission", noClassnameStored = true)
@TargetModule(HarnessModule._460_CAPABILITY)
public final class CapabilitySubjectPermission implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("byDelegate")
                 .unique(true)
                 .field(CapabilitySubjectPermissionKeys.delegateId)
                 .field(CapabilitySubjectPermissionKeys.capabilityId)
                 .build())
        .build();
  }

  // ID for individual entry
  @Id private String uuid;
  @FdIndex private String accountId;

  // The only valid entity type is delegate right now
  private String delegateId;
  @FdIndex private String capabilityId;

  // Moment in time until the existing check of the capability can be considered as valid
  @FdIndex private long maxValidUntil;

  // Moment in time after which the capability should be re-validated again
  @FdIndex private long revalidateAfter;

  // This is when mongo will delete the record
  @FdTtlIndex private Date validUntil;

  // Capability result: whether it is valid or not
  public enum PermissionResult { ALLOWED, DENIED, UNCHECKED }
  private PermissionResult permissionResult;
}
