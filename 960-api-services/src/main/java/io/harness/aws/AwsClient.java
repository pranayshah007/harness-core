/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.aws;

import io.harness.aws.beans.AwsInternalConfig;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.services.codecommit.model.Commit;
import com.amazonaws.services.codecommit.model.RepositoryMetadata;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.identitymanagement.model.EvaluationResult;
import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.s3.model.ObjectListing;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public interface AwsClient {
  void validateAwsAccountCredential(AwsConfig awsConfig);

  void validateAwsCodeCommitCredential(AwsConfig awsConfig, String region, String repo);

  RepositoryMetadata fetchRepositoryInformation(AwsConfig awsConfig, String region, String repo);

  List<Commit> fetchCommitInformation(AwsConfig awsConfig, String region, String repo, List<String> commitIds);

  void confirmSnsSubscription(String confirmationMessage, String topicArnString);

  String getAmazonEcrAuthToken(AwsConfig awsConfig, String account, String region);

  AWSCredentialsProvider getAssumedCredentialsProvider(
      AWSCredentialsProvider credentialsProvider, String crossAccountRoleArn, @Nullable String externalId);

  Optional<ReportDefinition> getReportDefinition(AWSCredentialsProvider credentialsProvider, String curReportName);

  AWSCredentialsProvider constructStaticBasicAwsCredentials(@NotNull String accessKey, @NotNull String secretKey);

  List<String> listRolePolicyNames(AWSCredentialsProvider awsCredentialsProvider, @NotNull String roleName);

  List<EvaluationResult> simulatePrincipalPolicy(AWSCredentialsProvider credentialsProvider,
      @NotNull String policySourceArn, @NotEmpty List<String> actionNames, @Nullable List<String> resourceArns);

  Policy getRolePolicy(AWSCredentialsProvider credentialsProvider, String roleName, String policyName);

  ObjectListing getBucket(AWSCredentialsProvider credentialsProvider, String s3BucketName, String s3Prefix);

  AWSOrganizationsClient getAWSOrganizationsClient(
      String crossAccountRoleArn, String externalId, String awsAccessKey, String awsSecretKey);

  Map<String, String> listIAMRoles(AwsInternalConfig awsInternalConfig);
}
