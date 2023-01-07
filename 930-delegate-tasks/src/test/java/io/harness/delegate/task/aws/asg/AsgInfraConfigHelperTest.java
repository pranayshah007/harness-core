/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.aws.asg.AsgCommandTaskNGHandler;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static junit.framework.TestCase.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@OwnedBy(CDP)


public class AsgInfraConfigHelperTest extends CategoryTest {
  private AsgInfraConfigHelper asgInfraConfigHelper;
  private AsgInfraConfig asgInfraConfig;
  private SecretDecryptionService secretDecryptionService;
  private AwsConnectorDTO awsConnectorDTO;
  private AwsCredentialDTO awsCredentialDTO;
  private AwsManualConfigSpecDTO awsManualConfigSpecDTO;
  private ExceptionMessageSanitizer exceptionMessageSanitizer;
  private List<EncryptedDataDetail> encryptedDataDetails;

  @Before
  public void setup() {
    asgInfraConfigHelper = new AsgInfraConfigHelper();
    asgInfraConfig = Mockito.mock(AsgInfraConfig.class);
    secretDecryptionService = Mockito.mock(SecretDecryptionService.class);
    awsConnectorDTO = Mockito.mock(AwsConnectorDTO.class);
    awsCredentialDTO = Mockito.mock(AwsCredentialDTO.class);
    awsManualConfigSpecDTO = Mockito.mock(AwsManualConfigSpecDTO.class);
    exceptionMessageSanitizer = Mockito.mock(ExceptionMessageSanitizer.class);
    encryptedDataDetails = Mockito.mock(List.class);

    Mockito.when(asgInfraConfig.getAwsConnectorDTO()).thenReturn(awsConnectorDTO);
    Mockito.when(awsConnectorDTO.getCredential()).thenReturn(awsCredentialDTO);
    Mockito.when(awsCredentialDTO.getAwsCredentialType()).thenReturn(AwsCredentialType.MANUAL_CREDENTIALS);
    Mockito.when(awsCredentialDTO.getConfig()).thenReturn(awsManualConfigSpecDTO);
    Mockito.when(asgInfraConfig.getEncryptionDataDetails()).thenReturn(encryptedDataDetails);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testDecryptAsgInfraConfig() {
    asgInfraConfigHelper.decryptAsgInfraConfig(asgInfraConfig);

    Mockito.verify(secretDecryptionService).decrypt(awsManualConfigSpecDTO, encryptedDataDetails);
    Mockito.verify(exceptionMessageSanitizer).storeAllSecretsForSanitizing(awsManualConfigSpecDTO, encryptedDataDetails);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetAsgCredentialType() {
    String expectedResult = AwsCredentialType.MANUAL_CREDENTIALS.name();
    String result = asgInfraConfigHelper.getAsgCredentialType(asgInfraConfig);

    assertEquals(expectedResult, result);
  }
}
