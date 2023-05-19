/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.setup.service.support.AwsCredentialHelper;
import io.harness.ccm.setup.service.support.impl.pojo.BucketPolicyJson;
import io.harness.ccm.setup.service.support.impl.pojo.BucketPolicyStatement;
import io.harness.ccm.setup.service.support.intfc.AwsEKSHelperService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReport;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsRequest;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsResult;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AWSCEConfigValidationServiceImplTest extends CategoryTest {
  @Spy @InjectMocks AWSCEConfigValidationServiceImpl awsceConfigValidationService;
  @Mock private AwsCredentialHelper awsCredentialHelper;
  @Mock AwsEKSHelperService awsEKSHelperService;

  private static final String S3_BUCKET_NAME = "s3BucketName";
  private static final String IAM_ROLE_ARN = "roleArn";
  private static final String EXTERNAL_ID = "externalId";
  private static final String CUR_REPORT_NAME = "curReport";
  private static final String AWS_ACCOUNT_ID = "awsAccount";
  private static final String s3Region = "us-east-1";
  private static final String compression = "GZIP";
  private static final String s3Prefix = "prefix";
  private static final String timeGranularity = "HOURLY";
  private static final String reportVersioning = "OVERWRITE_REPORT";
  private static final String invalidValue = "invalidValue";
  private static final String awsS3BucketName = "awsS3BucketName";
  private static final String roleArnPredefined = "arn:aws:iam::awsMasterAccount:role/roleArnPredefined";
  private static final String aws = "AWS";
  private static final String sid = "Sid";
  private static final String version = "version";
  private static final String effect = "allow";
  private static final String action = "*:*";
  private static final String resource = "*";
  private static final String crossAccountRole = "RoleArn";
  private static CEAwsConfig ceAwsConfig = CEAwsConfig.builder().build();
  private static ReportDefinition reportDefinition = new ReportDefinition();
  private static HashMap<String, String> exceptionParamsMap = new HashMap<>();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ceAwsConfig =
        CEAwsConfig.builder()
            .s3BucketDetails(AwsS3BucketDetails.builder().s3BucketName(S3_BUCKET_NAME).build())
            .awsCrossAccountAttributes(
                AwsCrossAccountAttributes.builder().crossAccountRoleArn(IAM_ROLE_ARN).externalId(EXTERNAL_ID).build())
            .curReportName(CUR_REPORT_NAME)
            .awsAccountId(AWS_ACCOUNT_ID)
            .build();

    reportDefinition.setReportVersioning(reportVersioning);
    reportDefinition.setCompression(compression);
    reportDefinition.setTimeUnit(timeGranularity);
    reportDefinition.setS3Bucket(S3_BUCKET_NAME);
    reportDefinition.setS3Region(s3Region);
    reportDefinition.setS3Prefix(s3Prefix);
    reportDefinition.setReportName(CUR_REPORT_NAME);
    reportDefinition.setRefreshClosedReports(true);
    reportDefinition.setAdditionalSchemaElements(Collections.singleton("RESOURCES"));
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void updateBucketPermissionsTest() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
    BucketPolicy mockBucketPolicy = mock(BucketPolicy.class);
    doReturn(mockCredential).when(awsCredentialHelper).constructBasicAwsCredentials();
    doReturn(mockS3Client).when(awsceConfigValidationService).getAmazonS3Client(mockCredential);
    Map<String, List<String>> principle = new HashMap<>();

    principle.put(aws, Collections.singletonList(roleArnPredefined));
    BucketPolicyStatement bucketPolicyStatement = BucketPolicyStatement.builder()
                                                      .Sid(sid)
                                                      .Effect(effect)
                                                      .Condition(null)
                                                      .Resource(resource)
                                                      .Action(action)
                                                      .Principal(principle)
                                                      .build();
    BucketPolicyJson policyJson =
        BucketPolicyJson.builder().Version(version).Statement(Collections.singletonList(bucketPolicyStatement)).build();
    Gson gson = new Gson();
    String policy = gson.toJson(policyJson);

    doReturn(awsS3BucketName).when(awsCredentialHelper).getAWSS3Bucket();
    doReturn(mockBucketPolicy).when(mockS3Client).getBucketPolicy(awsS3BucketName);
    doReturn(policy).when(mockBucketPolicy).getPolicyText();

    boolean updateBucketPolicy = awsceConfigValidationService.updateBucketPolicy(
        CEAwsConfig.builder()
            .awsCrossAccountAttributes(
                AwsCrossAccountAttributes.builder().crossAccountRoleArn(crossAccountRole).build())
            .build());

    ArgumentCaptor<String> bucketPolicyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> bucketNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockS3Client).setBucketPolicy(bucketNameCaptor.capture(), bucketPolicyCaptor.capture());
    BucketPolicyJson bucketPolicyJson = gson.fromJson(bucketPolicyCaptor.getAllValues().get(0), BucketPolicyJson.class);
    assertThat(bucketPolicyJson.getVersion()).isEqualTo(version);
    assertThat(bucketPolicyJson.getStatement().get(0).getSid()).isEqualTo(sid);
    assertThat(bucketPolicyJson.getStatement().get(0).getEffect()).isEqualTo(effect);
    assertThat(bucketPolicyJson.getStatement().get(0).getAction()).isEqualTo(action);
    assertThat(bucketPolicyJson.getStatement().get(0).getResource()).isEqualTo(resource);
    assertThat(bucketPolicyJson.getStatement().get(0).getCondition()).isNull();
    assertThat(bucketPolicyJson.getStatement().get(0).getPrincipal().get(aws).contains(roleArnPredefined)).isTrue();
    assertThat(updateBucketPolicy).isTrue();

    // Principle already exists case
    updateBucketPolicy = awsceConfigValidationService.updateBucketPolicy(
        CEAwsConfig.builder()
            .awsCrossAccountAttributes(
                AwsCrossAccountAttributes.builder().crossAccountRoleArn(roleArnPredefined).build())
            .build());
    assertThat(updateBucketPolicy).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetAmazonS3Client() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential).when(awsCredentialHelper).constructBasicAwsCredentials();
    AmazonS3Client amazonS3Client = awsceConfigValidationService.getAmazonS3Client(mockCredential);
    assertThat(amazonS3Client).isNotNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void validateCURReportAccessAndReturnS3Region() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());
    AwsS3BucketDetails s3Config = awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig);
    assertThat(s3Config.getRegion()).isEqualTo(s3Region);
    assertThat(s3Config.getS3Prefix()).isEqualTo(s3Prefix);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void reportNotFoundTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    exceptionParamsMap.put("args", "CUR Report: Invalid CUR Report Name");
    doReturn(null).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void invalidS3BucketTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setS3Bucket(invalidValue);
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: S3 Bucket Name Mismatch");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void invalidCompressionTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setCompression(invalidValue);
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: Compression is not GZIP");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void invalidTimeGranularityTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setTimeUnit(invalidValue);
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: Time Granularity is not Hourly");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void invalidReportVersioningTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setReportVersioning(invalidValue);
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: Report versioning should be OVERWRITE_REPORT");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void invalidRefreshSettingTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setRefreshClosedReports(false);
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: Data Refresh setting should be Automatic");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void resourceIdNotPresentTestCase() {
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    reportDefinition.setAdditionalSchemaElements(Collections.emptyList());
    doReturn(reportDefinition).when(awsceConfigValidationService).getReportDefinitionIfPresent(any(), anyString());

    exceptionParamsMap.put("args", "CUR Report Config: Missing Include ResourceIds in CUR configuration");
    assertThatThrownBy(() -> awsceConfigValidationService.validateCURReportAccessAndReturnS3Config(ceAwsConfig))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetReportDefinitionIfPresentMethod() {
    AWSCostAndUsageReport mockClient = mock(AWSCostAndUsageReport.class);
    doReturn(new DescribeReportDefinitionsResult().withReportDefinitions(reportDefinition).withNextToken(null))
        .when(mockClient)
        .describeReportDefinitions(new DescribeReportDefinitionsRequest().withNextToken(null));
    ReportDefinition report = awsceConfigValidationService.getReportDefinitionIfPresent(mockClient, CUR_REPORT_NAME);
    assertThat(report.getReportName()).isEqualTo(CUR_REPORT_NAME);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetReportDefinitionNotPresentMethod() {
    AWSCostAndUsageReport mockClient = mock(AWSCostAndUsageReport.class);
    reportDefinition.setReportName(CUR_REPORT_NAME + "_1");
    doReturn(new DescribeReportDefinitionsResult().withReportDefinitions(reportDefinition).withNextToken(null))
        .when(mockClient)
        .describeReportDefinitions(new DescribeReportDefinitionsRequest().withNextToken(null));
    ReportDefinition report = awsceConfigValidationService.getReportDefinitionIfPresent(mockClient, CUR_REPORT_NAME);
    assertThat(report).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void verifyCrossAccountAttributesWithIncorrectEKSPermissions() {
    SettingAttribute settingAttributeMock = mock(SettingAttribute.class);
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(ceAwsConfig).when(settingAttributeMock).getValue();
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    doReturn(true)
        .when(awsceConfigValidationService)
        .validateIfBucketIsPresent(mockCredential, ceAwsConfig.getS3BucketDetails());
    doReturn(true).when(awsceConfigValidationService).validateOrganisationReadOnlyAccess(mockCredential);
    doReturn(false).when(awsEKSHelperService).verifyAccess(s3Region, ceAwsConfig.getAwsCrossAccountAttributes());

    exceptionParamsMap.put("args", "Validation Failed: Can not Access EKS data");
    assertThatThrownBy(() -> awsceConfigValidationService.verifyCrossAccountAttributes(settingAttributeMock))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void verifyCrossAccountAttributesWithIncorrectS3Permissions() {
    SettingAttribute settingAttributeMock = mock(SettingAttribute.class);
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(ceAwsConfig).when(settingAttributeMock).getValue();
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    doReturn(false)
        .when(awsceConfigValidationService)
        .validateIfBucketIsPresent(mockCredential, ceAwsConfig.getS3BucketDetails());
    doReturn(true).when(awsceConfigValidationService).validateOrganisationReadOnlyAccess(mockCredential);
    doReturn(true).when(awsEKSHelperService).verifyAccess(s3Region, ceAwsConfig.getAwsCrossAccountAttributes());

    exceptionParamsMap.put("args", "Validation Failed: Can not Access S3 bucket");
    assertThatThrownBy(() -> awsceConfigValidationService.verifyCrossAccountAttributes(settingAttributeMock))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void verifyCrossAccountAttributesWithIncorrectOrganisationsPermissions() {
    SettingAttribute settingAttributeMock = mock(SettingAttribute.class);
    AWSCredentialsProvider mockCredential = mock(AWSCredentialsProvider.class);
    doReturn(ceAwsConfig).when(settingAttributeMock).getValue();
    doReturn(mockCredential)
        .when(awsceConfigValidationService)
        .getCredentialProvider(ceAwsConfig.getAwsCrossAccountAttributes());
    doReturn(true)
        .when(awsceConfigValidationService)
        .validateIfBucketIsPresent(mockCredential, ceAwsConfig.getS3BucketDetails());
    doReturn(false).when(awsceConfigValidationService).validateOrganisationReadOnlyAccess(mockCredential);
    doReturn(true).when(awsEKSHelperService).verifyAccess(s3Region, ceAwsConfig.getAwsCrossAccountAttributes());

    exceptionParamsMap.put("args", "Validation Failed: Issue with Listing Organisational Data");
    assertThatThrownBy(() -> awsceConfigValidationService.verifyCrossAccountAttributes(settingAttributeMock))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasFieldOrPropertyWithValue("params", exceptionParamsMap);
  }
}
