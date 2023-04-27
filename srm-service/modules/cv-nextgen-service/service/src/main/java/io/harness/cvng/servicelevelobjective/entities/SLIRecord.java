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
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "SLIRecordKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "sliRecords", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class SLIRecord extends VerificationTaskBase implements PersistentEntity, UuidAware, Bson {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("sli_timestamp")
                 .field(SLIRecordKeys.sliId)
                 .field(SLIRecordKeys.timestamp)
                 .build())
        .build();
  }

  @Override
  public <TDocument> BsonDocument toBsonDocument(Class<TDocument> aClass, CodecRegistry codecRegistry) {
    BsonDocument bsonDocument = new BsonDocument();
    if (uuid != null) {
      bsonDocument.append(SLIRecordKeys.uuid, new BsonString(uuid));
    }
    bsonDocument.append(SLIRecordKeys.verificationTaskId, new BsonString(verificationTaskId));
    bsonDocument.append(SLIRecordKeys.sliId, new BsonString(sliId));
    bsonDocument.append(SLIRecordKeys.epochMinute, new BsonInt64(epochMinute));
    bsonDocument.append(SLIRecordKeys.runningBadCount, new BsonInt64(runningBadCount));
    bsonDocument.append(SLIRecordKeys.runningGoodCount, new BsonInt64(runningGoodCount));
    bsonDocument.append(SLIRecordKeys.sliVersion, new BsonInt32(sliVersion));
    bsonDocument.append(SLIRecordKeys.sliState, new BsonString(sliState.toString()));
    return bsonDocument;
  }

  public static class SLIRecordBuilder {
    public SLIRecord build() {
      SLIRecord sliRecord = unsafeBuild();
      sliRecord.setEpochMinute(TimeUnit.MILLISECONDS.toMinutes(timestamp.toEpochMilli()));
      return sliRecord;
    }
  }
  @Version @Deprecated long version;
  @Id private String uuid;
  @FdIndex private String verificationTaskId;
  private String sliId;
  private Instant timestamp; // minute
  @Setter(AccessLevel.PRIVATE) private long epochMinute;
  private SLIState sliState;
  private long runningBadCount; // prevMinuteRecord.runningBadCount + sliState == BAD ? 1 : 0
  private long runningGoodCount; // // prevMinuteRecord.runningGoodCount + sliState == GOOD ? 1 : 0

  private int sliVersion;
  public enum SLIState { NO_DATA, GOOD, BAD, SKIP_DATA }

  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusDays(180).toInstant());
  @Data
  @Builder
  public static class SLIRecordParam {
    private SLIState sliState;
    private Instant timeStamp;
    private Long goodEventCount;
    private Long badEventCount;
  }
}
