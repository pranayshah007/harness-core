/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_BACKOFF_MAX_ERROR_RETRIES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsSdkClientEqualJitterBackoffStrategy;
import io.harness.aws.AwsSdkClientFullJitterBackoffStrategy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class AwsApiHelperServiceTest extends CategoryTest {
  @Mock AmazonS3Client amazonS3Client;
  @Mock AwsCallTracker tracker;
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks @Spy AwsApiHelperService awsApiHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetArtifactBuildDetailsWithoutVersion() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    String region = "us-east-1";
    String bucketName = "bucket";
    String filePath = "path.jar";

    doReturn(amazonS3Client).when(awsApiHelperService).getAmazonS3Client(any(), anyString());
    when(amazonS3Client.getBucketVersioningConfiguration(eq("bucket")))
        .thenReturn(new BucketVersioningConfiguration("OFF"));

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setHeader("x-amz-version-id", "abcd");
    when(amazonS3Client.getObjectMetadata(eq(bucketName), eq(filePath))).thenReturn(metadata);

    BuildDetails details = awsApiHelperService.getBuild(awsInternalConfig, region, bucketName, filePath);
    assertThat(details).isNotNull();
    assertThat(details.getArtifactPath()).isEqualTo("path.jar");
    assertThat(details.getNumber()).isEqualTo("path.jar");
    assertThat(details.getRevision()).isEqualTo("path.jar");
    assertThat(details.getArtifactPath()).isEqualTo("path.jar");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetArtifactBuildDetailsWithVersion() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    String region = "us-east-1";
    String bucketName = "bucket";
    String filePath = "file.jar";

    doReturn(amazonS3Client).when(awsApiHelperService).getAmazonS3Client(any(), anyString());
    when(amazonS3Client.getBucketVersioningConfiguration(eq("bucket")))
        .thenReturn(new BucketVersioningConfiguration("ENABLED"));

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setHeader("x-amz-version-id", "abcd");
    when(amazonS3Client.getObjectMetadata(eq(bucketName), eq(filePath))).thenReturn(metadata);

    BuildDetails details = awsApiHelperService.getBuild(awsInternalConfig, region, bucketName, filePath);
    assertThat(details).isNotNull();
    assertThat(details.getNumber()).isEqualTo("file.jar:abcd");
    assertThat(details.getRevision()).isEqualTo("file.jar:abcd");
    assertThat(details.getArtifactPath()).isEqualTo("file.jar");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetArtifactBuildDetailsWithVersionSpecified() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    String region = "us-east-1";
    String bucketName = "bucket";
    String filePath = "file.jar:abcd";

    doReturn(amazonS3Client).when(awsApiHelperService).getAmazonS3Client(any(), anyString());
    // enabled
    when(amazonS3Client.getBucketVersioningConfiguration(eq("bucket")))
        .thenReturn(new BucketVersioningConfiguration("ENABLED"));

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setHeader("x-amz-version-id", "abcd");
    when(amazonS3Client.getObjectMetadata(eq(bucketName), eq(filePath))).thenReturn(null);
    when(amazonS3Client.getObjectMetadata(any())).thenReturn(metadata);

    BuildDetails details = awsApiHelperService.getBuild(awsInternalConfig, region, bucketName, filePath);
    assertThat(details).isNotNull();
    assertThat(details.getNumber()).isEqualTo("file.jar:abcd");
    assertThat(details.getRevision()).isEqualTo("file.jar:abcd");
    assertThat(details.getArtifactPath()).isEqualTo("file.jar");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRetryPolicyWithFullJitterBackoff() {
    AwsInternalConfig awsInternalConfig =
        AwsInternalConfig.builder()
            .awsSdkClientBackoffStrategyOverride(
                AwsSdkClientFullJitterBackoffStrategy.builder().baseDelay(1).retryCount(1).maxBackoffTime(30).build())
            .build();

    RetryPolicy expected = awsApiHelperService.getRetryPolicy(awsInternalConfig);

    assertThat(expected.getBackoffStrategy()).isInstanceOf(PredefinedBackoffStrategies.FullJitterBackoffStrategy.class);
    assertThat(expected.getMaxErrorRetry()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRetryPolicyWithEqualJitterBackoff() {
    AwsInternalConfig awsInternalConfig =
        AwsInternalConfig.builder()
            .awsSdkClientBackoffStrategyOverride(
                AwsSdkClientEqualJitterBackoffStrategy.builder().maxBackoffTime(100).baseDelay(1).retryCount(1).build())
            .build();

    RetryPolicy expected = awsApiHelperService.getRetryPolicy(awsInternalConfig);

    assertThat(expected.getBackoffStrategy())
        .isInstanceOf(PredefinedBackoffStrategies.EqualJitterBackoffStrategy.class);
    assertThat(expected.getMaxErrorRetry()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRetryPolicyWithDefaultBackoffStrategy() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    RetryPolicy expected = awsApiHelperService.getRetryPolicy(awsInternalConfig);

    assertThat(expected.getBackoffStrategy()).isInstanceOf(PredefinedBackoffStrategies.SDKDefaultBackoffStrategy.class);
    assertThat(expected.getMaxErrorRetry()).isEqualTo(DEFAULT_BACKOFF_MAX_ERROR_RETRIES);
  }
}
