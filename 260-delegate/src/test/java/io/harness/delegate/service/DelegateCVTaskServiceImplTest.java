/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.managerclient.VerificationServiceClient;
import io.harness.rule.Owner;

import software.wings.service.impl.analysis.DataCollectionTaskResult;

import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class DelegateCVTaskServiceImplTest extends CategoryTest {
  @Mock private VerificationServiceClient verificationClient;
  @InjectMocks private FakeTimeLimiter timeLimiter;
  @InjectMocks DelegateCVTaskServiceImpl delegateCVTaskService;
  private DataCollectionTaskResult dataCollectionTaskResult;
  @Mock Call call;
  private String accountID = "accountId";
  private String cvTaskId = "cvTaskId";

  @Before
  public void setup() throws IllegalAccessException, IOException {
    initMocks(this);
    DelegateCVTaskServiceImpl.DELAY = Duration.ofMillis(1); // to make the test run faster
    FieldUtils.writeField(delegateCVTaskService, "timeLimiter", timeLimiter, true);

    when(verificationClient.updateCVTaskStatus(anyString(), anyString(), any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(null));
    dataCollectionTaskResult = DataCollectionTaskResult.builder().build();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void updateCVTaskStatus_noRetries() throws TimeoutException {
    delegateCVTaskService.updateCVTaskStatus(accountID, cvTaskId, dataCollectionTaskResult);
    verify(verificationClient, times(1)).updateCVTaskStatus(eq(accountID), eq(cvTaskId), eq(dataCollectionTaskResult));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void updateCVTaskStatus_successAfterOneFailure() throws TimeoutException {
    when(verificationClient.updateCVTaskStatus(anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("timeout"))
        .thenReturn(call);
    delegateCVTaskService.updateCVTaskStatus(accountID, cvTaskId, dataCollectionTaskResult);
    verify(verificationClient, times(2)).updateCVTaskStatus(eq(accountID), eq(cvTaskId), eq(dataCollectionTaskResult));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void updateCVTaskStatus_failureAfterAllRetryExceededWithTimeoutException() throws Exception {
    TimeLimiter timeLimiter = mock(TimeLimiter.class);
    FieldUtils.writeField(delegateCVTaskService, "timeLimiter", timeLimiter, true);
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter).thenThrow(new UncheckedTimeoutException("timeout"));
    assertThatThrownBy(() -> delegateCVTaskService.updateCVTaskStatus(accountID, cvTaskId, dataCollectionTaskResult))
        .isInstanceOf(TimeoutException.class)
        .hasMessage("Timeout of 5 sec and 2 retries exceeded while updating CVTask status");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void updateCVTaskStatus_failureAfterAllRetryExceededWithRuntimeException() {
    when(verificationClient.updateCVTaskStatus(anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("timeout"));
    assertThatThrownBy(() -> delegateCVTaskService.updateCVTaskStatus(accountID, cvTaskId, dataCollectionTaskResult))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("timeout");
  }
}
