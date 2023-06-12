/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.SENSITIVITY_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.cdng.beans.v2.BaselineType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "TestVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CV)
public class TestVerificationJob extends VerificationJob {
  private RuntimeParameter sensitivity;
  private String baselineVerificationJobInstanceId;
  private final VerificationJobType type = VerificationJobType.TEST;

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

  public String getBaselineVerificationJobInstanceId() {
    return baselineVerificationJobInstanceId;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(sensitivity, generateErrorMessageFromParam(TestVerificationJobKeys.sensitivity));
  }

  @Override
  public Optional<TimeRange> getPreActivityTimeRange(Instant deploymentStartTime) {
    return Optional.empty();
  }

  @Override
  public List<TimeRange> getPreActivityDataCollectionTimeRanges(Instant deploymentStartTime) {
    return Collections.emptyList();
  }

  @Override
  public Optional<TimeRange> getPostActivityTimeRange(Instant deploymentStartTime) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<TimeRange> getDataCollectionTimeRanges(Instant startTime) {
    return getTimeRangesForDuration(startTime);
  }

  @Override
  public void resolveJobParams(Map<String, String> runtimeParameters) {
    runtimeParameters.keySet().forEach(key -> {
      switch (key) {
        case SENSITIVITY_KEY:
          if (sensitivity.isRuntimeParam()) {
            this.setSensitivity(runtimeParameters.get(key), false);
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
    return false;
  }

  @Override
  public VerificationJob resolveAdditionsFields(
      VerificationJobInstanceService verificationJobInstanceService, BaselineType baselineType) {
    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(getAccountId())
                                                            .orgIdentifier(getOrgIdentifier())
                                                            .projectIdentifier(getProjectIdentifier())
                                                            .serviceIdentifier(getServiceIdentifier())
                                                            .environmentIdentifier(getEnvIdentifier())
                                                            .build();
    if (baselineVerificationJobInstanceId == null && (baselineType == null || baselineType.equals(BaselineType.LAST))) {
      baselineVerificationJobInstanceId =
          verificationJobInstanceService.getLastSuccessfulTestVerificationJobExecutionId(serviceEnvironmentParams)
              .orElse(null);
    } else if (baselineVerificationJobInstanceId == null && Objects.equals(baselineType, BaselineType.PINNED)) {
      verificationJobInstanceService.getPinnedBaselineVerificationJobInstance(serviceEnvironmentParams)
          .ifPresent(verificationJobInstance -> baselineVerificationJobInstanceId = verificationJobInstance.getUuid());
    }
    return this;
  }
}
