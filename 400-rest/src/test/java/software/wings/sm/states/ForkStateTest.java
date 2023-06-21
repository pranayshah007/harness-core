/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.FeatureName.SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.ExecutionStatusResponseData;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForkStateTest {
  public static final String RESPONSE_KEY = "key";
  @Mock private FeatureFlagService ffService;
  @Mock private ExecutionContext context;

  @InjectMocks private ForkState state = Mockito.spy(new ForkState("fork-unit-test"));

  @Before
  public void setup() {
    when(context.getAccountId()).thenReturn(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateSuccessfulResponseWhenResponseMapIsEmpty() {
    final ExecutionResponse response = state.handleAsyncResponse(context, Collections.emptyMap());
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResponseStatusBeTheLastMapElementWhenFFisDisabled() {
    when(ffService.isEnabled(SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE, ACCOUNT_ID)).thenReturn(false);

    Map<String, ResponseData> inputResponse = new HashMap<>();
    inputResponse.put("A", createExecutionStatusResponse(ExecutionStatus.FAILED));
    inputResponse.put("B", createExecutionStatusResponse(ExecutionStatus.REJECTED));
    inputResponse.put("C", createExecutionStatusResponse(ExecutionStatus.FAILED));

    final ExecutionResponse response = state.handleAsyncResponse(context, inputResponse);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResponseStatusBeRejectedWhenRejectIsPresentAndFFisEnabled() {
    when(ffService.isEnabled(SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE, ACCOUNT_ID)).thenReturn(true);

    Map<String, ResponseData> inputResponse = new HashMap<>();
    inputResponse.put("A", createExecutionStatusResponse(ExecutionStatus.FAILED));
    inputResponse.put("B", createExecutionStatusResponse(ExecutionStatus.REJECTED));
    inputResponse.put("C", createExecutionStatusResponse(ExecutionStatus.FAILED));

    final ExecutionResponse response = state.handleAsyncResponse(context, inputResponse);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.REJECTED);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResponseStatusBeRejectedWhenRejectAndSuccessArePresentAndFFisEnabled() {
    when(ffService.isEnabled(SPG_CG_REJECT_PRIORITY_WHEN_FORK_STATE, ACCOUNT_ID)).thenReturn(true);

    Map<String, ResponseData> inputResponse = new HashMap<>();
    inputResponse.put("A", createExecutionStatusResponse(ExecutionStatus.SUCCESS));
    inputResponse.put("B", createExecutionStatusResponse(ExecutionStatus.REJECTED));
    inputResponse.put("C", createExecutionStatusResponse(ExecutionStatus.FAILED));

    final ExecutionResponse response = state.handleAsyncResponse(context, inputResponse);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.REJECTED);
  }

  private ExecutionStatusResponseData createExecutionStatusResponse(ExecutionStatus executionStatus) {
    ExecutionStatusResponseData responseData = mock(ExecutionStatusResponseData.class);
    when(responseData.getExecutionStatus()).thenReturn(executionStatus);
    return responseData;
  }
}
