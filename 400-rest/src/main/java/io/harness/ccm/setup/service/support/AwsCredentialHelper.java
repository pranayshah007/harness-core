/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.setup.service.support;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.setup.config.CESetUpConfig;
import io.harness.remote.CEAwsServiceEndpointConfig;
import io.harness.remote.CEProxyConfig;

import software.wings.app.MainConfiguration;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.google.inject.Inject;

@OwnedBy(CE)
public class AwsCredentialHelper {
  @Inject private MainConfiguration configuration;
  private static final String ceAWSRegion = AWS_DEFAULT_REGION;

  public AWSSecurityTokenService constructAWSSecurityTokenService() {
    CESetUpConfig ceSetUpConfig = configuration.getCeSetUpConfig();
    AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(ceSetUpConfig.getAwsAccessKey(), ceSetUpConfig.getAwsSecretKey()));
    if (getCeAwsServiceEndpointConfig() != null && getCeAwsServiceEndpointConfig().isEnabled()) {
      return AWSSecurityTokenServiceClientBuilder.standard()
          .withCredentials(awsCredentialsProvider)
          .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
              getCeAwsServiceEndpointConfig().getStsEndPointUrl(), getCeAwsServiceEndpointConfig().getEndPointRegion()))
          .build();
    }
    return AWSSecurityTokenServiceClientBuilder.standard()
        .withRegion(ceAWSRegion)
        .withCredentials(awsCredentialsProvider)
        .build();
  }

  public AWSCredentialsProvider constructBasicAwsCredentials() {
    CESetUpConfig ceSetUpConfig = configuration.getCeSetUpConfig();
    return new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(ceSetUpConfig.getAwsAccessKey(), ceSetUpConfig.getAwsSecretKey()));
  }

  public String getAWSS3Bucket() {
    return configuration.getCeSetUpConfig().getAwsS3BucketName();
  }

  public CEProxyConfig getCeProxyConfig() {
    return configuration.getCeProxyConfig();
  }
  public CEAwsServiceEndpointConfig getCeAwsServiceEndpointConfig() {
    return configuration.getCeAwsServiceEndpointConfig();
  }
}
