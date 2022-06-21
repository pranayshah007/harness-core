/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.CVConstants.DEFAULT_HEALTH_JOB_ID;
import static io.harness.cvng.CVConstants.DEFAULT_HEALTH_JOB_NAME;

import io.harness.cvng.beans.job.HealthVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.utils.DateTimeUtils;

import java.time.Duration;
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
@FieldNameConstants(innerTypeName = "HealthVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class HealthVerificationJob extends VerificationJob {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.HEALTH;
  }

  @Override
  public VerificationJobDTO getVerificationJobDTO() {
    HealthVerificationJobDTO healthVerificationJobDTO = new HealthVerificationJobDTO();
    populateCommonFields(healthVerificationJobDTO);
    return healthVerificationJobDTO;
  }

  @Override
  public boolean shouldDoDataCollection() {
    return false;
  }

  @Override
  protected void validateParams() {}

  @Override
  public Optional<TimeRange> getPreActivityTimeRange(Instant deploymentStartTime) {
    return Optional.of(
        TimeRange.builder().startTime(deploymentStartTime.minus(getDuration())).endTime(deploymentStartTime).build());
  }

  @Override
  public Optional<TimeRange> getPostActivityTimeRange(Instant deploymentStartTime) {
    return Optional.of(
        TimeRange.builder().endTime(deploymentStartTime.plus(getDuration())).startTime(deploymentStartTime).build());
  }

  @Override
  public List<TimeRange> getDataCollectionTimeRanges(Instant startTime) {
    return getTimeRangesForDuration(startTime);
  }

  @Override
  public void fromDTO(VerificationJobDTO verificationJobDTO) {
    addCommonFileds(verificationJobDTO);
  }

  @Override
  public void resolveJobParams(Map<String, String> runtimeParameters) {}

  @Override
  public boolean collectHostData() {
    return false;
  }

  @Override
  public Instant roundToClosestBoundary(Instant deploymentStartTime, Instant startTime) {
    return DateTimeUtils.roundDownTo5MinBoundary(startTime);
  }

  @Override
  public Duration getExecutionDuration() {
    return getDuration().plus(getDuration());
  }

  @Override
  public Instant getAnalysisStartTime(Instant startTime) {
    return getPreActivityVerificationStartTime(startTime);
  }

  @Override
  public Instant eligibleToStartAnalysisTime(Instant startTime, Duration dataCollectionDelay, Instant createdAt) {
    // It is running as live monitoring so does not depend on dataCollectionDelay or createdAt
    return getAnalysisStartTime(startTime);
  }

  public Instant getPreActivityVerificationStartTime(Instant startTime) {
    return startTime.minus(getDuration());
  }

  public Instant getPostActivityVerificationStartTime(Instant startTime) {
    return startTime;
  }

  public static HealthVerificationJob createDefaultJob(
      String accountId, String orgIdentifier, String projectIdentifier) {
    HealthVerificationJob verificationJob =
        HealthVerificationJob.builder().jobName(DEFAULT_HEALTH_JOB_NAME).identifier(DEFAULT_HEALTH_JOB_ID).build();
    VerificationJob.setDefaultJobCommonParameters(verificationJob, accountId, orgIdentifier, projectIdentifier);
    return verificationJob;
  }
}
