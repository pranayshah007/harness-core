/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cvng;

import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorValidationParams;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CV)
public class CVConnectorValidationHandlerTest extends CategoryTest {
  private final String accountIdentifier = "accountIdentifier";
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private DataCollectionDSLService dataCollectionDSLService;
  @InjectMocks CVConnectorValidationHandler cvConnectorValidationHandler;
  private ConnectorValidationParams connectorValidationParams;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    String apiKeyRef = "apiKeyRef";
    String decryptedApiKeyRef = "apiKey";
    String applicationKeyRef = "appKeyRef";
    String decryptedApplicationKeyRef = "appKey";
    String url = "https://hqdatadog.com/";
    SecretRefData apiKeySecretRef = SecretRefData.builder()
                                        .identifier(apiKeyRef)
                                        .scope(Scope.ACCOUNT)
                                        .decryptedValue(decryptedApiKeyRef.toCharArray())
                                        .build();
    SecretRefData applicationKeySecretRef = SecretRefData.builder()
                                                .identifier(applicationKeyRef)
                                                .scope(Scope.ACCOUNT)
                                                .decryptedValue(decryptedApplicationKeyRef.toCharArray())
                                                .build();
    DatadogConnectorDTO datadogConnectorDTO = DatadogConnectorDTO.builder()
                                                  .apiKeyRef(apiKeySecretRef)
                                                  .applicationKeyRef(applicationKeySecretRef)
                                                  .url(url)
                                                  .build();

    List<List<EncryptedDataDetail>> encryptedDataList = new ArrayList<>();
    encryptedDataList.add(new ArrayList<>());
    encryptedDataList.get(0).add(new EncryptedDataDetail());

    connectorValidationParams = CVConnectorValidationParams.builder()
                                    .connectorConfigDTO(datadogConnectorDTO)
                                    .connectorName("TestDatadogConnector")
                                    .encryptedDataDetails(encryptedDataList)
                                    .connectorType(ConnectorType.DATADOG)
                                    .build();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testValidateSuccess() throws IllegalAccessException {
    Clock clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    FieldUtils.writeField(cvConnectorValidationHandler, "dataCollectionDSLService", dataCollectionDSLService, true);
    FieldUtils.writeField(cvConnectorValidationHandler, "clock", clock, true);
    when(dataCollectionDSLService.execute(any(), any())).thenReturn("true");

    ConnectorValidationResult result =
        cvConnectorValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testValidateFailure() throws IllegalAccessException {
    Clock clock = Clock.fixed(Instant.parse("2020-04-22T10:02:06Z"), ZoneOffset.UTC);
    FieldUtils.writeField(cvConnectorValidationHandler, "dataCollectionDSLService", dataCollectionDSLService, true);
    FieldUtils.writeField(cvConnectorValidationHandler, "clock", clock, true);
    when(dataCollectionDSLService.execute(any(), any())).thenReturn("false");

    ConnectorValidationResult result =
        cvConnectorValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testValidateFailureWithException() throws IllegalAccessException {
    Clock clock = null;
    FieldUtils.writeField(cvConnectorValidationHandler, "dataCollectionDSLService", dataCollectionDSLService, true);
    FieldUtils.writeField(cvConnectorValidationHandler, "clock", clock, true);
    when(dataCollectionDSLService.execute(any(), any())).thenReturn("true");

    ConnectorValidationResult result =
        cvConnectorValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }
}
