/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.HintException.HINT_AWS_CONNECTOR_NG_DOCUMENTATION;
import static io.harness.exception.HintException.IAM_DETAILS_COMMAND;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.arn.Arn;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codecommit.AWSCodeCommitClient;
import com.amazonaws.services.codecommit.AWSCodeCommitClientBuilder;
import com.amazonaws.services.codecommit.model.AWSCodeCommitException;
import com.amazonaws.services.codecommit.model.BatchGetCommitsRequest;
import com.amazonaws.services.codecommit.model.BatchGetCommitsResult;
import com.amazonaws.services.codecommit.model.Commit;
import com.amazonaws.services.codecommit.model.GetRepositoryRequest;
import com.amazonaws.services.codecommit.model.GetRepositoryResult;
import com.amazonaws.services.codecommit.model.ListRepositoriesRequest;
import com.amazonaws.services.codecommit.model.RepositoryMetadata;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReport;
import com.amazonaws.services.costandusagereport.AWSCostAndUsageReportClientBuilder;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsRequest;
import com.amazonaws.services.costandusagereport.model.DescribeReportDefinitionsResult;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.EvaluationResult;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetRolePolicyResult;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.SimulatePrincipalPolicyRequest;
import com.amazonaws.services.identitymanagement.model.SimulatePrincipalPolicyResult;
import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.AWSOrganizationsClientBuilder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.sns.message.DefaultSnsMessageHandler;
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class AwsClientImpl implements AwsClient {
  @Inject protected AwsCallTracker tracker;
  @Inject AwsApiHelperService awsApiHelperService;

  private static final Regions DEFAULT_REGION = Regions.US_EAST_1;

  @Override
  public void validateAwsAccountCredential(AwsConfig awsConfig, @NotNull String region) {
    try (CloseableAmazonWebServiceClient<AmazonEC2Client> closeableAmazonEC2Client =
             new CloseableAmazonWebServiceClient(getAmazonEc2Client(awsConfig, region))) {
      tracker.trackEC2Call("Get Ec2 client");
      closeableAmazonEC2Client.getClient().describeRegions();
    } catch (Exception exception) {
      log.error("Exception validateAwsAccountCredential", exception);
      StringBuilder hintMessageBuilder = new StringBuilder();
      StringBuilder explanationMessageBuilder = new StringBuilder();
      if (awsConfig.isIRSA()) {
        hintMessageBuilder.append(HintException.HINT_AWS_IRSA_CHECK);
        explanationMessageBuilder.append(ExplanationException.EXPLANATION_IRSA_ROLE_CHECK);
      } else if (awsConfig.isEc2IamCredentials()) {
        hintMessageBuilder.append(HintException.HINT_AWS_IAM_ROLE_CHECK);
        explanationMessageBuilder.append(ExplanationException.EXPLANATION_AWS_AM_ROLE_CHECK);
      } else {
        hintMessageBuilder.append(HintException.HINT_INCORRECT_ACCESS_KEY_SECRET_KEY_PERMISSIONS_KEY);
        explanationMessageBuilder.append(
            ExplanationException.EXPLANATION_INCORRECT_ACCESS_KEY_SECRET_KEY_PERMISSIONS_KEY);
      }

      if (awsConfig.getCrossAccountAccess() != null) {
        hintMessageBuilder.append(HintException.HINT_INVALID_CROSS_ACCOUNT_ROLE_ARN_EXTERNAL_ID_PERMISSIONS_KEY);
        explanationMessageBuilder.append(
            ExplanationException.EXPLANATION_INVALID_CROSS_ACCOUNT_ROLE_ARN_EXTERNAL_ID_PERMISSIONS_KEY);
      }
      hintMessageBuilder.append(IAM_DETAILS_COMMAND).append(HINT_AWS_CONNECTOR_NG_DOCUMENTATION);
      throw NestedExceptionUtils.hintWithExplanationException(hintMessageBuilder.toString(),
          explanationMessageBuilder.toString(), new InvalidRequestException(exception.getMessage()));
    }
  }

  @Override
  public void validateAwsCodeCommitCredential(AwsConfig awsConfig, String region, String repo) {
    try (CloseableAmazonWebServiceClient<AWSCodeCommitClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeCommitClient(awsConfig, region))) {
      tracker.trackEC2Call("Get CodeCommit client");
      if (isNotEmpty(repo)) {
        closeableAmazonECSClient.getClient().getRepository(new GetRepositoryRequest().withRepositoryName(repo));
      } else {
        closeableAmazonECSClient.getClient().listRepositories(new ListRepositoriesRequest());
      }
    } catch (AWSCodeCommitException awsCodeCommitException) {
      if (awsCodeCommitException.getStatusCode() == 401) {
        checkCredentials(awsConfig);
      }
      throw awsCodeCommitException;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private void checkCredentials(AwsConfig awsConfig) {
    if (!awsConfig.isEc2IamCredentials() && !awsConfig.isIRSA()) {
      if (isEmpty(awsConfig.getAwsAccessKeyCredential().getAccessKey())) {
        throw new InvalidRequestException("Access Key should not be empty");
      } else if (isEmpty(awsConfig.getAwsAccessKeyCredential().getSecretKey())) {
        throw new InvalidRequestException("Secret Key should not be empty");
      }
    }
  }

  @Override
  public void confirmSnsSubscription(String confirmationMessage, String topicArnString) {
    if (isEmpty(topicArnString)) {
      throw new InvalidRequestException("Topic arn can't be empty");
    }
    String region = Arn.fromString(topicArnString).getRegion();
    SnsMessageManager snsMessageManager = new SnsMessageManager(region);
    snsMessageManager.handleMessage(
        IOUtils.toInputStream(confirmationMessage, Charset.defaultCharset()), new DefaultSnsMessageHandler() {
          @Override
          public void handle(SnsNotification message) {
            throw new InvalidRequestException("Only SubscriptionConfirmation message type is supported");
          }
        });
  }

  @Override
  public RepositoryMetadata fetchRepositoryInformation(AwsConfig awsConfig, String region, String repo) {
    try (CloseableAmazonWebServiceClient<AWSCodeCommitClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeCommitClient(awsConfig, region))) {
      GetRepositoryResult repository =
          closeableAmazonECSClient.getClient().getRepository(new GetRepositoryRequest().withRepositoryName(repo));
      return repository.getRepositoryMetadata();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public List<Commit> fetchCommitInformation(AwsConfig awsConfig, String region, String repo, List<String> commitIds) {
    try (CloseableAmazonWebServiceClient<AWSCodeCommitClient> closeableAmazonECSClient =
             new CloseableAmazonWebServiceClient(getAmazonCodeCommitClient(awsConfig, region))) {
      BatchGetCommitsResult batchGetCommitsResult = closeableAmazonECSClient.getClient().batchGetCommits(
          new BatchGetCommitsRequest().withRepositoryName(repo).withCommitIds(commitIds));
      return batchGetCommitsResult.getCommits();

    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public String getAmazonEcrAuthToken(AwsConfig awsConfig, String account, String region) {
    try (CloseableAmazonWebServiceClient<AmazonECRClient> closeableAmazonECRClient =
             new CloseableAmazonWebServiceClient(getAmazonEcrClient(region, awsConfig))) {
      tracker.trackECRCall("Get Auth Token");
      return closeableAmazonECRClient.getClient()
          .getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(singletonList(account)))
          .getAuthorizationData()
          .get(0)
          .getAuthorizationToken();
    } catch (AmazonEC2Exception amazonEC2Exception) {
      if (amazonEC2Exception.getStatusCode() == 401) {
        checkCredentials(awsConfig);
      }
      throw amazonEC2Exception;
    } catch (Exception e) {
      log.error("Exception getAmazonEcrAuthToken", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @VisibleForTesting
  AmazonEC2Client getAmazonEc2Client(AwsConfig awsConfig, String region) {
    AWSCredentialsProvider credentialsProvider = getCredentialProvider(awsConfig, region);
    return (AmazonEC2Client) AmazonEC2ClientBuilder.standard()
        .withRegion(region)
        .withCredentials(credentialsProvider)
        .build();
  }

  @VisibleForTesting
  AWSCodeCommitClient getAmazonCodeCommitClient(AwsConfig awsConfig, String region) {
    AWSCredentialsProvider credentialsProvider = getCredentialProvider(awsConfig, region);
    return (AWSCodeCommitClient) AWSCodeCommitClientBuilder.standard()
        .withRegion(region)
        .withCredentials(credentialsProvider)
        .build();
  }

  @VisibleForTesting
  AmazonECRClient getAmazonEcrClient(String region, AwsConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider = getCredentialProvider(awsConfig, region);
    return (AmazonECRClient) AmazonECRClientBuilder.standard()
        .withRegion(region)
        .withCredentials(credentialsProvider)
        .build();
  }

  protected AWSCredentialsProvider getCredentialProvider(AwsConfig awsConfig, String region) {
    AWSCredentialsProvider credentialsProvider;
    if (awsConfig.isEc2IamCredentials()) {
      log.info("Instantiating EC2ContainerCredentialsProviderWrapper");
      credentialsProvider = new EC2ContainerCredentialsProviderWrapper();
    }

    else if (awsConfig.isIRSA()) {
      WebIdentityTokenCredentialsProvider.Builder providerBuilder = WebIdentityTokenCredentialsProvider.builder();
      providerBuilder.roleSessionName("IRSA" + UUIDGenerator.generateUuid());

      credentialsProvider = providerBuilder.build();
    }

    else {
      credentialsProvider =
          constructStaticBasicAwsCredentials(defaultString(awsConfig.getAwsAccessKeyCredential().getAccessKey(), ""),
              awsConfig.getAwsAccessKeyCredential().getSecretKey() != null
                  ? new String(awsConfig.getAwsAccessKeyCredential().getSecretKey())
                  : "");
    }
    if (awsConfig.getCrossAccountAccess() != null) {
      CrossAccountAccess crossAccountAttributes = awsConfig.getCrossAccountAccess();
      region = EmptyPredicate.isNotEmpty(region) ? region : DEFAULT_REGION.getName();
      credentialsProvider = getAssumedCredentialsProvider(credentialsProvider, crossAccountAttributes, region);
    }
    return credentialsProvider;
  }

  public AWSCredentialsProvider getAssumedCredentialsProvider(
      AWSCredentialsProvider credentialsProvider, CrossAccountAccess crossAccountAccess, String region) {
    return getAssumedCredentialsProviderWithRegion(
        credentialsProvider, crossAccountAccess.getCrossAccountRoleArn(), crossAccountAccess.getExternalId(), region);
  }

  @Override
  public AWSCredentialsProvider getAssumedCredentialsProviderWithRegion(AWSCredentialsProvider credentialsProvider,
      String crossAccountRoleArn, @Nullable String externalId, @NotNull String region) {
    final AWSSecurityTokenService awsSecurityTokenService =
        AWSSecurityTokenServiceClientBuilder.standard().withRegion(region).withCredentials(credentialsProvider).build();
    return new STSAssumeRoleSessionCredentialsProvider.Builder(crossAccountRoleArn, UUID.randomUUID().toString())
        .withExternalId(externalId)
        .withStsClient(awsSecurityTokenService)
        .build();
  }

  @Override
  public AWSCredentialsProvider getAssumedCredentialsProvider(
      AWSCredentialsProvider credentialsProvider, String crossAccountRoleArn, @Nullable String externalId) {
    final AWSSecurityTokenService awsSecurityTokenService = constructAWSSecurityTokenService(credentialsProvider);
    return new STSAssumeRoleSessionCredentialsProvider.Builder(crossAccountRoleArn, UUID.randomUUID().toString())
        .withExternalId(externalId)
        .withStsClient(awsSecurityTokenService)
        .build();
  }

  public AmazonS3Client getAmazonS3Client(AWSCredentialsProvider credentialsProvider) {
    return (AmazonS3Client) AmazonS3ClientBuilder.standard()
        .withRegion(DEFAULT_REGION)
        .withForceGlobalBucketAccessEnabled(Boolean.TRUE)
        .withCredentials(credentialsProvider)
        .build();
  }

  @Override
  public Optional<ReportDefinition> getReportDefinition(
      AWSCredentialsProvider credentialsProvider, String curReportName) {
    AWSCostAndUsageReport awsCostAndUsageReportClient = getAwsCurClient(credentialsProvider);
    DescribeReportDefinitionsRequest describeReportDefinitionsRequest = new DescribeReportDefinitionsRequest();

    String nextToken = null;
    do {
      describeReportDefinitionsRequest.withNextToken(nextToken);
      DescribeReportDefinitionsResult describeReportDefinitionsResult =
          awsCostAndUsageReportClient.describeReportDefinitions(describeReportDefinitionsRequest);
      List<ReportDefinition> reportDefinitionsList = describeReportDefinitionsResult.getReportDefinitions();

      Optional<ReportDefinition> requiredReport =
          reportDefinitionsList.stream().filter(r -> r.getReportName().equals(curReportName)).findFirst();
      if (requiredReport.isPresent()) {
        return requiredReport;
      }

      nextToken = describeReportDefinitionsRequest.getNextToken();
    } while (nextToken != null);
    return Optional.empty();
  }

  protected AmazonIdentityManagement getAwsIAMClient(AWSCredentialsProvider credentialsProvider, Regions region) {
    return AmazonIdentityManagementClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(region)
        .build();
  }

  protected AWSCostAndUsageReport getAwsCurClient(AWSCredentialsProvider credentialsProvider) {
    return AWSCostAndUsageReportClientBuilder.standard()
        .withRegion(DEFAULT_REGION)
        .withCredentials(credentialsProvider)
        .build();
  }

  protected AWSSecurityTokenService constructAWSSecurityTokenService(AWSCredentialsProvider credentialsProvider) {
    return AWSSecurityTokenServiceClientBuilder.standard()
        .withRegion(DEFAULT_REGION)
        .withCredentials(credentialsProvider)
        .build();
  }

  @Override
  public AWSCredentialsProvider constructStaticBasicAwsCredentials(
      @NotNull String accessKey, @NotNull String secretKey) {
    return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
  }

  @Override
  public List<String> listRolePolicyNames(
      AWSCredentialsProvider awsCredentialsProvider, @NotNull final String roleName) {
    final AmazonIdentityManagement iam = getAwsIAMClient(awsCredentialsProvider, DEFAULT_REGION);

    List<String> policyNames = new ArrayList<>();

    final ListRolePoliciesRequest request = new ListRolePoliciesRequest().withRoleName(roleName);
    final ListRolePoliciesResult response = iam.listRolePolicies(request);
    do {
      policyNames.addAll(response.getPolicyNames());
      request.setMarker(response.getMarker());
    } while (Boolean.TRUE.equals(response.getIsTruncated()));

    return policyNames;
  }

  /**
   * @param credentialsProvider the credential (can also be assumed role creds) used while simulating policies.
   * @param policySourceArn the crossRoleArn whose policies needs to be verified
   * @param actionNames the list of actions e.g., "rds:ModifyDBInstance", "rds:*", etc.
   * @param resourceArns the arn of resources e.g., "*", "arn:aws:s3:::customerBucketName". On null defaults to "*"
   * @return EvaluationResultList the results when each actions is performed on each resources.
   */
  @Override
  public List<EvaluationResult> simulatePrincipalPolicy(final AWSCredentialsProvider credentialsProvider,
      @NotNull String policySourceArn, @NotEmpty List<String> actionNames, @Nullable List<String> resourceArns,
      @NotNull String region) {
    final AmazonIdentityManagement iam = getAwsIAMClient(credentialsProvider, Regions.fromName(region));
    final SimulatePrincipalPolicyRequest request =
        new SimulatePrincipalPolicyRequest().withPolicySourceArn(policySourceArn).withActionNames(actionNames);
    if (isNotEmpty(resourceArns)) {
      request.withResourceArns(resourceArns);
    }

    final List<EvaluationResult> evaluationResultList = new ArrayList<>();
    final SimulatePrincipalPolicyResult response = iam.simulatePrincipalPolicy(request);
    do {
      evaluationResultList.addAll(response.getEvaluationResults());
      request.setMarker(response.getMarker());
    } while (Boolean.TRUE.equals(response.getIsTruncated()));
    return evaluationResultList;
  }

  @SneakyThrows
  @Override
  public Policy getRolePolicy(
      AWSCredentialsProvider awsCredentialsProvider, @NotNull final String roleName, @NotNull final String policyName) {
    final AmazonIdentityManagement iam = getAwsIAMClient(awsCredentialsProvider, DEFAULT_REGION);
    final GetRolePolicyResult result =
        iam.getRolePolicy(new GetRolePolicyRequest().withPolicyName(policyName).withRoleName(roleName));

    final String policyDocumentAsEncodedJson = result.getPolicyDocument();
    return Policy.fromJson(java.net.URLDecoder.decode(policyDocumentAsEncodedJson, "UTF-8"));
  }

  @Override
  public ObjectListing getBucket(
      AWSCredentialsProvider credentialsProvider, @NotNull String s3BucketName, @Nullable String s3Prefix) {
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(getAmazonS3Client(credentialsProvider))) {
      return closeableAmazonS3Client.getClient().listObjects(s3BucketName, s3Prefix);

    } catch (Exception e) {
      log.error("Exception getBucket", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public S3Objects getIterableS3ObjectSummaries(
      AWSCredentialsProvider credentialsProvider, String s3BucketName, String s3Prefix) {
    s3Prefix = s3Prefix.replaceFirst("^//", "/");
    try {
      return S3Objects.withPrefix(getAmazonS3Client(credentialsProvider), s3BucketName, s3Prefix);
    } catch (Exception e) {
      log.error("Exception getIterableS3ObjectSummaries", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public AWSOrganizationsClient getAWSOrganizationsClient(
      String crossAccountRoleArn, String externalId, String awsAccessKey, String awsSecretKey) {
    AWSSecurityTokenService awsSecurityTokenService =
        constructAWSSecurityTokenService(constructStaticBasicAwsCredentials(awsAccessKey, awsSecretKey));
    AWSOrganizationsClientBuilder builder = AWSOrganizationsClientBuilder.standard().withRegion(DEFAULT_REGION);
    AWSCredentialsProvider credentialsProvider =
        new STSAssumeRoleSessionCredentialsProvider.Builder(crossAccountRoleArn, UUID.randomUUID().toString())
            .withExternalId(externalId)
            .withStsClient(awsSecurityTokenService)
            .build();
    builder.withCredentials(credentialsProvider);
    return (AWSOrganizationsClient) builder.build();
  }

  @Override
  public Map<String, String> listIAMRoles(AwsInternalConfig awsInternalConfig) {
    try (CloseableAmazonWebServiceClient<AmazonIdentityManagementClient> closeableAmazonIdentityManagementClient =
             new CloseableAmazonWebServiceClient(getAmazonIdentityManagementClient(awsInternalConfig))) {
      Map<String, String> result = new HashMap<>();
      String nextMarker = null;
      do {
        ListRolesRequest listRolesRequest = new ListRolesRequest().withMaxItems(400).withMarker(nextMarker);
        tracker.trackIAMCall("List Roles");
        ListRolesResult listRolesResult =
            closeableAmazonIdentityManagementClient.getClient().listRoles(listRolesRequest);
        listRolesResult.getRoles().forEach(role -> result.put(role.getArn(), role.getRoleName()));
        nextMarker = listRolesResult.getMarker();
      } while (nextMarker != null);
      return result;

    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception listIAMRoles", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyMap();
  }

  @VisibleForTesting
  AmazonIdentityManagementClient getAmazonIdentityManagementClient(AwsInternalConfig awsConfig) {
    AmazonIdentityManagementClientBuilder builder =
        AmazonIdentityManagementClient.builder().withRegion(getRegion(awsConfig));
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonIdentityManagementClient) builder.build();
  }

  private String getRegion(AwsInternalConfig awsConfig) {
    return isNotBlank(awsConfig.getDefaultRegion()) ? awsConfig.getDefaultRegion() : AWS_DEFAULT_REGION;
  }
}
