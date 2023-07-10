/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.connector.task.tas.TasValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.delegate.beans.connector.tasconnector.TasValidationParams;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TasValidationHandlerTest extends CategoryTest {
  @Mock protected CfDeploymentManager cfDeploymentManager;
  @Mock private TasNgConfigMapper ngConfigMapper;
  @Mock ExceptionManager exceptionManager;
  @InjectMocks TasValidationHandler tasValidationHandler;
  private final String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails() throws PivotalClientApiException {
    when(cfDeploymentManager.getOrganizations(any())).thenReturn(Collections.singletonList("org"));
    CloudFoundryConfig cloudFoundryConfig = CloudFoundryConfig.builder()
                                                .endpointUrl("https://google.com")
                                                .password("pass".toCharArray())
                                                .userName("username".toCharArray())
                                                .build();
    TasConnectorDTO tasConnectorDTO =
        TasConnectorDTO.builder()
            .credential(TasCredentialDTO.builder()
                            .spec(TasManualDetailsDTO.builder().endpointUrl("https://google.com").build())
                            .build())
            .build();
    when(ngConfigMapper.mapTasConfigWithDecryption(any(), any())).thenReturn(cloudFoundryConfig);
    ConnectorValidationParams connectorValidationParams =
        TasValidationParams.builder().tasConnectorDTO(tasConnectorDTO).build();
    ConnectorValidationResult result = tasValidationHandler.validate(connectorValidationParams, accountIdentifier);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithWrongUrl() throws PivotalClientApiException {
    when(cfDeploymentManager.getOrganizations(any())).thenReturn(Collections.singletonList("org"));
    when(exceptionManager.processException(any())).thenThrow(WingsException.class);
    CloudFoundryConfig cloudFoundryConfig = CloudFoundryConfig.builder()
                                                .endpointUrl("http://api.system.pcf-harness.com/")
                                                .password("pass".toCharArray())
                                                .userName("username".toCharArray())
                                                .build();
    TasConnectorDTO tasConnectorDTO =
        TasConnectorDTO.builder()
            .credential(
                TasCredentialDTO.builder()
                    .spec(TasManualDetailsDTO.builder().endpointUrl("http://api.system.pcf-harness.com/").build())
                    .build())
            .build();
    when(ngConfigMapper.mapTasConfigWithDecryption(any(), any())).thenReturn(cloudFoundryConfig);
    ConnectorValidationParams connectorValidationParams =
        TasValidationParams.builder().tasConnectorDTO(tasConnectorDTO).build();
    assertThatThrownBy(() -> tasValidationHandler.validate(connectorValidationParams, accountIdentifier))
        .isInstanceOf(NullPointerException.class);
  }
}
