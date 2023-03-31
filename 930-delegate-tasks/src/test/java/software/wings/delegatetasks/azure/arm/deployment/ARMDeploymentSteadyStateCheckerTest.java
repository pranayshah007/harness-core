/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.deployment;

import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.impl.AzureManagementClientImpl;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.ARMDeploymentSteadyStateChecker;
import io.harness.exception.runtime.azure.AzureARMDeploymentException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

public class ARMDeploymentSteadyStateCheckerTest extends CategoryTest {
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureManagementClientImpl azureManagementClient;
  @InjectMocks private ARMDeploymentSteadyStateChecker armSteadyStateChecker;
  private final TimeLimiter timeLimiter = new FakeTimeLimiter();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(armSteadyStateChecker).set("timeLimiter", timeLimiter);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMDeploymentSteadyStateChecker() {
    String resourceGroup = "arm-rg";
    String subscriptionId = "subId";
    String deploymentName = "deploy";

    ARMDeploymentSteadyStateContext context = ARMDeploymentSteadyStateContext.builder()
                                                  .resourceGroup(resourceGroup)
                                                  .scopeType(ARMScopeType.RESOURCE_GROUP)
                                                  .azureConfig(AzureConfig.builder().build())
                                                  .subscriptionId(subscriptionId)
                                                  .deploymentName(deploymentName)
                                                  .statusCheckIntervalInSeconds(1)
                                                  .steadyCheckTimeoutInMinutes(10)
                                                  .build();

    List<DeploymentOperationInner> responseList = new ArrayList<>();
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);
    doReturn(getPagedIterable(simpleResponse)).when(azureManagementClient).getDeploymentOperations(eq(context));
    LongRunningOperationStatus longRunningOperationStatus1 = LongRunningOperationStatus.SUCCESSFULLY_COMPLETED;
    SyncPoller syncPoller1 = mock(SyncPoller.class);
    PollResponse syncPollResponse = mock(PollResponse.class);
    doReturn(syncPollResponse).when(syncPoller1).poll();
    doReturn(longRunningOperationStatus1).when(syncPollResponse).getStatus();
    armSteadyStateChecker.waitUntilCompleteWithTimeout(context, azureManagementClient, mockLogCallback, syncPoller1);
    verify(syncPoller1, times(1)).poll();

    LongRunningOperationStatus longRunningOperationStatus2 = LongRunningOperationStatus.FAILED;
    SyncPoller syncPoller2 = mock(SyncPoller.class);
    PollResponse syncPollResponse2 = mock(PollResponse.class);
    doReturn(syncPollResponse2).when(syncPoller2).poll();
    doReturn(longRunningOperationStatus2).when(syncPollResponse2).getStatus();
    PollResult pollResult2 = mock(PollResult.class);
    PollResult.Error pollResultError2 = mock(PollResult.Error.class);
    doReturn("ARM Deployment failed for deployment").when(pollResultError2).getMessage();
    doReturn(pollResultError2).when(pollResult2).getError();
    doReturn(pollResult2).when(syncPollResponse2).getValue();
    assertThatThrownBy(()
                           -> armSteadyStateChecker.waitUntilCompleteWithTimeout(
                               context, azureManagementClient, mockLogCallback, syncPoller2))
        .isInstanceOf(AzureARMDeploymentException.class)
        .hasMessageContaining("ARM Deployment failed for deployment");
  }

  @NotNull
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }
}
