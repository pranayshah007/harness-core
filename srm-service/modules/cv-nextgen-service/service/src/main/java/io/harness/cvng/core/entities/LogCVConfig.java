/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.models.VerificationType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "LogCVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public abstract class LogCVConfig extends CVConfig {
  private String queryName;
  private String query;

  @Override
  public boolean isSLIEnabled() {
    return false;
  }

  @Override
  public boolean isLiveMonitoringEnabled() {
    return true;
  }

  @Override
  public boolean isDeploymentVerificationEnabled() {
    return true;
  }

  @JsonIgnore
  @Override
  public TimeRange getFirstTimeDataCollectionTimeRange() {
    TimeRange baseline = getBaseline();
    return TimeRange.builder()
        .startTime(baseline.getStartTime())
        .endTime(baseline.getStartTime().plus(5, ChronoUnit.MINUTES))
        .build();
  }

  @JsonIgnore
  public TimeRange getBaseline() {
    Preconditions.checkState(this.getCreatedAt() != 0, "CreatedAt needs to be set to get the baseline");
    Instant endTime = DateTimeUtils.roundDownTo5MinBoundary(getFirstTimeDataCollectionStartTime());
    return TimeRange.builder().endTime(endTime).startTime(endTime.minus(30, ChronoUnit.MINUTES)).build();
  }
  @Override
  public VerificationType getVerificationType() {
    return VerificationType.LOG;
  }

  public abstract String getHostCollectionDSL();
  @Override
  public void validate() {
    super.validate();
    checkNotNull(query, generateErrorMessageFromParam(LogCVConfigKeys.query));
  }
  @Override
  public boolean queueAnalysisForPreDeploymentTask() {
    return true;
  }

  public abstract static class LogCVConfigUpdatableEntity<T extends LogCVConfig, D extends LogCVConfig>
      extends CVConfigUpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D logCVConfig) {
      super.setCommonOperations(updateOperations, logCVConfig);
      updateOperations.set(LogCVConfigKeys.query, logCVConfig.getQuery());
    }
  }
}
