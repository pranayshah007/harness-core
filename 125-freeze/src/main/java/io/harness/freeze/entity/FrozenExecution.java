/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "FrozenExecutionKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "frozenExecution", noClassnameStored = true)
@Document("frozenExecution")
@HarnessEntity(exportable = true)
public class FrozenExecution {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("unique_accountId_orgId_projectId_planExecutionId_stageExecutionId_stepExecutionId_idx")
                 .field(FrozenExecutionKeys.accountId)
                 .field(FrozenExecutionKeys.orgId)
                 .field(FrozenExecutionKeys.projectId)
                 .field(FrozenExecutionKeys.planExecutionId)
                 .field(FrozenExecutionKeys.stageExecutionId)
                 .field(FrozenExecutionKeys.stepExecutionId)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String uuid;
  @NotEmpty String accountId;
  @NotEmpty String orgId;
  @NotEmpty String projectId;
  @NotEmpty String pipelineId;
  @NotEmpty String planExecutionId;
  @NotEmpty String stageExecutionId;
  @NotEmpty String stageNodeId;
  String stepNodeId;
  String stepExecutionId;
  String stepType;
  List<FreezeSummaryResponseDTO> manualFreezeList;
  List<FreezeSummaryResponseDTO> globalFreezeList;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
}
