/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgInfraConfigHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock SecretDecryptionService secretDecryptionService;

  @Spy @InjectMocks private AsgInfraConfigHelper asgInfraConfigHelper;

  AsgInfraConfig asgInfraConfig =
      AsgInfraConfig.builder()
          .region("us-east-1")
          .awsConnectorDTO(
              AwsConnectorDTO.builder()
                  .credential(
                      AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
                  .build())
          .build();

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void decryptAsgInfraConfigTest() {
    DecryptableEntity decryptableEntity = Mockito.mock(DecryptableEntity.class);
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), any());
    asgInfraConfigHelper.decryptAsgInfraConfig(asgInfraConfig);
    verify(secretDecryptionService).decrypt(any(), any());
  }
}
