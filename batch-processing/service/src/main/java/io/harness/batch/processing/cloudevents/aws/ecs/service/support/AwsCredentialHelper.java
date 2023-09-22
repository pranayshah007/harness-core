/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.remote.CEProxyConfig;

import software.wings.security.authentication.AwsS3SyncConfig;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Component
public class AwsCredentialHelper {
  @Autowired private BatchMainConfig batchMainConfig;
  private static final String ceAWSRegion = "us-east-1";

  public AWSSecurityTokenService constructAWSSecurityTokenService() {
    AwsS3SyncConfig awsS3SyncConfig = batchMainConfig.getAwsS3SyncConfig();
    AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(awsS3SyncConfig.getAwsAccessKey(), awsS3SyncConfig.getAwsSecretKey()));
    if (getCeProxyConfig() != null && getCeProxyConfig().isEnabled()) {
      return AWSSecurityTokenServiceClientBuilder.standard()
          .withCredentials(awsCredentialsProvider)
          .withRegion(ceAWSRegion)
          .withClientConfiguration(getClientConfiguration())
          .build();
    }
    return AWSSecurityTokenServiceClientBuilder.standard()
        .withRegion(ceAWSRegion)
        .withCredentials(awsCredentialsProvider)
        .build();
  }

  public AwsCredentialsProvider getAwsCredentialsProvider() {
    AwsS3SyncConfig awsS3SyncConfig = batchMainConfig.getAwsS3SyncConfig();
    AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(
        new String(awsS3SyncConfig.getAwsAccessKey()), new String(awsS3SyncConfig.getAwsSecretKey()));
    return StaticCredentialsProvider.create(awsBasicCredentials);
  }

  public CEProxyConfig getCeProxyConfig() {
    return batchMainConfig.getCeProxyConfig();
  }

  public ClientConfiguration getClientConfiguration() {
    CEProxyConfig ceProxyConfig = getCeProxyConfig();
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
