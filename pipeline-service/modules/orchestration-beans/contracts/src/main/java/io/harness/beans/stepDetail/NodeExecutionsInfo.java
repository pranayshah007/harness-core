/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.stepDetail;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "NodeExecutionsInfoKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "nodeExecutionsInfo", noClassnameStored = true)
@Document("nodeExecutionsInfo")
@TypeAlias("nodeExecutionsInfo")
public class NodeExecutionsInfo {
  public static final long TTL_MONTHS = 6;

  @Id @dev.morphia.annotations.Id String uuid;
  String planExecutionId;
  String nodeExecutionId;
  @Singular("stepDetails") List<NodeExecutionDetailsInfo> nodeExecutionDetailsInfoList;
  PmsStepParameters resolvedInputs;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
  ConcurrentChildInstance concurrentChildInstance;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("nodeExecutionId_unique_idx")
                 .field(NodeExecutionsInfoKeys.nodeExecutionId)
                 .unique(true)
                 .build())
        // Used by ttl update
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_idx")
                 .field(NodeExecutionsInfoKeys.planExecutionId)
                 .build())
        .build();
  }
}
