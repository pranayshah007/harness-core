/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.Date;
import java.util.List;
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
@OwnedBy(CI)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CIResourceCleanupResponseKeys")
@StoreIn(DbAliases.CIMANAGER)
@Entity(value = "ciResourceCleanup", noClassnameStored = true)
@Document("ciResourceCleanup")
@TypeAlias("ciResourceCleanup")
@RecasterAlias("io.harness.ci.beans.entities.CIResourceCleanup")
@HarnessEntity(exportable = false)
public class CIResourceCleanup implements UuidAware, PersistentEntity, CreatedAtAware, UpdatedAtAware {
  @Id @dev.morphia.annotations.Id String uuid;
  @NotNull String accountId;
  @NotNull String planExecutionId;
  @FdIndex @NotNull String stageExecutionId;
  @NotNull String type;
  @NotNull long processAfter;
  @NotNull boolean shouldStart;
  @NotNull long createdAt;
  @NotNull long lastUpdatedAt;
  @NotNull int retryCount;
  @FdTtlIndex(24 * 60 * 60) Date validUntil;
  @NotNull byte[] data;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("processAfterAndShouldStartAndType")
                 .field(CIResourceCleanupResponseKeys.processAfter)
                 .field(CIResourceCleanupResponseKeys.shouldStart)
                 .field(CIResourceCleanupResponseKeys.type)
                 .build())
        .build();
  }
}