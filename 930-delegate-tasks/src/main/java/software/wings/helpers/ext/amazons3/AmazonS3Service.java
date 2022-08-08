/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.amazons3;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author rktummala on 7/29/17.
 */
@OwnedBy(CDC)
public interface AmazonS3Service {
  /**
   * Get Repositories
   *
   * @return map RepoId and Name
   */
  Map<String, String> getBuckets(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails);

  /**
   * Get artifact paths for a given repo from the given bucket.
   *
   * @param awsConfig  aws cloud provider config
   * @param bucketName s3 bucket name
   * @return
   */
  List<String> getArtifactPaths(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName);

  /**
   * downloads artifacts from s3 based on the given inputs
   *
   * @param awsConfig     aws config
   * @param bucketName    bucket name
   * @param artifactPaths artifact paths
   * @param delegateId    delegate id
   * @param taskId        task id
   * @param accountId     account id
   * @return
   */
  ListNotifyResponseData downloadArtifacts(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, List<String> artifactPaths, String delegateId, String taskId, String accountId)
      throws IOException, URISyntaxException;

  Pair<String, InputStream> downloadArtifact(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key);
  /**
   * Gets the artifact related information
   *
   * @param config     aws cloud provider config
   * @param bucketName bucket name
   * @param key        artifact path / key
   * @param versioningEnabledForBucket versioning enabled
   * @param artifactFileSize artifactFileSize
   * @return
   */
  BuildDetails getArtifactBuildDetails(AwsConfig config, List<EncryptedDataDetail> encryptionDetails, String bucketName,
      String key, boolean versioningEnabledForBucket, Long artifactFileSize);

  List<BuildDetails> getArtifactsBuildDetails(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String bucketName, List<String> artifactPaths, boolean isExpression);

  Long getFileSize(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String bucketName, String key);
}
