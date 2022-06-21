/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.CVConstants.RUNTIME_PARAM_STRING;
import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.SENSITIVITY_KEY;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.TRAFFIC_SPLIT_PERCENTAGE_KEY;

import static com.google.common.base.Preconditions.checkState;

import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.core.beans.TimeRange;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "CanaryBlueGreenVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public abstract class CanaryBlueGreenVerificationJob extends VerificationJob {
  // TODO: move sensitivity to common base class.
  private RuntimeParameter sensitivity;
  private Integer trafficSplitPercentage; // TODO: make this runtime param and write migration.
  private RuntimeParameter trafficSplitPercentageV2;

  public void setTrafficSplitPercentageV2(String trafficSplit, boolean isRuntimeParam) {
    if (isRuntimeParam) {
      this.trafficSplitPercentage = 0;
    } else {
      this.trafficSplitPercentage = trafficSplit == null ? null : Integer.valueOf(trafficSplit);
    }
    this.trafficSplitPercentageV2 = trafficSplit == null
        ? null
        : RuntimeParameter.builder().isRuntimeParam(isRuntimeParam).value(trafficSplit).build();
  }

  public Integer getTrafficSplitPercentage() {
    if (getTrafficSplitPercentageV2() == null) {
      return trafficSplitPercentage;
    } else if (getTrafficSplitPercentageV2().isRuntimeParam()) {
      return null;
    }
    return Integer.valueOf(getTrafficSplitPercentageV2().getValue());
  }

  public Sensitivity getSensitivity() {
    if (sensitivity.isRuntimeParam()) {
      return null;
    }
    return Sensitivity.getEnum(sensitivity.getValue());
  }

  public void setSensitivity(String sensitivity, boolean isRuntimeParam) {
    this.sensitivity = sensitivity == null
        ? null
        : RuntimeParameter.builder().isRuntimeParam(isRuntimeParam).value(sensitivity).build();
  }

  public void setSensitivity(Sensitivity sensitivity) {
    this.sensitivity =
        sensitivity == null ? null : RuntimeParameter.builder().isRuntimeParam(false).value(sensitivity.name()).build();
  }

  @Override
  public boolean shouldDoDataCollection() {
    return true;
  }

  public abstract VerificationJobDTO getVerificationJobDTO();

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(
        sensitivity, generateErrorMessageFromParam(CanaryBlueGreenVerificationJobKeys.sensitivity));
    if (trafficSplitPercentageV2 == null) {
      Optional.ofNullable(trafficSplitPercentage)
          .ifPresent(percentage
              -> checkState(percentage >= 0 && percentage <= 50,
                  CanaryBlueGreenVerificationJobKeys.trafficSplitPercentage + " is not in appropriate range"));
    } else if (!trafficSplitPercentageV2.isRuntimeParam()) {
      Preconditions.checkState(Integer.valueOf(trafficSplitPercentageV2.getValue()) > 0
              && Integer.valueOf(trafficSplitPercentageV2.getValue()) <= 50,
          CanaryBlueGreenVerificationJobKeys.trafficSplitPercentage + " is not in appropriate range");
    }
  }

  @Override
  public Optional<TimeRange> getPreActivityTimeRange(Instant deploymentStartTime) {
    return Optional.of(
        TimeRange.builder().startTime(deploymentStartTime.minus(getDuration())).endTime(deploymentStartTime).build());
  }

  @Override
  public Optional<TimeRange> getPostActivityTimeRange(Instant deploymentStartTime) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<TimeRange> getDataCollectionTimeRanges(Instant startTime) {
    return getTimeRangesForDuration(startTime);
  }

  public abstract void fromDTO(VerificationJobDTO verificationJobDTO);

  @Override
  public void resolveJobParams(Map<String, String> runtimeParameters) {
    runtimeParameters.keySet().forEach(key -> {
      switch (key) {
        case SENSITIVITY_KEY:
          if (sensitivity.isRuntimeParam()) {
            this.setSensitivity(runtimeParameters.get(key), false);
          }
          break;
        case TRAFFIC_SPLIT_PERCENTAGE_KEY:
          if (trafficSplitPercentageV2.isRuntimeParam()) {
            this.setTrafficSplitPercentageV2(runtimeParameters.get(key), false);
          }
          break;
        default:
          break;
      }
    });
    this.validateParams();
  }

  @Override
  public boolean collectHostData() {
    return true;
  }

  public static void setCanaryBLueGreenDefaultJobParameters(
      CanaryBlueGreenVerificationJob canaryBlueGreenVerificationJob, String accountId, String orgIdentifier,
      String projectIdentifier) {
    canaryBlueGreenVerificationJob.setSensitivity(RUNTIME_PARAM_STRING, true);
    canaryBlueGreenVerificationJob.setTrafficSplitPercentageV2(RUNTIME_PARAM_STRING, true);
    VerificationJob.setDefaultJobCommonParameters(
        canaryBlueGreenVerificationJob, accountId, orgIdentifier, projectIdentifier);
  }
}
