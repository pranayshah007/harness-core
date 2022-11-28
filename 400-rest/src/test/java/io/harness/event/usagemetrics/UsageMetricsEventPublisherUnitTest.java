/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.usagemetrics;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.RUSHABH;

import io.harness.category.element.UnitTests;
import io.harness.event.timeseries.processor.StepEventProcessor;
import io.harness.event.usagemetrics.UsageMetricsTestUtils.UsageMetricsTestKeys;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentStepTimeSeriesEvent;
import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.api.ExecutionInterruptTimeSeriesEvent;
import software.wings.beans.WorkflowExecution;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.StateExecutionInstance;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UsageMetricsEventPublisherUnitTest extends WingsBaseTest {
  @Inject UsageMetricsEventPublisher usageMetricsEventPublisher;

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testConstructDeploymentEvent() {
    WorkflowExecution workflowExecution = UsageMetricsTestUtils.generateWorkflowExecution(0);
    DeploymentTimeSeriesEvent timeSeriesEvent = usageMetricsEventPublisher.constructDeploymentTimeSeriesEvent(
        UsageMetricsTestKeys.ACCOUNTID, workflowExecution, Collections.emptyMap());
    UsageMetricsTestUtils.validateTimeSeriesEventInfo(timeSeriesEvent, 0);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testConstructDeploymentStepEvent() {
    StateExecutionInstance stateExecutionInstance = UsageMetricsTestUtils.generateStateExecutionInstance();
    DeploymentStepTimeSeriesEvent timeSeriesEvent = usageMetricsEventPublisher.constructDeploymentStepTimeSeriesEvent(
        StepEventProcessor.ACCOUNT_ID, stateExecutionInstance);
    UsageMetricsTestUtils.validateTimeSeriesEventInfo(timeSeriesEvent);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testConstructExecutionInterrupt() {
    ExecutionInterrupt executionInterrupt = UsageMetricsTestUtils.generateExecutionInterrupt();
    ExecutionInterruptTimeSeriesEvent timeSeriesEvent =
        usageMetricsEventPublisher.constructExecutionInterruptTimeSeriesEvent(
            StepEventProcessor.ACCOUNT_ID, executionInterrupt);
    UsageMetricsTestUtils.validateTimeSeriesEventInfo(timeSeriesEvent);
  }
}
