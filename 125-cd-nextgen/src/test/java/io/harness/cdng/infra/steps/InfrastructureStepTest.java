/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.NAVNEET;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.K8sAzureInfraMapping;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.infra.beans.K8sGcpInfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure.K8SDirectInfrastructureBuilder;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.reflection.ReflectionUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;

import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class InfrastructureStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock EnvironmentService environmentService;
  @Mock ConnectorService connectorService;
  @InjectMocks private InfrastructureStep infrastructureStep;

  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock OutcomeService outcomeService;
  @Mock K8sStepHelper k8sStepHelper;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock InfrastructureStepHelper infrastructureStepHelper;
  @Mock NGLogCallback ngLogCallback;
  @Mock NGLogCallback ngLogCallbackOpen;

  private final String ACCOUNT_ID = "accountId";

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testValidateResource() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    K8SDirectInfrastructureBuilder k8SDirectInfrastructureBuilder = K8SDirectInfrastructure.builder();

    infrastructureStep.validateResources(ambiance, k8SDirectInfrastructureBuilder.build());
  }

  @Test
  @Owner(developers = {ACHYUTH, NAVNEET})
  @Category(UnitTests.class)
  public void testExecSyncAfterRbac() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

    GcpConnectorDTO gcpConnectorServiceAccount =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().build())
                            .build())
            .build();
    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .connector(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build())
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), eq("gcp-sa"));

    Infrastructure infrastructureSpec = K8sGcpInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField("account.gcp-sa"))
                                            .namespace(ParameterField.createValueField("namespace"))
                                            .releaseName(ParameterField.createValueField("releaseName"))
                                            .cluster(ParameterField.createValueField("cluster"))
                                            .build();

    when(infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true)).thenReturn(ngLogCallbackOpen);
    when(infrastructureStepHelper.getInfrastructureLogCallback(ambiance)).thenReturn(ngLogCallback);

    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.KUBERNETES).build());
    when(k8sStepHelper.getK8sInfraDelegateConfig(any(), eq(ambiance))).thenReturn(k8sInfraDelegateConfig);

    infrastructureStep.executeSyncAfterRbac(ambiance, infrastructureSpec, StepInputPackage.builder().build(), null);

    // Verifies `getInfrastructureLogCallback` is called with `shouldOpenStream` as `true` only once
    // Verifies `ngLogCallbackOpen` is used at least 4 times for the static logs
    // Verifies `ngLogCallbackOpen` is passed a success `CommandExecutionStatus` at the end of logs
    verify(infrastructureStepHelper, times(1)).getInfrastructureLogCallback(ambiance, true);
    verify(ngLogCallbackOpen, atLeast(4)).saveExecutionLog(anyString());
    verify(ngLogCallbackOpen, times(1))
        .saveExecutionLog(anyString(), eq(LogLevel.INFO), eq(CommandExecutionStatus.SUCCESS));

    // Verifies `getInfrastructureLogCallback` is called without `shouldOpenStream` for 3 times -> internal method calls
    // Verifies `ngLogCallback` is used at least 2 times for the static logs
    verify(infrastructureStepHelper, atLeast(3)).getInfrastructureLogCallback(ambiance);
    verify(ngLogCallback, atLeast(2)).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecSyncAfterRbacWithPdcInfra() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = PdcSshInfraDelegateConfig.builder().build();
    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                            .build();

    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(k8sStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(pdcSshInfraDelegateConfig);

    infrastructureStep.executeSyncAfterRbac(ambiance, infrastructureSpec, StepInputPackage.builder().build(), null);

    ArgumentCaptor<SshInfraDelegateConfigOutput> pdcConfigOutputCaptor =
        ArgumentCaptor.forClass(SshInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            pdcConfigOutputCaptor.capture(), eq(StepOutcomeGroup.STAGE.name()));
    SshInfraDelegateConfigOutput k8sInfraDelegateConfigOutput = pdcConfigOutputCaptor.getValue();
    assertThat(k8sInfraDelegateConfigOutput).isNotNull();
    assertThat(k8sInfraDelegateConfigOutput.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testPrivelegaedAccessControlClient() throws NoSuchFieldException {
    assertThat(ReflectionUtils.getFieldByName(InfrastructureStep.class, "accessControlClient")
                   .getAnnotation(Named.class)
                   .value())
        .isEqualTo("PRIVILEGED");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testCreateInfraMappingObject() {
    String namespace = "namespace";
    String connector = "connector";

    Infrastructure infrastructureSpec = K8SDirectInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connector))
                                            .namespace(ParameterField.createValueField(namespace))
                                            .build();

    InfraMapping expectedInfraMapping =
        K8sDirectInfraMapping.builder().k8sConnector(connector).namespace(namespace).build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testCreateK8sGcpInfraMapping() {
    String namespace = "namespace";
    String connector = "connector";
    String cluster = "cluster";

    Infrastructure infrastructureSpec = K8sGcpInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connector))
                                            .namespace(ParameterField.createValueField(namespace))
                                            .cluster(ParameterField.createValueField(cluster))
                                            .build();

    InfraMapping expectedInfraMapping =
        K8sGcpInfraMapping.builder().gcpConnector(connector).namespace(namespace).cluster(cluster).build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreatePdcInfraMappingWithHosts() {
    List<String> hosts = Arrays.asList("host1", "host2");
    String sshKeyRef = "some-key-ref";

    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .hosts(ParameterField.createValueField(hosts))
                                            .credentialsRef(ParameterField.createValueField(sshKeyRef))
                                            .build();

    InfraMapping expectedInfraMapping = PdcInfraMapping.builder().hosts(hosts).credentialsRef(sshKeyRef).build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreatePdcInfraMappingWithConnectorAndHostFilters() {
    String sshKeyRef = "some-key-ref";
    String connectorRef = "some-connector-ref";
    List<String> hostFilters = Arrays.asList("filter-host1", "filter-host2");

    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .credentialsRef(ParameterField.createValueField(sshKeyRef))
                                            .connectorRef(ParameterField.createValueField(connectorRef))
                                            .hostFilters(ParameterField.createValueField(hostFilters))
                                            .build();

    InfraMapping expectedInfraMapping =
        PdcInfraMapping.builder().credentialsRef(sshKeyRef).connectorRef(connectorRef).hostFilters(hostFilters).build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreatePdcInfraMappingWithConnectorAndAttributeFilters() {
    String sshKeyRef = "some-key-ref";
    String connectorRef = "some-connector-ref";
    Map<String, String> attributeFilters = new HashMap<>();
    attributeFilters.put("some-attribute", "some-value");
    attributeFilters.put("another-attribute", "another-value");

    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .credentialsRef(ParameterField.createValueField(sshKeyRef))
                                            .connectorRef(ParameterField.createValueField(connectorRef))
                                            .attributeFilters(ParameterField.createValueField(attributeFilters))
                                            .build();

    InfraMapping expectedInfraMapping = PdcInfraMapping.builder()
                                            .credentialsRef(sshKeyRef)
                                            .connectorRef(connectorRef)
                                            .attributeFilters(attributeFilters)
                                            .build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testCreateSshWinRmAzureInfraMapping() {
    String connectorRef = "some-connector-ref";
    String credentialsRef = "some-credentials-ref";
    String subscriptionId = "some-sub-id";
    String resourceGroup = "some-resource-group";
    Boolean usePublicDns = true;
    Map<String, String> tags = new HashMap<>();
    tags.put("some-tag", "some-value");
    tags.put("another-tag", "another-value");

    Infrastructure infrastructureSpec = SshWinRmAzureInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connectorRef))
                                            .credentialsRef(ParameterField.createValueField(credentialsRef))
                                            .subscriptionId(ParameterField.createValueField(subscriptionId))
                                            .resourceGroup(ParameterField.createValueField(resourceGroup))
                                            .tags(ParameterField.createValueField(tags))
                                            .usePublicDns(ParameterField.createValueField(usePublicDns))
                                            .build();

    InfraMapping expectedInfraMapping = SshWinRmAzureInfraMapping.builder()
                                            .credentialsRef(credentialsRef)
                                            .connectorRef(connectorRef)
                                            .subscriptionId(subscriptionId)
                                            .resourceGroup(resourceGroup)
                                            .tags(tags)
                                            .usePublicDns(usePublicDns)
                                            .build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testProcessEnvironment() {
    // TODO this test is not asserting anything.
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();

    EnvironmentYaml environmentYaml = EnvironmentYaml.builder()
                                          .identifier("test-id")
                                          .name("test-id")
                                          .type(EnvironmentType.PreProduction)
                                          .tags(Collections.emptyMap())
                                          .build();

    PipelineInfrastructure pipelineInfrastructure =
        PipelineInfrastructure.builder().environment(environmentYaml).build();

    Environment expectedEnv = Environment.builder()
                                  .identifier("test-id")
                                  .type(EnvironmentType.PreProduction)
                                  .tags(Collections.emptyList())
                                  .build();

    doReturn(expectedEnv).when(environmentService).upsert(expectedEnv, UpsertOptions.DEFAULT);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateInfrastructure() {
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Infrastructure definition can't be null or empty");

    K8SDirectInfrastructureBuilder k8SDirectInfrastructureBuilder = K8SDirectInfrastructure.builder();
    infrastructureStep.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null);

    k8SDirectInfrastructureBuilder.connectorRef(ParameterField.createValueField("connector"));
    infrastructureStep.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null);

    k8SDirectInfrastructureBuilder.connectorRef(new ParameterField<>(null, null, true, "expression1", null, true));
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(k8SDirectInfrastructureBuilder.build(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructure() {
    PdcInfrastructure infrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
                                           .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                           .build();

    infrastructureStep.validateInfrastructure(infrastructure, null);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructureSshKeyExpression() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(new ParameterField<>(null, null, true, "expression1", null, true))
            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructureHostsAndConnectorAreExpressions() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
            .hosts(new ParameterField<>(null, null, true, "expression1", null, true))
            .connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expressions : [expression1] , [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructure() {
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    infrastructureStep.validateInfrastructure(infrastructure, null);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureCredentialsIsExpression() {
    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .credentialsRef(new ParameterField<>(null, null, true, "expression1", null, true))
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .subscriptionId(ParameterField.createValueField("subscription-id"))
            .resourceGroup(ParameterField.createValueField("resource-group"))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureConnectorIsExpression() {
    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("credentials-ref"))
            .connectorRef(new ParameterField<>(null, null, true, "expression1", null, true))
            .subscriptionId(ParameterField.createValueField("subscription-id"))
            .resourceGroup(ParameterField.createValueField("resource-group"))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureSubscriptionIsExpression() {
    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("credentials-ref"))
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .subscriptionId(new ParameterField<>(null, null, true, "expression2", null, true))
            .resourceGroup(ParameterField.createValueField("resource-group"))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureResourceGroupIsExpression() {
    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("credentials-ref"))
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .subscriptionId(ParameterField.createValueField("subscription-id"))
            .resourceGroup(new ParameterField<>(null, null, true, "expression2", null, true))
            .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateConnector() {
    GcpConnectorDTO gcpConnectorServiceAccount =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().build())
                            .build())
            .build();
    GcpConnectorDTO gcpConnectorInheritFromDelegate =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();
    doReturn(Optional.empty()).when(connectorService).get(any(), any(), any(), eq("missing"));
    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .connector(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build())
                             .build()))
        .when(connectorService)
        .get(any(), any(), any(), eq("gcp-sa"));
    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                        .connector(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorInheritFromDelegate).build())
                        .build()))
        .when(connectorService)
        .get(any(), any(), any(), eq("gcp-delegate"));

    assertConnectorValidationMessage(
        K8sGcpInfrastructure.builder().connectorRef(ParameterField.createValueField("account.missing")).build(),
        "Connector not found for identifier : [account.missing]");

    assertThatCode(
        ()
            -> infrastructureStep.validateConnector(
                K8sGcpInfrastructure.builder().connectorRef(ParameterField.createValueField("account.gcp-sa")).build(),
                Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testCreateK8sAzureInfraMapping() {
    String namespace = "namespace";
    String connector = "connector";
    String subscriptionId = "subscriptionId";
    String resourceGroup = "resourceGroup";
    String cluster = "cluster";

    Infrastructure infrastructureSpec = K8sAzureInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connector))
                                            .namespace(ParameterField.createValueField(namespace))
                                            .subscriptionId(ParameterField.createValueField(subscriptionId))
                                            .resourceGroup(ParameterField.createValueField(resourceGroup))
                                            .cluster(ParameterField.createValueField(cluster))
                                            .build();

    InfraMapping expectedInfraMapping = K8sAzureInfraMapping.builder()
                                            .azureConnector(connector)
                                            .namespace(namespace)
                                            .subscription(subscriptionId)
                                            .resourceGroup(resourceGroup)
                                            .cluster(cluster)
                                            .build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAwsInfrastructure() {
    SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();

    infrastructureStep.validateInfrastructure(builder.build(), null);

    builder.credentialsRef(new ParameterField<>(null, null, true, "expression1", null, true))
        .connectorRef(ParameterField.createValueField("value"))
        .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");

    builder.connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
        .credentialsRef(ParameterField.createValueField("value"))
        .build();

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateAzureWebAppInfrastructure() {
    AzureWebAppInfrastructure infrastructure = AzureWebAppInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("connector-ref"))
                                                   .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                   .resourceGroup(ParameterField.createValueField("resource-group"))
                                                   .appService(ParameterField.createValueField("appService"))
                                                   .deploymentSlot(ParameterField.createValueField("deployment-slot"))
                                                   .build();

    infrastructureStep.validateInfrastructure(infrastructure, null);
  }

  private void assertConnectorValidationMessage(Infrastructure infrastructure, String message) {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance))
        .hasMessageContaining(message);
  }
}
