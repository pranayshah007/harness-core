/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "LogAnalysisClusterKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "logAnalysisClusters", noClassnameStored = true)
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.CVNG)
public final class LogAnalysisCluster implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(LogAnalysisClusterKeys.verificationTaskId)
                 .field(LogAnalysisClusterKeys.isEvicted)
                 .build())
        .build();
  }

  @Id private String uuid;
  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;
  private String verificationTaskId;
  private Instant analysisStartTime;
  private Instant analysisEndTime;
  @FdIndex private String accountId;
  private long analysisMinute;
  private long label;
  private List<Frequency> frequencyTrend;
  private String text;
  private boolean isEvicted;
  private long firstSeenTime;
  private double x;
  private double y;

  public List<Frequency> getFrequencyTrend() {
    if (frequencyTrend == null) {
      return new ArrayList<>();
    }
    return frequencyTrend;
  }

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "FrequencyKeys")
  public static class Frequency implements Bson {
    Integer count;
    Long timestamp;
    Double riskScore;

    @Override
    public <TDocument> BsonDocument toBsonDocument(Class<TDocument> aClass, CodecRegistry codecRegistry) {
      BsonDocument bsonDocument = new BsonDocument();
      if (count != null) {
        bsonDocument.append(FrequencyKeys.count, new BsonInt64(count));
      }
      if (timestamp != null) {
        bsonDocument.append(FrequencyKeys.timestamp, new BsonInt64(timestamp));
      }
      if (riskScore != null) {
        bsonDocument.append(FrequencyKeys.riskScore, new BsonDouble(riskScore));
      }
      return bsonDocument;
    }
  }

  @PrePersist
  public void updateValidUntil() {
    if (isEvicted) {
      validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
    }
  }
}
