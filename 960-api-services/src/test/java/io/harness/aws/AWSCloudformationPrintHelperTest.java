/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.rule.OwnerRule.TMACARI;

import static freemarker.template.utility.Collections12.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;

import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class AWSCloudformationPrintHelperTest extends CategoryTest {
  AwsCloudformationPrintHelper awsCloudformationPrintHelper = new AwsCloudformationPrintHelper();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPrintStackResources() {
    String stackName = "HarnessStack-test";
    Date timeStamp = new Date();
    LogCallback logCallback = mock(NoopExecutionCallback.class);
    StackResource stackResource =
        new StackResource().withStackName(stackName).withResourceStatusReason("statusReason").withTimestamp(timeStamp);
    awsCloudformationPrintHelper.printStackResources(singletonList(stackResource), logCallback);
    String message = String.format("[%s] [%s] [%s] [%s] [%s]", stackResource.getResourceStatus(),
        stackResource.getResourceType(), stackResource.getLogicalResourceId(), stackResource.getResourceStatusReason(),
        stackResource.getPhysicalResourceId());
    verify(logCallback).saveExecutionLog(message);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPrintStackEvents() {
    String stackName = "HarnessStack-test";
    Date timeStamp = new Date();
    LogCallback logCallback = mock(NoopExecutionCallback.class);
    StackEvent stackEvent = new StackEvent()
                                .withStackName(stackName)
                                .withEventId("id")
                                .withResourceStatusReason("statusReason")
                                .withTimestamp(timeStamp);
    long resStackEventTs = awsCloudformationPrintHelper.printStackEvents(singletonList(stackEvent), 1000L, logCallback);
    String message =
        String.format("[%s] [%s] [%s] [%s] [%s]", stackEvent.getResourceStatus(), stackEvent.getResourceType(),
            stackEvent.getLogicalResourceId(), "statusReason", stackEvent.getPhysicalResourceId());
    verify(logCallback).saveExecutionLog(message);
    assertThat(resStackEventTs).isEqualTo(timeStamp.getTime());
  }
}
