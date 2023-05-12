/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.amazons3;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.RAMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 09/30/17
 */
public class AmazonS3ServiceTest extends WingsBaseTest {
  @Mock AwsHelperService awsHelperService;
  @Mock AwsS3HelperServiceDelegate awsS3HelperServiceDelegate;
  //  @Mock AwsS3HelperServiceDelegate mockAwsS3HelperServiceDelegate;
  @Inject private AmazonS3Service amazonS3Service;
  @Inject @InjectMocks private DelegateFileManager delegateFileManager;

  private static final AwsConfig awsConfig = AwsConfig.builder()
                                                 .accessKey("access".toCharArray())
                                                 .secretKey("secret".toCharArray())
                                                 .accountId("accountId")
                                                 .build();

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(amazonS3Service, "awsS3HelperServiceDelegate", awsS3HelperServiceDelegate, true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetBuckets() {
    when(awsS3HelperServiceDelegate.listBucketNames(awsConfig, null)).thenReturn(Lists.newArrayList("bucket1"));
    Map<String, String> buckets = amazonS3Service.getBuckets(awsConfig, null);
    assertThat(buckets).hasSize(1).containsKeys("bucket1");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() {
    ListObjectsV2Result listObjectsV2Result = new ListObjectsV2Result();
    listObjectsV2Result.setBucketName("bucket1");
    S3ObjectSummary objectSummary = new S3ObjectSummary();
    objectSummary.setKey("key1");
    objectSummary.setBucketName("bucket1");
    objectSummary.setLastModified(new Date());
    listObjectsV2Result.getObjectSummaries().add(objectSummary);
    when(awsS3HelperServiceDelegate.listObjectsInS3(any(AwsConfig.class), any(), any()))
        .thenReturn(listObjectsV2Result);
    List<String> artifactPaths = amazonS3Service.getArtifactPaths(awsConfig, null, "bucket1");
    assertThat(artifactPaths).hasSize(1).contains("key1");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldDownloadArtifacts() throws IOException, URISyntaxException {
    File file = new File("test.txt");
    file.createNewFile();

    try {
      ListObjectsV2Result listObjectsV2Result = new ListObjectsV2Result();
      listObjectsV2Result.setBucketName("bucket1");
      S3ObjectSummary objectSummary = new S3ObjectSummary();
      objectSummary.setKey("key1");
      objectSummary.setBucketName("bucket1");
      objectSummary.setLastModified(new Date());
      listObjectsV2Result.getObjectSummaries().add(objectSummary);
      when(awsS3HelperServiceDelegate.listObjectsInS3(any(AwsConfig.class), any(), any()))
          .thenReturn(listObjectsV2Result);

      ObjectMetadata objectMetadata = new ObjectMetadata();
      objectMetadata.setLastModified(new Date());

      try (S3Object s3Object = new S3Object()) {
        s3Object.setBucketName("bucket1");
        s3Object.setKey("key1");
        s3Object.setObjectMetadata(objectMetadata);

        DelegateFile delegateFile = new DelegateFile();
        delegateFile.setFileId(UUID.randomUUID().toString());

        s3Object.setObjectContent(new FileInputStream(file));
        when(awsS3HelperServiceDelegate.getObjectFromS3(any(AwsConfig.class), any(), any(), any()))
            .thenReturn(s3Object);
        when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);
      }

      ListNotifyResponseData listNotifyResponseData =
          amazonS3Service.downloadArtifacts(awsConfig, null, "bucket1", Lists.newArrayList("key1"), null, null, null);
      List<ArtifactFile> artifactFileList =
          listNotifyResponseData.getData().stream().map(ArtifactFile::fromDTO).collect(Collectors.toList());
      ArtifactFile artifactFile = new ArtifactFile();
      artifactFile.setName("key1");
      assertThat(artifactFileList).hasSize(1);
      assertThat(artifactFileList.get(0).getName()).isEqualTo("key1");
    } finally {
      file.delete();
    }
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetArtifactBuildDetails() {
    ListObjectsV2Result listObjectsV2Result = new ListObjectsV2Result();
    listObjectsV2Result.setBucketName("bucket1");
    S3ObjectSummary objectSummary = new S3ObjectSummary();
    objectSummary.setKey("key1");
    objectSummary.setBucketName("bucket1");
    objectSummary.setLastModified(new Date());
    objectSummary.setSize(4856L);
    listObjectsV2Result.getObjectSummaries().add(objectSummary);
    when(awsS3HelperServiceDelegate.listObjectsInS3(any(AwsConfig.class), any(), any()))
        .thenReturn(listObjectsV2Result);

    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setLastModified(new Date());

    when(awsS3HelperServiceDelegate.getObjectMetadataFromS3(any(AwsConfig.class), any(), any(), any()))
        .thenReturn(objectMetadata);

    // Test without versioning enabled
    BuildDetails artifactBuildDetails =
        amazonS3Service.getArtifactBuildDetails(awsConfig, null, "bucket1", "key1", false, 4856L);
    assertThat(artifactBuildDetails.getArtifactPath()).isEqualTo("key1");
    assertThat(artifactBuildDetails.getRevision()).isEqualTo("key1");
    assertThat(artifactBuildDetails.getNumber()).isEqualTo("key1");

    // Test with versioning enabled
    objectMetadata.setHeader("x-amz-version-id", "v1");
    artifactBuildDetails = amazonS3Service.getArtifactBuildDetails(awsConfig, null, "bucket1", "key1", true, 4856L);
    assertThat(artifactBuildDetails.getArtifactPath()).isEqualTo("key1");
    assertThat(artifactBuildDetails.getRevision()).isEqualTo("key1:v1");
    assertThat(artifactBuildDetails.getNumber()).isEqualTo("key1:v1");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetArtifactsBuildDetails() {
    ListObjectsV2Result listObjectsV2Result = new ListObjectsV2Result();
    listObjectsV2Result.setBucketName("bucket1");
    S3ObjectSummary objectSummary = new S3ObjectSummary();
    objectSummary.setKey("key1");
    objectSummary.setBucketName("bucket1");
    objectSummary.setLastModified(new Date());
    listObjectsV2Result.getObjectSummaries().add(objectSummary);
    when(awsS3HelperServiceDelegate.listObjectsInS3(any(AwsConfig.class), any(), any()))
        .thenReturn(listObjectsV2Result);

    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setLastModified(new Date());

    when(awsS3HelperServiceDelegate.getObjectMetadataFromS3(any(AwsConfig.class), any(), any(), any()))
        .thenReturn(objectMetadata);

    List<BuildDetails> artifactsBuildDetails =
        amazonS3Service.getArtifactsBuildDetails(awsConfig, null, "bucket1", Lists.newArrayList("key1"), false);
    assertThat(artifactsBuildDetails).hasSize(1);
    assertThat(artifactsBuildDetails.get(0).getArtifactPath()).isEqualTo("key1");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetArtifactsBuildDetailsForExpression() {
    ListObjectsV2Result listObjectsV2Result = new ListObjectsV2Result();
    listObjectsV2Result.setBucketName("bucket1");
    S3ObjectSummary objectSummary = new S3ObjectSummary();
    objectSummary.setKey("key1");
    objectSummary.setBucketName("bucket1");
    objectSummary.setLastModified(new Date());
    listObjectsV2Result.getObjectSummaries().add(objectSummary);
    when(awsS3HelperServiceDelegate.listObjectsInS3(any(AwsConfig.class), any(), any()))
        .thenReturn(listObjectsV2Result);

    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setLastModified(new Date());

    when(awsS3HelperServiceDelegate.getObjectMetadataFromS3(any(AwsConfig.class), any(), any(), any()))
        .thenReturn(objectMetadata);

    List<BuildDetails> artifactsBuildDetails =
        amazonS3Service.getArtifactsBuildDetails(awsConfig, null, "bucket1", Lists.newArrayList("key1*"), true);
    assertThat(artifactsBuildDetails).hasSize(1);
    assertThat(artifactsBuildDetails.get(0).getArtifactPath()).isEqualTo("key1");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetFileSize() {
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(100);
    when(awsS3HelperServiceDelegate.getObjectMetadataFromS3(awsConfig, null, "bucket1", "key")).thenReturn(metadata);
    assertThat(amazonS3Service.getFileSize(awsConfig, null, "bucket1", "key")).isEqualTo(100);

    when(awsS3HelperServiceDelegate.getObjectMetadataFromS3(eq(awsConfig), eq(null), any(), any())).thenReturn(null);
    assertThatThrownBy(() -> amazonS3Service.getFileSize(awsConfig, null, "doesNotExist", "doesNotExist"))
        .isInstanceOf(InvalidRequestException.class)
        .extracting("message")
        .isEqualTo("No object metadata found for key doesNotExist in bucket doesNotExist");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForGetArtifactsBuildDetails() {
    when(awsS3HelperServiceDelegate.isVersioningEnabledForBucket(awsConfig, null, "bucket1"))
        .thenThrow(new AmazonS3Exception("Bucket does not exist"));
    assertThatThrownBy(
        () -> amazonS3Service.getArtifactsBuildDetails(awsConfig, null, "bucket1", new ArrayList<>(), true))
        .isInstanceOf(InvalidArtifactServerException.class);
  }
}
