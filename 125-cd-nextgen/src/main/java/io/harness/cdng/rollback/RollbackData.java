/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.rollback;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.utils.StageStatus;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@ToString
@FieldNameConstants(innerTypeName = "RollbackDataKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "rollbackData", noClassnameStored = true)
@Document("rollbackData")
@TypeAlias("rollbackData")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.NG_MANAGER)
@OwnedBy(HarnessTeam.CDP)
public class RollbackData implements PersistentEntity, UuidAware {
  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastModifiedAt;

  private StageStatus stageStatus;
  @NotNull private String stageExecutionId;
  @NotNull private RollbackDeploymentInfo value;
  @NotNull private String key;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("key_stageStatus_createdAt")
                 .field(RollbackDataKeys.key)
                 .field(RollbackDataKeys.stageStatus)
                 .descSortField(RollbackDataKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder().name("stageExecutionId").field(RollbackDataKeys.stageExecutionId).build())
        .add(CompoundMongoIndex.builder().name("key").field(RollbackDataKeys.key).build())
        .build();
  }
}
