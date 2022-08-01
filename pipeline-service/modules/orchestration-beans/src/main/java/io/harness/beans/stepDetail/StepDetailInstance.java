/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.stepDetail;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ToBeDeleted;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldNameConstants(innerTypeName = "StepDetailInstanceKeys")
@Entity(value = "stepDetailInstance", noClassnameStored = true)
@Document("stepDetailInstance")
@TypeAlias("stepDetailInstance")
@StoreIn(DbAliases.PMS)
@ToBeDeleted
// Delete after six months from 27-Jan
public class StepDetailInstance {
  public static final long TTL_MONTHS = 6;

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  String name;
  String planExecutionId;
  String nodeExecutionId;
  PmsStepDetails stepDetails;

  PmsStepParameters resolvedInputs;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("nodeExecutionId_name_unique_idx")
                 .field(StepDetailInstanceKeys.nodeExecutionId)
                 .field(StepDetailInstanceKeys.name)
                 .unique(true)
                 .build())
        .build();
  }

  public static StepDetailInstance cloneForRetry(
      StepDetailInstance stepDetailInstance, String newPlanExecutionId, String newNodeExecutionId) {
    return StepDetailInstance.builder()
        .name(stepDetailInstance.getName())
        .stepDetails(stepDetailInstance.getStepDetails())
        .nodeExecutionId(newNodeExecutionId)
        .resolvedInputs(stepDetailInstance.getResolvedInputs())
        .planExecutionId(newPlanExecutionId)
        .build();
  }
}
