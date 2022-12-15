/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.PMS)
@Entity(value = "executionInputInstance", noClassnameStored = true)
@Document("executionInputInstance")
@FieldNameConstants(innerTypeName = "ExecutionInputInstanceKeys")
@TypeAlias("executionInputInstance")
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionInputInstance {
  public static final long TTL_MONTHS = 6;

  @Id @org.mongodb.morphia.annotations.Id String inputInstanceId;
  @FdUniqueIndex String nodeExecutionId;
  @CreatedDate Long createdAt;
  @CreatedDate Long validUntil;
  // TTL index
  @Builder.Default @FdTtlIndex Date expireAt = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
  String template;
  String userInput;
  String fieldYaml;
  Map<String, Object> mergedInputTemplate;
}