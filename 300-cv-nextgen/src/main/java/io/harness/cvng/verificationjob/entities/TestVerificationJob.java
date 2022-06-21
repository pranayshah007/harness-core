/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.CVConstants.DEFAULT_TEST_JOB_ID;
import static io.harness.cvng.CVConstants.DEFAULT_TEST_JOB_NAME;
import static io.harness.cvng.CVConstants.RUNTIME_PARAM_STRING;
import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.cvng.verificationjob.CVVerificationJobConstants.SENSITIVITY_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.TestVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.verificationjob.CVVerificationJobConstants;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

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
@FieldNameConstants(innerTypeName = "TestVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CV)
public class TestVerificationJob extends VerificationJob {
  private RuntimeParameter sensitivity;
  private String baselineVerificationJobInstanceId;
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.TEST;
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
  public VerificationJobDTO getVerificationJobDTO() {
    TestVerificationJobDTO testVerificationJobDTO = new TestVerificationJobDTO();
    populateCommonFields(testVerificationJobDTO);
    testVerificationJobDTO.setSensitivity(
        sensitivity.isRuntimeParam() ? CVVerificationJobConstants.RUNTIME_STRING : getSensitivity().name());
    if (baselineVerificationJobInstanceId == null) {
      testVerificationJobDTO.setBaselineVerificationJobInstanceId("LAST");
    } else {
      testVerificationJobDTO.setBaselineVerificationJobInstanceId(baselineVerificationJobInstanceId);
    }
    return testVerificationJobDTO;
  }

  @Override
  public boolean shouldDoDataCollection() {
    return true;
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
  public Optional<TimeRange> getPostActivityTimeRange(Instant deploymentStartTime) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<TimeRange> getDataCollectionTimeRanges(Instant startTime) {
    return getTimeRangesForDuration(startTime);
  }

  @Override
  public void fromDTO(VerificationJobDTO verificationJobDTO) {
    addCommonFileds(verificationJobDTO);
    TestVerificationJobDTO testVerificationJobDTO = (TestVerificationJobDTO) verificationJobDTO;
    this.setSensitivity(testVerificationJobDTO.getSensitivity(),
        VerificationJobDTO.isRuntimeParam(testVerificationJobDTO.getSensitivity()));
    if (!testVerificationJobDTO.getBaselineVerificationJobInstanceId().equals("LAST")) {
      this.setBaselineVerificationJobInstanceId(testVerificationJobDTO.getBaselineVerificationJobInstanceId());
    }
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
  public VerificationJob resolveAdditionsFields(VerificationJobInstanceService verificationJobInstanceService) {
    if (baselineVerificationJobInstanceId == null) {
      baselineVerificationJobInstanceId = verificationJobInstanceService
                                              .getLastSuccessfulTestVerificationJobExecutionId(getAccountId(),
                                                  getOrgIdentifier(), getProjectIdentifier(), getIdentifier())
                                              .orElse(null);
    }
    return this;
  }

  public static TestVerificationJob createDefaultJob(String accountId, String orgIdentifier, String projectIdentifier) {
    TestVerificationJob verificationJob =
        TestVerificationJob.builder()
            .jobName(DEFAULT_TEST_JOB_NAME)
            .identifier(DEFAULT_TEST_JOB_ID)
            .sensitivity(VerificationJob.getRunTimeParameter(RUNTIME_PARAM_STRING, true))
            .build();
    VerificationJob.setDefaultJobCommonParameters(verificationJob, accountId, orgIdentifier, projectIdentifier);
    return verificationJob;
  }
}
