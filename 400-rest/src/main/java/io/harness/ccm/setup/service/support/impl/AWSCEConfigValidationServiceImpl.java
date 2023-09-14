/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.setup.service.support.AwsCredentialHelper;
import io.harness.ccm.setup.service.support.impl.pojo.BucketPolicyJson;
import io.harness.ccm.setup.service.support.impl.pojo.BucketPolicyStatement;
import io.harness.ccm.setup.service.support.intfc.AWSCEConfigValidationService;
import io.harness.ccm.setup.service.support.intfc.AwsEKSHelperService;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.CEProxyConfig;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReport;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReportClientBuilder;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsRequest;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsResult;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.AWSOrganizationsClientBuilder;
import com.amazonaws.services.organizations.model.AWSOrganizationsNotInUseException;
import com.amazonaws.services.organizations.model.AccessDeniedException;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Slf4j
@Singleton
@OwnedBy(CE)
public class AWSCEConfigValidationServiceImpl implements AWSCEConfigValidationService {
  @Inject private AwsCredentialHelper awsCredentialHelper;
  @Inject private AwsEKSHelperService awsEKSHelperService;
  private static final String rolePrefix = "arn:aws:iam";
  private static final String ceAWSRegion = AWS_DEFAULT_REGION;
  private static final String compression = "GZIP";
  private static final String timeGranularity = "HOURLY";
  private static final String reportVersioning = "OVERWRITE_REPORT";
  private static final String curReportKey = "CUR Report";
  private static final String curReportConfigKey = "CUR Report Config";
  private static final String validationFailureKey = "Validation Failed";
  public static final String resources = "RESOURCES";
  private static final String aws = "AWS";

  @Override
  public AwsS3BucketDetails validateCURReportAccessAndReturnS3Config(CEAwsConfig awsConfig) {
    AwsCrossAccountAttributes awsCrossAccountAttributes = awsConfig.getAwsCrossAccountAttributes();
    AWSCredentialsProvider credentialsProvider = getCredentialProvider(awsCrossAccountAttributes);
    AWSCostAndUsageReportClientBuilder awsCostAndUsageReportClientBuilder =
        AWSCostAndUsageReportClientBuilder.standard().withRegion(ceAWSRegion).withCredentials(credentialsProvider);
    if (awsCredentialHelper.getCeProxyConfig().isEnabled()) {
      log.info("awsCostAndUsageReportClientBuilder initializing with proxy config");
      awsCostAndUsageReportClientBuilder.withClientConfiguration(
          getClientConfiguration(awsCredentialHelper.getCeProxyConfig()));
    }
    AWSCostAndUsageReport awsCostAndUsageReportClient = awsCostAndUsageReportClientBuilder.build();

    ReportDefinition report = getReportDefinitionIfPresent(awsCostAndUsageReportClient, awsConfig.getCurReportName());
    if (report == null) {
      throw new InvalidArgumentsException(ImmutablePair.of(curReportKey, "Invalid CUR Report Name"));
    }
    return validateReportAndGetS3Region(report, awsConfig);
  }

