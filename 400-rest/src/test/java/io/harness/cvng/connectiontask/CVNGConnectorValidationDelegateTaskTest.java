/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.connectiontask;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskResponse;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.WingsBaseTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CVNGConnectorValidationDelegateTaskTest extends WingsBaseTest {
  @Mock SecretDecryptionService secretDecryptionService;

  String passwordRef = "passwordRef";
  SecretRefData passwordSecretRef =
      SecretRefData.builder().identifier(passwordRef).decryptedValue("123".toCharArray()).scope(Scope.ACCOUNT).build();

  SplunkConnectorDTO splunkConnectorDTO = SplunkConnectorDTO.builder()
                                              .username("username")
                                              .splunkUrl("url")
                                              .accountId("accountId")
                                              .passwordRef(passwordSecretRef)
                                              .build();

  @InjectMocks
  CVNGConnectorValidationDelegateTask cvngConnectorValidateTaskDelegateRunnableTask =
      new CVNGConnectorValidationDelegateTask(
          DelegateTaskPackage.builder()
              .delegateId("delegateId")
              .data((TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                        .parameters(new TaskParameters[] {CVConnectorTaskParams.builder()
                                                              .connectorConfigDTO(splunkConnectorDTO)
                                                              .encryptionDetails(Collections.emptyList())
                                                              .build()})
                        .build())
              .build(),
          null, notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRun() throws IllegalAccessException {
    when(secretDecryptionService.decrypt(any(), anyList())).thenReturn(splunkConnectorDTO);
    DataCollectionDSLService dataCollectionDSLService = mock(DataCollectionDSLService.class);
    Clock clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    FieldUtils.writeField(
        cvngConnectorValidateTaskDelegateRunnableTask, "dataCollectionDSLService", dataCollectionDSLService, true);
    FieldUtils.writeField(cvngConnectorValidateTaskDelegateRunnableTask, "clock", clock, true);
    when(dataCollectionDSLService.execute(any(), any())).thenReturn("true");
    List<List<EncryptedDataDetail>> encryptedDetails = new ArrayList<>();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(EncryptedDataDetail.builder().build());
    encryptedDetails.add(encryptedDataDetails);

    DelegateResponseData delegateResponseData =
        cvngConnectorValidateTaskDelegateRunnableTask.run(CVConnectorTaskParams.builder()
                                                              .connectorConfigDTO(splunkConnectorDTO)
                                                              .encryptionDetails(encryptedDetails)
                                                              .build());
    CVConnectorTaskResponse cvConnectorTaskResponse = (CVConnectorTaskResponse) delegateResponseData;
    assertThat(cvConnectorTaskResponse.isValid()).isEqualTo(true);
  }
}
