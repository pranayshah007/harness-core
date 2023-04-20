/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class AwsLambdaInfraConfigHelperTest extends CategoryTest {
  @InjectMocks AwsLambdaInfraConfigHelper awsLambdaInfraConfigHelper;

  @Mock private SecretDecryptionService secretDecryptionService;

  @Mock private LogCallback logCallback;

  private final AwsConnectorDTO awsConnectorDTO =
      AwsConnectorDTO.builder()
          .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
          .delegateSelectors(ImmutableSet.of("delegate1"))
          .build();
  private final ConnectorInfoDTO awsConnectorInfoDTO =
      ConnectorInfoDTO.builder().connectorType(ConnectorType.AWS).connectorConfig(awsConnectorDTO).build();

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void decryptInfraConfigTest() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    List<EncryptedDataDetail> encryptedDataDetailList = Arrays.asList();
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig = AwsLambdaFunctionsInfraConfig.builder()
                                                                      .awsConnectorDTO(awsConnectorDTO)
                                                                      .encryptionDataDetails(encryptedDataDetailList)
                                                                      .build();
    awsLambdaInfraConfigHelper.decryptInfraConfig(awsLambdaFunctionsInfraConfig);
  }
}