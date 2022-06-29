/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.amazons3;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.AWS_ACCESS_DENIED;
import static io.harness.exception.WingsException.EVERYBODY;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class AmazonS3ServiceImpl implements AmazonS3Service {
  @Inject AwsS3HelperServiceDelegate awsS3HelperServiceDelegate;
  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;
  private static final int MAX_FILES_TO_SHOW_IN_UI = 1000;
  private static final int FETCH_FILE_COUNT_IN_BUCKET = 500;

  @Override
  public Map<String, String> getBuckets(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<String> bucketNames = awsS3HelperServiceDelegate.listBucketNames(awsConfig, encryptionDetails);
    return bucketNames.stream().collect(Collectors.toMap(s -> s, s -> s));
  }

  @Override
  public List<String> getArtifactPaths(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName) {
    ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
    listObjectsV2Request.withBucketName(bucketName).withMaxKeys(FETCH_FILE_COUNT_IN_BUCKET);
    ListObjectsV2Result result;

    List<S3ObjectSummary> objectSummaryListFinal = new ArrayList<>();
    do {
      result = awsS3HelperServiceDelegate.listObjectsInS3(awsConfig, encryptionDetails, listObjectsV2Request);
      List<S3ObjectSummary> objectSummaryList = result.getObjectSummaries();
      if (EmptyPredicate.isNotEmpty(objectSummaryList)) {
        objectSummaryListFinal.addAll(objectSummaryList.stream()
                                          .filter(objectSummary -> !objectSummary.getKey().endsWith("/"))
                                          .collect(Collectors.toList()));
      }

      listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
    } while (result.isTruncated() && objectSummaryListFinal.size() < MAX_FILES_TO_SHOW_IN_UI);

    sortDescending(objectSummaryListFinal);
    return objectSummaryListFinal.stream().map(S3ObjectSummary::getKey).collect(toList());
  }

  private String getPrefix(String artifactPath) {
    int index = artifactPath.indexOf('*');
    String prefix = null;
    if (index != -1) {
      prefix = artifactPath.substring(0, index);
    }
    return prefix;
  }

  @Override
  public List<BuildDetails> getArtifactsBuildDetails(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, List<String> artifactPaths, boolean isExpression) {
    try {
      boolean versioningEnabledForBucket =
          awsS3HelperServiceDelegate.isVersioningEnabledForBucket(awsConfig, encryptionDetails, bucketName);
      List<BuildDetails> buildDetailsList = Lists.newArrayList();
      for (String artifactPath : artifactPaths) {
        List<BuildDetails> buildDetailsListForArtifactPath = getArtifactsBuildDetails(
            awsConfig, encryptionDetails, bucketName, artifactPath, isExpression, versioningEnabledForBucket);
        buildDetailsList.addAll(buildDetailsListForArtifactPath);
      }
      return buildDetailsList;
    } catch (WingsException e) {
      e.excludeReportTarget(AWS_ACCESS_DENIED, EVERYBODY);
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    } catch (RuntimeException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), USER);
    }
  }

  private List<BuildDetails> getArtifactsBuildDetails(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, String artifactPath, boolean isExpression, boolean versioningEnabledForBucket) {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    if (isExpression) {
      ListObjectsV2Request listObjectsV2Request = getListObjectsV2Request(bucketName, artifactPath);
      ListObjectsV2Result result;
      Pattern pattern = Pattern.compile(artifactPath.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

      List<S3ObjectSummary> objectSummaryListFinal = new ArrayList<>();
      do {
        result = awsS3HelperServiceDelegate.listObjectsInS3(awsConfig, encryptionDetails, listObjectsV2Request);
        List<S3ObjectSummary> objectSummaryList = result.getObjectSummaries();
        if (EmptyPredicate.isNotEmpty(objectSummaryList)) {
          objectSummaryListFinal.addAll(objectSummaryList);
        }

        listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
      } while (result.isTruncated());

      sortAscending(objectSummaryListFinal);
      List<BuildDetails> pageBuildDetails =
          getObjectSummaries(pattern, objectSummaryListFinal, awsConfig, encryptionDetails, versioningEnabledForBucket);
      int size = pageBuildDetails.size();
      if (size > FETCH_FILE_COUNT_IN_BUCKET) {
        pageBuildDetails.subList(0, size - FETCH_FILE_COUNT_IN_BUCKET).clear();
      }
      buildDetailsList.addAll(pageBuildDetails);
    } else {
      long size = 0;
      ObjectMetadata objectMetadata =
          awsS3HelperServiceDelegate.getObjectMetadataFromS3(awsConfig, encryptionDetails, bucketName, artifactPath);
      if (objectMetadata != null) {
        size = objectMetadata.getContentLength();
      }

      BuildDetails artifactMetadata = getArtifactBuildDetails(
          awsConfig, encryptionDetails, bucketName, artifactPath, versioningEnabledForBucket, size);
      buildDetailsList.add(artifactMetadata);
    }

    return buildDetailsList;
  }

  private void sortDescending(List<S3ObjectSummary> objectSummaryList) {
    if (EmptyPredicate.isEmpty(objectSummaryList)) {
      return;
    }

    objectSummaryList.sort((o1, o2) -> o2.getLastModified().compareTo(o1.getLastModified()));
  }

  private void sortAscending(List<S3ObjectSummary> objectSummaryList) {
    if (EmptyPredicate.isEmpty(objectSummaryList)) {
      return;
    }

    objectSummaryList.sort((o1, o2) -> o1.getLastModified().compareTo(o2.getLastModified()));
  }

  private List<String> getObjectSummaries(Pattern pattern, List<S3ObjectSummary> objectSummaryList) {
    return objectSummaryList.stream()
        .filter(
            objectSummary -> !objectSummary.getKey().endsWith("/") && pattern.matcher(objectSummary.getKey()).find())
        .map(S3ObjectSummary::getKey)
        .collect(toList());
  }

  private List<BuildDetails> getObjectSummaries(Pattern pattern, List<S3ObjectSummary> objectSummaryList,
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails, boolean versioningEnabled) {
    return objectSummaryList.stream()
        .filter(
            objectSummary -> !objectSummary.getKey().endsWith("/") && pattern.matcher(objectSummary.getKey()).find())
        .map(objectSummary
            -> getArtifactBuildDetails(awsConfig, encryptedDataDetails, objectSummary.getBucketName(),
                objectSummary.getKey(), versioningEnabled, objectSummary.getSize()))
        .collect(toList());
  }

  @Override
  public ListNotifyResponseData downloadArtifacts(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, List<String> artifactPaths, String delegateId, String taskId, String accountId)
      throws IOException {
    ListNotifyResponseData res = new ListNotifyResponseData();

    for (String artifactPath : artifactPaths) {
      downloadArtifactsUsingFilter(
          awsConfig, encryptionDetails, bucketName, artifactPath, res, delegateId, taskId, accountId);
    }
    return res;
  }

  private void downloadArtifactsUsingFilter(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, String artifactpathRegex, ListNotifyResponseData res, String delegateId, String taskId,
      String accountId) throws IOException {
    Pattern pattern = Pattern.compile(artifactpathRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    ListObjectsV2Request listObjectsV2Request = getListObjectsV2Request(bucketName, artifactpathRegex);

    List<String> objectKeyList = Lists.newArrayList();
    ListObjectsV2Result result;
    do {
      result = awsS3HelperServiceDelegate.listObjectsInS3(awsConfig, encryptionDetails, listObjectsV2Request);
      List<S3ObjectSummary> objectSummaryList = result.getObjectSummaries();
      // in descending order. The most recent one comes first
      objectSummaryList.sort((o1, o2) -> o2.getLastModified().compareTo(o1.getLastModified()));

      List<String> objectKeyListForCurrentBatch = getObjectSummaries(pattern, objectSummaryList);
      objectKeyList.addAll(objectKeyListForCurrentBatch);
      listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
    } while (result.isTruncated());

    // We are not using stream here since addDataToResponse throws a bunch of exceptions and we want to throw them back
    // to the caller.
    for (String objectKey : objectKeyList) {
      Pair<String, InputStream> stringInputStreamPair =
          downloadArtifact(awsConfig, encryptionDetails, bucketName, objectKey);
      artifactCollectionCommonTaskHelper.addDataToResponse(
          stringInputStreamPair, artifactpathRegex, res, delegateId, taskId, accountId);
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

  @Override
  public Pair<String, InputStream> downloadArtifact(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key) {
    S3Object object = awsS3HelperServiceDelegate.getObjectFromS3(awsConfig, encryptionDetails, bucketName, key);
    if (object != null) {
      return Pair.of(object.getKey(), object.getObjectContent());
    }
    return null;
  }

  @Override
  public BuildDetails getArtifactBuildDetails(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, String key, boolean versioningEnabledForBucket, Long artifactFileSize) {
    String versionId = null;
    if (versioningEnabledForBucket) {
      ObjectMetadata objectMetadata =
          awsS3HelperServiceDelegate.getObjectMetadataFromS3(awsConfig, encryptionDetails, bucketName, key);
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

  @Override
  public Long getFileSize(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key) {
    ObjectMetadata objectMetadata =
        awsS3HelperServiceDelegate.getObjectMetadataFromS3(awsConfig, encryptionDetails, bucketName, key);
    if (objectMetadata == null) {
      throw new InvalidRequestException(
          String.format("No object metadata found for key %s in bucket %s", key, bucketName),
          ErrorCode.ARTIFACT_SERVER_ERROR, null);
    }
    return objectMetadata.getContentLength();
  }
}
