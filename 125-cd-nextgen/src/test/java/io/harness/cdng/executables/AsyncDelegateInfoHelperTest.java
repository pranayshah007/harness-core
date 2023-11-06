/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.client.DelegateSelectionLogHttpClient;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class AsyncDelegateInfoHelperTest {
  @Mock DelegateSelectionLogHttpClient delegateSelectionLogHttpClient;

  @InjectMocks AsyncDelegateInfoHelper asyncDelegateInfoHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void getDelegateInfoForGivenTask() throws IOException {
    Call<RestResponse<DelegateSelectionLogParams>> getConnectorResourceCall = mock(Call.class);
    RestResponse<DelegateSelectionLogParams> responseDTO =
        new RestResponse<>(DelegateSelectionLogParams.builder().build());

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(delegateSelectionLogHttpClient.getDelegateInfo(any(), any())).thenReturn(getConnectorResourceCall);

    asyncDelegateInfoHelper.getDelegateInformationForGivenTask("taskName", "taskId", "accountId");
    verify(delegateSelectionLogHttpClient, times(1)).getDelegateInfo(any(), any());
  }
}
