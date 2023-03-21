/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "MonitoringSourcePerpetualTaskKeys")
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "monitoringSourcePerpetualTasks", noClassnameStored = true)
@HarnessEntity(exportable = true)
public final class MonitoringSourcePerpetualTask
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_insert_index_v2")
                 .unique(true)
                 .field(MonitoringSourcePerpetualTaskKeys.accountId)
                 .field(MonitoringSourcePerpetualTaskKeys.orgIdentifier)
                 .field(MonitoringSourcePerpetualTaskKeys.projectIdentifier)
                 .field(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier)
                 .field(MonitoringSourcePerpetualTaskKeys.verificationType)
                 .field(MonitoringSourcePerpetualTaskKeys.connectorIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("perpetualTaskId_dataCollectionTaskIteration_idx")
                 .field(MonitoringSourcePerpetualTaskKeys.perpetualTaskId)
                 .field(MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotNull private String accountId;
  @FdIndex private long createdAt;
  private long lastUpdatedAt;

  @NotNull private String orgIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String monitoringSourceIdentifier;
  @NotNull private String connectorIdentifier;
  @NotNull private VerificationType verificationType;

  @FdIndex private Long dataCollectionTaskIteration;
  private String perpetualTaskId;
  private String dataCollectionWorkerId;

  private boolean isDemo;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration.equals(fieldName)) {
      this.dataCollectionTaskIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration.equals(fieldName)) {
      return this.dataCollectionTaskIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public enum VerificationType { LIVE_MONITORING, DEPLOYMENT }
}
