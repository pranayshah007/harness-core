/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdSparseIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Duration;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "SRMAnalysisStepExecutionDetailsKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "srmAnalysisStepExecutionDetails", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class SRMAnalysisStepExecutionDetail
    implements CreatedAtAware, UpdatedAtAware, PersistentEntity, UuidAware, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_org_project_monitoredService_status_idx")
                 .field(SRMAnalysisStepExecutionDetailsKeys.accountId)
                 .field(SRMAnalysisStepExecutionDetailsKeys.orgIdentifier)
                 .field(SRMAnalysisStepExecutionDetailsKeys.projectIdentifier)
                 .field(SRMAnalysisStepExecutionDetailsKeys.monitoredServiceIdentifier)
                 .field(SRMAnalysisStepExecutionDetailsKeys.analysisStatus)
                 .build())
        .build();
  }
  @Id private String uuid;

  @NotNull private long analysisStartTime;
  @NotNull private long analysisEndTime;

  private Duration analysisDuration;

  @NotNull private SRMAnalysisStatus analysisStatus;

  @NotNull private String accountId;
  String monitoredServiceIdentifier;
  @NotNull private String projectIdentifier;
  @NotNull private String orgIdentifier;

  @FdSparseIndex String planExecutionId;
  String pipelineId;
  String stageStepId;
  String stageId;

  Long reportNotificationIteration;

  List<MonitoredServiceNotificationRule> notificationRulesSent;

  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (SRMAnalysisStepExecutionDetailsKeys.reportNotificationIteration.equals(fieldName)) {
      return this.reportNotificationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (SRMAnalysisStepExecutionDetailsKeys.reportNotificationIteration.equals(fieldName)) {
      this.reportNotificationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
