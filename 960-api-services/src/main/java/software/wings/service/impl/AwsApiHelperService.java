/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_BACKOFF_MAX_ERROR_RETRIES;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsCallTracker;
import io.harness.aws.CloseableAmazonWebServiceClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AwsAutoScaleException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;
import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.codedeploy.model.AmazonCodeDeployException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AmazonECRException;
import com.amazonaws.services.ecr.model.BatchGetImageRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.Image;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsApiHelperService {
  @Inject private AwsCallTracker tracker;

  public AmazonECRClient getAmazonEcrClient(AwsInternalConfig awsConfig, String region) {
    AmazonECRClientBuilder builder = AmazonECRClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonECRClient) builder.build();
  }
  public AmazonEC2Client getAmazonEc2Client(AwsInternalConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(getRegion(awsConfig));
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonEC2Client) builder.build();
  }
  public AmazonS3Client getAmazonS3Client(AwsInternalConfig awsConfig, String region) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(region).withForceGlobalBucketAccessEnabled(Boolean.TRUE);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonS3Client) builder.build();
  }

  public AWSSecurityTokenServiceClient getAWSSecurityTokenServiceClient(AwsInternalConfig awsConfig, String region) {
    AWSSecurityTokenServiceClientBuilder builder = AWSSecurityTokenServiceClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AWSSecurityTokenServiceClient) builder.build();
  }

  public List<String> listRegions(AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsConfig))) {
      tracker.trackEC2Call("List Regions");
      return closeableAmazonEC2Client.getClient()
          .describeRegions()
          .getRegions()
          .stream()
          .map(Region::getRegionName)
          .collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listRegions", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }
  public ListImagesResult listEcrImages(
      AwsInternalConfig awsConfig, String region, ListImagesRequest listImagesRequest) {
    return getAmazonEcrClient(awsConfig, region).listImages(listImagesRequest);
  }
  public DescribeRepositoriesResult listRepositories(
      AwsInternalConfig awsConfig, DescribeRepositoriesRequest describeRepositoriesRequest, String region) {
    try {
      tracker.trackECRCall("List Repositories");
      return getAmazonEcrClient(awsConfig, region).describeRepositories(describeRepositoriesRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return new DescribeRepositoriesResult();
  }

  public List<String> listS3Buckets(AwsInternalConfig awsInternalConfig, String region) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsInternalConfig, region))) {
      tracker.trackS3Call("List Buckets");
      List<Bucket> buckets = closeableAmazonS3Client.getClient().listBuckets();
      if (isEmpty(buckets)) {
        return emptyList();
      }
      return buckets.stream().map(Bucket::getName).collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listS3Buckets", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  public S3Object getObjectFromS3(AwsInternalConfig awsInternalConfig, String region, String bucketName, String key) {
    try {
      tracker.trackS3Call("Get Object");
      return getAmazonS3Client(awsInternalConfig, getBucketRegion(awsInternalConfig, bucketName, region))
          .getObject(bucketName, key);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  private String getBucketRegion(AwsInternalConfig awsConfig, String bucketName, String region) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsConfig, region))) {
      // You can query the bucket location using any region, it returns the result. So, using the default
      String bucketRegion = closeableAmazonS3Client.getClient().getBucketLocation(bucketName);
      // Aws returns US if the bucket was created in the default region. Not sure why it doesn't return just the region
      // name in all cases. Also, their documentation says it would return empty string if its in the default region.
      // http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGETlocation.html But it returns US. Added additional
      // checks based on other stuff
      if (bucketRegion == null || bucketRegion.equals("US")) {
        return AWS_DEFAULT_REGION;
      } else if (bucketRegion.equals("EU")) {
        return "eu-west-1";
      }
      return bucketRegion;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception getBucketRegion", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }

  public Map<String, String> fetchLabels(
      AwsInternalConfig awsConfig, String imageName, String region, List<String> tags) {
    AmazonECRClient ecrClient = getAmazonEcrClient(awsConfig, region);
    return tags.stream()
        .map(tag
            -> ecrClient.batchGetImage(
                new BatchGetImageRequest()
                    .withRepositoryName(imageName)
                    .withImageIds(new ImageIdentifier().withImageTag(tag))
                    .withAcceptedMediaTypes("application/vnd.docker.distribution.manifest.v1+json")))
        .flatMap(batchGetImageResult -> batchGetImageResult.getImages().stream())
        .map(Image::getImageManifest)
        .flatMap(imageManifest
            -> ((List<Map<String, Object>>) JsonUtils.asObject(imageManifest, HashMap.class).get("history"))
                   .stream()
                   .flatMap(history
                       -> ((Map<String, Object>) (JsonUtils.asObject(
                               (String) history.get("v1Compatibility"), HashMap.class)))
                              .entrySet()
                              .stream()))
        .filter(entry -> entry.getKey().equals("config"))
        .flatMap(config
            -> ((Map<String, String>) ((Map<String, Object>) config.getValue()).get("Labels")).entrySet().stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public void attachCredentialsAndBackoffPolicy(AwsClientBuilder builder, AwsInternalConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider = getAwsCredentialsProvider(awsConfig);
    builder.withCredentials(credentialsProvider);
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    RetryPolicy retryPolicy = getRetryPolicy(awsConfig);
    clientConfiguration.setRetryPolicy(retryPolicy);
    builder.withClientConfiguration(clientConfiguration);
  }

  @NotNull
  private RetryPolicy getRetryPolicy(AwsInternalConfig awsConfig) {
    AmazonClientSDKDefaultBackoffStrategy defaultBackoffStrategy = awsConfig.getAmazonClientSDKDefaultBackoffStrategy();
    return defaultBackoffStrategy != null
        ? new RetryPolicy(new PredefinedRetryPolicies.SDKDefaultRetryCondition(),
            new PredefinedBackoffStrategies.SDKDefaultBackoffStrategy(defaultBackoffStrategy.getBaseDelayInMs(),
                defaultBackoffStrategy.getThrottledBaseDelayInMs(), defaultBackoffStrategy.getMaxBackoffInMs()),
            defaultBackoffStrategy.getMaxErrorRetry(), false)
        : new RetryPolicy(new PredefinedRetryPolicies.SDKDefaultRetryCondition(),
            new PredefinedBackoffStrategies.SDKDefaultBackoffStrategy(), DEFAULT_BACKOFF_MAX_ERROR_RETRIES, false);
  }

  public AWSCredentialsProvider getAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider;
    if (awsConfig.isUseEc2IamCredentials()) {
      log.info("Instantiating EC2ContainerCredentialsProviderWrapper");
      credentialsProvider = new EC2ContainerCredentialsProviderWrapper();
    } else if (awsConfig.isUseIRSA()) {
      WebIdentityTokenCredentialsProvider.Builder providerBuilder = WebIdentityTokenCredentialsProvider.builder();
      providerBuilder.roleSessionName(awsConfig.getAccountId() + UUIDGenerator.generateUuid());

      credentialsProvider = providerBuilder.build();
    } else {
      credentialsProvider = new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(new String(awsConfig.getAccessKey()), new String(awsConfig.getSecretKey())));
    }
    if (awsConfig.isAssumeCrossAccountRole()) {
      // For the security token service we default to us-east-1.
      AWSSecurityTokenService securityTokenService =
          AWSSecurityTokenServiceClientBuilder.standard()
              .withRegion(isNotBlank(awsConfig.getDefaultRegion()) ? awsConfig.getDefaultRegion() : AWS_DEFAULT_REGION)
              .withCredentials(credentialsProvider)
              .build();
      AwsCrossAccountAttributes crossAccountAttributes = awsConfig.getCrossAccountAttributes();
      credentialsProvider = new STSAssumeRoleSessionCredentialsProvider
                                .Builder(crossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
                                .withStsClient(securityTokenService)
                                .withExternalId(crossAccountAttributes.getExternalId())
                                .build();
    }
    return credentialsProvider;
  }

  public void handleAmazonClientException(AmazonClientException amazonClientException) {
    log.error("AWS API Client call exception", amazonClientException);
    String errorMessage = amazonClientException.getMessage();
    if (isNotEmpty(errorMessage) && errorMessage.contains("/meta-data/iam/security-credentials/")) {
      throw new InvalidRequestException("The IAM role on the Ec2 delegate does not exist OR does not"
              + " have required permissions.",
          amazonClientException, USER);
    } else {
      log.error("Unhandled aws exception");
      throw new InvalidRequestException(
          amazonClientException.getMessage() != null ? amazonClientException.getMessage() : "Exception Message",
          ErrorCode.AWS_ACCESS_DENIED, USER);
    }
  }

  public void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    log.error("AWS API call exception", amazonServiceException);
    if (amazonServiceException instanceof AmazonCodeDeployException) {
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", amazonServiceException.getMessage());
    } else if (amazonServiceException instanceof AmazonEC2Exception) {
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", amazonServiceException.getMessage());
    } else if (amazonServiceException instanceof ClusterNotFoundException) {
      throw new WingsException(ErrorCode.AWS_CLUSTER_NOT_FOUND)
          .addParam("message", amazonServiceException.getMessage());
    } else if (amazonServiceException instanceof ServiceNotFoundException) {
      throw new WingsException(ErrorCode.AWS_SERVICE_NOT_FOUND)
          .addParam("message", amazonServiceException.getMessage());
    } else if (amazonServiceException instanceof AmazonAutoScalingException) {
      throw new AwsAutoScaleException(amazonServiceException.getMessage(), ErrorCode.GENERAL_ERROR, USER);
    } else if (amazonServiceException instanceof AmazonECSException
        || amazonServiceException instanceof AmazonECRException) {
      if (amazonServiceException instanceof ClientException) {
        log.warn(amazonServiceException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", amazonServiceException.getMessage());
    } else if (amazonServiceException instanceof AmazonCloudFormationException) {
      if (amazonServiceException.getMessage().contains("No updates are to be performed")) {
        log.error("Nothing to update on stack" + amazonServiceException.getMessage());
      } else {
        throw new InvalidRequestException(amazonServiceException.getMessage(), amazonServiceException);
      }
    } else {
      log.error("Unhandled aws exception");
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", amazonServiceException.getMessage());
    }
  }

  private String getRegion(AwsInternalConfig awsConfig) {
    if (isNotBlank(awsConfig.getDefaultRegion())) {
      return awsConfig.getDefaultRegion();
    } else {
      return AWS_DEFAULT_REGION;
    }
  }
}
