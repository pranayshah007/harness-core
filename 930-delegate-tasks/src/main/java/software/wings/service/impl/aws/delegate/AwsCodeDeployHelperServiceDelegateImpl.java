/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsCodeDeployS3LocationData;
import software.wings.service.intfc.aws.delegate.AwsCodeDeployHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClient;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.codedeploy.model.DeploymentGroupInfo;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupResult;
import com.amazonaws.services.codedeploy.model.InstanceStatus;
import com.amazonaws.services.codedeploy.model.ListApplicationsRequest;
import com.amazonaws.services.codedeploy.model.ListApplicationsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentConfigsRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentConfigsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentGroupsRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentGroupsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentInstancesRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentInstancesResult;
import com.amazonaws.services.codedeploy.model.RevisionLocation;
import com.amazonaws.services.codedeploy.model.S3Location;
import com.amazonaws.services.ec2.model.Instance;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
@OwnedBy(CDP)
public class AwsCodeDeployHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsCodeDeployHelperServiceDelegate {
  @Inject private AwsUtils awsUtils;
  @Inject private AwsEc2HelperServiceDelegate awsEc2HelperServiceDelegate;

  @VisibleForTesting
  AmazonCodeDeployClient getAmazonCodeDeployClient(Regions region, AwsConfig awsConfig) {
    AmazonCodeDeployClientBuilder builder = AmazonCodeDeployClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonCodeDeployClient) builder.build();
  }

  @Override
  public List<String> listApplications(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      List<String> applications = new ArrayList<>();
      String nextToken = null;
      ListApplicationsResult listApplicationsResult;
      ListApplicationsRequest listApplicationsRequest;
      do {
        listApplicationsRequest = new ListApplicationsRequest().withNextToken(nextToken);
        listApplicationsResult = closeableAmazonCodeDeployClient.getClient().listApplications(listApplicationsRequest);
        tracker.trackCDCall("Get Applications");
        applications.addAll(listApplicationsResult.getApplications());
        nextToken = listApplicationsResult.getNextToken();
      } while (nextToken != null);
      return applications;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listApplications", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public List<String> listDeploymentConfiguration(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      String nextToken = null;
      List<String> deploymentConfigurations = new ArrayList<>();
      ListDeploymentConfigsResult listDeploymentConfigsResult;
      ListDeploymentConfigsRequest listDeploymentConfigsRequest;
      do {
        listDeploymentConfigsRequest = new ListDeploymentConfigsRequest().withNextToken(nextToken);
        tracker.trackCDCall("List Deployment Configs");
        listDeploymentConfigsResult =
            closeableAmazonCodeDeployClient.getClient().listDeploymentConfigs(listDeploymentConfigsRequest);
        deploymentConfigurations.addAll(listDeploymentConfigsResult.getDeploymentConfigsList());
        nextToken = listDeploymentConfigsResult.getNextToken();
      } while (nextToken != null);
      return deploymentConfigurations;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listDeploymentConfiguration", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public List<String> listDeploymentGroups(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region, String appName) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      String nextToken = null;
      List<String> deploymentGroups = new ArrayList<>();
      ListDeploymentGroupsResult listDeploymentGroupsResult;
      ListDeploymentGroupsRequest listDeploymentGroupsRequest;
      do {
        listDeploymentGroupsRequest =
            new ListDeploymentGroupsRequest().withNextToken(nextToken).withApplicationName(appName);
        tracker.trackCDCall("List Deployment Groups");
        listDeploymentGroupsResult =
            closeableAmazonCodeDeployClient.getClient().listDeploymentGroups(listDeploymentGroupsRequest);
        deploymentGroups.addAll(listDeploymentGroupsResult.getDeploymentGroups());
        nextToken = listDeploymentGroupsResult.getNextToken();
      } while (nextToken != null);
      return deploymentGroups;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listDeploymentGroups", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public List<Instance> listDeploymentInstances(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, String region, String deploymentId) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      String nextToken = null;
      List<String> instanceIds = new ArrayList<>();
      ListDeploymentInstancesRequest listDeploymentInstancesRequest;
      ListDeploymentInstancesResult listDeploymentInstancesResult;
      do {
        listDeploymentInstancesRequest = new ListDeploymentInstancesRequest()
                                             .withNextToken(nextToken)
                                             .withDeploymentId(deploymentId)
                                             .withInstanceStatusFilter(asList(InstanceStatus.Succeeded.name()));
        tracker.trackCDCall("List Deployment Instances");
        listDeploymentInstancesResult =
            closeableAmazonCodeDeployClient.getClient().listDeploymentInstances(listDeploymentInstancesRequest);
        instanceIds.addAll(listDeploymentInstancesResult.getInstancesList());
        nextToken = listDeploymentInstancesResult.getNextToken();
      } while (nextToken != null);
      if (isNotEmpty(instanceIds)) {
        Set<String> instancesIdsSet = Sets.newHashSet(instanceIds);
        List<Instance> runningInstances = awsEc2HelperServiceDelegate.listEc2Instances(
            awsConfig, encryptedDataDetails, region, awsUtils.getFilters(null, null), false);
        List<Instance> result = new ArrayList<>();
        runningInstances.forEach(instance -> {
          if (instancesIdsSet.contains(instance.getInstanceId())) {
            result.add(instance);
          }
        });
        return result;
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listDeploymentInstances", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public AwsCodeDeployS3LocationData listAppRevision(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String region, String appName, String deploymentGroupName) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      GetDeploymentGroupRequest getDeploymentGroupRequest =
          new GetDeploymentGroupRequest().withApplicationName(appName).withDeploymentGroupName(deploymentGroupName);
      tracker.trackCDCall("Get Deployment Group");
      GetDeploymentGroupResult getDeploymentGroupResult =
          closeableAmazonCodeDeployClient.getClient().getDeploymentGroup(getDeploymentGroupRequest);
      DeploymentGroupInfo deploymentGroupInfo = getDeploymentGroupResult.getDeploymentGroupInfo();
      RevisionLocation revisionLocation = deploymentGroupInfo.getTargetRevision();
      if (revisionLocation == null || revisionLocation.getS3Location() == null) {
        return null;
      }
      S3Location s3Location = revisionLocation.getS3Location();
      return AwsCodeDeployS3LocationData.builder()
          .bucket(s3Location.getBucket())
          .key(s3Location.getKey())
          .bundleType(s3Location.getBundleType())
          .build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listAppRevision", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }
}
