/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.S3SyncRecord;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.security.authentication.AwsS3SyncConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AwsS3SyncServiceImplTest extends CategoryTest {
  @InjectMocks @Spy AwsS3SyncServiceImpl awsS3SyncService;
  @Mock BatchMainConfig configuration;

  private S3SyncRecord s3SyncRecord;

  private static final String AWS_ACCESS_KEY = "awsAccessKey";
  private static final String AWS_SECRET_KEY = "awsSecretKey";
  private static final String AWS_REGION = "awsRegion";
  private static final String AWS_S3_BASE_PATH = "baseS3BucketPath";
  private final String CUR_REPORT_NAME = "CUR_REPORT_NAME_" + this.getClass().getSimpleName();
  private final String TEST_ACCOUNT_ID = "S3_SYNC_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String TEST_SETTING_ID = "S3_SYNC_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String BILLING_ACCOUNT_ID = "S3_SYNC_BILLING_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String BILLING_BUCKET_PATH = "S3_SYNC_BILLING_BUCKET_PATH_" + this.getClass().getSimpleName();
  private final String BILLING_BUCKET_REGION = "S3_SYNC_BILLING_BUCKET_REGION_ID_" + this.getClass().getSimpleName();
  private final String EXTERNAL_ID = "EXTERNAL_ID" + this.getClass().getSimpleName();
  private final String ROLE_ARN = "ROLE_ARN" + this.getClass().getSimpleName();

  @Before
  public void setup() {
    AwsS3SyncConfig s3SyncConfig = AwsS3SyncConfig.builder()
                                       .awsS3BucketName(AWS_S3_BASE_PATH)
                                       .awsAccessKey(AWS_ACCESS_KEY)
                                       .awsSecretKey(AWS_SECRET_KEY)
                                       .region(AWS_REGION)
                                       .build();

    when(configuration.getAwsS3SyncConfig()).thenReturn(s3SyncConfig);

    s3SyncRecord = S3SyncRecord.builder()
                       .settingId(TEST_SETTING_ID)
                       .accountId(TEST_ACCOUNT_ID)
                       .billingBucketRegion(BILLING_BUCKET_REGION)
                       .billingBucketPath(BILLING_BUCKET_PATH)
                       .billingAccountId(BILLING_ACCOUNT_ID)
                       .curReportName(CUR_REPORT_NAME)
                       .roleArn(ROLE_ARN)
                       .externalId(EXTERNAL_ID)
                       .build();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testAwsS3SyncServiceImplCG() {
    // This is not applicable in NG now.
    // assertThatExceptionOfType(BatchProcessingException.class)
    //    .isThrownBy(() -> awsS3SyncService.syncBuckets(s3SyncRecord));
  }
}
