/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo.ExecutionSummaryUpdateInfoKeys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "GraphUpdateInfoKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "graphUpdateInfo", noClassnameStored = true)
@Document("graphUpdateInfo")
@TypeAlias("graphUpdateInfo")
public class GraphUpdateInfo implements PersistentEntity, UuidAware {
  public static final long TTL_WEEKS = 1;

  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  String planExecutionId;
  String nodeExecutionId;
  ExecutionSummaryUpdateInfo executionSummaryUpdateInfo;

  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusWeeks(TTL_WEEKS).toInstant());

  @UtilityClass
  public static class GraphUpdateInfoKeys {
    public static final String stepCategory =
        GraphUpdateInfoKeys.executionSummaryUpdateInfo + "." + ExecutionSummaryUpdateInfoKeys.stepCategory;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_planExecutionId_stepCategory_nodeExecutionId")
                 .unique(true)
                 .field(GraphUpdateInfoKeys.planExecutionId)
                 .field(GraphUpdateInfoKeys.stepCategory)
                 .field(GraphUpdateInfoKeys.nodeExecutionId)
                 .build())
        .build();
  }
}
