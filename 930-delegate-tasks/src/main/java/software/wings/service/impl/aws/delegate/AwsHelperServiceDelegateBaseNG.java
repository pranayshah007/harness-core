/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.AwsCallTracker;

import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegateBase;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsHelperServiceDelegateBaseNG {
  @VisibleForTesting static final String HARNESS_AUTOSCALING_GROUP_TAG = "HARNESS_REVISION";
  @Inject protected AwsCallTracker tracker;
  @Inject protected AwsEcrApiHelperServiceDelegateBase awsEcrApiHelperServiceDelegateBase;

  protected void attachCredentialsAndBackoffPolicy(AwsClientBuilder builder, AwsInternalConfig awsConfig) {
    awsEcrApiHelperServiceDelegateBase.attachCredentialsAndBackoffPolicy(builder, awsConfig);
  }

  @VisibleForTesting
  void handleAmazonClientException(AmazonClientException amazonClientException) {
    awsEcrApiHelperServiceDelegateBase.handleAmazonClientException(amazonClientException);
  }

  @VisibleForTesting
  void handleAmazonServiceException(AmazonServiceException amazonServiceException) {
    awsEcrApiHelperServiceDelegateBase.handleAmazonServiceException(amazonServiceException);
  }

  protected boolean isHarnessManagedTag(String infraMappingId, TagDescription tagDescription) {
    return tagDescription.getKey().equals(HARNESS_AUTOSCALING_GROUP_TAG)
        && tagDescription.getValue().startsWith(infraMappingId);
  }

  protected String getRegion(AwsInternalConfig awsConfig) {
    if (isNotBlank(awsConfig.getDefaultRegion())) {
      return awsConfig.getDefaultRegion();
    } else {
      return AWS_DEFAULT_REGION;
    }
  }
}
