/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.AwsClientHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.exception.InvalidArgumentsException;

import software.wings.beans.AwsCrossAccountAttributes;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;

@Singleton
public class SimpleAwsClientHelper extends AwsClientHelper {
  @Override
  public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String client() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void handleClientServiceException(AwsServiceException awsServiceException) {
    throw new UnsupportedOperationException("Not implemented");
  }

  private AwsInternalConfig createAwsInternalConfig(AwsConnectorDTO awsConnectorDTO) {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    if (awsConnectorDTO == null) {
      throw new InvalidArgumentsException("Aws Connector DTO cannot be null");
    }

    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    if (MANUAL_CREDENTIALS == credential.getAwsCredentialType()) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) credential.getConfig();

      String accessKey = getSecretAsStringFromPlainTextOrSecretRef(
          awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef());

      if (StringUtils.isEmpty(accessKey)) {
        throw new InvalidArgumentsException(Pair.of("accessKeyId", "Missing or empty"));
      }

      char[] secretKey = awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue();

      awsInternalConfig = AwsInternalConfig.builder().accessKey(accessKey.toCharArray()).secretKey(secretKey).build();

    } else if (INHERIT_FROM_DELEGATE == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseEc2IamCredentials(true);
    } else if (IRSA == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseIRSA(true);
    }

    CrossAccountAccessDTO crossAccountAccess = credential.getCrossAccountAccess();
    if (crossAccountAccess != null) {
      awsInternalConfig.setAssumeCrossAccountRole(true);
      awsInternalConfig.setCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                                      .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
                                                      .externalId(crossAccountAccess.getExternalId())
                                                      .build());
    }
    return awsInternalConfig;
  }

  private AwsCredentialsProvider getAwsCredentialsProvider(AwsConnectorDTO awsConnectorDTO) {
    AwsInternalConfig awsInternalConfig = createAwsInternalConfig(awsConnectorDTO);
    return super.getAwsCredentialsProvider(awsInternalConfig);
  }

  public AwsCredentials getAwsCredentials(AwsConnectorDTO awsConnectorDTO) {
    return getAwsCredentialsProvider(awsConnectorDTO).resolveCredentials();
  }
}
