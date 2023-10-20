/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.VINIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class InfrastructureStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ConnectorService connectorService;
  @Mock private NGLogCallback ngLogCallback;
  @InjectMocks private InfrastructureStepHelper infrastructureStepHelper;

  private static final String ACCOUNT_ID = "accId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetAndValidateConnectorSuccess() {
    ParameterField connectorRef = ParameterField.createValueField("connectorRef");
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                            .build();

    GcpConnectorDTO gcpConnectorServiceAccount =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().build())
                            .build())
            .build();

    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .connector(ConnectorInfoDTO.builder()
                                            .name("GCP connector")
                                            .connectorType(ConnectorType.GCP)
                                            .connectorConfig(gcpConnectorServiceAccount)
                                            .build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), eq("connectorRef"));

    ConnectorInfoDTO result = infrastructureStepHelper.validateAndGetConnector(connectorRef, ambiance, ngLogCallback);
    assertThat(result).isNotNull();
    assertThat(result.getConnectorConfig()).isEqualTo(gcpConnectorServiceAccount);
    verify(ngLogCallback, times(4)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetAndValidateConnectorFailure() {
    ParameterField connectorRef = ParameterField.createValueField("connectorRef");
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                            .build();

    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), eq("connectorRef"));

    assertThatThrownBy(() -> infrastructureStepHelper.validateAndGetConnector(connectorRef, ambiance, ngLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector not found for identifier : [connectorRef]");

    verify(ngLogCallback, times(1)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetAndValidateConnectorUnresolvedRuntimeInput() {
    ParameterField connectorRef = ParameterField.createExpressionField(true, "<+input>", null, true);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                            .build();

    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), eq("connectorRef"));

    assertThatThrownBy(() -> infrastructureStepHelper.validateAndGetConnector(connectorRef, ambiance, ngLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector ref is a runtime input but its value is not provided in the infrastructure");

    verify(ngLogCallback, times(1)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetAndValidateConnectorUnresolvedExpression() {
    ParameterField connectorRef = ParameterField.createExpressionField(true, "<+my.var>", null, true);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                            .build();

    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), eq("connectorRef"));

    assertThatThrownBy(() -> infrastructureStepHelper.validateAndGetConnector(connectorRef, ambiance, ngLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector ref [<+my.var>] could not be resolved in infrastructure");

    verify(ngLogCallback, times(1)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetAndValidateConnectorNoRef() {
    ParameterField connectorRef = ParameterField.ofNull();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                            .build();

    assertThatThrownBy(() -> infrastructureStepHelper.validateAndGetConnector(connectorRef, ambiance, ngLogCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector ref field not present in infrastructure");

    verify(ngLogCallback, times(1)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testValidateExpressions() {
    infrastructureStepHelper.validateExpression(ParameterField.createValueField("test"));

    assertThatThrownBy(()
                           -> infrastructureStepHelper.validateExpression(
                               ParameterField.createExpressionField(true, "<+test>", null, true)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unresolved Expression : [<+test>]");
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateExpressionMap() {
    InfrastructureStepHelper infrastructureStepHelper = new InfrastructureStepHelper();

    ParameterField<String> validField = ParameterField.createValueField("test");
    ParameterField<String> unresolvedField = ParameterField.createExpressionField(true, "<+test>", null, true);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("validField", validField);
    fieldNameValueMap.put("unresolvedField", unresolvedField);
    assertThatThrownBy(() -> infrastructureStepHelper.validateExpression(fieldNameValueMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unresolved Expression : [<+test>], for field [unresolvedField]");
  }
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testRequireOne() {
    infrastructureStepHelper.requireOne(
        ParameterField.createValueField("test"), ParameterField.createValueField("passed"));

    assertThatThrownBy(
        ()
            -> infrastructureStepHelper.requireOne(ParameterField.createExpressionField(true, "<+test1>", null, true),
                ParameterField.createExpressionField(true, "<+test2>", null, true)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unresolved Expressions : [<+test1>] , [<+test2>]");
  }
}
