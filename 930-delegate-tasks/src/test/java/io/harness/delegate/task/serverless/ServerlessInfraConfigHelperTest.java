/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessInfraConfigHelperTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock SecretDecryptionService secretDecryptionService;
  @Mock DecryptableEntity decryptableEntity;

  @InjectMocks private ServerlessInfraConfigHelper serverlessInfraConfigHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void decryptServerlessInfraConfigTest() {
    AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder().accessKey("accessKey").build();
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).config(config).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ServerlessAwsLambdaInfraConfig serverlessInfraConfig =
        ServerlessAwsLambdaInfraConfig.builder().awsConnectorDTO(awsConnectorDTO).build();

    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(eq(config), any(List.class));
    serverlessInfraConfigHelper.decryptServerlessInfraConfig(serverlessInfraConfig);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createServerlessConfigTest() {
    SecretRefData secretKeyRef = SecretRefData.builder().decryptedValue(new char[] {'a', 'b', 'c'}).build();
    AwsManualConfigSpecDTO config =
        AwsManualConfigSpecDTO.builder().accessKey("accessKey").secretKeyRef(secretKeyRef).build();
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).config(config).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ServerlessAwsLambdaInfraConfig serverlessInfraConfig =
        ServerlessAwsLambdaInfraConfig.builder().awsConnectorDTO(awsConnectorDTO).build();
    ServerlessAwsLambdaConfig serverlessConfig =
        ServerlessAwsLambdaConfig.builder().provider("aws").accessKey("accessKey").secretKey("abc").build();
    serverlessInfraConfigHelper.createServerlessConfig(serverlessInfraConfig);
  }
}