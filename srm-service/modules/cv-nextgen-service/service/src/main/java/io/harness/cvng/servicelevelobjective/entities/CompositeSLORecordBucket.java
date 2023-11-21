/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.analysis.entities.VerificationTaskBase;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "CompositeSLORecordBucketKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "compositeSLORecordBuckets", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class CompositeSLORecordBucket extends VerificationTaskBase implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("verificationtaskid_bucketstarttime")
                 .field(CompositeSLORecordBucketKeys.verificationTaskId)
                 .field(CompositeSLORecordBucketKeys.bucketStartTime)
                 .build())
        .build();
  }
  public static class CompositeSLORecordBucketBuilder {
    public CompositeSLORecordBucket build() {
      return unsafeBuild();
    }
  }
  @Version long version;
  @Id private String uuid;
  @FdIndex private String verificationTaskId;
  private Instant bucketStartTime; // minute
  private double runningBadCount;
  private double runningGoodCount;
  private Map<String, SLIRecordBucket> scopedIdentifierSLIRecordBucketMap;
  private int sloVersion;
  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusDays(92).toInstant());
}
