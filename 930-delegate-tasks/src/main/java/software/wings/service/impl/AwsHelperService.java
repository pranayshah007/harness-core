/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_CLOUD_PROVIDER;
import static io.harness.exception.WingsException.USER;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.util.AwsCallTracker;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AWSTemporaryCredentials;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcrConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.dto.SettingAttribute;
import software.wings.common.InfrastructureConstants;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.amazons3.AWSTemporaryCredentialsRestClient;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.Activity;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClient;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.codedeploy.model.CreateDeploymentRequest;
import com.amazonaws.services.codedeploy.model.CreateDeploymentResult;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupResult;
import com.amazonaws.services.codedeploy.model.GetDeploymentRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentResult;
import com.amazonaws.services.codedeploy.model.ListApplicationsRequest;
import com.amazonaws.services.codedeploy.model.ListApplicationsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentConfigsRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentConfigsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentGroupsRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentGroupsResult;
import com.amazonaws.services.codedeploy.model.ListDeploymentInstancesRequest;
import com.amazonaws.services.codedeploy.model.ListDeploymentInstancesResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.ResourceType;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.CreateClusterResult;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.CreateServiceResult;
import com.amazonaws.services.ecs.model.DeleteServiceRequest;
import com.amazonaws.services.ecs.model.DeleteServiceResult;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DeregisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.ServiceField;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.ecs.model.TagResourceRequest;
import com.amazonaws.services.ecs.model.TagResourceResult;
import com.amazonaws.services.ecs.model.UntagResourceRequest;
import com.amazonaws.services.ecs.model.UntagResourceResult;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.amazonaws.services.ecs.model.UpdateServiceResult;
import com.amazonaws.services.ecs.waiters.AmazonECSWaiters;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.waiters.FixedDelayStrategy;
import com.amazonaws.waiters.MaxAttemptsRetryStrategy;
import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsHelperService {
  @Inject private EncryptionService encryptionService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private ManagerExpressionEvaluator expressionEvaluator;
  @Inject private AwsCallTracker tracker;
  @Inject private AwsApiHelperService awsApiHelperService;

  public static final long AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);
  public AmazonECRClient getAmazonEcrClient(AwsConfig awsConfig, String region) {
    return awsApiHelperService.getAmazonEcrClient(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region);
  }
  public AmazonEC2Client getAmazonEc2Client(AwsConfig awsConfig) {
    return awsApiHelperService.getAmazonEc2Client(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
  }

  public AWSSecurityTokenServiceClient getAmazonAWSSecurityTokenServiceClient(AwsConfig awsConfig, String region) {
    return awsApiHelperService.getAWSSecurityTokenServiceClient(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region);
  }

  public AWSCredentialsProvider getAWSCredentialsProvider(AwsConfig awsConfig) {
    return awsApiHelperService.getAwsCredentialsProvider(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
  }

  public boolean validateAwsAccountCredential(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsConfig))) {
      tracker.trackEC2Call("Describe Regions");
      closeableAmazonEC2Client.getClient().describeRegions();
    } catch (AmazonEC2Exception amazonEC2Exception) {
      if (amazonEC2Exception.getStatusCode() == 401) {
        throw new InvalidRequestException("Invalid AWS credentials", INVALID_CLOUD_PROVIDER, USER);
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception validateAwsAccountCredential", sanitizedException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
    return true;
  }

  public void validateAwsAccountCredential(String accessKey, char[] secretKey) {
    try (
        CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client = new CloseableAmazonWebServiceClient(
            getAmazonEc2Client(AwsConfig.builder().accessKey(accessKey.toCharArray()).secretKey(secretKey).build()))) {
      closeableAmazonEC2Client.getClient().describeRegions();
      tracker.trackEC2Call("Describe Regions");
    } catch (AmazonEC2Exception amazonEC2Exception) {
      if (amazonEC2Exception.getStatusCode() == 401) {
        throw new InvalidRequestException("Invalid AWS credentials", ErrorCode.INVALID_CLOUD_PROVIDER, USER);
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception validateAwsAccountCredential", sanitizedException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
  }

  public AmazonCloudWatchClient getAwsCloudWatchClient(String region, AwsConfig awsConfig) {
    AmazonCloudWatchClientBuilder builder = AmazonCloudWatchClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonCloudWatchClient) builder.build();
  }

  private AmazonECSClient getAmazonEcsClient(String region, AwsConfig awsConfig) {
    AmazonECSClientBuilder builder = AmazonECSClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonECSClient) builder.build();
  }

  public AmazonECRClient getAmazonEcrClient(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(ecrConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(ecrConfig, encryptedDataDetails);
    return (AmazonECRClient) AmazonECRClientBuilder.standard()
        .withRegion(ecrConfig.getRegion())
        .withCredentials(new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(ecrConfig.getAccessKey(), new String(ecrConfig.getSecretKey()))))
        .build();
  }

  /**
   * Gets amazon ecr client.
   *
   * @return the auth token
   */
  public String getAmazonEcrAuthToken(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    encryptionService.decrypt(ecrConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(ecrConfig, encryptedDataDetails);
    AmazonECRClient ecrClient = (AmazonECRClient) AmazonECRClientBuilder.standard()
                                    .withRegion(ecrConfig.getRegion())
                                    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                                        ecrConfig.getAccessKey(), new String(ecrConfig.getSecretKey()))))
                                    .build();

    String url = ecrConfig.getEcrUrl();
    // Example: https://830767422336.dkr.ecr.us-east-1.amazonaws.com/
    String awsAccount = url.substring(8, url.indexOf('.'));
    tracker.trackECRCall("Get Auth Token");
    return ecrClient
        .getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(singletonList(awsAccount)))
        .getAuthorizationData()
        .get(0)
        .getAuthorizationToken();
  }

  private AmazonS3Client getAmazonS3Client(String region, AwsConfig awsConfig) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(region).withForceGlobalBucketAccessEnabled(true);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonS3Client) builder.build();
  }

  private AmazonS3Client getAmazonS3Client(AwsConfig awsConfig) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(getRegion(awsConfig)).withForceGlobalBucketAccessEnabled(true);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonS3Client) builder.build();
  }

  private AmazonEC2Client getAmazonEc2Client(String region, AwsConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonEC2Client) builder.build();
  }

  private AmazonCodeDeployClient getAmazonCodeDeployClient(Regions region, AwsConfig awsConfig) {
    AmazonCodeDeployClientBuilder builder = AmazonCodeDeployClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonCodeDeployClient) builder.build();
  }

  public AmazonAutoScalingClient getAmazonAutoScalingClient(Regions region, AwsConfig awsConfig) {
    AmazonAutoScalingClientBuilder builder = AmazonAutoScalingClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonAutoScalingClient) builder.build();
  }

  private AmazonElasticLoadBalancingClient getAmazonElasticLoadBalancingClient(Regions region, AwsConfig awsConfig) {
    com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder builder =
        com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.standard().withRegion(
            region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonElasticLoadBalancingClient) builder.build();
  }

  private com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient getClassicElbClient(
      Regions region, AwsConfig awsConfig) {
    AmazonElasticLoadBalancingClientBuilder builder =
        AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient) builder.build();
  }

  /**
   * Gets hostname from dns name.
   *
   * @param dnsName the dns name
   * @return the hostname from dns name
   */
  public String getHostnameFromPrivateDnsName(String dnsName) {
    return isNotEmpty(dnsName) ? dnsName.split("\\.")[0] : "";
  }

  public String getHostnameFromConvention(Map<String, Object> context, String hostNameConvention) {
    if (isEmpty(hostNameConvention)) {
      hostNameConvention = InfrastructureConstants.DEFAULT_AWS_HOST_NAME_CONVENTION;
    }
    return expressionEvaluator.substitute(hostNameConvention, context);
  }

  public String getInstanceId(Regions region, String hostName, AwsConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region.getName(), awsConfig))) {
      String instanceId;
      tracker.trackEC2Call("Describe Instances");
      DescribeInstancesResult describeInstancesResult = closeableAmazonEC2Client.getClient().describeInstances(
          new DescribeInstancesRequest().withFilters(new Filter("private-dns-name").withValues(hostName + "*")));
      instanceId = describeInstancesResult.getReservations()
                       .stream()
                       .flatMap(reservation -> reservation.getInstances().stream())
                       .map(Instance::getInstanceId)
                       .findFirst()
                       .orElse(null);

      if (isBlank(instanceId)) {
        tracker.trackEC2Call("Describe Instances");
        describeInstancesResult = closeableAmazonEC2Client.getClient().describeInstances(
            new DescribeInstancesRequest().withFilters(new Filter("private-ip-address").withValues(hostName)));
        instanceId = describeInstancesResult.getReservations()
                         .stream()
                         .flatMap(reservation -> reservation.getInstances().stream())
                         .map(Instance::getInstanceId)
                         .findFirst()
                         .orElse(instanceId);
      }

      if (isBlank(instanceId)) {
        tracker.trackEC2Call("Describe Instances");
        describeInstancesResult = closeableAmazonEC2Client.getClient().describeInstances(
            new DescribeInstancesRequest().withFilters(new Filter("dns-name").withValues(hostName + "*")));
        instanceId = describeInstancesResult.getReservations()
                         .stream()
                         .flatMap(reservation -> reservation.getInstances().stream())
                         .map(Instance::getInstanceId)
                         .findFirst()
                         .orElse(instanceId);
      }

      if (isBlank(instanceId)) {
        tracker.trackEC2Call("Describe Instances");
        describeInstancesResult = closeableAmazonEC2Client.getClient().describeInstances(
            new DescribeInstancesRequest().withFilters(new Filter("ip-address").withValues(hostName)));
        instanceId = describeInstancesResult.getReservations()
                         .stream()
                         .flatMap(reservation -> reservation.getInstances().stream())
                         .map(Instance::getInstanceId)
                         .findFirst()
                         .orElse(instanceId);
      }
      return instanceId;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getInstanceId", sanitizedException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizedException), sanitizedException);
    }
  }

  public List<Bucket> listS3Buckets(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsConfig))) {
      tracker.trackS3Call("List Buckets");
      return closeableAmazonS3Client.getClient().listBuckets();
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listS3Buckets", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return Collections.emptyList();
  }

  public AwsConfig validateAndGetAwsConfig(
      SettingAttribute connectorConfig, List<EncryptedDataDetail> encryptedDataDetails, boolean isInstanceSync) {
    if (connectorConfig == null || connectorConfig.getValue() == null
        || !(connectorConfig.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("connectorConfig is not of type AwsConfig");
    }
    encryptionService.decrypt((EncryptableSetting) connectorConfig.getValue(), encryptedDataDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        (EncryptableSetting) connectorConfig.getValue(), encryptedDataDetails);
    return (AwsConfig) connectorConfig.getValue();
  }

  public ListDeploymentGroupsResult listDeploymentGroupsResult(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      ListDeploymentGroupsRequest listDeploymentGroupsRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("List Deployment Groups");
      return closeableAmazonCodeDeployClient.getClient().listDeploymentGroups(listDeploymentGroupsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listDeploymentGroupsResult", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListDeploymentGroupsResult();
  }

  public ListApplicationsResult listApplicationsResult(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, ListApplicationsRequest listApplicationsRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("List Applications");
      return closeableAmazonCodeDeployClient.getClient().listApplications(listApplicationsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listApplicationsResult", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListApplicationsResult();
  }

  public ListDeploymentConfigsResult listDeploymentConfigsResult(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      ListDeploymentConfigsRequest listDeploymentConfigsRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("List Deployment Configs");
      return closeableAmazonCodeDeployClient.getClient().listDeploymentConfigs(listDeploymentConfigsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listDeploymentConfigsResult", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListDeploymentConfigsResult();
  }

  public GetDeploymentResult getCodeDeployDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, GetDeploymentRequest getDeploymentRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("Get Deployment");
      return closeableAmazonCodeDeployClient.getClient().getDeployment(getDeploymentRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getCodeDeployDeployment", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new GetDeploymentResult();
  }

  public GetDeploymentGroupResult getCodeDeployDeploymentGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, GetDeploymentGroupRequest getDeploymentGroupRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("Get Deployment Group");
      return closeableAmazonCodeDeployClient.getClient().getDeploymentGroup(getDeploymentGroupRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getCodeDeployDeploymentGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new GetDeploymentGroupResult();
  }

  public CreateDeploymentResult createCodeDeployDeployment(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, CreateDeploymentRequest createDeploymentRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("Create Deployment");
      return closeableAmazonCodeDeployClient.getClient().createDeployment(createDeploymentRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createCodeDeployDeployment", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new CreateDeploymentResult();
  }

  public ListDeploymentInstancesResult listDeploymentInstances(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      ListDeploymentInstancesRequest listDeploymentInstancesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCodeDeployClient> closeableAmazonCodeDeployClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeDeployClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCDCall("List Deployment Instances");
      return closeableAmazonCodeDeployClient.getClient().listDeploymentInstances(listDeploymentInstancesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listDeploymentInstances", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListDeploymentInstancesResult();
  }

  /**
   * Be a bit careful when calling this method.
   * When describeInstancesRequest.instanceIds is empty, AmazonEc2Client.describeInstances(), returns all instances
   * in aws farm or if filter is present then all the instances that match the filter  in describeInstancesRequest.
   * @param awsConfig
   * @param encryptionDetails
   * @param region
   * @param describeInstancesRequest
   * @return
   */
  public DescribeInstancesResult describeEc2Instances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, DescribeInstancesRequest describeInstancesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      tracker.trackEC2Call("Describe Instances");
      return closeableAmazonEC2Client.getClient().describeInstances(describeInstancesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception describeEc2Instances", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeInstancesResult();
  }

  public DescribeImagesResult desribeEc2Images(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, DescribeImagesRequest describeImagesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      tracker.trackEC2Call("Describe Images");
      return closeableAmazonEC2Client.getClient().describeImages(describeImagesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception desribeEc2Images", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeImagesResult();
  }

  public List<String> listRegions(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    return awsApiHelperService.listRegions(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
  }

  public Set<String> listTags(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return listTags(awsConfig, encryptionDetails, region, ResourceType.Instance);
  }

  public Set<String> listTags(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, ResourceType resourceType) {
    String nextToken = null;
    Set<String> tags = new HashSet<>();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      do {
        tracker.trackEC2Call("Describe Tags");

        DescribeTagsResult describeTagsResult = closeableAmazonEC2Client.getClient().describeTags(
            new DescribeTagsRequest()
                .withNextToken(nextToken)
                .withFilters(new Filter("resource-type").withValues(resourceType.toString()))
                .withMaxResults(1000));
        tags.addAll(describeTagsResult.getTags().stream().map(TagDescription::getKey).collect(Collectors.toSet()));
        nextToken = describeTagsResult.getNextToken();
      } while (nextToken != null);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listTags", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return tags;
  }

  public CreateClusterResult createCluster(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, CreateClusterRequest createClusterRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Create Cluster");
      return closeableAmazonECSClient.getClient().createCluster(createClusterRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new CreateClusterResult();
  }

  public DescribeClustersResult describeClusters(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeClustersRequest describeClustersRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Describe Cluster");
      return closeableAmazonECSClient.getClient().describeClusters(describeClustersRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeClustersResult();
  }

  public ListClustersResult listClusters(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, ListClustersRequest listClustersRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("List Clusters");
      return closeableAmazonECSClient.getClient().listClusters(listClustersRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListClustersResult();
  }

  public RegisterTaskDefinitionResult registerTaskDefinition(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, RegisterTaskDefinitionRequest registerTaskDefinitionRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Register Task Definition");
      return closeableAmazonECSClient.getClient().registerTaskDefinition(registerTaskDefinitionRequest);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public ListServicesResult listServices(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, ListServicesRequest listServicesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("List Services");
      return closeableAmazonECSClient.getClient().listServices(listServicesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListServicesResult();
  }

  public DescribeServicesResult describeServices(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeServicesRequest describeServicesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Describe Services");
      return closeableAmazonECSClient.getClient().describeServices(describeServicesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeServicesResult();
  }

  public void waitTillECSServiceIsStable(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeServicesRequest describeServicesRequest,
      int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback) {
    String serviceName = "";
    List<String> services = describeServicesRequest.getServices();
    if (services != null && isNotEmpty(services)) {
      serviceName = services.get(0);
    }
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Wait for Service to be stable");
      AmazonECSWaiters waiter = new AmazonECSWaiters(closeableAmazonECSClient.getClient());

      // Polling interval of 10 sec with total waiting done till a timeout of <serviceSteadyStateTimeout> min
      int delayInSeconds = 10;
      int retryAttempts = (int) TimeUnit.MINUTES.toSeconds(serviceSteadyStateTimeout) / delayInSeconds;
      waiter.servicesStable().run(
          new WaiterParameters<>(describeServicesRequest)
              .withPollingStrategy(new PollingStrategy(
                  new MaxAttemptsRetryStrategy(retryAttempts), new FixedDelayStrategy(delayInSeconds))));

    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (WaiterTimedOutException waiterTimedOutException) {
      String msg = format("Timed out while waiting for service %s to be in stable state", serviceName);
      executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new TimeoutException(msg, "Timeout",
          (WaiterTimedOutException) ExceptionMessageSanitizer.sanitizeException(waiterTimedOutException),
          WingsException.EVERYBODY);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      if (sanitizeException instanceof InterruptedException) {
        String msg = format("Interrupted while waiting for service %s to reach stable state", serviceName);
        executionLogCallback.saveExecutionLog(msg, LogLevel.ERROR);
        throw new InvalidRequestException(msg, sanitizeException);
      }
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public List<Service> getServiceForCluster(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String cluster, String region) {
    ListServicesRequest listServicesRequest = new ListServicesRequest().withCluster(cluster);
    ListServicesResult listServicesResult = listServices(region, awsConfig, encryptionDetails, listServicesRequest);

    List<Service> services = new ArrayList<>();

    if (isNotEmpty(listServicesResult.getServiceArns())) {
      do {
        services.addAll(describeServices(region, awsConfig, encryptionDetails,
            new DescribeServicesRequest()
                .withCluster(cluster)
                .withServices(listServicesResult.getServiceArns())
                .withInclude(ServiceField.TAGS))
                            .getServices());

        listServicesRequest.setNextToken(listServicesResult.getNextToken());
      } while (listServicesResult.getNextToken() != null && listServicesResult.getServiceArns().size() == 10);
    }

    return services;
  }

  public CreateServiceResult createService(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, CreateServiceRequest createServiceRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Create Service");
      return closeableAmazonECSClient.getClient().createService(createServiceRequest);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public RunTaskResult triggerEcsRunTask(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, RunTaskRequest runTaskRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Ecs Run Task Request");
      return closeableAmazonECSClient.getClient().runTask(runTaskRequest);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public UpdateServiceResult updateService(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, UpdateServiceRequest updateServiceRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Update Service");
      return closeableAmazonECSClient.getClient().updateService(updateServiceRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new UpdateServiceResult();
  }

  public DeleteServiceResult deleteService(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeleteServiceRequest deleteServiceRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Delete Service");
      return closeableAmazonECSClient.getClient().deleteService(deleteServiceRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DeleteServiceResult();
  }

  public DeregisterTaskDefinitionResult deregisterTaskDefinitions(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DeregisterTaskDefinitionRequest deregisterTaskDefinitionRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("List Tasks");
      return closeableAmazonECSClient.getClient().deregisterTaskDefinition(deregisterTaskDefinitionRequest);
    } catch (ClusterNotFoundException ex) {
      throw new WingsException(ErrorCode.AWS_CLUSTER_NOT_FOUND)
          .addParam("message", ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)));
    } catch (ServiceNotFoundException ex) {
      throw new WingsException(ErrorCode.AWS_SERVICE_NOT_FOUND)
          .addParam("message", ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)));
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DeregisterTaskDefinitionResult();
  }

  public ListTasksResult listTasks(String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      ListTasksRequest listTasksRequest, boolean isInstanceSync) {
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("List Tasks");
      return closeableAmazonECSClient.getClient().listTasks(listTasksRequest.withMaxResults(100));
    } catch (ClusterNotFoundException ex) {
      throw new WingsException(ErrorCode.AWS_CLUSTER_NOT_FOUND)
          .addParam("message", ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)));
    } catch (ServiceNotFoundException ex) {
      throw new WingsException(ErrorCode.AWS_SERVICE_NOT_FOUND)
          .addParam("message", ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)));
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListTasksResult();
  }

  public DescribeTaskDefinitionResult describeTaskDefinition(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeTaskDefinitionRequest describeTaskDefinitionRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Desscribe Task Definition");
      return closeableAmazonECSClient.getClient().describeTaskDefinition(describeTaskDefinitionRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeTaskDefinitionResult();
  }

  public DescribeTasksResult describeTasks(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, DescribeTasksRequest describeTasksRequest, boolean isInstanceSync) {
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Describe Tasks");
      return closeableAmazonECSClient.getClient().describeTasks(describeTasksRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeTasksResult();
  }

  public DescribeContainerInstancesResult describeContainerInstances(String region, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails,
      DescribeContainerInstancesRequest describeContainerInstancesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Describe Container Instances");
      return closeableAmazonECSClient.getClient().describeContainerInstances(describeContainerInstancesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeContainerInstancesResult();
  }

  public ListImagesResult listEcrImages(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      ListImagesRequest listImagesRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    return awsApiHelperService.listEcrImages(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region, listImagesRequest);
  }

  public ListImagesResult listEcrImages(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptedDataDetails, ListImagesRequest listImagesRequest) {
    try (CloseableAmazonWebServiceClient<AmazonECRClient> closeableAmazonECRClient =
             new CloseableAmazonWebServiceClient(getAmazonEcrClient(ecrConfig, encryptedDataDetails))) {
      tracker.trackECRCall("List Images");
      return closeableAmazonECRClient.getClient().listImages(listImagesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listEcrImages", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new ListImagesResult();
  }

  public DescribeRepositoriesResult listRepositories(EcrConfig ecrConfig,
      List<EncryptedDataDetail> encryptedDataDetails, DescribeRepositoriesRequest describeRepositoriesRequest) {
    try (CloseableAmazonWebServiceClient<AmazonECRClient> closeableAmazonECRClient =
             new CloseableAmazonWebServiceClient(getAmazonEcrClient(ecrConfig, encryptedDataDetails))) {
      tracker.trackECRCall("List Repositories");
      return closeableAmazonECRClient.getClient().describeRepositories(describeRepositoriesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listRepositories", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeRepositoriesResult();
  }

  public DescribeRepositoriesResult listRepositories(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      DescribeRepositoriesRequest describeRepositoriesRequest, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    return awsApiHelperService.listRepositories(
        AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), describeRepositoriesRequest, region);
  }

  public List<LoadBalancerDescription> getLoadBalancerDescriptions(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(Regions.fromName(region), awsConfig))) {
      tracker.trackClassicELBCall("Describe Load Balancers");
      return closeableAmazonElasticLoadBalancingClient.getClient()
          .describeLoadBalancers(
              new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest().withPageSize(400))
          .getLoadBalancerDescriptions();
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getLoadBalancerDescriptions", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  public TargetGroup getTargetGroupForAlb(
      String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String targetGroupArn) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClient(Regions.fromName(region), awsConfig))) {
      DescribeTargetGroupsRequest describeTargetGroupsRequest = new DescribeTargetGroupsRequest().withPageSize(5);
      describeTargetGroupsRequest.withTargetGroupArns(targetGroupArn);
      tracker.trackELBCall("Describe Target Groups");
      List<TargetGroup> targetGroupList = closeableAmazonElasticLoadBalancingClient.getClient()
                                              .describeTargetGroups(describeTargetGroupsRequest)
                                              .getTargetGroups();
      if (isNotEmpty(targetGroupList)) {
        if (targetGroupList.get(0).getTargetGroupArn().equalsIgnoreCase(targetGroupArn)) {
          return targetGroupList.get(0);
        }
      }
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getTargetGroupForAlb", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  public AutoScalingGroup getAutoScalingGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName) {
    try {
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
          describeAutoScalingGroups(awsConfig, encryptionDetails, region,
              new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName));
      return describeAutoScalingGroupsResult.getAutoScalingGroups().isEmpty()
          ? null
          : describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  public List<String> listInstanceIdsFromAutoScalingGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName) {
    AutoScalingGroup group = getAutoScalingGroup(awsConfig, encryptionDetails, region, autoScalingGroupName);
    if (group != null) {
      return group.getInstances()
          .stream()
          .map(com.amazonaws.services.autoscaling.model.Instance::getInstanceId)
          .collect(toList());
    } else {
      return emptyList();
    }
  }

  public List<String> listClassicLoadBalancers(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    try {
      List<LoadBalancerDescription> describeLoadBalancers =
          getLoadBalancerDescriptions(region, awsConfig, encryptionDetails);
      return describeLoadBalancers.stream().map(LoadBalancerDescription::getLoadBalancerName).collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  public CreateAutoScalingGroupResult createAutoScalingGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest, LogCallback logCallback) {
    AmazonAutoScalingClient amazonAutoScalingClient = null;
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      amazonAutoScalingClient = closeableAmazonAutoScalingClient.getClient();
      tracker.trackASGCall("Create ASG");
      return amazonAutoScalingClient.createAutoScalingGroup(createAutoScalingGroupRequest);
    } catch (AmazonServiceException amazonServiceException) {
      if (amazonAutoScalingClient != null && logCallback != null) {
        describeAutoScalingGroupActivities(amazonAutoScalingClient,
            createAutoScalingGroupRequest.getAutoScalingGroupName(), new HashSet<>(), logCallback, true);
      }
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createAutoScalingGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new CreateAutoScalingGroupResult();
  }

  public DescribeAutoScalingGroupsResult describeAutoScalingGroups(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      DescribeAutoScalingGroupsRequest autoScalingGroupsRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      tracker.trackASGCall("Describe ASGs");
      return closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(autoScalingGroupsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception describeAutoScalingGroups", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new DescribeAutoScalingGroupsResult();
  }

  public void describeAutoScalingGroupActivities(AmazonAutoScalingClient amazonAutoScalingClient,
      String autoScalingGroupName, Set<String> completedActivities, LogCallback callback, boolean withCause) {
    if (callback == null) {
      log.info("Not describing autoScalingGroupActivities for {} since logCallback is null", completedActivities);
      return;
    }
    try {
      tracker.trackASGCall("Describe Scaling Activities");
      DescribeScalingActivitiesResult activitiesResult = amazonAutoScalingClient.describeScalingActivities(
          new DescribeScalingActivitiesRequest().withAutoScalingGroupName(autoScalingGroupName));
      List<Activity> activities = activitiesResult.getActivities();
      if (activities != null && activities.size() > 0) {
        activities.stream()
            .filter(activity -> !completedActivities.contains(activity.getActivityId()))
            .forEach(activity -> {
              String activityId = activity.getActivityId();
              String details = activity.getDetails();
              Integer progress = activity.getProgress();
              String activityDescription = activity.getDescription();
              String statuscode = activity.getStatusCode();
              String statusMessage = activity.getStatusMessage();
              String logStatement =
                  format("AutoScalingGroup [%s] activity [%s] progress [%d percent] , statuscode [%s]  details [%s]",
                      autoScalingGroupName, activityDescription, progress, statuscode, details);
              if (withCause) {
                String cause = activity.getCause();
                logStatement = format(logStatement + " cause [%s]", cause);
              }

              callback.saveExecutionLog(logStatement);
              if (progress == 100) {
                completedActivities.add(activityId);
              }
            });
      }
    } catch (Exception e) {
      log.warn("Failed to describe autoScalingGroup for [{}]", autoScalingGroupName,
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  public List<Metric> getCloudWatchMetrics(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(getAwsCloudWatchClient(region, awsConfig))) {
      List<Metric> rv = new ArrayList<>();
      String nextToken = null;
      do {
        ListMetricsRequest request = new ListMetricsRequest();
        request.withNextToken(nextToken);
        tracker.trackCloudWatchCall("List Metrics");
        ListMetricsResult listMetricsResult = closeableAmazonCloudWatchClient.getClient().listMetrics(request);
        nextToken = listMetricsResult.getNextToken();
        rv.addAll(listMetricsResult.getMetrics());
      } while (nextToken != null);

      return rv;
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getCloudWatchMetrics", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  public List<Metric> getCloudWatchMetrics(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, ListMetricsRequest listMetricsRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonCloudWatchClient> closeableAmazonCloudWatchClient =
             new CloseableAmazonWebServiceClient(getAwsCloudWatchClient(region, awsConfig))) {
      List<Metric> rv = new ArrayList<>();
      String nextToken = null;
      do {
        listMetricsRequest.withNextToken(nextToken);
        tracker.trackCloudWatchCall("List Metrics");
        ListMetricsResult listMetricsResult =
            closeableAmazonCloudWatchClient.getClient().listMetrics(listMetricsRequest);
        nextToken = listMetricsResult.getNextToken();
        rv.addAll(listMetricsResult.getMetrics());
      } while (nextToken != null);

      return rv;

    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getCloudWatchMetrics", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  public boolean registerInstancesWithLoadBalancer(
      Regions region, String loadBalancerName, String instanceId, AwsConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(region, awsConfig))) {
      tracker.trackClassicELBCall("Register Instances");
      return closeableAmazonElasticLoadBalancingClient.getClient()
          .registerInstancesWithLoadBalancer(
              new RegisterInstancesWithLoadBalancerRequest()
                  .withLoadBalancerName(loadBalancerName)
                  .withInstances(new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId)))
          .getInstances()
          .stream()
          .anyMatch(inst -> inst.getInstanceId().equals(instanceId));
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception registerInstancesWithLoadBalancer", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return false;
  }

  public boolean deregisterInstancesFromLoadBalancer(
      Regions region, String loadBalancerName, String instanceId, AwsConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(region, awsConfig))) {
      tracker.trackClassicELBCall("De register Instances");
      return closeableAmazonElasticLoadBalancingClient.getClient()
          .deregisterInstancesFromLoadBalancer(
              new DeregisterInstancesFromLoadBalancerRequest()
                  .withLoadBalancerName(loadBalancerName)
                  .withInstances(new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId)))
          .getInstances()
          .stream()
          .noneMatch(inst -> inst.getInstanceId().equals(instanceId));
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception deregisterInstancesFromLoadBalancer", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return false;
  }

  public String getBucketRegion(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsConfig))) {
      // You can query the bucket location using any region, it returns the result. So, using the default
      String region = closeableAmazonS3Client.getClient().getBucketLocation(bucketName);
      // Aws returns US if the bucket was created in the default region. Not sure why it doesn't return just the region
      // name in all cases. Also, their documentation says it would return empty string if its in the default region.
      // http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETlocation.html But it returns US. Added additional
      // checks based on other stuff
      if (region == null || region.equals("US")) {
        return AWS_DEFAULT_REGION;
      } else if (region.equals("EU")) {
        return "eu-west-1";
      }
      return region;
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getBucketRegion", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  public static boolean isInAwsRegion(String region) {
    // Regions.getCurrentRegion() internally call Ec2Metadata api.
    // It also does exception handling and returns null in that case.
    // This will work except when delegate is run as fargate task.
    com.amazonaws.regions.Region regionForContainer = Regions.getCurrentRegion();

    if (regionForContainer != null) {
      return regionForContainer.getName().equals(region);
    }

    // When delegate is running as fargate task, rely on ENV variable: AWS_REGION
    String currentRegion = System.getenv("AWS_REGION");
    log.info("ECS Current Region Value from ENV var {AWS_REGION}: " + currentRegion);
    if (isNotBlank(currentRegion)) {
      return currentRegion.equals(region);
    }

    log.info("Failed in ECS validation, failed to fetch current region");
    return false;
  }

  public TagResourceResult tagService(String region, List<EncryptedDataDetail> encryptedDataDetails,
      TagResourceRequest tagResourceRequest, AwsConfig awsConfig) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptedDataDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Tag Resource");
      return closeableAmazonECSClient.getClient().tagResource(tagResourceRequest);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public UntagResourceResult untagService(String region, List<EncryptedDataDetail> encryptedDataDetails,
      UntagResourceRequest untagResourceRequest, AwsConfig awsConfig) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptedDataDetails);
    try (CloseableAmazonWebServiceClient<AmazonECSClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonEcsClient(region, awsConfig))) {
      tracker.trackECSCall("Untag Resource");
      return closeableAmazonECSClient.getClient().untagResource(untagResourceRequest);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  AWSTemporaryCredentialsRestClient getAWSTemporaryCredentialsRestClient(String url, AwsConfig awsConfig) {
    OkHttpClient okHttpClient = Http.getOkHttpClient(url, awsConfig.isCertValidationRequired());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AWSTemporaryCredentialsRestClient.class);
  }

  @VisibleForTesting
  public AWSTemporaryCredentials getCredentialsForIAMROleOnDelegate(String url, AwsConfig awsConfig) {
    AWSTemporaryCredentialsRestClient credentialsRestClient = getAWSTemporaryCredentialsRestClient(url, awsConfig);
    String roleName;
    try {
      Response<ResponseBody> response = credentialsRestClient.getRoleName().execute();
      roleName = response.body().string();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot get the role name", ExceptionMessageSanitizer.sanitizeException(e));
    }

    if (isEmpty(roleName)) {
      throw new InvalidRequestException("No role attached to the instance");
    }
    try {
      Response<AWSTemporaryCredentials> response = credentialsRestClient.getTemporaryCredentials(roleName).execute();
      if (response.isSuccessful()) {
        return response.body();
      } else {
        throw new InvalidRequestException(
            "Cannot get the temporary credentials, api returned error code: " + response.code());
      }

    } catch (IOException e) {
      throw new InvalidRequestException(
          "Cannot get the temporary credentials", ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  public Map<String, String> fetchLabels(
      AwsConfig awsConfig, ArtifactStreamAttributes artifactStreamAttributes, List<String> tags) {
    return awsApiHelperService.fetchLabels(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), null,
        artifactStreamAttributes.getImageName(), artifactStreamAttributes.getRegion(), tags);
  }

  private String getRegion(AwsConfig awsConfig) {
    if (isNotBlank(awsConfig.getDefaultRegion())) {
      return awsConfig.getDefaultRegion();
    } else {
      return AWS_DEFAULT_REGION;
    }
  }
}
