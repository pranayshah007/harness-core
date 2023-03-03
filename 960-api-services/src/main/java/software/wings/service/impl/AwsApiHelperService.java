/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.AWS_ACCESS_DENIED;
import static io.harness.exception.WingsException.EVERYBODY;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_BACKOFF_MAX_ERROR_RETRIES;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.streaming.dtos.AuditBatchDTO;
import io.harness.audit.streaming.dtos.PutObjectResultResponse;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AwsAutoScaleException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

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
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
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
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsApiHelperService {
  private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  @Inject private AwsCallTracker tracker;
  @Inject private KryoSerializer kryoSerializer;

  private static final int FETCH_FILE_COUNT_IN_BUCKET = 500;

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
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listRegions", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }
  public ListImagesResult listEcrImages(
      AwsInternalConfig awsConfig, String region, ListImagesRequest listImagesRequest) {
    return getAmazonEcrClient(awsConfig, region).listImages(listImagesRequest);
  }

  public DescribeImagesResult describeEcrImages(
      AwsInternalConfig awsConfig, String region, DescribeImagesRequest describeImagesRequest) {
    return getAmazonEcrClient(awsConfig, region).describeImages(describeImagesRequest);
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

  public ListObjectsV2Result listObjectsInS3(
      AwsInternalConfig awsConfig, String region, ListObjectsV2Request listObjectsV2Request) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsConfig, region))) {
      tracker.trackS3Call("List Objects In S3");
      return closeableAmazonS3Client.getClient().listObjectsV2(listObjectsV2Request);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listObjectsInS3", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new ListObjectsV2Result();
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
      if (amazonServiceException.getStatusCode() == 403) {
        throw new InvalidRequestException("Please provide the correct region corresponding to the AWS access key.");
      }

      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listS3Buckets", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  public PutObjectResultResponse putAuditBatchToBucket(
      AwsInternalConfig awsInternalConfig, String region, String bucketName, AuditBatchDTO auditBatch) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsInternalConfig, region))) {
      tracker.trackS3Call("Put Audit Batch to S3 Bucket");
      String key = getKey(auditBatch);
      String messageJson = JsonUtils.asJson(auditBatch.getOutgoingAuditMessages());
      return convertToPutObjectResultResponse(
          closeableAmazonS3Client.getClient().putObject(bucketName, key, messageJson));
    } catch (AmazonServiceException amazonServiceException) {
      if (amazonServiceException.getStatusCode() == 403) {
        throw new InvalidRequestException(
            String.format("Unable to write to S3 bucket [%s]. Please check the credentials.", bucketName));
      }

      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception while writing to S3 bucket", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return PutObjectResultResponse.builder().build();
  }

  private PutObjectResultResponse convertToPutObjectResultResponse(PutObjectResult putObjectResult) {
    return PutObjectResultResponse.builder()
        .versionId(putObjectResult.getVersionId())
        .eTag(putObjectResult.getETag())
        .expirationTime(putObjectResult.getExpirationTime())
        .expirationTimeRuleId(putObjectResult.getExpirationTimeRuleId())
        .contentMd5(putObjectResult.getContentMd5())
        .isRequesterCharged(putObjectResult.isRequesterCharged())
        .build();
  }

  private String getKey(AuditBatchDTO auditBatchDTO) {
    List<OutgoingAuditMessage> outgoingMessages = auditBatchDTO.getOutgoingAuditMessages();
    Long startTime = outgoingMessages.get(0).getAuditEventTime().toEpochMilli();
    Long endTime = outgoingMessages.get(outgoingMessages.size() - 1).getAuditEventTime().toEpochMilli();
    return String.format("%s_%s_%s", startTime, endTime, Instant.now().toEpochMilli());
  }

  public List<BuildDetails> listBuilds(
      AwsInternalConfig awsInternalConfig, String region, String bucketName, String filePathRegex) {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();

    boolean isExpression = filePathRegex.contains("*") || filePathRegex.endsWith("/");

    if (isExpression == false) {
      return null;
    }

    try {
      boolean versioningEnabledForBucket = isVersioningEnabledForBucket(awsInternalConfig, bucketName, region);

      ListObjectsV2Request listObjectsV2Request = getListObjectsV2Request(bucketName, filePathRegex);
      ListObjectsV2Result result;

      Pattern pattern = Pattern.compile(filePathRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

      List<S3ObjectSummary> objectSummaryListFinal = new ArrayList<>();

      do {
        result = listBuildsInS3(awsInternalConfig, listObjectsV2Request, region);

        List<S3ObjectSummary> objectSummaryList = result.getObjectSummaries();

        if (EmptyPredicate.isNotEmpty(objectSummaryList)) {
          objectSummaryListFinal.addAll(objectSummaryList);
        }

        listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());

      } while (result.isTruncated());

      sortDescending(objectSummaryListFinal);

      List<BuildDetails> pageBuildDetails =
          getObjectSummariesNG(pattern, objectSummaryListFinal, awsInternalConfig, versioningEnabledForBucket, region);

      int size = pageBuildDetails.size();

      if (size > FETCH_FILE_COUNT_IN_BUCKET) {
        pageBuildDetails.subList(0, size - FETCH_FILE_COUNT_IN_BUCKET).clear();
      }

      buildDetailsList.addAll(pageBuildDetails);

      return buildDetailsList;

    } catch (WingsException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    } catch (RuntimeException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
  }

  public BuildDetails getBuild(AwsInternalConfig awsInternalConfig, String region, String bucketName, String filePath) {
    BuildDetails buildDetails;

    try {
      boolean versioningEnabledForBucket = isVersioningEnabledForBucket(awsInternalConfig, bucketName, region);

      buildDetails =
          getArtifactBuildDetails(awsInternalConfig, bucketName, filePath, versioningEnabledForBucket, 1, region);

    } catch (WingsException e) {
      e.excludeReportTarget(AWS_ACCESS_DENIED, EVERYBODY);
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    } catch (RuntimeException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }

    return buildDetails;
  }

  private boolean isVersioningEnabledForBucket(AwsInternalConfig awsInternalConfig, String bucketName, String region) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsInternalConfig, region))) {
      tracker.trackS3Call("Get Bucket Versioning Configuration");

      BucketVersioningConfiguration bucketVersioningConfiguration =
          closeableAmazonS3Client.getClient().getBucketVersioningConfiguration(bucketName);

      return "ENABLED".equals(bucketVersioningConfiguration.getStatus());

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception isVersioningEnabledForBucket", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return false;
  }

  private List<BuildDetails> getObjectSummariesNG(Pattern pattern, List<S3ObjectSummary> objectSummaryList,
      AwsInternalConfig awsInternalConfig, boolean versioningEnabledForBucket, String region) {
    return objectSummaryList.stream()
        .filter(
            objectSummary -> !objectSummary.getKey().endsWith("/") && pattern.matcher(objectSummary.getKey()).find())
        .map(objectSummary
            -> getArtifactBuildDetails(awsInternalConfig, objectSummary.getBucketName(), objectSummary.getKey(),
                versioningEnabledForBucket, objectSummary.getSize(), region))
        .collect(toList());
  }

  private BuildDetails getArtifactBuildDetails(AwsInternalConfig awsInternalConfig, String bucketName, String key,
      boolean versioningEnabledForBucket, long artifactFileSize, String region) {
    String versionId = null;

    if (versioningEnabledForBucket) {
      ObjectMetadata objectMetadata = getObjectMetadataFromS3(awsInternalConfig, bucketName, key, region);
      if (objectMetadata != null) {
        versionId = key + ":" + objectMetadata.getVersionId();
      }
    }

    if (versionId == null) {
      versionId = key;
    }

    Map<String, String> map = new HashMap<>();

    map.put(ArtifactMetadataKeys.url, "https://s3.amazonaws.com/" + bucketName + "/" + key);
    map.put(ArtifactMetadataKeys.buildNo, versionId);
    map.put(ArtifactMetadataKeys.bucketName, bucketName);
    map.put(ArtifactMetadataKeys.artifactPath, key);
    map.put(ArtifactMetadataKeys.key, key);
    map.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(artifactFileSize));

    return aBuildDetails()
        .withNumber(versionId)
        .withRevision(versionId)
        .withArtifactPath(key)
        .withArtifactFileSize(String.valueOf(artifactFileSize))
        .withBuildParameters(map)
        .withUiDisplayName("Build# " + versionId)
        .build();
  }

  private ObjectMetadata getObjectMetadataFromS3(
      AwsInternalConfig awsInternalConfig, String bucketName, String key, String region) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsInternalConfig, region))) {
      tracker.trackS3Call("Get Object Metadata");

      return closeableAmazonS3Client.getClient().getObjectMetadata(bucketName, key);

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception getObjectMetadataFromS3", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return null;
  }

  private ListObjectsV2Result listBuildsInS3(
      AwsInternalConfig awsInternalConfig, ListObjectsV2Request listObjectsV2Request, String region) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsInternalConfig, region))) {
      tracker.trackS3Call("Get Builds");

      return closeableAmazonS3Client.getClient().listObjectsV2(listObjectsV2Request);

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception list builds", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }

    return new ListObjectsV2Result();
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
        .filter(imageManifest -> (JsonUtils.asObject(imageManifest, HashMap.class).get("history")) != null)
        .flatMap(imageManifest
            -> ((List<Map<String, Object>>) JsonUtils.asObject(imageManifest, HashMap.class).get("history"))
                   .stream()
                   .flatMap(history
                       -> ((Map<String, Object>) (JsonUtils.asObject(
                               (String) history.get("v1Compatibility"), HashMap.class)))
                              .entrySet()
                              .stream()))
        .filter(
            entry -> entry.getKey().equals("config") && ((Map<String, Object>) entry.getValue()).get("Labels") != null)
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
      log.debug("Instantiating EC2ContainerCredentialsProviderWrapper");
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
    AmazonClientException sanitizeException =
        (AmazonClientException) ExceptionMessageSanitizer.sanitizeException(amazonClientException);
    log.error("AWS API Client call exception", sanitizeException);
    String errorMessage = sanitizeException.getMessage();
    if (isNotEmpty(errorMessage) && errorMessage.contains("/meta-data/iam/security-credentials/")) {
      throw new InvalidRequestException("The IAM role on the Ec2 delegate does not exist OR does not"
              + " have required permissions.",
          sanitizeException, USER);
    } else {
      log.error("Unhandled aws exception");
      throw new InvalidRequestException(
          sanitizeException.getMessage() != null ? sanitizeException.getMessage() : "Exception Message",
          ErrorCode.AWS_ACCESS_DENIED, USER);
    }
  }

  public void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    AmazonServiceException sanitizeException =
        (AmazonServiceException) ExceptionMessageSanitizer.sanitizeException(amazonServiceException);
    log.error("AWS API call exception", sanitizeException);
    if (amazonServiceException instanceof AmazonCodeDeployException) {
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonEC2Exception) {
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", sanitizeException.getMessage());
    } else if (amazonServiceException instanceof ClusterNotFoundException) {
      throw new WingsException(ErrorCode.AWS_CLUSTER_NOT_FOUND).addParam("message", sanitizeException.getMessage());
    } else if (amazonServiceException instanceof ServiceNotFoundException) {
      throw new WingsException(ErrorCode.AWS_SERVICE_NOT_FOUND).addParam("message", sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonAutoScalingException) {
      throw new AwsAutoScaleException(sanitizeException.getMessage(), ErrorCode.GENERAL_ERROR, USER);
    } else if (amazonServiceException instanceof AmazonECSException
        || amazonServiceException instanceof AmazonECRException) {
      if (amazonServiceException instanceof ClientException) {
        log.warn(sanitizeException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonCloudFormationException) {
      if (sanitizeException.getMessage().contains("No updates are to be performed")) {
        log.error("Nothing to update on stack" + sanitizeException.getMessage());
      } else {
        throw new InvalidRequestException(sanitizeException.getMessage(), sanitizeException);
      }
    } else {
      log.error("Unhandled aws exception");
      throw new WingsException(ErrorCode.AWS_ACCESS_DENIED).addParam("message", sanitizeException.getMessage());
    }
  }

  private String getRegion(AwsInternalConfig awsConfig) {
    if (isNotBlank(awsConfig.getDefaultRegion())) {
      return awsConfig.getDefaultRegion();
    } else {
      return AWS_DEFAULT_REGION;
    }
  }

  private ListObjectsV2Request getListObjectsV2Request(String bucketName, String artifactpathRegex) {
    ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();

    String prefix = getPrefix(artifactpathRegex);

    if (prefix != null) {
      listObjectsV2Request.withPrefix(prefix);
    }

    listObjectsV2Request.withBucketName(bucketName).withMaxKeys(FETCH_FILE_COUNT_IN_BUCKET);

    return listObjectsV2Request;
  }

  private static String getPrefix(String artifactPath) {
    int index = artifactPath.indexOf('*');

    String prefix = null;

    if (index != -1) {
      prefix = artifactPath.substring(0, index);
    }

    return prefix;
  }

  private static void sortDescending(List<S3ObjectSummary> objectSummaryList) {
    if (EmptyPredicate.isEmpty(objectSummaryList)) {
      return;
    }

    objectSummaryList.sort((o1, o2) -> o2.getLastModified().compareTo(o1.getLastModified()));
  }

  private static void sortAscending(List<S3ObjectSummary> objectSummaryList) {
    if (EmptyPredicate.isEmpty(objectSummaryList)) {
      return;
    }

    objectSummaryList.sort((o1, o2) -> o1.getLastModified().compareTo(o2.getLastModified()));
  }
}
