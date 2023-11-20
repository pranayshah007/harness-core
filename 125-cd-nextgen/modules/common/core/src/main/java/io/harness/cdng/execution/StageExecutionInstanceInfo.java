/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "StageExecutionInstanceInfoKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "stageExecutionInstanceInfo", noClassnameStored = true)
@Document("stageExecutionInstanceInfo")
@TypeAlias("stageExecutionInstanceInfo")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDP)
public class StageExecutionInstanceInfo implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  @NotNull private String accountIdentifier;
  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String pipelineExecutionId;
  @NotNull private String stageExecutionId;
  @NotNull private List<StepExecutionInstanceInfo> instanceInfos;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_idx")
                 .unique(true)
                 .field(StageExecutionInstanceInfoKeys.accountIdentifier)
                 .field(StageExecutionInstanceInfoKeys.orgIdentifier)
                 .field(StageExecutionInstanceInfoKeys.projectIdentifier)
                 .field(StageExecutionInstanceInfoKeys.pipelineExecutionId)
                 .field(StageExecutionInstanceInfoKeys.stageExecutionId)
                 .build())
        .build();
  }
}
