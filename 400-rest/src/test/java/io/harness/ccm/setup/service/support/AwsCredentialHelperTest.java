/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.setup.config.CESetUpConfig;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsCredentialHelperTest extends CategoryTest {
  @Mock MainConfiguration configuration;
  @InjectMocks AwsCredentialHelper awsCredentialHelper;

  private final String AWS_SECRET_KEY = "awsSecretKey";
  private final String AWS_ACCESS_KEY = "awsAccessKey";
  private final String AWS_BUCKET = "awsBucketName";

  @Before
  public void setup() {
    when(configuration.getCeSetUpConfig())
        .thenReturn(CESetUpConfig.builder()
                        .awsAccessKey(AWS_ACCESS_KEY)
                        .awsSecretKey(AWS_SECRET_KEY)
                        .awsS3BucketName(AWS_BUCKET)
                        .build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void constructAWSSecurityTokenServiceTest() {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    assertThat(awsSecurityTokenService).isNotNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void constructBasicAwsCredentialsTest() {
    AWSCredentialsProvider awsCredentialsProvider = awsCredentialHelper.constructBasicAwsCredentials();
    assertThat(awsCredentialsProvider).isNotNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getAWSS3BucketTest() {
    String s3Bucket = awsCredentialHelper.getAWSS3Bucket();
    assertThat(s3Bucket).isEqualTo(AWS_BUCKET);
  }
}