  @Override
  public boolean updateBucketPolicy(CEAwsConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider = awsCredentialHelper.constructBasicAwsCredentials();
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(credentialsProvider))) {
      String crossAccountRoleArn = awsConfig.getAwsCrossAccountAttributes().getCrossAccountRoleArn();
      String awsS3Bucket = awsCredentialHelper.getAWSS3Bucket();

      BucketPolicy bucketPolicy = closeableAmazonS3Client.getClient().getBucketPolicy(awsS3Bucket);
      String policyText = bucketPolicy.getPolicyText();
      BucketPolicyJson policyJson = new Gson().fromJson(policyText, BucketPolicyJson.class);
      List<BucketPolicyStatement> listStatements = new ArrayList<>();
      for (BucketPolicyStatement statement : policyJson.getStatement()) {
        Map<String, List<String>> principal = statement.getPrincipal();
        List<String> rolesList = principal.get(aws);
        rolesList = rolesList.stream().filter(roleArn -> roleArn.contains(rolePrefix)).collect(Collectors.toList());
        if (rolesList.contains(crossAccountRoleArn)) {
          return true;
        }
        rolesList.add(crossAccountRoleArn);
        principal.put(aws, rolesList);
        statement.setPrincipal(principal);
        listStatements.add(statement);
      }
      policyJson = BucketPolicyJson.builder().Version(policyJson.getVersion()).Statement(listStatements).build();
      String updatedBucketPolicy = new Gson().toJson(policyJson);
      closeableAmazonS3Client.getClient().setBucketPolicy(awsS3Bucket, updatedBucketPolicy);
      return true;
    } catch (Exception e) {
      log.error("Exception updateBucketPolicy", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected AmazonS3Client getAmazonS3Client(AWSCredentialsProvider credentialsProvider) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(ceAWSRegion).withForceGlobalBucketAccessEnabled(Boolean.TRUE);
    builder.withCredentials(credentialsProvider);
    return (AmazonS3Client) builder.build();
  }

  private AwsS3BucketDetails validateReportAndGetS3Region(ReportDefinition report, CEAwsConfig awsConfig) {
    if (!report.getS3Bucket().equals(awsConfig.getS3BucketDetails().getS3BucketName())) {
      throw new InvalidArgumentsException(ImmutablePair.of(curReportConfigKey, "S3 Bucket Name Mismatch"));
    }
    if (!report.getCompression().equals(compression)) {
      throw new InvalidArgumentsException(ImmutablePair.of(curReportConfigKey, "Compression is not GZIP"));
    }
    if (!report.getTimeUnit().equals(timeGranularity)) {
      throw new InvalidArgumentsException(ImmutablePair.of(curReportConfigKey, "Time Granularity is not Hourly"));
    }
    if (!report.getReportVersioning().equals(reportVersioning)) {
      throw new InvalidArgumentsException(
          ImmutablePair.of(curReportConfigKey, "Report versioning should be OVERWRITE_REPORT"));
    }
    if (!report.isRefreshClosedReports()) {
      throw new InvalidArgumentsException(
          ImmutablePair.of(curReportConfigKey, "Data Refresh setting should be Automatic"));
    }
    if (!report.getAdditionalSchemaElements().contains(resources)) {
      throw new InvalidArgumentsException(
          ImmutablePair.of(curReportConfigKey, "Missing Include ResourceIds in CUR configuration"));
    }
    return AwsS3BucketDetails.builder().region(report.getS3Region()).s3Prefix(report.getS3Prefix()).build();
  }

  @VisibleForTesting
  ReportDefinition getReportDefinitionIfPresent(
      AWSCostAndUsageReport awsCostAndUsageReportClient, String curReportName) {
    List<ReportDefinition> reportDefinitionsList = new ArrayList<>();
    String nextToken = null;
    DescribeReportDefinitionsRequest describeReportDefinitionsRequest = new DescribeReportDefinitionsRequest();
    do {
      describeReportDefinitionsRequest.withNextToken(nextToken);
      DescribeReportDefinitionsResult describeReportDefinitionsResult =
          awsCostAndUsageReportClient.describeReportDefinitions(describeReportDefinitionsRequest);
      reportDefinitionsList.addAll(describeReportDefinitionsResult.getReportDefinitions());
      nextToken = describeReportDefinitionsRequest.getNextToken();
    } while (nextToken != null);

    for (ReportDefinition report : reportDefinitionsList) {
      if (report.getReportName().equals(curReportName)) {
        return report;
      }
    }
    return null;
  }

  @Override
  public void verifyCrossAccountAttributes(SettingAttribute settingAttribute) {
    CEAwsConfig awsConfig = (CEAwsConfig) settingAttribute.getValue();
    AwsCrossAccountAttributes awsCrossAccountAttributes = awsConfig.getAwsCrossAccountAttributes();
    AwsS3BucketDetails s3BucketDetails = awsConfig.getS3BucketDetails();
    AWSCredentialsProvider credentialsProvider = getCredentialProvider(awsCrossAccountAttributes);

    boolean isBucketAccessValid = false;

    try {
      isBucketAccessValid = validateIfBucketIsPresent(credentialsProvider, s3BucketDetails);
    } catch (AmazonS3Exception awsS3) {
      throw new InvalidArgumentsException(ImmutablePair.of(validationFailureKey, awsS3.getErrorCode()));
    } catch (Exception ex) {
      log.error("Exception while validating s3 bucket ", ex);
    }
    if (!isBucketAccessValid) {
      throw new InvalidArgumentsException(ImmutablePair.of(validationFailureKey, "Can not Access S3 bucket"));
    }
    boolean isOrganisationalReadAccessValid = false;

    try {
      isOrganisationalReadAccessValid = validateOrganisationReadOnlyAccess(credentialsProvider);
    } catch (AWSOrganizationsNotInUseException ex) {
      throw new InvalidArgumentsException(
          ImmutablePair.of(validationFailureKey, "Your account must be a member of an organization."));
    } catch (AccessDeniedException accessDeniedException) {
      throw new InvalidArgumentsException(ImmutablePair.of(
          validationFailureKey, accessDeniedException.getServiceName() + " " + accessDeniedException.getErrorCode()));
    }

    if (!isOrganisationalReadAccessValid) {
      throw new InvalidArgumentsException(
          ImmutablePair.of(validationFailureKey, "Issue with Listing Organisational Data"));
    }
    boolean isEksAccessValid = awsEKSHelperService.verifyAccess(ceAWSRegion, awsCrossAccountAttributes);
    if (!isEksAccessValid) {
      throw new InvalidArgumentsException(ImmutablePair.of(validationFailureKey, "Can not Access EKS data"));
    }
  }

  @VisibleForTesting
  boolean validateOrganisationReadOnlyAccess(AWSCredentialsProvider credentialsProvider) {
    AWSOrganizationsClientBuilder builder = AWSOrganizationsClientBuilder.standard().withRegion(ceAWSRegion);
    builder.withCredentials(credentialsProvider);
    if (awsCredentialHelper.getCeProxyConfig().isEnabled()) {
      log.info("AWSOrganizationsClientBuilder initializing with proxy config");
      builder.withClientConfiguration(getClientConfiguration(awsCredentialHelper.getCeProxyConfig()));
    }
    AWSOrganizationsClient awsOrganizationsClient = (AWSOrganizationsClient) builder.build();
    ListAccountsResult listAccountsResult = awsOrganizationsClient.listAccounts(new ListAccountsRequest());
    return !listAccountsResult.getAccounts().isEmpty();
  }

  @VisibleForTesting
  boolean validateIfBucketIsPresent(AWSCredentialsProvider credentialsProvider, AwsS3BucketDetails s3BucketDetails) {
    AmazonS3ClientBuilder builder =
        AmazonS3ClientBuilder.standard().withRegion(ceAWSRegion).withForceGlobalBucketAccessEnabled(Boolean.TRUE);
    builder.withCredentials(credentialsProvider);
    AmazonS3Client amazonS3Client = (AmazonS3Client) builder.build();
    List<S3ObjectSummary> objectSummaries =
        amazonS3Client.listObjects(s3BucketDetails.getS3BucketName()).getObjectSummaries();
    return objectSummaries != null;
  }

  @VisibleForTesting
  AWSCredentialsProvider getCredentialProvider(AwsCrossAccountAttributes awsCrossAccountAttributes) {
    AWSSecurityTokenService awsSecurityTokenService = awsCredentialHelper.constructAWSSecurityTokenService();
    return new STSAssumeRoleSessionCredentialsProvider
        .Builder(awsCrossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
        .withExternalId(awsCrossAccountAttributes.getExternalId())
        .withStsClient(awsSecurityTokenService)
        .build();
  }

  private ClientConfiguration getClientConfiguration(CEProxyConfig ceProxyConfig) {
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    clientConfiguration.setProxyHost(ceProxyConfig.getHost());
    clientConfiguration.setProxyPort(ceProxyConfig.getPort());
    if (!ceProxyConfig.getUsername().isEmpty()) {
      clientConfiguration.setProxyUsername(ceProxyConfig.getUsername());
    }
    if (!ceProxyConfig.getPassword().isEmpty()) {
      clientConfiguration.setProxyPassword(ceProxyConfig.getPassword());
    }
    clientConfiguration.setProtocol(
        ceProxyConfig.getProtocol().equalsIgnoreCase("http") ? Protocol.HTTP : Protocol.HTTPS);
    return clientConfiguration;
  }
}
