/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SLIRecordBucketKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "sliRecordBuckets", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class SLIRecordBucket extends VerificationTaskBase implements PersistentEntity, UuidAware, Bson {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("slibucket_timestamp")
                 .field(SLIRecordBucketKeys.sliId)
                 .field(SLIRecordBucketKeys.bucketStartTime)
                 .build())
        .build();
  }

  @Override
  public <TDocument> BsonDocument toBsonDocument(Class<TDocument> aClass, CodecRegistry codecRegistry) {
    BsonDocument bsonDocument = new BsonDocument();
    if (uuid != null) {
      bsonDocument.append(SLIRecordBucketKeys.uuid, new BsonString(uuid));
    }
    bsonDocument.append(SLIRecordBucketKeys.sliId, new BsonString(sliId));
    bsonDocument.append(SLIRecordBucketKeys.runningBadCount, new BsonInt64(runningBadCount));
    bsonDocument.append(SLIRecordBucketKeys.runningGoodCount, new BsonInt64(runningGoodCount));
    bsonDocument.append(SLIRecordBucketKeys.sliVersion, new BsonInt32(sliVersion));
    bsonDocument.append(SLIRecordBucketKeys.sliStates, new BsonString(sliStates.toString()));
    return bsonDocument;
  }
  @Id private String uuid;
  private String sliId;
  private Instant bucketStartTime; // will be 5-minute buckets
  private List<SLIState> sliStates;
  private long runningBadCount; // will store badCount till the bucket end time
  private long runningGoodCount; // will store goodCount till the bucket end time

  private int sliVersion;

  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusDays(90).toInstant());

  public static SLIRecordBucket getSLIRecordBucketFromSLIRecords(List<SLIRecord> sliRecords) {
    Preconditions.checkArgument(sliRecords.size() == 5);
    Preconditions.checkArgument(sliRecords.get(0).getEpochMinute() % 5 == 0);
    List<SLIState> sliStates = new ArrayList<>();
    for (SLIRecord sliRecord : sliRecords) {
      sliStates.add(sliRecord.getSliState());
    }
    return SLIRecordBucket.builder()
        .sliId(sliRecords.get(0).getSliId())
        .bucketStartTime(sliRecords.get(0).getTimestamp())
        .sliVersion(sliRecords.get(0).getSliVersion())
        .runningBadCount(sliRecords.get(4).getRunningBadCount())
        .runningGoodCount(sliRecords.get(4).getRunningGoodCount())
        .sliStates(sliStates)
        .build();
  }

  public static List<SLIRecordBucket> getSLIRecordBucketsFromSLIRecords(List<SLIRecord> sliRecords) {
    Preconditions.checkArgument((sliRecords.size() % 5 == 0) && !sliRecords.isEmpty());
    List<SLIRecordBucket> sliRecordBuckets = new ArrayList<>();
    for (int idx = 0; idx < sliRecords.size(); idx += 5) {
      List<SLIState> sliStates = new ArrayList<>();
      for (int bucketIdx = idx; bucketIdx < idx + 5; bucketIdx++) {
        sliStates.add(sliRecords.get(bucketIdx).getSliState());
      }
      sliRecordBuckets.add(SLIRecordBucket.builder()
                               .sliId(sliRecords.get(idx).getSliId())
                               .bucketStartTime(sliRecords.get(idx).getTimestamp())
                               .sliVersion(sliRecords.get(idx).getSliVersion())
                               .runningBadCount(sliRecords.get(idx + 4).getRunningBadCount())
                               .runningGoodCount(sliRecords.get(idx + 4).getRunningGoodCount())
                               .sliStates(sliStates)
                               .build());
    }
    return sliRecordBuckets;
  }
}
