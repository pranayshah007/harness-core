/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.aws.cf.DeployStackResult;
import io.harness.aws.cf.Status;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest;
import com.amazonaws.services.cloudformation.model.CreateChangeSetResult;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.GetTemplateSummaryResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.ParameterDeclaration;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterUnrecoverableException;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AWSCloudformationClientTest extends CategoryTest {
  private AmazonCloudFormationClient mockClient;
  private LogCallback logCallback;
  @InjectMocks @Inject @Spy private AWSCloudformationClientImpl service;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private AwsCloudformationPrintHelper awsCloudformationPrintHelper;
  @Mock private AwsCallTracker mockTracker;
  @Mock private TimeLimiter mockTimeLimiter;

  private static final char[] accessKey = "abcd".toCharArray();
  private static final char[] secretKey = "pqrs".toCharArray();

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    logCallback = mock(LogCallback.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldUpdateStack() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    UpdateStackRequest request = new UpdateStackRequest().withStackName(stackName);
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.updateStack(region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    verify(mockClient).updateStack(request);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldDeleteStack() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeleteStackRequest request = new DeleteStackRequest().withStackName(stackName);
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.deleteStack(region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    verify(mockClient).deleteStack(request);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldDescribeStack() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest request = new DescribeStacksRequest().withStackName(stackName);
    DescribeStacksResult result = new DescribeStacksResult().withStacks(new Stack().withStackName(stackName));
    doReturn(result).when(mockClient).describeStacks(request);
    doNothing().when(mockTracker).trackCFCall(anyString());
    DescribeStacksResult actual = service.describeStacks(
        region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    assertThat(actual).isNotNull();
    assertThat(actual.getStacks().size()).isEqualTo(1);
    assertThat(actual.getStacks().get(0).getStackName()).isEqualTo(stackName);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldGetAllEvents() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStackEventsRequest request = new DescribeStackEventsRequest().withStackName(stackName);
    DescribeStackEventsResult result = new DescribeStackEventsResult().withStackEvents(
        new StackEvent().withStackName(stackName).withTimestamp(new Date()).withEventId("id"));
    doReturn(result).when(mockClient).describeStackEvents(request);
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<StackEvent> events = service.getAllStackEvents(
        region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), 0L);
    assertThat(events).isNotNull();
    assertThat(events.size()).isEqualTo(1);
    assertThat(events.get(0).getStackName()).isEqualTo(stackName);
    assertThat(events.get(0).getEventId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void shouldGetEventsAndDescribeStackEventsOnlyOnce() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";

    long lastStackEventsTs = System.currentTimeMillis();

    StackEvent beforeLastStackTimestamp = new StackEvent().withStackName(stackName).withEventId("id").withTimestamp(
        new Date(lastStackEventsTs - 3600 * 1000));
    StackEvent afterLastStackTimestamp = new StackEvent().withStackName(stackName).withEventId("id-2").withTimestamp(
        new Date(lastStackEventsTs + 3600 * 1000));

    DescribeStackEventsRequest request = new DescribeStackEventsRequest().withStackName(stackName);
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    DescribeStackEventsResult result = new DescribeStackEventsResult()
                                           .withStackEvents(beforeLastStackTimestamp, afterLastStackTimestamp)
                                           .withNextToken("dummy-next-token");

    DescribeStackEventsResult result2 = new DescribeStackEventsResult().withStackEvents(
        new StackEvent().withStackName(stackName).withEventId("id-3").withTimestamp(new Date()));

    when(mockClient.describeStackEvents(request)).thenReturn(result, result2);

    doNothing().when(mockTracker).trackCFCall(anyString());
    List<StackEvent> events = service.getAllStackEvents(region, request,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), lastStackEventsTs);
    assertThat(events).isNotNull();
    assertThat(events.size()).isEqualTo(2);
    assertThat(events.get(0).getStackName()).isEqualTo(stackName);
    assertThat(events.get(0).getEventId()).isEqualTo("id");
    assertThat(events.get(1).getStackName()).isEqualTo(stackName);
    assertThat(events.get(1).getEventId()).isEqualTo("id-2");
    verify(mockClient, times(1)).describeStackEvents(any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void shouldGetEventsAndDescribeAllExistingStackEvents() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";

    long lastStackEventsTs = System.currentTimeMillis();

    StackEvent beforeLastStackTimestamp = new StackEvent().withStackName(stackName).withEventId("id").withTimestamp(
        new Date(lastStackEventsTs + 3600 * 1000));
    StackEvent afterLastStackTimestamp = new StackEvent().withStackName(stackName).withEventId("id-2").withTimestamp(
        new Date(lastStackEventsTs + 3700 * 1000));

    DescribeStackEventsRequest request = new DescribeStackEventsRequest().withStackName(stackName);
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    DescribeStackEventsResult result = new DescribeStackEventsResult()
                                           .withStackEvents(beforeLastStackTimestamp, afterLastStackTimestamp)
                                           .withNextToken("dummy-next-token");

    DescribeStackEventsResult result2 =
        new DescribeStackEventsResult()
            .withStackEvents(new StackEvent().withStackName(stackName).withEventId("id-3").withTimestamp(new Date()))
            .withNextToken(null);

    when(mockClient.describeStackEvents(request)).thenReturn(result, result2);

    doNothing().when(mockTracker).trackCFCall(anyString());
    List<StackEvent> events = service.getAllStackEvents(region, request,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), lastStackEventsTs);
    assertThat(events).isNotNull();
    assertThat(events.size()).isEqualTo(3);
    assertThat(events.get(0).getStackName()).isEqualTo(stackName);
    assertThat(events.get(0).getEventId()).isEqualTo("id");
    assertThat(events.get(1).getStackName()).isEqualTo(stackName);
    assertThat(events.get(1).getEventId()).isEqualTo("id-2");
    assertThat(events.get(2).getStackName()).isEqualTo(stackName);
    assertThat(events.get(2).getEventId()).isEqualTo("id-3");

    verify(mockClient, times(2)).describeStackEvents(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldCreateStack() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    CreateStackRequest request = new CreateStackRequest().withStackName(stackName);
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.createStack(region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    verify(mockClient).createStack(request);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldListStacks() {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest request = new DescribeStacksRequest().withStackName(stackName);
    DescribeStacksResult result = new DescribeStacksResult().withStacks(new Stack().withStackName(stackName));
    doReturn(result).when(mockClient).describeStacks(request);
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<Stack> stacks = service.getAllStacks(
        region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    assertThat(stacks).isNotNull();
    assertThat(stacks.size()).isEqualTo(1);
    assertThat(stacks.get(0).getStackName()).isEqualTo(stackName);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDeployStack() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    String changeSetName;
    DeployStackRequest deployStackRequest = DeployStackRequest.builder()
                                                .stackName(stackName)
                                                .parameters(Arrays.asList(new Parameter()
                                                                              .withParameterKey("K1")
                                                                              .withParameterValue("V1")
                                                                              .withResolvedValue("RV1")
                                                                              .withUsePreviousValue(true)))
                                                .tags(Arrays.asList(new Tag().withKey("K1").withValue("V1")))
                                                .roleARN("ROLE")
                                                .capabilities(Arrays.asList("CAPABILITY_IAM"))
                                                .templateURL("TEMPLATE_URL")
                                                .templateBody("TEMPLATE_BODY")
                                                .build();
    ArgumentCaptor<CreateChangeSetRequest> changeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(CreateChangeSetRequest.class);
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(changeSetRequestArgumentCaptor.capture());

    ArgumentCaptor<DescribeChangeSetRequest> describeChangeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeChangeSetRequest.class);
    DescribeChangeSetResult describeChangeSetResult = new DescribeChangeSetResult().withStatus("CREATE_COMPLETE");
    doReturn(describeChangeSetResult)
        .when(mockClient)
        .describeChangeSet(describeChangeSetRequestArgumentCaptor.capture());

    ArgumentCaptor<ExecuteChangeSetRequest> executeChangeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(ExecuteChangeSetRequest.class);
    doReturn(null).when(mockClient).executeChangeSet(executeChangeSetRequestArgumentCaptor.capture());

    DeployStackResult deployStackResult = service.deployStack(region, deployStackRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Duration.ofDays(1), logCallback);

    assertThat(deployStackResult.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(deployStackResult.isNoUpdatesToPerform()).isEqualTo(false);

    CreateChangeSetRequest createChangeSetRequest = changeSetRequestArgumentCaptor.getValue();
    assertThat(createChangeSetRequest.getStackName()).isEqualTo(stackName);
    assertThat(createChangeSetRequest.getRoleARN()).isEqualTo("ROLE");
    assertThat(createChangeSetRequest.getCapabilities()).containsExactly("CAPABILITY_IAM");
    assertThat(createChangeSetRequest.getTemplateURL()).isEqualTo("TEMPLATE_URL");
    assertThat(createChangeSetRequest.getTemplateBody()).isEqualTo("TEMPLATE_BODY");
    assertThat(createChangeSetRequest.getTags())
        .containsExactly(new com.amazonaws.services.cloudformation.model.Tag().withKey("K1").withValue("V1"));
    assertThat(createChangeSetRequest.getParameters())
        .containsExactly(new com.amazonaws.services.cloudformation.model.Parameter()
                             .withParameterKey("K1")
                             .withParameterValue("V1")
                             .withResolvedValue("RV1")
                             .withUsePreviousValue(true));
    changeSetName = createChangeSetRequest.getChangeSetName();

    DescribeChangeSetRequest describeChangeSetRequest = describeChangeSetRequestArgumentCaptor.getValue();
    assertThat(describeChangeSetRequest.getChangeSetName()).isEqualTo(changeSetName);
    assertThat(describeChangeSetRequest.getStackName()).isEqualTo(stackName);

    ExecuteChangeSetRequest executeChangeSetRequest = executeChangeSetRequestArgumentCaptor.getValue();
    assertThat(executeChangeSetRequest.getChangeSetName()).isEqualTo(changeSetName);
    assertThat(executeChangeSetRequest.getStackName()).isEqualTo(stackName);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testDeployStackFailedToCreateChangeSet() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeployStackRequest deployStackRequest = DeployStackRequest.builder()
                                                .stackName(stackName)
                                                .parameters(Arrays.asList(new Parameter()
                                                                              .withParameterKey("K1")
                                                                              .withParameterValue("V1")
                                                                              .withResolvedValue("RV1")
                                                                              .withUsePreviousValue(true)))
                                                .tags(Arrays.asList(new Tag().withKey("K1").withValue("V1")))
                                                .roleARN("ROLE")
                                                .capabilities(Arrays.asList("CAPABILITY_IAM"))
                                                .templateURL("TEMPLATE_URL")
                                                .templateBody("TEMPLATE_BODY")
                                                .build();
    ArgumentCaptor<CreateChangeSetRequest> changeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(CreateChangeSetRequest.class);
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(changeSetRequestArgumentCaptor.capture());

    ArgumentCaptor<DescribeChangeSetRequest> describeChangeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(DescribeChangeSetRequest.class);
    DescribeChangeSetResult describeChangeSetResult = new DescribeChangeSetResult().withStatus("RANDOM_STATUS");
    doReturn(describeChangeSetResult)
        .when(mockClient)
        .describeChangeSet(describeChangeSetRequestArgumentCaptor.capture());

    DeployStackResult deployStackResult = service.deployStack(region, deployStackRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Duration.ofDays(1), logCallback);

    assertThat(deployStackResult.getStatus()).isEqualTo(Status.FAILURE);
    assertThat(deployStackResult.isNoUpdatesToPerform()).isEqualTo(false);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testDeployStackCreateChangeSetExceptions() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeployStackRequest deployStackRequest = DeployStackRequest.builder()
                                                .stackName(stackName)
                                                .parameters(Arrays.asList(new Parameter()
                                                                              .withParameterKey("K1")
                                                                              .withParameterValue("V1")
                                                                              .withResolvedValue("RV1")
                                                                              .withUsePreviousValue(true)))
                                                .tags(Arrays.asList(new Tag().withKey("K1").withValue("V1")))
                                                .roleARN("ROLE")
                                                .capabilities(Arrays.asList("CAPABILITY_IAM"))
                                                .templateURL("TEMPLATE_URL")
                                                .templateBody("TEMPLATE_BODY")
                                                .build();
    ArgumentCaptor<CreateChangeSetRequest> changeSetRequestArgumentCaptor =
        ArgumentCaptor.forClass(CreateChangeSetRequest.class);
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(changeSetRequestArgumentCaptor.capture());

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException("timeout"));
    assertThatThrownBy(()
                           -> service.deployStack(region, deployStackRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(),
                               Duration.ofSeconds(5), logCallback))
        .isInstanceOf(TimeoutException.class);

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new RuntimeException("runtime"));
    assertThatThrownBy(()
                           -> service.deployStack(region, deployStackRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(),
                               Duration.ofSeconds(5), logCallback))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDeployStackFailureWithNoReason() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeployStackRequest deployStackRequest = DeployStackRequest.builder().stackName(stackName).build();
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(any());

    DescribeChangeSetResult describeChangeSetResult = new DescribeChangeSetResult().withStatus("FAILED");
    doReturn(describeChangeSetResult).when(mockClient).describeChangeSet(any());

    doReturn(null).when(mockClient).executeChangeSet(any());

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(null);
    DeployStackResult deployStackResult = service.deployStack(region, deployStackRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Duration.ofDays(1), logCallback);

    assertThat(deployStackResult.getStatus()).isEqualTo(Status.FAILURE);
    assertThat(deployStackResult.isNoUpdatesToPerform()).isEqualTo(false);
    assertThat(deployStackResult.getStatusReason()).isNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDeployStackFailureWithReason() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeployStackRequest deployStackRequest = DeployStackRequest.builder().stackName(stackName).build();
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(any());

    DescribeChangeSetResult describeChangeSetResult =
        new DescribeChangeSetResult().withStatus("FAILED").withStatusReason("R1");
    doReturn(describeChangeSetResult).when(mockClient).describeChangeSet(any());

    doReturn(null).when(mockClient).executeChangeSet(any());

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(null);
    DeployStackResult deployStackResult = service.deployStack(region, deployStackRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Duration.ofDays(1), logCallback);

    assertThat(deployStackResult.getStatus()).isEqualTo(Status.FAILURE);
    assertThat(deployStackResult.isNoUpdatesToPerform()).isEqualTo(false);
    assertThat(deployStackResult.getStatusReason()).isEqualTo("R1");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testDeployStackFailureWithNoChanges() throws Exception {
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeployStackRequest deployStackRequest = DeployStackRequest.builder().stackName(stackName).build();
    CreateChangeSetResult createChangeSetResult = new CreateChangeSetResult().withId("ID").withStackId("STACK_ID");
    doReturn(createChangeSetResult).when(mockClient).createChangeSet(any());

    DescribeChangeSetResult describeChangeSetResult =
        new DescribeChangeSetResult().withStatus("FAILED").withStatusReason("No updates are to be performed.");
    doReturn(describeChangeSetResult).when(mockClient).describeChangeSet(any());

    doReturn(null).when(mockClient).executeChangeSet(any());

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(null);
    DeployStackResult deployStackResult = service.deployStack(region, deployStackRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), Duration.ofDays(1), logCallback);

    assertThat(deployStackResult.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(deployStackResult.isNoUpdatesToPerform()).isEqualTo(true);
    assertThat(deployStackResult.getStatusReason()).isEqualTo("No updates are to be performed.");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void getAllStackResources() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStackResourcesRequest request = new DescribeStackResourcesRequest().withStackName(stackName);
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    DescribeStackResourcesResult result =
        new DescribeStackResourcesResult().withStackResources(new StackResource().withStackName(stackName));
    doReturn(result).when(mockClient).describeStackResources(request);
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<StackResource> stacks = service.getAllStackResources(
        region, request, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    assertThat(stacks).isNotNull();
    assertThat(stacks.size()).isEqualTo(1);
    assertThat(stacks.get(0).getStackName()).isEqualTo(stackName);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void shouldWaitForDeleteStackCompletion() {
    char[] accessKey = "abcd".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest request = new DescribeStacksRequest().withStackName(stackName);
    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    AmazonCloudFormationWaiters mockWaiter = mock(AmazonCloudFormationWaiters.class);
    Future future = mock(Future.class);

    Waiter<DescribeStacksRequest> mockWaiterStack = mock(Waiter.class);

    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    doReturn(mockWaiter).when(service).getAmazonCloudFormationWaiter(any());
    DescribeStackEventsResult result = new DescribeStackEventsResult().withStackEvents(
        new StackEvent().withStackName(stackName).withTimestamp(new Date()).withEventId("id"));
    doReturn(result).when(mockClient).describeStackEvents(any());
    doReturn(new ArrayList<>()).when(service).getAllStackResources(any(), any(), any());
    doReturn(mockWaiterStack).when(mockWaiter).stackDeleteComplete();
    when(future.isDone()).thenReturn(false).thenReturn(true);
    doReturn(future).when(mockWaiterStack).runAsync(any(), any());
    doNothing().when(awsCloudformationPrintHelper).printStackResources(any(), any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.waitForStackDeletionCompleted(request,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, logCallback, 1000L);
    verify(mockWaiterStack).runAsync(any(), any());
    verify(mockWaiter).stackDeleteComplete();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkServiceException() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackName);
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackName);
    DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest().withStackName(stackName);
    CreateStackRequest createStackRequest = new CreateStackRequest().withStackName(stackName);
    UpdateStackRequest updateStackRequest = new UpdateStackRequest().withStackName(stackName);
    CreateChangeSetRequest createChangeRequest = new CreateChangeSetRequest().withStackName(stackName);
    DescribeChangeSetRequest describeChangeSetRequest = new DescribeChangeSetRequest().withChangeSetName(stackName);
    ExecuteChangeSetRequest executeChangeSetRequest = new ExecuteChangeSetRequest().withChangeSetName(stackName);
    DeleteChangeSetRequest deleteChangeSetRequest = new DeleteChangeSetRequest().withChangeSetName(stackName);

    doThrow(AmazonServiceException.class).when(service).getAmazonCloudFormationClient(any(), any());
    DescribeStackResourcesRequest describeStackResourcesRequest =
        new DescribeStackResourcesRequest().withStackName(stackName);

    service.createChangeSet(
        region, createChangeRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.describeChangeSet(region, describeChangeSetRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.executeChangeSet(
        region, executeChangeSetRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.deleteChangeSet(
        region, deleteChangeSetRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.getAllStacks(
        region, describeStacksRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.deleteStack(
        region, deleteStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.getAllStackEvents(region, describeStackEventsRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), 0L);

    service.createStack(
        region, createStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.updateStack(
        region, updateStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.describeStacks(
        region, describeStacksRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.getAllStackResources(region, describeStackResourcesRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.waitForStackDeletionCompleted(describeStacksRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, null, 1000L);
    verify(awsApiHelperService, times(12)).handleAmazonServiceException(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkClientException() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackName);
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackName);
    DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest().withStackName(stackName);
    CreateStackRequest createStackRequest = new CreateStackRequest().withStackName(stackName);
    UpdateStackRequest updateStackRequest = new UpdateStackRequest().withStackName(stackName);
    DescribeStackResourcesRequest describeStackResourcesRequest =
        new DescribeStackResourcesRequest().withStackName(stackName);
    CreateChangeSetRequest createChangeRequest = new CreateChangeSetRequest().withStackName(stackName);
    DescribeChangeSetRequest describeChangeSetRequest = new DescribeChangeSetRequest().withChangeSetName(stackName);
    ExecuteChangeSetRequest executeChangeSetRequest = new ExecuteChangeSetRequest().withChangeSetName(stackName);
    DeleteChangeSetRequest deleteChangeSetRequest = new DeleteChangeSetRequest().withChangeSetName(stackName);

    doThrow(AmazonClientException.class).when(service).getAmazonCloudFormationClient(any(), any());
    service.createChangeSet(
        region, createChangeRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.describeChangeSet(region, describeChangeSetRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.executeChangeSet(
        region, executeChangeSetRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.deleteChangeSet(
        region, deleteChangeSetRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.getAllStacks(
        region, describeStacksRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
    service.deleteStack(
        region, deleteStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.getAllStackEvents(region, describeStackEventsRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), 0L);

    service.createStack(
        region, createStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.updateStack(
        region, updateStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.describeStacks(
        region, describeStacksRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    service.getAllStackResources(region, describeStackResourcesRequest,
        AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());

    verify(awsApiHelperService, times(11)).handleAmazonClientException(any());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkException() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackName);
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackName);
    DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest().withStackName(stackName);
    CreateStackRequest createStackRequest = new CreateStackRequest().withStackName(stackName);
    UpdateStackRequest updateStackRequest = new UpdateStackRequest().withStackName(stackName);
    CreateChangeSetRequest createChangeRequest = new CreateChangeSetRequest().withStackName(stackName);
    DescribeChangeSetRequest describeChangeSetRequest = new DescribeChangeSetRequest().withChangeSetName(stackName);
    ExecuteChangeSetRequest executeChangeSetRequest = new ExecuteChangeSetRequest().withChangeSetName(stackName);
    DeleteChangeSetRequest deleteChangeSetRequest = new DeleteChangeSetRequest().withChangeSetName(stackName);
    DescribeStackResourcesRequest describeStackResourcesRequest =
        new DescribeStackResourcesRequest().withStackName(stackName);
    when(service.getAmazonCloudFormationClient(any(), any())).thenAnswer(invocationOnMock -> {
      throw new Exception("");
    });
    assertThatThrownBy(()
                           -> service.createChangeSet(region, createChangeRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.describeChangeSet(region, describeChangeSetRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.executeChangeSet(region, executeChangeSetRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.deleteChangeSet(region, deleteChangeSetRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.getAllStacks(region, describeStacksRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.deleteStack(region, deleteStackRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.getAllStackEvents(region, describeStackEventsRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), 0L))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.createStack(region, createStackRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.updateStack(region, updateStackRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.describeStacks(region, describeStacksRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> service.getAllStackResources(region, describeStackResourcesRequest,
                               AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build()))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(
        ()
            -> service.waitForStackDeletionCompleted(describeStacksRequest,
                AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, null, 1000L))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(
        ()
            -> service.getParamsData(
                AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, null, null))
        .isInstanceOf(InvalidRequestException.class);
    doThrow(WaiterUnrecoverableException.class).when(service).getAmazonCloudFormationClient(any(), any());
    assertThatThrownBy(
        ()
            -> service.waitForStackDeletionCompleted(describeStacksRequest,
                AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, null, 100L))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test(expected = Exception.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGeneralExceptionGetAllStacks() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stackName);
    doThrow(Exception.class).when(service).getAmazonCloudFormationClient(any(), any());
    service.getAllStacks(
        region, describeStacksRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
  }

  @Test(expected = Exception.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGeneralExceptionDeleteStacks() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String stackName = "Stack Name";
    String region = "us-west-1";
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(stackName);
    doThrow(Exception.class).when(service).getAmazonCloudFormationClient(any(), any());
    service.deleteStack(
        region, deleteStackRequest, AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGetParamsDataForS3Template() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String data = "s3://bucket/template.yaml";
    String region = "us-west-1";

    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    GetTemplateSummaryResult result = new GetTemplateSummaryResult();
    List<ParameterDeclaration> parameters = new ArrayList<>();
    parameters.add(new ParameterDeclaration().withParameterKey("key1").withParameterType("String"));
    parameters.add(new ParameterDeclaration().withParameterKey("key2").withParameterType("String"));
    result.setParameters(parameters);
    doReturn(result).when(mockClient).getTemplateSummary(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    List<ParameterDeclaration> response =
        service.getParamsData(AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region,
            data, AwsCFTemplatesType.S3);
    assertThat(response).size().isEqualTo(2);
    assertThat(response.get(0).getParameterKey()).isEqualTo("key1");
    assertThat(response.get(1).getParameterKey()).isEqualTo("key2");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGetParamsDataForS3TemplateThrowsServiceException() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String data = "s3://bucket/template.yaml";
    String region = "us-west-1";

    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    GetTemplateSummaryResult result = new GetTemplateSummaryResult();
    List<ParameterDeclaration> parameters = new ArrayList<>();
    parameters.add(new ParameterDeclaration().withParameterKey("key1").withParameterType("String"));
    parameters.add(new ParameterDeclaration().withParameterKey("key2").withParameterType("String"));
    result.setParameters(parameters);
    doThrow(AmazonServiceException.class).when(mockClient).getTemplateSummary(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.getParamsData(AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, data,
        AwsCFTemplatesType.S3);
    verify(awsApiHelperService, times(1));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void checkGetParamsDataForS3TemplateThrowsClientException() {
    char[] accessKey = "qwer".toCharArray();
    char[] secretKey = "pqrs".toCharArray();
    String data = "s3://bucket/template.yaml";
    String region = "us-west-1";

    AmazonCloudFormationClient mockClient = mock(AmazonCloudFormationClient.class);
    doReturn(mockClient).when(service).getAmazonCloudFormationClient(any(), any());
    GetTemplateSummaryResult result = new GetTemplateSummaryResult();
    List<ParameterDeclaration> parameters = new ArrayList<>();
    parameters.add(new ParameterDeclaration().withParameterKey("key1").withParameterType("String"));
    parameters.add(new ParameterDeclaration().withParameterKey("key2").withParameterType("String"));
    result.setParameters(parameters);
    doThrow(AmazonClientException.class).when(mockClient).getTemplateSummary(any());
    doNothing().when(mockTracker).trackCFCall(anyString());
    service.getParamsData(AwsInternalConfig.builder().accessKey(accessKey).secretKey(secretKey).build(), region, data,
        AwsCFTemplatesType.S3);
    verify(awsApiHelperService, times(1));
  }
}
