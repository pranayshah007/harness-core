/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsResponse;
import software.wings.service.impl.aws.model.AwsEc2ValidateCredentialsResponse.AwsEc2ValidateCredentialsResponseBuilder;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplateVersionsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagDescription;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
@OwnedBy(CDP)
public class AwsEc2HelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsEc2HelperServiceDelegate {
  private static final String NAME = "Name";

  @VisibleForTesting
  AmazonEC2Client getAmazonEc2Client(String region, AwsConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonEC2Client) builder.build();
  }

  @VisibleForTesting
  AmazonEC2Client getAmazonEc2Client(AwsConfig awsConfig) {
    AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withRegion(getRegion(awsConfig));
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonEC2Client) builder.build();
  }

  @Override
  public AwsEc2ValidateCredentialsResponse validateAwsAccountCredential(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsConfig))) {
      tracker.trackEC2Call("Get Ec2 client");
      closeableAmazonEC2Client.getClient().describeRegions();
    } catch (AmazonEC2Exception amazonEC2Exception) {
      if (amazonEC2Exception.getStatusCode() == 401) {
        AwsEc2ValidateCredentialsResponseBuilder responseBuilder =
            AwsEc2ValidateCredentialsResponse.builder().valid(false).executionStatus(SUCCESS);
        if (!awsConfig.isUseEc2IamCredentials() && !awsConfig.isUseIRSA()) {
          if (isEmpty(awsConfig.getAccessKey())) {
            responseBuilder.errorMessage("Access Key should not be empty");
          } else if (isEmpty(awsConfig.getSecretKey())) {
            responseBuilder.errorMessage("Secret Key should not be empty");
          }
        }
        return responseBuilder.build();
      }
      handleAmazonServiceException(amazonEC2Exception);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception validateAwsAccountCredential", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return AwsEc2ValidateCredentialsResponse.builder().valid(true).executionStatus(SUCCESS).build();
  }

  @Override
  public List<String> listRegions(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsConfig))) {
      tracker.trackEC2Call("List Ec2 regions");
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

  @Override
  public List<AwsVPC> listVPCs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      tracker.trackEC2Call("List VPCs");
      return closeableAmazonEC2Client.getClient()
          .describeVpcs(new DescribeVpcsRequest().withFilters(new Filter("state").withValues("available")))
          .getVpcs()
          .stream()
          .map(vpc
              -> AwsVPC.builder()
                     .id(vpc.getVpcId())
                     .name(CollectionUtils.emptyIfNull(vpc.getTags())
                               .stream()
                               .filter(tag -> NAME.equals(tag.getKey()))
                               .findFirst()
                               .orElse(new Tag(NAME, ""))
                               .getValue())
                     .build())
          .collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listVPCs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public List<AwsSubnet> listSubnets(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, List<String> vpcIds) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      List<Filter> filters = new ArrayList<>();
      if (isNotEmpty(vpcIds)) {
        filters.add(new Filter("vpc-id", vpcIds));
      }
      filters.add(new Filter("state").withValues("available"));
      tracker.trackEC2Call("List Subnets");
      return closeableAmazonEC2Client.getClient()
          .describeSubnets(new DescribeSubnetsRequest().withFilters(filters))
          .getSubnets()
          .stream()
          .map(subnet
              -> AwsSubnet.builder()
                     .id(subnet.getSubnetId())
                     .name(CollectionUtils.emptyIfNull(subnet.getTags())
                               .stream()
                               .filter(tag -> NAME.equals(tag.getKey()))
                               .findFirst()
                               .orElse(new Tag(NAME, ""))
                               .getValue())
                     .build())
          .collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listSubnets", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public List<AwsSecurityGroup> listSGs(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, List<String> vpcIds) {
    List<AwsSecurityGroup> result = new ArrayList<>();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      String nextToken = null;
      do {
        List<Filter> filters = new ArrayList<>();
        if (isNotEmpty(vpcIds)) {
          filters.add(new Filter("vpc-id", vpcIds));
        }
        tracker.trackEC2Call("List SGs");
        DescribeSecurityGroupsResult describeSecurityGroupsResult =
            closeableAmazonEC2Client.getClient().describeSecurityGroups(
                new DescribeSecurityGroupsRequest().withNextToken(nextToken).withFilters(filters));
        List<SecurityGroup> securityGroups = describeSecurityGroupsResult.getSecurityGroups();
        result.addAll(securityGroups.stream()
                          .map(securityGroup
                              -> AwsSecurityGroup.builder()
                                     .id(securityGroup.getGroupId())
                                     .name(securityGroup.getGroupName())
                                     .build())
                          .collect(toList()));
        nextToken = describeSecurityGroupsResult.getNextToken();
      } while (nextToken != null);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listSGs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return result;
  }

  @Override
  public Set<String> listTags(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String resourceType) {
    String nextToken = null;
    Set<String> tags = new HashSet<>();
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      do {
        tracker.trackEC2Call("List Tags");
        DescribeTagsResult describeTagsResult = closeableAmazonEC2Client.getClient().describeTags(
            new DescribeTagsRequest()
                .withNextToken(nextToken)
                .withFilters(new Filter("resource-type").withValues(resourceType))
                .withMaxResults(1000));
        tags.addAll(describeTagsResult.getTags().stream().map(TagDescription::getKey).collect(toSet()));
        nextToken = describeTagsResult.getNextToken();
      } while (nextToken != null);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listTags", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return tags;
  }

  @Override
  public List<Instance> listEc2Instances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<Filter> filters, boolean isInstanceSync) {
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      List<Instance> result = new ArrayList<>();
      String nextToken = null;
      if (isNotEmpty(filters)) {
        tracker.trackEC2Call("Filters: "
            + filters.stream()
                  .map(f
                      -> String.format("[Name:%s]-[Values:[%s]]", f.getName(),
                          isNotEmpty(f.getValues()) ? String.join(",", f.getValues()) : ""))
                  .collect(Collectors.joining(",")));
      }
      do {
        DescribeInstancesRequest describeInstancesRequest =
            new DescribeInstancesRequest().withNextToken(nextToken).withFilters(filters);
        tracker.trackEC2Call("List Ec2 instances");
        DescribeInstancesResult describeInstancesResult =
            closeableAmazonEC2Client.getClient().describeInstances(describeInstancesRequest);
        result.addAll(getInstanceList(describeInstancesResult));
        nextToken = describeInstancesResult.getNextToken();
      } while (nextToken != null);
      if (isNotEmpty(result)) {
        tracker.trackEC2Call(
            "Found instances: " + result.stream().map(Instance::getInstanceId).collect(Collectors.joining(",")));
      } else {
        tracker.trackEC2Call("Found 0 instances");
      }
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listEc2Instances", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public List<Instance> listEc2Instances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<String> instanceIds, String region, boolean isInstanceSync) {
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      if (instanceIds.isEmpty()) {
        return emptyList();
      }
      List<Instance> result = new ArrayList<>();
      String nextToken = null;
      do {
        DescribeInstancesRequest describeInstancesRequest =
            new DescribeInstancesRequest().withNextToken(nextToken).withInstanceIds(instanceIds);
        tracker.trackEC2Call("List Ec2 instances");
        DescribeInstancesResult describeInstancesResult =
            closeableAmazonEC2Client.getClient().describeInstances(describeInstancesRequest);
        result.addAll(getInstanceList(describeInstancesResult));
        nextToken = describeInstancesResult.getNextToken();
      } while (nextToken != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listEc2Instances", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public Set<String> listBlockDeviceNamesOfAmi(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String amiId) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      if (isEmpty(amiId)) {
        return emptySet();
      }
      DescribeImagesRequest request = new DescribeImagesRequest().withImageIds(amiId);
      tracker.trackEC2Call("List Images");
      DescribeImagesResult result = closeableAmazonEC2Client.getClient().describeImages(request);
      List<Image> images = result.getImages();
      if (isNotEmpty(images)) {
        Optional<Image> optionalImage = images.stream().filter(image -> amiId.equals(image.getImageId())).findFirst();
        if (optionalImage.isPresent()) {
          List<BlockDeviceMapping> blockDeviceMappings = optionalImage.get().getBlockDeviceMappings();
          if (isNotEmpty(blockDeviceMappings)) {
            return blockDeviceMappings.stream().map(BlockDeviceMapping::getDeviceName).collect(toSet());
          }
        }
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listBlockDeviceNamesOfAmi", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptySet();
  }

  @Override
  public LaunchTemplateVersion getLaunchTemplateVersion(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String launchTemplateId, String version) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      tracker.trackEC2Call("Get Launch Template Version");
      final DescribeLaunchTemplateVersionsResult describeLaunchTemplateVersionsResult =
          closeableAmazonEC2Client.getClient().describeLaunchTemplateVersions(
              new DescribeLaunchTemplateVersionsRequest().withLaunchTemplateId(launchTemplateId).withVersions(version));
      if (describeLaunchTemplateVersionsResult != null
          && isNotEmpty(describeLaunchTemplateVersionsResult.getLaunchTemplateVersions())) {
        return describeLaunchTemplateVersionsResult.getLaunchTemplateVersions().get(0);
      }

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getLaunchTemplateVersion", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return null;
  }

  @Override
  public CreateLaunchTemplateVersionResult createLaunchTemplateVersion(
      CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest, AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(region, awsConfig))) {
      tracker.trackEC2Call("Create Launch Template Version");
      return closeableAmazonEC2Client.getClient().createLaunchTemplateVersion(createLaunchTemplateVersionRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createLaunchTemplateVersion", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return null;
  }

  private List<Instance> getInstanceList(DescribeInstancesResult result) {
    List<Instance> instanceList = Lists.newArrayList();
    result.getReservations().forEach(reservation -> instanceList.addAll(reservation.getInstances()));
    return instanceList;
  }
}
