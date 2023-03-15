/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "PlanExecutionExpansionKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionExpansions", noClassnameStored = true)
@org.springframework.data.mongodb.core.mapping.Document("planExecutionExpansions")
@TypeAlias("planExecutionExpansion")
public class PlanExecutionExpansion {
  /**
   * This contains the expanded json for every node execution
   * For example:
   * pipeline:
   *   name: "pip1"
   *   identifier: "pip1"
   *   stepInputs: ..
   *   stages:
   *      name: stages
   *      identifier: stages
   *      stepInputs: ...
   *      outcomes:
   *        key1: OutcomeObject1
   *        key2: OutcomeObject2
   *      stage1:
   *         name: stage1
   *         identifier: stage1
   *         stepInputs: ...
   *         spec:
   *           name: "spec"
   *           identifier: "spec"
   *           stepInputs: ...
   *           execution:
   *              ....
   *
   */
  Document expandedJson;

  @Wither @Id @dev.morphia.annotations.Id String uuid;

  @FdIndex String planExecutionId;

  // We need to delete this document after a month
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
}
