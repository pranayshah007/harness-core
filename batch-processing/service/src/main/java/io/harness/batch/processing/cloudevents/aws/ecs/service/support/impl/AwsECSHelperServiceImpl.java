/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.amazonaws.services.ecs.model.ServiceField.TAGS;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;

import io.harness.batch.processing.cloudevents.aws.ecs.service.support.AwsCredentialHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.remote.CEAwsServiceEndpointConfig;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.ClusterField;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceField;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskField;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AwsECSHelperServiceImpl implements AwsECSHelperService {
  @Autowired private AwsCredentialHelper awsCredentialHelper;
  private static final String exceptionMessage = "Error while calling cluster  {} {} {} ";

  @Override
  public List<String> listECSClusters(String region, AwsCrossAccountAttributes awsCrossAccountAttributes) {
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      return listECSClusters(closeableAmazonECSClient.getClient());
    } catch (Exception ex) {
      log.error(exceptionMessage, awsCrossAccountAttributes, region, ex.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public List<Cluster> describeECSClusters(
      String region, AwsCrossAccountAttributes awsCrossAccountAttributes, List<String> ecsClusters) {
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      int counter = 0;
      List<Cluster> clusters = newArrayList();
      DescribeClustersRequest describeClustersRequest = new DescribeClustersRequest().withInclude(ClusterField.TAGS);
      List<String> arnsBatch = newArrayList();
      for (String clusterArn : ecsClusters) {
        arnsBatch.add(clusterArn);
        counter++;
        if (counter % 100 == 0 || counter == ecsClusters.size()) {
          describeClustersRequest.withClusters(arnsBatch);
          DescribeClustersResult describeClustersResult =
              closeableAmazonECSClient.getClient().describeClusters(describeClustersRequest);
          clusters.addAll(describeClustersResult.getClusters());
          arnsBatch = newArrayList();
        }
      }
      return clusters;
    } catch (Exception ex) {
      log.warn("Exception describing Clusters - Skipping Adding Tags {}", ex.getMessage());
    }
    return emptyList();
  }

  @Override
  public List<Service> listServicesForCluster(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String region, String cluster) {
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      List<String> serviceArns = newArrayList();
      String nextToken = null;
      ListServicesRequest listServicesRequest = new ListServicesRequest().withCluster(cluster);
      do {
        listServicesRequest.setNextToken(nextToken);
        ListServicesResult listServicesResult = closeableAmazonECSClient.getClient().listServices(listServicesRequest);
        List<String> arnsBatch = listServicesResult.getServiceArns();
        if (isNotEmpty(arnsBatch)) {
          serviceArns.addAll(arnsBatch);
        }
        nextToken = listServicesResult.getNextToken();
      } while (nextToken != null);
      int counter = 0;
      List<Service> allServices = newArrayList();
      DescribeServicesRequest describeServicesRequest =
          new DescribeServicesRequest().withCluster(cluster).withInclude(TAGS);

      while (counter < serviceArns.size()) {
        // We can ONLY describe 10 services at a time.
        List<String> arnsBatch = newArrayList();
        for (int i = 0; i < 10 && counter < serviceArns.size(); i++, counter++) {
          arnsBatch.add(serviceArns.get(counter));
        }
        describeServicesRequest.withServices(arnsBatch);
        DescribeServicesResult describeServicesResult =
            closeableAmazonECSClient.getClient().describeServices(describeServicesRequest);
        allServices.addAll(describeServicesResult.getServices());
      }
      return allServices;
    } catch (Exception ex) {
      log.error("Exception listServicesForCluster {}", ex.getMessage());
    }
    return emptyList();
  }

  @Override
  public List<ContainerInstance> listContainerInstancesForCluster(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String region, String cluster) {
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      List<String> containerInstanceArns = newArrayList();
      String nextToken = null;
      ListContainerInstancesRequest listContainerInstancesRequest =
          new ListContainerInstancesRequest().withCluster(cluster);
      do {
        listContainerInstancesRequest.withNextToken(nextToken);
        ListContainerInstancesResult listContainerInstancesResult =
            closeableAmazonECSClient.getClient().listContainerInstances(listContainerInstancesRequest);
        List<String> arnsBatch = listContainerInstancesResult.getContainerInstanceArns();
        if (isNotEmpty(arnsBatch)) {
          containerInstanceArns.addAll(arnsBatch);
        }
        nextToken = listContainerInstancesResult.getNextToken();
      } while (nextToken != null);

      int counter = 0;
      List<ContainerInstance> allContainerInstance = newArrayList();
      DescribeContainerInstancesRequest describeContainerInstancesRequest =
          new DescribeContainerInstancesRequest().withCluster(cluster).withInclude(ContainerInstanceField.TAGS);
      List<String> arnsBatch = newArrayList();
      for (String containerInstanceArn : containerInstanceArns) {
        arnsBatch.add(containerInstanceArn);
        counter++;
        if (counter % 100 == 0 || counter == containerInstanceArns.size()) {
          describeContainerInstancesRequest.withContainerInstances(arnsBatch);
          DescribeContainerInstancesResult describeContainerInstancesResult =
              closeableAmazonECSClient.getClient().describeContainerInstances(describeContainerInstancesRequest);
          allContainerInstance.addAll(describeContainerInstancesResult.getContainerInstances());
          arnsBatch = newArrayList();
        }
      }
      return allContainerInstance;
    } catch (Exception ex) {
      log.error("Exception listContainerInstancesForCluster {}", ex.getMessage());
    }
    return emptyList();
  }

  @Override
  public List<String> listTasksArnForService(AwsCrossAccountAttributes awsCrossAccountAttributes, String region,
      String cluster, String service, DesiredStatus desiredStatus) {
    List<String> taskArns = newArrayList();
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      String nextToken = null;
      ListTasksRequest listTasksRequest = new ListTasksRequest().withCluster(cluster);
      if (null != desiredStatus) {
        listTasksRequest.withDesiredStatus(desiredStatus);
      }
      do {
        listTasksRequest.withNextToken(nextToken);
        if (null != service) {
          listTasksRequest.withServiceName(service);
        }
        ListTasksResult listTasksResult = closeableAmazonECSClient.getClient().listTasks(listTasksRequest);
        List<String> arnsBatch = listTasksResult.getTaskArns();
        if (isNotEmpty(arnsBatch)) {
          taskArns.addAll(arnsBatch);
        }
        nextToken = listTasksResult.getNextToken();
      } while (nextToken != null);
    } catch (Exception ex) {
      log.error("Exception listTasksArnForService {}", ex.getMessage());
    }
    return taskArns;
  }

  @Override
  public List<Task> listTasksForService(AwsCrossAccountAttributes awsCrossAccountAttributes, String region,
      String cluster, String service, DesiredStatus desiredStatus) {
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonECSClient(region, awsCrossAccountAttributes))) {
      List<String> taskArns =
          listTasksArnForService(awsCrossAccountAttributes, region, cluster, service, desiredStatus);
      int counter = 0;
      List<Task> allTasks = newArrayList();
      DescribeTasksRequest describeTasksRequest =
          new DescribeTasksRequest().withCluster(cluster).withInclude(TaskField.TAGS);
      while (counter < taskArns.size()) {
        // We can ONLY describe 100 tasks at a time.
        List<String> arnsBatch = newArrayList();
        for (int i = 0; i < 100 && counter < taskArns.size(); i++, counter++) {
          arnsBatch.add(taskArns.get(counter));
        }
        describeTasksRequest.withTasks(arnsBatch);
        DescribeTasksResult describeTasksResult =
            closeableAmazonECSClient.getClient().describeTasks(describeTasksRequest);
        allTasks.addAll(describeTasksResult.getTasks());
      }
      return allTasks;
    } catch (Exception ex) {
      log.error("Exception listTasksForService ", ex);
    }
    return emptyList();
  }

  @VisibleForTesting
  AmazonECSClient getAmazonECSClient(String region, AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    AmazonECSClientBuilder builder = AmazonECSClientBuilder.standard();
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider
            .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
            .withExternalId(awsCrossAccountAttributes.getExternalId())
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    CEAwsServiceEndpointConfig ceAwsServiceEndpointConfig = awsCredentialHelper.getCeAwsServiceEndpointConfig();
    if (ceAwsServiceEndpointConfig != null && ceAwsServiceEndpointConfig.isEnabled()) {
      return (AmazonECSClient) builder
          .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
              ceAwsServiceEndpointConfig.getEcsEndPointUrl(), ceAwsServiceEndpointConfig.getEndPointRegion()))
          .build();
    }
    return (AmazonECSClient) builder.withRegion(region).build();
  }

  private List<String> listECSClusters(AmazonECSClient amazonECSClient) {
    List<String> clusterList = new ArrayList<>();
    String nextToken = null;
    ListClustersRequest listClustersRequest = new ListClustersRequest();
    do {
      listClustersRequest.withNextToken(nextToken);
      ListClustersResult listClustersResult = amazonECSClient.listClusters(listClustersRequest);
      clusterList.addAll(listClustersResult.getClusterArns());
      nextToken = listClustersResult.getNextToken();
    } while (nextToken != null);
    return clusterList;
  }
}
