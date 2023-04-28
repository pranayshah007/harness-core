/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.triggers.TriggerPayload;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Data
@Builder(builderClassName = "Builder")
@FieldNameConstants(innerTypeName = "PlanExecutionMetadataKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionsMetadata", noClassnameStored = true)
@Document("planExecutionsMetadata")
@TypeAlias("planExecutionMetadata")
public class PlanExecutionMetadata implements PersistentEntity, UuidAware, PmsNodeExecutionMetadata {
  public static final long TTL_MONTHS = 6;

  @With @Id @dev.morphia.annotations.Id private String uuid;

  @With private String planExecutionId;

  // Merged input set given by the customer
  private String inputSetYaml;

  // Final yaml after merging input sets to given yaml, given to plan creation
  private String yaml;

  // Yaml having injectedUUid which is processed by PlanCreation
  @With private String processedYaml;

  // Expanded pipeline (after connectors, etc) in json format.
  private String expandedPipelineJson;

  private StagesExecutionMetadata stagesExecutionMetadata;
  private Boolean allowStagesExecution;
  private Boolean executionInputConfigured;
  @Wither private String triggerJsonPayload;
  @Wither private TriggerPayload triggerPayload;
  private Boolean notifyOnlyUser;
  @With String notes;

  @Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_idx")
                 .field(PlanExecutionMetadataKeys.planExecutionId)
                 .unique(true)
                 .build())
        .build();
  }

  @Override
  public NodeType forNodeType() {
    return NodeType.PLAN;
  }

  public boolean isStagesExecutionAllowed() {
    return allowStagesExecution != null && allowStagesExecution;
  }
}
