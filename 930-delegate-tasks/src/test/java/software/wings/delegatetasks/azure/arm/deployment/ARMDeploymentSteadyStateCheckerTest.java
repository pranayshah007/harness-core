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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.impl.AzureManagementClientImpl;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.ARMDeploymentSteadyStateChecker;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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

    doReturn("Succeeded").when(azureManagementClient).getARMDeploymentStatus(eq(context));
    doReturn(getPageList()).when(azureManagementClient).getDeploymentOperations(eq(context));
    armSteadyStateChecker.waitUntilCompleteWithTimeout(context, azureManagementClient, mockLogCallback);
    verify(azureManagementClient).getARMDeploymentStatus(eq(context));

    doReturn("Failed").when(azureManagementClient).getARMDeploymentStatus(eq(context));
    assertThatThrownBy(
        () -> armSteadyStateChecker.waitUntilCompleteWithTimeout(context, azureManagementClient, mockLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("ARM Deployment failed for deployment");

    doReturn("Accepted").when(azureManagementClient).getARMDeploymentStatus(eq(context));
    doReturn(getPageList()).when(azureManagementClient).getDeploymentOperations(eq(context));
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(new ARMStatusChanger(context, azureManagementClient));
    executorService.shutdown();
    armSteadyStateChecker.waitUntilCompleteWithTimeout(context, azureManagementClient, mockLogCallback);
    verify(azureManagementClient, Mockito.atLeast(1)).getARMDeploymentStatus(eq(context));
  }

  private static class ARMStatusChanger implements Runnable {
    private final ARMDeploymentSteadyStateContext context;
    private final AzureManagementClientImpl azureManagementClient;
    ARMStatusChanger(ARMDeploymentSteadyStateContext context, AzureManagementClientImpl azureManagementClient) {
      this.context = context;
      this.azureManagementClient = azureManagementClient;
    }

    @Override
    public void run() {
      try {
        doReturn("Accepted").when(azureManagementClient).getARMDeploymentStatus(eq(context));
        Thread.sleep(3000);
        doReturn("Succeeded").when(azureManagementClient).getARMDeploymentStatus(eq(context));
      } catch (InterruptedException exception) {
        exception.printStackTrace();
      }
    }
  }

  @NotNull
  public <T> PagedList<T> getPageList() {
    return new PagedList<T>() {
      @Override
      public Page<T> nextPage(String s) {
        return new Page<T>() {
          @Override
          public String nextPageLink() {
            return null;
          }
          @Override
          public List<T> items() {
            return null;
          }
        };
      }
    };
  }
}
