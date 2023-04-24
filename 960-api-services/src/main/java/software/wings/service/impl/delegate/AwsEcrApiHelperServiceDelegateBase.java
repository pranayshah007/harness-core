/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.delegate;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.AWS_ACCESS_DENIED;
import static io.harness.eraro.ErrorCode.AWS_CLUSTER_NOT_FOUND;
import static io.harness.eraro.ErrorCode.AWS_SERVICE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.codedeploy.model.AmazonCodeDeployException;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ecr.model.AmazonECRException;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ClientException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.google.inject.Inject;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsEcrApiHelperServiceDelegateBase {
  @Inject protected AwsCallTracker tracker;
  @Inject private AwsApiHelperService awsApiHelperService;
  public void attachCredentialsAndBackoffPolicy(AwsClientBuilder builder, AwsInternalConfig awsConfig) {
    AWSCredentialsProvider credentialsProvider;

    if (awsConfig.isUseEc2IamCredentials()) {
      log.debug("Instantiating EC2ContainerCredentialsProviderWrapper");
      credentialsProvider = new EC2ContainerCredentialsProviderWrapper();
    } else if (awsConfig.isUseIRSA()) {
      WebIdentityTokenCredentialsProvider.Builder providerBuilder = WebIdentityTokenCredentialsProvider.builder();
      providerBuilder.roleSessionName(awsConfig.getAccountId() + UUIDGenerator.generateUuid());

      credentialsProvider = providerBuilder.build();
    } else {
      credentialsProvider = new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(defaultString(String.valueOf(awsConfig.getAccessKey()), ""),
              awsConfig.getSecretKey() != null ? new String(awsConfig.getSecretKey()) : ""));
    }
    if (awsConfig.isAssumeCrossAccountRole() && awsConfig.getCrossAccountAttributes() != null) {
      // For the security token service we default to us-east-1.

      AWSSecurityTokenService securityTokenService =
          AWSSecurityTokenServiceClientBuilder.standard()
              .withRegion(isNotBlank(awsConfig.getDefaultRegion()) ? awsConfig.getDefaultRegion() : AWS_DEFAULT_REGION)
              .withCredentials(credentialsProvider)
              .build();
      AwsCrossAccountAttributes crossAccountAttributes = awsConfig.getCrossAccountAttributes();
      credentialsProvider = new STSAssumeRoleSessionCredentialsProvider
                                .Builder(crossAccountAttributes.getCrossAccountRoleArn(), UUID.randomUUID().toString())
                                .withStsClient(securityTokenService)
                                .withExternalId(crossAccountAttributes.getExternalId())
                                .build();
    }

    builder.withCredentials(credentialsProvider);
    ClientConfiguration clientConfiguration = new ClientConfiguration();
    RetryPolicy retryPolicy = awsApiHelperService.getRetryPolicy(awsConfig);
    clientConfiguration.setRetryPolicy(retryPolicy);
    builder.withClientConfiguration(clientConfiguration);
  }

  public void handleAmazonClientException(AmazonClientException amazonClientException) {
    log.error("AWS API Client call exception: {}", amazonClientException.getMessage());
    String errorMessage = amazonClientException.getMessage();
    if (isNotEmpty(errorMessage) && errorMessage.contains("/meta-data/iam/security-credentials/")) {
      throw new InvalidRequestException("The IAM role on the Ec2 delegate does not exist OR does not"
              + " have required permissions.",
          amazonClientException, USER);
    } else {
      log.error("Unhandled aws exception");
      throw new InvalidRequestException(isNotEmpty(errorMessage) ? errorMessage : "Unknown Aws client exception", USER);
    }
  }

  public void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    log.error("AWS API call exception: {}", amazonServiceException.getMessage());
    if (amazonServiceException instanceof AmazonCodeDeployException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof AmazonEC2Exception) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof ClusterNotFoundException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_CLUSTER_NOT_FOUND, USER);
    } else if (amazonServiceException instanceof ServiceNotFoundException) {
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_SERVICE_NOT_FOUND, USER);
    } else if (amazonServiceException instanceof AmazonAutoScalingException) {
      if (amazonServiceException.getMessage().contains(
              "Trying to remove Target Groups that are not part of the group")) {
        log.info("Target Group already not attached: [{}]", amazonServiceException.getMessage());
      } else if (amazonServiceException.getMessage().contains(
                     "Trying to remove Load Balancers that are not part of the group")) {
        log.info("Classic load balancer already not attached: [{}]", amazonServiceException.getMessage());
      } else {
        log.warn(amazonServiceException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
    } else if (amazonServiceException instanceof AmazonECSException
        || amazonServiceException instanceof AmazonECRException) {
      if (amazonServiceException instanceof ClientException) {
        log.warn(amazonServiceException.getErrorMessage(), amazonServiceException);
        throw amazonServiceException;
      }
      throw new InvalidRequestException(amazonServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (amazonServiceException instanceof AmazonCloudFormationException) {
      if (amazonServiceException.getMessage().contains("No updates are to be performed")) {
        log.info("Nothing to update on stack" + amazonServiceException.getMessage());
      } else {
        throw new InvalidRequestException(amazonServiceException.getMessage(), amazonServiceException, USER);
      }
    } else {
      throw new InvalidRequestException(amazonServiceException.getMessage(), amazonServiceException, USER);
    }
  }
}
