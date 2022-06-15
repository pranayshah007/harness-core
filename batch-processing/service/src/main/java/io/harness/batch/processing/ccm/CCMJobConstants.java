/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import io.harness.ccm.commons.beans.JobConstants;

import java.time.Instant;
import java.util.Objects;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;

public class CCMJobConstants extends JobConstants {
  public static final String JOB_ID = "JobID";
  public static final String ACCOUNT_ID = "accountId";
  public static final String JOB_END_DATE = "endDate";
  public static final String JOB_START_DATE = "startDate";
  public static final String BATCH_JOB_TYPE = "batchJobType";

  public CCMJobConstants(final JobParameters jobParameters) {
    super.accountId = jobParameters.getString(ACCOUNT_ID);
    super.jobStartTime = getFieldLongValueFromJobParams(jobParameters, JOB_START_DATE);
    super.jobEndTime = getFieldLongValueFromJobParams(jobParameters, JOB_END_DATE);
  }

  public static CCMJobConstants fromContext(final ChunkContext chunkContext) {
    return new CCMJobConstants(chunkContext);
  }

  public CCMJobConstants(final StepExecution stepExecution) {
    this(stepExecution.getJobParameters());
  }

  public CCMJobConstants(final ChunkContext chunkContext) {
    this(chunkContext.getStepContext().getStepExecution());
  }

  public static Instant getFieldValueFromJobParams(JobParameters parameters, String fieldName) {
    return Instant.ofEpochMilli(Long.parseLong(Objects.requireNonNull(parameters.getString(fieldName))));
  }

  public static Long getFieldLongValueFromJobParams(JobParameters parameters, String fieldName) {
    return Long.valueOf(Objects.requireNonNull(parameters.getString(fieldName)));
  }

  public static BatchJobType getBatchJobTypeFromJobParams(JobParameters parameters) {
    return BatchJobType.valueOf(parameters.getString(BATCH_JOB_TYPE));
  }
}
