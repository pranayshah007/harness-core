/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.entities;

import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "StreamingBatchKeys")
@StoreIn(DbAliases.AUDITS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("streamingBatches")
@TypeAlias("StreamingBatch")
public class StreamingBatch {
  @Id String id;

  @NotBlank String streamingDestinationIdentifier;
  @NotBlank String accountIdentifier;
  @NotNull Long startTime;
  @NotNull Long endTime;
  Long lastSuccessfulRecordTimestamp;
  Long numberOfRecords;
  Long numberOfRecordsPublished;
  @NotNull BatchStatus status;
  int retryCount;
  @Valid BatchFailureInfo failureInfo;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_streamingDestinationIdentifier_unique_index")
                 .field(StreamingBatchKeys.accountIdentifier)
                 .field(StreamingBatchKeys.streamingDestinationIdentifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_status_retryCount_index")
                 .field(StreamingBatchKeys.accountIdentifier)
                 .field(StreamingBatchKeys.status)
                 .field(StreamingBatchKeys.retryCount)
                 .build())
        .build();
  }
}
