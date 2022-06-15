/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsS3HelperServiceDelegateImpl extends AwsHelperServiceDelegateBase implements AwsS3HelperServiceDelegate {
  @Inject private AwsApiHelperService awsApiHelperService;
  @VisibleForTesting
  AmazonS3Client getAmazonS3Client(AwsConfig awsConfig) {
    // S3 does not have region selection
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(getRegion(awsConfig)).withForceGlobalBucketAccessEnabled(true);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonS3Client) builder.build();
  }

  @Override
  public List<String> listBucketNames(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(awsConfig))) {
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
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listBucketNames", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public ListObjectsV2Result listObjectsInS3(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, ListObjectsV2Request listObjectsV2Request) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(
                 getBucketRegion(awsConfig, encryptionDetails, listObjectsV2Request.getBucketName()), awsConfig))) {
      tracker.trackS3Call("Get Bucket Region");
      return closeableAmazonS3Client.getClient().listObjectsV2(listObjectsV2Request);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listObjectsInS3", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new ListObjectsV2Result();
  }

  public boolean isVersioningEnabledForBucket(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client = new CloseableAmazonWebServiceClient(
             getAmazonS3Client(getBucketRegion(awsConfig, encryptionDetails, bucketName), awsConfig))) {
      tracker.trackS3Call("Get Bucket Versioning Configuration");
      BucketVersioningConfiguration bucketVersioningConfiguration =
          closeableAmazonS3Client.getClient().getBucketVersioningConfiguration(bucketName);
      return "ENABLED".equals(bucketVersioningConfiguration.getStatus());
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception isVersioningEnabledForBucket", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return false;
  }

  public ObjectMetadata getObjectMetadataFromS3(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client = new CloseableAmazonWebServiceClient(
             getAmazonS3Client(getBucketRegion(awsConfig, encryptionDetails, bucketName), awsConfig))) {
      tracker.trackS3Call("Get Object Metadata");
      return closeableAmazonS3Client.getClient().getObjectMetadata(bucketName, key);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getObjectMetadataFromS3", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  public S3Object getObjectFromS3(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, false);
      tracker.trackS3Call("Get Object");
      return getAmazonS3Client(getBucketRegion(awsConfig, encryptionDetails, bucketName), awsConfig)
          .getObject(bucketName, key);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  private AmazonS3Client getAmazonS3Client(String region, AwsConfig awsConfig) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(region).withForceGlobalBucketAccessEnabled(true);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(
        builder, AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig));
    return (AmazonS3Client) builder.build();
  }

  public String getBucketRegion(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
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
}
