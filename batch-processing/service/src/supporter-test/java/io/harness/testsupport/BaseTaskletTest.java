/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testsupport;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.CCMJobConstants;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.mockito.Mock;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;

public abstract class BaseTaskletTest extends CategoryTest {
  protected static final String ACCOUNT_ID = "accountId";
  protected long START_TIME_MILLIS = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
  protected long END_TIME_MILLIS = Instant.now().toEpochMilli();

  @Mock(answer = RETURNS_DEEP_STUBS) protected ChunkContext chunkContext;

  @Before
  public void mockChunkContext() {
    Map<String, JobParameter> parameterMap = new HashMap<>();
    parameterMap.put(CCMJobConstants.JOB_START_DATE, new JobParameter(String.valueOf(START_TIME_MILLIS)));
    parameterMap.put(CCMJobConstants.JOB_END_DATE, new JobParameter(String.valueOf(END_TIME_MILLIS)));
    parameterMap.put(CCMJobConstants.ACCOUNT_ID, new JobParameter(ACCOUNT_ID));

    when(chunkContext.getStepContext().getStepExecution().getJobParameters())
        .thenReturn(new JobParameters(parameterMap));
  }
}
