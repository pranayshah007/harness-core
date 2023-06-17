/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.connector.ConnectorTestConstants.ACCOUNT_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.CONNECTOR_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.DELEGATE_SELECTOR;
import static io.harness.connector.ConnectorTestConstants.HOST_NAME;
import static io.harness.connector.ConnectorTestConstants.ORG_IDENTIFIER;
import static io.harness.connector.ConnectorTestConstants.PROJECT_IDENTIFIER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.exception.HintException;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PhysicalDataCenterConnectorValidatorTest extends CategoryTest {
  @Mock private NGHostValidationService hostValidationService;
  @InjectMocks private PhysicalDataCenterConnectorValidator physicalDataCenterConnectorValidator;

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateConnectivityStatusSuccess() {
    List<HostValidationDTO> validationHostsSuccess = Arrays.asList(
        HostValidationDTO.builder().host("host1").status(HostValidationDTO.HostValidationStatus.SUCCESS).build(),
        HostValidationDTO.builder().host("host2").status(HostValidationDTO.HostValidationStatus.SUCCESS).build());
    doReturn(validationHostsSuccess)
        .when(hostValidationService)
        .validateHostsConnectivity(Collections.singletonList(HOST_NAME), ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR));

    ConnectorValidationResult validationResult =
        physicalDataCenterConnectorValidator.validate(getPhysicalDataCenterConnectorDTO(), ACCOUNT_IDENTIFIER,
            ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_IDENTIFIER);

    assertThat(validationResult).isNotNull();
    assertThat(validationResult).isInstanceOf(ConnectorValidationResult.class);
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateConnectivityStatusFailure() {
    String errorMsg = "Error message for host: 2.2.2.2";
    String errorReason = "Validation failed for host: 2.2.2.2";
    List<HostValidationDTO> validationHosts = Arrays.asList(
        HostValidationDTO.builder().host("host1").status(HostValidationDTO.HostValidationStatus.SUCCESS).build(),
        HostValidationDTO.builder().host("host2").status(HostValidationDTO.HostValidationStatus.SUCCESS).build(),
        HostValidationDTO.builder()
            .host("2.2.2.2")
            .error(ErrorDetail.builder().reason(errorReason).message(errorMsg).build())
            .status(HostValidationDTO.HostValidationStatus.FAILED)
            .build());
    doReturn(validationHosts)
        .when(hostValidationService)
        .validateHostsConnectivity(Collections.singletonList(HOST_NAME), ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR));

    assertThatThrownBy(()
                           -> physicalDataCenterConnectorValidator.validate(getPhysicalDataCenterConnectorDTO(),
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_IDENTIFIER))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please ensure if port is opened on host. Check firewall rules between the delegate and host. Try to test connectivity by telnet");
  }

  @Test
  @Owner(developers = OwnerRule.VLAD)
  @Category(UnitTests.class)
  public void testValidateConnectivityStatusFailureWhenNoHosts() {
    List<HostValidationDTO> validationHosts = Collections.emptyList();
    doReturn(validationHosts)
        .when(hostValidationService)
        .validateHostsConnectivity(Collections.singletonList(HOST_NAME), ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, Sets.newHashSet(DELEGATE_SELECTOR));

    ConnectorValidationResult validationResult =
        physicalDataCenterConnectorValidator.validate(getPhysicalDataCenterConnectorDTO(), ACCOUNT_IDENTIFIER,
            ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_IDENTIFIER);
    assertThat(validationResult).isNotNull();
    assertThat(validationResult).isInstanceOf(ConnectorValidationResult.class);
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(validationResult.getErrorSummary()).isEqualTo("No hosts provided");
  }

  private PhysicalDataCenterConnectorDTO getPhysicalDataCenterConnectorDTO() {
    HostDTO hostDTO = new HostDTO();
    hostDTO.setHostName(HOST_NAME);
    return PhysicalDataCenterConnectorDTO.builder()
        .hosts(Collections.singletonList(hostDTO))
        .delegateSelectors(Sets.newHashSet(DELEGATE_SELECTOR))
        .build();
  }
}
