/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.service.impl.AwsS3SyncServiceImpl;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorResourceClient;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.impl.instance.CloudToHarnessMappingServiceImpl;
import software.wings.settings.SettingValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
@RunWith(MockitoJUnitRunner.class)
public class S3SyncEventWriterTest extends CategoryTest {
  @InjectMocks S3SyncEventWriter s3SyncEventWriter;
  @Mock private AwsS3SyncServiceImpl awsS3SyncService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private NGConnectorHelper ngConnectorHelper;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock JobParameters parameters;
  @Mock private CloudToHarnessMappingServiceImpl cloudToHarnessMappingService;

  private final String TEST_ACCOUNT_ID = "S3_SYNC_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String BILLING_ACCOUNT_ID = "S3_SYNC_BILLING_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String BILLING_BUCKET_PATH = "S3_SYNC_BILLING_BUCKET_PATH_" + this.getClass().getSimpleName();
  private final String BILLING_BUCKET_REGION = "S3_SYNC_BILLING_BUCKET_REGION_ID_" + this.getClass().getSimpleName();
  private final String BILLING_BUCKET_PREFIX = "S3_SYNC_BILLING_BUCKET_PREFIX_" + this.getClass().getSimpleName();
  private final String CUR_REPORT_NAME = "CUR_REPORT_NAME_" + this.getClass().getSimpleName();
  private final String EXTERNAL_ID = "EXTERNAL_ID" + this.getClass().getSimpleName();
  private final String ROLE_ARN = "ROLE_ARN" + this.getClass().getSimpleName();
  private final String MASTER = "MASTER";

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void setS3SyncEventWriterTest() throws IOException {
    AwsCrossAccountAttributes awsCrossAccountAttributes =
        AwsCrossAccountAttributes.builder().externalId(EXTERNAL_ID).crossAccountRoleArn(ROLE_ARN).build();
    AwsS3BucketDetails s3BucketDetails = AwsS3BucketDetails.builder()
                                             .s3Prefix(BILLING_BUCKET_PREFIX)
                                             .s3BucketName(BILLING_BUCKET_PATH)
                                             .region(BILLING_BUCKET_REGION)
                                             .build();
    SettingValue settingValue = CEAwsConfig.builder()
                                    .s3BucketDetails(s3BucketDetails)
                                    .awsCrossAccountAttributes(awsCrossAccountAttributes)
                                    .awsAccountId(BILLING_ACCOUNT_ID)
                                    .awsAccountType(MASTER)
                                    .curReportName(CUR_REPORT_NAME)
                                    .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withAccountId(TEST_ACCOUNT_ID)
                                            .withCategory(SettingAttribute.SettingCategory.CE_CONNECTOR)
                                            .withValue(settingValue)
                                            .build();
    Mockito.doReturn(true).when(awsS3SyncService).syncBuckets(any());
    Mockito.doReturn(Arrays.asList(settingAttribute))
        .when(cloudToHarnessMappingService)
        .listSettingAttributesCreatedInDuration(any(), any(), any());
    Mockito.doReturn(TEST_ACCOUNT_ID).when(parameters).getString(any());

    when(ngConnectorHelper.getNextGenConnectors(any(), any(), any(), any())).thenReturn(Collections.emptyList());

    List<SettingAttribute> settingAttributeList = new ArrayList<>();
    s3SyncEventWriter.write(settingAttributeList);
    Collection<Invocation> invocations = Mockito.mockingDetails(awsS3SyncService).getInvocations();
    int numberOfCalls = invocations.size();
    assertThat(numberOfCalls).isEqualTo(1);
  }
}
