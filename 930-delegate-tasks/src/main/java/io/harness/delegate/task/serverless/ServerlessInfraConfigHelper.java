/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessAwsLambdaConfig.ServerlessAwsLambdaConfigBuilder;
import io.harness.serverless.model.ServerlessConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class ServerlessInfraConfigHelper {
  @Inject private SecretDecryptionService secretDecryptionService;

  public void decryptServerlessInfraConfig(ServerlessInfraConfig serverlessInfraConfig) {
    if (serverlessInfraConfig instanceof ServerlessAwsLambdaInfraConfig) {
      ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
          (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig;
      decryptAwsInfraConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO(),
          serverlessAwsLambdaInfraConfig.getEncryptionDataDetails());
    }
  }

  private void decryptAwsInfraConfig(AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    if (awsConnectorDTO.getCredential().getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO awsCredentialSpecDTO =
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig();
      secretDecryptionService.decrypt(awsCredentialSpecDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsCredentialSpecDTO, encryptedDataDetails);
    }
  }

  public ServerlessConfig createServerlessConfig(ServerlessInfraConfig serverlessInfraConfigDTO) {
    if (serverlessInfraConfigDTO instanceof ServerlessAwsLambdaInfraConfig) {
      return createServerlessAwsConfig((ServerlessAwsLambdaInfraConfig) serverlessInfraConfigDTO);
    } else {
      throw new InvalidRequestException("Unhandled ServerlessInfraConfig " + serverlessInfraConfigDTO.getClass());
    }
  }

  public ServerlessConfig createServerlessAwsConfig(ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig) {
    AwsCredentialType awsCredentialType =
        serverlessAwsLambdaInfraConfig.getAwsConnectorDTO().getCredential().getAwsCredentialType();
    switch (awsCredentialType) {
      case MANUAL_CREDENTIALS:
        return getServerlessAwsConfigFromManualCreds(
            (AwsManualConfigSpecDTO) serverlessAwsLambdaInfraConfig.getAwsConnectorDTO().getCredential().getConfig());
      default:
        throw new UnsupportedOperationException(
            String.format("Unsupported Serverless Aws Credential type: [%s]", awsCredentialType));
    }
  }

  public ServerlessConfig getServerlessAwsConfigFromManualCreds(AwsManualConfigSpecDTO awsManualConfigSpecDTO) {
    ServerlessAwsLambdaConfigBuilder serverlessAwsConfigBuilder = ServerlessAwsLambdaConfig.builder();
    serverlessAwsConfigBuilder.provider("aws");
    serverlessAwsConfigBuilder.accessKey(getSecretAsStringFromPlainTextOrSecretRef(
        awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef()));
    serverlessAwsConfigBuilder.secretKey(String.valueOf(awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue()));
    return serverlessAwsConfigBuilder.build();
  }
}
