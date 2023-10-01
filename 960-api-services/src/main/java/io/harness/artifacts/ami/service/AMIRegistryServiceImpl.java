/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.ami.service;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsApiHelperService;
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
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.codedeploy.model.AmazonCodeDeployException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.ecr.model.AmazonECRException;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class AMIRegistryServiceImpl implements AMIRegistryService {
  private static final String AMI_RESOURCE_FILTER_PREFIX = "ami-";

  @Inject private AwsCallTracker tracker;
  @Inject private AwsApiHelperService awsApiHelperService;

  @Override
  public List<BuildDetails> listBuilds(AwsInternalConfig awsInternalConfig, String region,
      Map<String, List<String>> tags, Map<String, String> filters, String versionRegex) {
    log.info("Retrieving images from Aws region: " + region);

    List<BuildDetails> buildDetails = new ArrayList<>();

    List<Filter> filterList = getFilters(tags, filters);

    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(filterList);

    DescribeImagesResult describeImagesResult = null;

    try {
      describeImagesResult = describeEc2Images(awsInternalConfig, region, describeImagesRequest);
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to list versions for the AMI");
    }

    Collections.sort(
        describeImagesResult.getImages(), Collections.reverseOrder(Comparator.comparing(Image::getCreationDate)));

    int numberOfNewImages = describeImagesResult.getImages().size();

    List<Image> limitedImages = describeImagesResult.getImages()
                                    .stream()
                                    .filter(image -> isNotBlank(image.getName()))
                                    .collect(Collectors.toList());

    limitedImages.forEach(image -> constructBuildDetails(buildDetails, image));

    if (EmptyPredicate.isEmpty(buildDetails)) {
      log.info("No images found matching with the given Region {}, and filters {}", region, filters);

      return new ArrayList<>();
    } else {
      log.info("Images found of size {}", buildDetails.size());
    }

    Pattern pattern = Pattern.compile(versionRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    return buildDetails.stream()
        .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
        .collect(Collectors.toList());
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(AwsInternalConfig awsInternalConfig, String region,
      Map<String, List<String>> tags, Map<String, String> filters, String versionRegex) {
    log.info("Retrieving images from Aws");

    List<BuildDetails> buildDetails = new ArrayList<>();

    List<Filter> filterList = getFilters(tags, filters);

    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(filterList);

    DescribeImagesResult describeImagesResult;

    describeImagesResult = describeEc2Images(awsInternalConfig, region, describeImagesRequest);

    log.info("Sorting on creation time");

    Collections.sort(
        describeImagesResult.getImages(), Collections.reverseOrder(Comparator.comparing(Image::getCreationDate)));

    int numberOfNewImages = describeImagesResult.getImages().size();

    List<Image> limitedImages = describeImagesResult.getImages()
                                    .stream()
                                    .filter(image -> isNotBlank(image.getName()))
                                    .collect(Collectors.toList());

    limitedImages.forEach(image -> constructBuildDetails(buildDetails, image));

    if (EmptyPredicate.isEmpty(buildDetails)) {
      log.info("No images found matching with the given Region {}, and filters {}", region, filters);
      return null;
    } else {
      log.info("Images found of size {}", buildDetails.size());
    }

    Pattern pattern = Pattern.compile(versionRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    List<BuildDetails> builds =
        buildDetails.stream()
            .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
            .collect(Collectors.toList());

    return builds.get(0);
  }

  @Override
  public BuildDetails getBuild(AwsInternalConfig awsInternalConfig, String region, Map<String, List<String>> tags,
      Map<String, String> filters, String version) {
    log.info("Retrieving images from Aws");

    List<BuildDetails> buildDetails = new ArrayList<>();

    List<Filter> filterList = getFilters(tags, filters);

    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(filterList);

    DescribeImagesResult describeImagesResult;

    describeImagesResult = describeEc2Images(awsInternalConfig, region, describeImagesRequest);

    log.info("Sorting on creation time");

    Collections.sort(
        describeImagesResult.getImages(), Collections.reverseOrder(Comparator.comparing(Image::getCreationDate)));

    int numberOfNewImages = describeImagesResult.getImages().size();

    List<Image> limitedImages = describeImagesResult.getImages()
                                    .stream()
                                    .filter(image -> isNotBlank(image.getName()))
                                    .collect(Collectors.toList());

    limitedImages.forEach(image -> constructBuildDetails(buildDetails, image));

    if (EmptyPredicate.isEmpty(buildDetails)) {
      log.info("No images found matching with the given Region {}, and filters {}", region, filters);

      return null;
    } else {
      log.info("Images found of size {}", buildDetails.size());
    }

    for (BuildDetails b : buildDetails) {
      if (b.getNumber().equals(version)) {
        return b;
      }
    }

    throw new HintException("Version " + version + " not found.");
  }

  @Override
  public AMITagsResponse listTags(AwsInternalConfig awsInternalConfig, String region) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsInternalConfig))) {
      String nextToken = null;

      List<String> tags = new ArrayList<>();

      String resourceType = "image";

      do {
        tracker.trackEC2Call("List Tags");

        DescribeTagsResult describeTagsResult = closeableAmazonEC2Client.getClient().describeTags(
            new DescribeTagsRequest()
                .withNextToken(nextToken)
                .withFilters(new Filter("resource-type").withValues(resourceType))
                .withMaxResults(10000));

        tags.addAll(describeTagsResult.getTags().stream().map(TagDescription::getKey).collect(toSet()));

        nextToken = describeTagsResult.getNextToken();

      } while (nextToken != null);

      return AMITagsResponse.builder().tags(tags).commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    } catch (Exception e) {
      log.error("Exception get tag list", e);

      throw new HintException(ExceptionUtils.getMessage(e), e);
    }
  }

  private Map<String, String> convertToMap(DescribeTagsResult result) {
    return CollectionUtils.emptyIfNull(result.getTags())
        .stream()
        .collect(Collectors.toMap(TagDescription::getKey, TagDescription::getValue, (key1, key2) -> key1));
  }

  public DescribeImagesResult describeEc2Images(
      AwsInternalConfig awsInternalConfig, String region, DescribeImagesRequest describeImagesRequest) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsInternalConfig))) {
      tracker.trackEC2Call("Describe Images");

      return closeableAmazonEC2Client.getClient().describeImages(describeImagesRequest);

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);

    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);

    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);

      log.error("Exception desribeEc2Images", sanitizeException);

      throw new HintException(ExceptionUtils.getMessage(sanitizeException));
    }

    return new DescribeImagesResult();
  }

  protected List<Filter> getFilters(Map<String, List<String>> tags, Map<String, String> filterMap) {
    List<Filter> filters = new ArrayList<>();

    filters.add(new Filter("is-public").withValues("false"));

    filters.add(new Filter("state").withValues("available"));

    if (isNotEmpty(tags)) {
      tags.keySet().forEach(key -> filters.add(new Filter("tag:" + key, new ArrayList<>(tags.get(key)))));
    }

    if (isNotEmpty(filterMap)) {
      filterMap.entrySet()
          .stream()
          .filter(entry -> isNotBlank(entry.getKey()))
          .filter(entry -> isNotBlank(entry.getValue()))
          .filter(entry -> entry.getKey().startsWith(AMI_RESOURCE_FILTER_PREFIX))
          .forEach(entry -> filters.add(new Filter(entry.getKey().substring(4)).withValues(entry.getValue())));
    }

    return filters;
  }

  public AmazonEC2Client getAmazonEc2Client(AwsInternalConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(getRegion(awsConfig));

    attachCredentialsAndBackoffPolicy(builder, awsConfig);

    return (AmazonEC2Client) builder.build();
  }

  private String getRegion(AwsInternalConfig awsConfig) {
    if (isNotBlank(awsConfig.getDefaultRegion())) {
      return awsConfig.getDefaultRegion();
    } else {
      return AWS_DEFAULT_REGION;
    }
  }

  public void attachCredentialsAndBackoffPolicy(AwsClientBuilder builder, AwsInternalConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider = getAwsCredentialsProvider(awsConfig);

    builder.withCredentials(credentialsProvider);

    ClientConfiguration clientConfiguration = new ClientConfiguration();

    RetryPolicy retryPolicy = awsApiHelperService.getRetryPolicy(awsConfig);

    clientConfiguration.setRetryPolicy(retryPolicy);

    builder.withClientConfiguration(clientConfiguration);
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

  private void constructBuildDetails(List<BuildDetails> buildDetails, Image image) {
    // filtering out tags with "." in the keys as these lead to an exception when saving to mongo

    Map<String, String> metadata = image.getTags()
                                       .stream()
                                       .filter(tag -> !tag.getKey().contains("."))
                                       .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

    metadata.put("ownerId", image.getOwnerId());

    metadata.put("imageType", image.getImageType());

    buildDetails.add(aBuildDetails()
                         .withNumber(image.getName())
                         .withRevision(image.getImageId())
                         .withUiDisplayName("Image: " + image.getName())
                         .withMetadata(metadata)
                         .build());
  }

  public void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    AmazonServiceException sanitizeException =
        (AmazonServiceException) ExceptionMessageSanitizer.sanitizeException(amazonServiceException);

    log.error("AWS API call exception", sanitizeException);

    if (amazonServiceException instanceof AmazonCodeDeployException) {
      throw new HintException(ErrorCode.AWS_ACCESS_DENIED + sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonEC2Exception) {
      throw new HintException(ErrorCode.AWS_ACCESS_DENIED + sanitizeException.getMessage());
    } else if (amazonServiceException instanceof ClusterNotFoundException) {
      throw new HintException(ErrorCode.AWS_CLUSTER_NOT_FOUND + sanitizeException.getMessage());
    } else if (amazonServiceException instanceof ServiceNotFoundException) {
      throw new HintException(ErrorCode.AWS_SERVICE_NOT_FOUND + sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonAutoScalingException) {
      throw new HintException(sanitizeException.getMessage() + ErrorCode.GENERAL_ERROR);
    } else if (amazonServiceException instanceof AmazonECSException
        || amazonServiceException instanceof AmazonECRException) {
      if (amazonServiceException instanceof ClientException) {
        log.warn(sanitizeException.getErrorMessage(), amazonServiceException);

        throw amazonServiceException;
      }

      throw new HintException(ErrorCode.AWS_ACCESS_DENIED + sanitizeException.getMessage());
    } else if (amazonServiceException instanceof AmazonCloudFormationException) {
      if (sanitizeException.getMessage().contains("No updates are to be performed")) {
        log.error("Nothing to update on stack" + sanitizeException.getMessage());
      } else {
        throw new HintException(sanitizeException.getMessage());
      }

    } else {
      throw new HintException(ErrorCode.AWS_ACCESS_DENIED + sanitizeException.getMessage());
    }
  }

  public void handleAmazonClientException(AmazonClientException amazonClientException) {
    AmazonClientException sanitizeException =
        (AmazonClientException) ExceptionMessageSanitizer.sanitizeException(amazonClientException);

    log.error("AWS API Client call exception", sanitizeException);

    String errorMessage = sanitizeException.getMessage();

    if (isNotEmpty(errorMessage) && errorMessage.contains("/meta-data/iam/security-credentials/")) {
      throw new HintException("The IAM role on the Ec2 delegate does not exist OR does not"
          + " have required permissions.");
    } else {
      throw new HintException(ErrorCode.AWS_ACCESS_DENIED + sanitizeException.getMessage() != null
              ? sanitizeException.getMessage()
              : "Exception Message");
    }
  }
}
