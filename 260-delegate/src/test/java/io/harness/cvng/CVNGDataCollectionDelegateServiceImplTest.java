/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.splunk.SplunkSavedSearchRequest;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.intfc.cvng.CVNGDataCollectionDelegateService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CVNGDataCollectionDelegateServiceImplTest extends CategoryTest {
  private CVNGDataCollectionDelegateService cvngDataCollectionDelegateService;
  @Mock private SecretDecryptionService secretDecryptionService;
  private Clock clock;
  @Mock private DataCollectionDSLService dataCollectionDSLService;

  private String accountId;
  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    accountId = generateUuid();
    cvngDataCollectionDelegateService = new CVNGDataCollectionDelegateServiceImpl();
    FieldUtils.writeField(cvngDataCollectionDelegateService, "secretDecryptionService", secretDecryptionService, true);
    clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    FieldUtils.writeField(cvngDataCollectionDelegateService, "clock", clock, true);
    FieldUtils.writeField(
        cvngDataCollectionDelegateService, "dataCollectionDSLService", dataCollectionDSLService, true);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDataCollectionResult() {
    ConnectorConfigDTO connectorConfigDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("123".toCharArray()).build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(EncryptedDataDetail.builder().build());
    List<List<EncryptedDataDetail>> details = new ArrayList<>();
    details.add(encryptedDataDetails);

    when(dataCollectionDSLService.execute(any(), any(), any())).thenReturn(Collections.singletonMap("a", 1));
    String result = cvngDataCollectionDelegateService.getDataCollectionResult(accountId,
        SplunkSavedSearchRequest.builder()
            .connectorInfoDTO(ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build())
            .build(),
        new ArrayList<>(details));
    assertThat(result).isEqualTo("{\"a\":1}");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDataCollectionResult_dslException() {
    ConnectorConfigDTO connectorConfigDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("123".toCharArray()).build())
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(EncryptedDataDetail.builder().build());
    List<List<EncryptedDataDetail>> details = new ArrayList<>();
    details.add(encryptedDataDetails);

    when(dataCollectionDSLService.execute(any(), any(), any()))
        .thenThrow(new RuntimeException(
            "io.harness.datacollection.exception.DataCollectionException: Response code: 404, Message: Not Found"));
    try {
      String result = cvngDataCollectionDelegateService.getDataCollectionResult(accountId,
          SplunkSavedSearchRequest.builder()
              .connectorInfoDTO(ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build())
              .build(),
          new ArrayList<>(details));
    } catch (Exception e) {
      assertThat(e.getMessage().startsWith("Response"));
    }
  }
}
