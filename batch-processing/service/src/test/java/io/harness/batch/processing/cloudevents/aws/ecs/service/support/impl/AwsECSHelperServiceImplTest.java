/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Service;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AwsECSHelperServiceImplTest extends CategoryTest {
  @Spy private AwsECSHelperServiceImpl awsECSHelperService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListECSClusters() {
    String nextToken = "nextClusterToken";
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsECSHelperService).getAmazonECSClient(any(), any());
    doReturn(
        new ListClustersResult().withClusterArns(ImmutableList.of("cluster1", "cluster2")).withNextToken(nextToken))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));

    doReturn(new ListClustersResult().withClusterArns(ImmutableList.of("cluster3", "cluster4")).withNextToken(null))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(nextToken));

    List<String> eksClusters = awsECSHelperService.listECSClusters(any(), any());
    assertThat(eksClusters).hasSize(4);
    assertThat(eksClusters.get(0)).isEqualTo("cluster1");
    assertThat(eksClusters.get(1)).isEqualTo("cluster2");
    assertThat(eksClusters.get(2)).isEqualTo("cluster3");
    assertThat(eksClusters.get(3)).isEqualTo("cluster4");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListEmptyECSClustersWhenExceptionOccur() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsECSHelperService).getAmazonECSClient(any(), any());

    doThrow(new AmazonECSException("ECS Exception"))
        .when(mockClient)
        .listClusters(new ListClustersRequest().withNextToken(null));
    List<String> eksClusters = awsECSHelperService.listECSClusters(any(), any());
    assertThat(eksClusters).hasSize(0);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListServicesForCluster() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsECSHelperService).getAmazonECSClient(any(), any());
    doReturn(new ListServicesResult().withServiceArns("arn0", "arn1")).when(mockClient).listServices(any());
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withServiceArn("arn0"), new Service().withServiceArn("arn1")))
        .when(mockClient)
        .describeServices(any());
    List<Service> services =
        awsECSHelperService.listServicesForCluster(AwsCrossAccountAttributes.builder().build(), "us-east-1", "cluster");
    assertThat(services).hasSize(2);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListContainerInstancesForCluster() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsECSHelperService).getAmazonECSClient(any(), any());
    doReturn(new ListServicesResult().withServiceArns("arn0", "arn1")).when(mockClient).listServices(any());
    ContainerInstance containerInstance = new ContainerInstance().withContainerInstanceArn("arn1");
    doReturn(new DescribeContainerInstancesResult().withContainerInstances(containerInstance))
        .when(mockClient)
        .describeContainerInstances(any());
    doReturn(new ListContainerInstancesResult().withContainerInstanceArns("arn1"))
        .when(mockClient)
        .listContainerInstances(any());
    List<ContainerInstance> containerInstances = awsECSHelperService.listContainerInstancesForCluster(
        AwsCrossAccountAttributes.builder().build(), "us-east-1", "cluster");
    assertThat(containerInstances).hasSize(1).contains(containerInstance);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testListTaskArnForService() {
    AmazonECSClient mockClient = mock(AmazonECSClient.class);
    doReturn(mockClient).when(awsECSHelperService).getAmazonECSClient(any(), any());
    doReturn(new ListTasksResult().withTaskArns("arn0", "arn1")).when(mockClient).listTasks(any());
    List<String> taskArns = awsECSHelperService.listTasksArnForService(
        AwsCrossAccountAttributes.builder().build(), "us-east-1", "cluster", "service", DesiredStatus.RUNNING);
    assertThat(taskArns).hasSize(2);
  }
}
