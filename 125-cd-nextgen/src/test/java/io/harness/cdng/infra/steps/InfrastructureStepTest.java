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
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.NAVNEET;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.InfrastructureOutcomeProvider;
import io.harness.cdng.infra.InfrastructureValidator;
import io.harness.cdng.infra.beans.GoogleFunctionsInfraMapping;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfraMapping;
import io.harness.cdng.infra.beans.K8sAzureInfraMapping;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.infra.beans.K8sGcpInfraMapping;
import io.harness.cdng.infra.beans.PdcInfraMapping;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfraMapping;
import io.harness.cdng.infra.beans.host.HostAttributesFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure.K8SDirectInfrastructureBuilder;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure.SshWinRmAwsInfrastructureBuilder;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.instance.InstanceOutcomeHelper;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.ssh.output.WinRmInfraDelegateConfigOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.reflection.ReflectionUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.common.collect.Lists;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class InfrastructureStepTest extends CategoryTest {
  @Mock EnvironmentService environmentService;
  @InjectMocks private InfrastructureStep infrastructureStep;

  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock OutcomeService outcomeService;
  @Mock CDStepHelper cdStepHelper;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock InfrastructureStepHelper infrastructureStepHelper;
  @Mock StageExecutionHelper stageExecutionHelper;
  @Mock NGLogCallback ngLogCallback;
  @Mock NGLogCallback ngLogCallbackOpen;
  @Mock InfrastructureOutcomeProvider infrastructureOutcomeProvider;
  @Mock InfrastructureValidator infrastructureValidator;
  @Mock InstanceOutcomeHelper instanceOutcomeHelper;

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
    List<ParameterField<String>> connectorRefs = new ArrayList<>();
    connectorRefs.add(ParameterField.createValueField("account.gcp-sa"));
    GcpConnectorDTO gcpConnectorServiceAccount =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(GcpManualDetailsDTO.builder().build())
                            .build())
            .build();

    doReturn(Arrays.asList(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(connectorRefs), eq(ambiance), eq(ngLogCallback));

    Infrastructure infrastructureSpec = K8sGcpInfrastructure.builder()
                                            .connectorRef(connectorRefs.get(0))
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
    when(cdStepHelper.getK8sInfraDelegateConfig(any(), eq(ambiance))).thenReturn(k8sInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            PdcInfrastructureOutcome.builder()
                .credentialsRef("sshKeyRef")
                .hosts(Arrays.asList("host1", "host2"))
                .hostFilter(
                    HostFilterDTO.builder().type(HostFilterType.ALL).spec(AllHostsFilterDTO.builder().build()).build())
                .infrastructureKey("0ebad79c13bd2f86edbae354b72b4d2a410f3bab")
                .build());

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
    verify(infrastructureStepHelper, atLeast(2)).getInfrastructureLogCallback(ambiance);
    verify(ngLogCallback, atLeast(2)).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecSyncAfterRbacWithPdcInfra() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig =
        PdcSshInfraDelegateConfig.builder().hosts(Collections.singleton("host1")).build();
    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                            .hostFilter(HostFilter.builder().type(HostFilterType.ALL).build())
                                            .build();
    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.SSH).build());
    when(cdStepHelper.getSshInfraDelegateConfig(any(), eq(ambiance))).thenReturn(pdcSshInfraDelegateConfig);
    doNothing()
        .when(stageExecutionHelper)
        .saveStageExecutionInfoAndPublishExecutionInfoKey(
            eq(ambiance), any(ExecutionInfoKey.class), eq(InfrastructureKind.PDC));
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            PdcInfrastructureOutcome.builder()
                .credentialsRef("sshKeyRef")
                .hosts(Arrays.asList("host1", "host2"))
                .hostFilter(
                    HostFilterDTO.builder().type(HostFilterType.ALL).spec(AllHostsFilterDTO.builder().build()).build())
                .infrastructureKey("0ebad79c13bd2f86edbae354b72b4d2a410f3bab")
                .build());
    when(instanceOutcomeHelper.saveAndGetInstancesOutcome(
             eq(ambiance), any(InfrastructureOutcome.class), any(Set.class)))
        .thenReturn(getInstancesOutcome());

    StepResponse stepResponse =
        infrastructureStep.executeSyncAfterRbac(ambiance, infrastructureSpec, StepInputPackage.builder().build(), null);

    ArgumentCaptor<SshInfraDelegateConfigOutput> pdcConfigOutputCaptor =
        ArgumentCaptor.forClass(SshInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            pdcConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    SshInfraDelegateConfigOutput pdcInfraDelegateConfigOutput = pdcConfigOutputCaptor.getValue();
    assertThat(pdcInfraDelegateConfigOutput).isNotNull();
    assertThat(pdcInfraDelegateConfigOutput.getSshInfraDelegateConfig()).isEqualTo(pdcSshInfraDelegateConfig);
    Collection<StepResponse.StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes.contains(StepResponse.StepOutcome.builder()
                                         .outcome(getInstancesOutcome())
                                         .name(OutcomeExpressionConstants.INSTANCES)
                                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                                         .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecSyncAfterRbacWithPdcInfraWinRm() {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();

    PdcWinRmInfraDelegateConfig pdcWinRmInfraDelegateConfig =
        PdcWinRmInfraDelegateConfig.builder().hosts(Collections.singleton("host1")).build();
    Infrastructure infrastructureSpec = PdcInfrastructure.builder()
                                            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                            .hostFilter(HostFilter.builder().type(HostFilterType.ALL).build())
                                            .build();
    when(executionSweepingOutputService.resolve(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT))))
        .thenReturn(EnvironmentOutcome.builder().build());
    when(outcomeService.resolve(any(), eq(RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE))))
        .thenReturn(ServiceStepOutcome.builder().type(ServiceSpecType.WINRM).build());
    when(cdStepHelper.getWinRmInfraDelegateConfig(any(), eq(ambiance))).thenReturn(pdcWinRmInfraDelegateConfig);
    when(infrastructureOutcomeProvider.getOutcome(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            PdcInfrastructureOutcome.builder()
                .credentialsRef("sshKeyRef")
                .hosts(Arrays.asList("host1", "host2"))
                .hostFilter(
                    HostFilterDTO.builder().type(HostFilterType.ALL).spec(AllHostsFilterDTO.builder().build()).build())
                .infrastructureKey("0ebad79c13bd2f86edbae354b72b4d2a410f3bab")
                .build());
    when(instanceOutcomeHelper.saveAndGetInstancesOutcome(
             eq(ambiance), any(InfrastructureOutcome.class), any(Set.class)))
        .thenReturn(getInstancesOutcome());

    StepResponse stepResponse =
        infrastructureStep.executeSyncAfterRbac(ambiance, infrastructureSpec, StepInputPackage.builder().build(), null);

    ArgumentCaptor<WinRmInfraDelegateConfigOutput> pdcConfigOutputCaptor =
        ArgumentCaptor.forClass(WinRmInfraDelegateConfigOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME),
            pdcConfigOutputCaptor.capture(), eq(StepCategory.STAGE.name()));
    WinRmInfraDelegateConfigOutput pdcInfraDelegateConfigOutput = pdcConfigOutputCaptor.getValue();
    assertThat(pdcInfraDelegateConfigOutput).isNotNull();
    assertThat(pdcInfraDelegateConfigOutput.getWinRmInfraDelegateConfig()).isEqualTo(pdcWinRmInfraDelegateConfig);
    Collection<StepResponse.StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes.contains(StepResponse.StepOutcome.builder()
                                         .outcome(getInstancesOutcome())
                                         .name(OutcomeExpressionConstants.INSTANCES)
                                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                                         .build()))
        .isTrue();
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

    Infrastructure infrastructureSpec =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField(sshKeyRef))
            .connectorRef(ParameterField.createValueField(connectorRef))
            .hostFilter(HostFilter.builder()
                            .type(HostFilterType.HOST_NAMES)
                            .spec(HostNamesFilter.builder().value(ParameterField.createValueField(hostFilters)).build())
                            .build())
            .build();

    InfraMapping expectedInfraMapping =
        PdcInfraMapping.builder()
            .credentialsRef(sshKeyRef)
            .connectorRef(connectorRef)
            .hostFilter(HostFilter.builder()
                            .type(HostFilterType.HOST_NAMES)
                            .spec(HostNamesFilter.builder().value(ParameterField.createValueField(hostFilters)).build())
                            .build())
            .build();

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

    Infrastructure infrastructureSpec =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField(sshKeyRef))
            .connectorRef(ParameterField.createValueField(connectorRef))
            .hostFilter(
                HostFilter.builder()
                    .type(HostFilterType.HOST_ATTRIBUTES)
                    .spec(
                        HostAttributesFilter.builder().value(ParameterField.createValueField(attributeFilters)).build())
                    .build())
            .build();

    InfraMapping expectedInfraMapping =
        PdcInfraMapping.builder()
            .credentialsRef(sshKeyRef)
            .connectorRef(connectorRef)
            .hostFilter(
                HostFilter.builder()
                    .type(HostFilterType.HOST_ATTRIBUTES)
                    .spec(
                        HostAttributesFilter.builder().value(ParameterField.createValueField(attributeFilters)).build())
                    .build())
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
    String connectionType = "Hostname";
    Map<String, String> tags = new HashMap<>();
    tags.put("some-tag", "some-value");
    tags.put("another-tag", "another-value");

    Infrastructure infrastructureSpec = SshWinRmAzureInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connectorRef))
                                            .credentialsRef(ParameterField.createValueField(credentialsRef))
                                            .subscriptionId(ParameterField.createValueField(subscriptionId))
                                            .resourceGroup(ParameterField.createValueField(resourceGroup))
                                            .tags(ParameterField.createValueField(tags))
                                            .hostConnectionType(ParameterField.createValueField(connectionType))
                                            .build();

    InfraMapping expectedInfraMapping = SshWinRmAzureInfraMapping.builder()
                                            .credentialsRef(credentialsRef)
                                            .connectorRef(connectorRef)
                                            .subscriptionId(subscriptionId)
                                            .resourceGroup(resourceGroup)
                                            .tags(tags)
                                            .hostConnectionType(connectionType)
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

    ParameterField param = new ParameterField<>(null, null, true, "expression1", null, true);
    k8SDirectInfrastructureBuilder.connectorRef(param);
    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(param), any());

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
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    PdcInfrastructure infrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(credentialsRef)
                                           .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                           .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(credentialsRef));

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidatePdcInfrastructureHostsAndConnectorAreExpressions() {
    ParameterField<String> credentialsRef = ParameterField.createValueField("ssh-key-ref");
    ParameterField hosts = new ParameterField<>(null, null, true, "expression1", null, true);
    ParameterField connectorRef = new ParameterField<>(null, null, true, "expression2", null, true);
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder().credentialsRef(credentialsRef).hosts(hosts).connectorRef(connectorRef).build();

    doNothing().when(infrastructureStepHelper).validateExpression(eq(credentialsRef));
    doThrow(new InvalidRequestException("Unresolved Expressions : [expression1] , [expression2]"))
        .when(infrastructureStepHelper)
        .requireOne(eq(hosts), eq(connectorRef));

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
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(credentialsRef)
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(any(), any(), any(), eq(credentialsRef));
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureConnectorIsExpression() {
    ParameterField connectorRef = new ParameterField<>(null, null, true, "expression1", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(connectorRef)
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(connectorRef), any(), any(), any());
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureSubscriptionIsExpression() {
    ParameterField subscriptionId = new ParameterField<>(null, null, true, "expression2", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(subscriptionId)
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(infrastructureStepHelper)
        .validateExpression(any(), eq(subscriptionId), any(), any());
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructureResourceGroupIsExpression() {
    ParameterField resourceGroup = new ParameterField<>(null, null, true, "expression2", null, true);
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .connectorRef(ParameterField.createValueField("connector-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(resourceGroup)
                                                     .build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(infrastructureStepHelper)
        .validateExpression(any(), any(), eq(resourceGroup), any());
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression2]");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateConnector() {
    List<ParameterField<String>> gcpSaConnectorRefs = Arrays.asList(ParameterField.createValueField("account.gcp-sa"));
    List<ParameterField<String>> gcpDelegateConnectorRefs =
        Arrays.asList(ParameterField.createValueField("account.gcp-delegate"));
    List<ParameterField<String>> missingConnectorRefs =
        Arrays.asList(ParameterField.createValueField("account.missing"));

    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
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

    doThrow(new InvalidRequestException(
                format("Connector not found for identifier : [%s]", missingConnectorRefs.get(0).getValue())))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(missingConnectorRefs), eq(ambiance), any());

    doReturn(Arrays.asList(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorServiceAccount).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(gcpSaConnectorRefs), eq(ambiance), any());

    doReturn(Arrays.asList(ConnectorInfoDTO.builder().connectorConfig(gcpConnectorInheritFromDelegate).build()))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(eq(gcpDelegateConnectorRefs), eq(ambiance), any());

    assertConnectorValidationMessage(K8sGcpInfrastructure.builder().connectorRef(missingConnectorRefs.get(0)).build(),
        "Connector not found for identifier : [account.missing]");

    assertThatCode(()
                       -> infrastructureStep.validateConnector(
                           K8sGcpInfrastructure.builder().connectorRef(gcpSaConnectorRefs.get(0)).build(), ambiance))
        .doesNotThrowAnyException();

    assertThatCode(
        ()
            -> infrastructureStep.validateConnector(
                K8sGcpInfrastructure.builder().connectorRef(gcpDelegateConnectorRefs.get(0)).build(), ambiance))
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
                                            .useClusterAdminCredentials(ParameterField.createValueField(true))
                                            .build();

    InfraMapping expectedInfraMapping = K8sAzureInfraMapping.builder()
                                            .azureConnector(connector)
                                            .namespace(namespace)
                                            .subscription(subscriptionId)
                                            .resourceGroup(resourceGroup)
                                            .cluster(cluster)
                                            .useClusterAdminCredentials(true)
                                            .build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);

    infrastructureSpec = K8sAzureInfrastructure.builder()
                             .connectorRef(ParameterField.createValueField(connector))
                             .namespace(ParameterField.createValueField(namespace))
                             .subscriptionId(ParameterField.createValueField(subscriptionId))
                             .resourceGroup(ParameterField.createValueField(resourceGroup))
                             .cluster(ParameterField.createValueField(cluster))
                             .useClusterAdminCredentials(ParameterField.createValueField(null))
                             .build();

    expectedInfraMapping = K8sAzureInfraMapping.builder()
                               .azureConnector(connector)
                               .namespace(namespace)
                               .subscription(subscriptionId)
                               .resourceGroup(resourceGroup)
                               .cluster(cluster)
                               .useClusterAdminCredentials(null)
                               .build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);

    infrastructureSpec = K8sAzureInfrastructure.builder()
                             .connectorRef(ParameterField.createValueField(connector))
                             .namespace(ParameterField.createValueField(namespace))
                             .subscriptionId(ParameterField.createValueField(subscriptionId))
                             .resourceGroup(ParameterField.createValueField(resourceGroup))
                             .cluster(ParameterField.createValueField(cluster))
                             .useClusterAdminCredentials(ParameterField.createValueField(false))
                             .build();

    expectedInfraMapping = K8sAzureInfraMapping.builder()
                               .azureConnector(connector)
                               .namespace(namespace)
                               .subscription(subscriptionId)
                               .resourceGroup(resourceGroup)
                               .cluster(cluster)
                               .useClusterAdminCredentials(false)
                               .build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateElastigroupInfrastructure() {
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.createValueField("this is content")).build();

    ElastigroupInfrastructure infrastructure =
        ElastigroupInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(""))
            .configuration(
                ElastigroupConfiguration.builder()
                    .store(StoreConfigWrapper.builder().type(StoreConfigType.INLINE).spec(storeConfig).build())
                    .build())
            .build();
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    doThrow(InvalidRequestException.class).when(infrastructureStepHelper).validateExpression(any());
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, ambiance))
        .isInstanceOf(InvalidRequestException.class);
    doNothing().when(infrastructureStepHelper).validateExpression(any());
    assertThatCode(() -> infrastructureStep.validateInfrastructure(infrastructure, ambiance))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateAsgInfrastructure() {
    AsgInfrastructure infrastructure = AsgInfrastructure.builder()
                                           .connectorRef(ParameterField.createValueField(""))
                                           .region(ParameterField.createValueField("region"))
                                           .build();
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    doThrow(InvalidRequestException.class).when(infrastructureStepHelper).validateExpression(any(), any());
    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(infrastructure, ambiance))
        .isInstanceOf(InvalidRequestException.class);
    doNothing().when(infrastructureStepHelper).validateExpression(any(), any());
    assertThatCode(() -> infrastructureStep.validateInfrastructure(infrastructure, ambiance))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateTASConnector() {
    TanzuApplicationServiceInfrastructure infrastructure =
        TanzuApplicationServiceInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("tanzuConnector"))
            .organization(ParameterField.createValueField("devTest"))
            .space(ParameterField.createValueField("devSpace"))
            .build();

    Ambiance ambiance = Mockito.mock(Ambiance.class);
    when(infrastructureStepHelper.getInfrastructureLogCallback(ambiance)).thenReturn(ngLogCallback);

    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(AwsConnectorDTO.builder().build())
                                            .connectorType(ConnectorType.TAS)
                                            .build();

    doReturn(Lists.newArrayList(connectorInfoDTO))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(any(), any(), any());

    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance))
        .isInstanceOf(InvalidRequestException.class);

    connectorInfoDTO.setConnectorConfig(TasConnectorDTO.builder().build());

    assertThatCode(() -> infrastructureStep.validateConnector(infrastructure, ambiance)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateElastigroupConnector() {
    StoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.createValueField("this is content")).build();

    ElastigroupInfrastructure infrastructure =
        ElastigroupInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(""))
            .configuration(
                ElastigroupConfiguration.builder()
                    .store(StoreConfigWrapper.builder().type(StoreConfigType.INLINE).spec(storeConfig).build())
                    .build())
            .build();
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    when(infrastructureStepHelper.getInfrastructureLogCallback(ambiance)).thenReturn(ngLogCallback);
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(AwsConnectorDTO.builder().build())
                                            .connectorType(ConnectorType.AWS)
                                            .build();

    doReturn(Lists.newArrayList(connectorInfoDTO))
        .when(infrastructureStepHelper)
        .validateAndGetConnectors(any(), any(), any());

    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance))
        .isInstanceOf(InvalidRequestException.class);
    connectorInfoDTO.setConnectorConfig(SpotConnectorDTO.builder().build());

    assertThatCode(() -> infrastructureStep.validateConnector(infrastructure, ambiance)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAwsInfrastructure() {
    SshWinRmAwsInfrastructureBuilder builder = SshWinRmAwsInfrastructure.builder();

    infrastructureStep.validateInfrastructure(builder.build(), null);
    ParameterField credentialsRef = new ParameterField<>(null, null, true, "expression1", null, true);
    builder.credentialsRef(credentialsRef).connectorRef(ParameterField.createValueField("value")).build();

    doThrow(new InvalidRequestException("Unresolved Expression : [expression1]"))
        .when(infrastructureStepHelper)
        .validateExpression(any(), eq(credentialsRef), any(), any());

    assertThatThrownBy(() -> infrastructureStep.validateInfrastructure(builder.build(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unresolved Expression : [expression1]");

    ParameterField connectorRef2 = new ParameterField<>(null, null, true, "expression2", null, true);
    builder.connectorRef(new ParameterField<>(null, null, true, "expression2", null, true))
        .credentialsRef(ParameterField.createValueField("value"))
        .build();
    doThrow(new InvalidRequestException("Unresolved Expression : [expression2]"))
        .when(infrastructureStepHelper)
        .validateExpression(eq(connectorRef2), any(), any(), any());

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
                                                   .build();

    infrastructureStep.validateInfrastructure(infrastructure, null);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCreateK8sAwsInfraMapping() {
    String namespace = "namespace";
    String connector = "connector";
    String cluster = "cluster";

    Infrastructure infrastructureSpec = K8sAwsInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connector))
                                            .namespace(ParameterField.createValueField(namespace))
                                            .cluster(ParameterField.createValueField(cluster))
                                            .build();

    InfraMapping expectedInfraMapping =
        K8sAwsInfraMapping.builder().awsConnector(connector).namespace(namespace).cluster(cluster).build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);

    infrastructureSpec = K8sAwsInfrastructure.builder()
                             .connectorRef(ParameterField.createValueField(connector))
                             .namespace(ParameterField.createValueField(namespace))
                             .cluster(ParameterField.createValueField(cluster))
                             .build();

    expectedInfraMapping =
        K8sAwsInfraMapping.builder().awsConnector(connector).namespace(namespace).cluster(cluster).build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);

    infrastructureSpec = K8sAwsInfrastructure.builder()
                             .connectorRef(ParameterField.createValueField(connector))
                             .namespace(ParameterField.createValueField(namespace))
                             .cluster(ParameterField.createValueField(cluster))
                             .build();

    expectedInfraMapping =
        K8sAwsInfraMapping.builder().awsConnector(connector).namespace(namespace).cluster(cluster).build();

    assertThat(infrastructureStep.createInfraMappingObject(infrastructureSpec)).isEqualTo(expectedInfraMapping);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testCreateGoogleFunctionsInfraMapping() {
    String region = "region";
    String connector = "connector";
    String project = "project";

    Infrastructure infrastructureSpec = GoogleFunctionsInfrastructure.builder()
                                            .connectorRef(ParameterField.createValueField(connector))
                                            .region(ParameterField.createValueField(region))
                                            .project(ParameterField.createValueField(project))
                                            .build();

    InfraMapping expectedInfraMapping =
        GoogleFunctionsInfraMapping.builder().gcpConnector(connector).region(region).project(project).build();

    InfraMapping infraMapping = infrastructureStep.createInfraMappingObject(infrastructureSpec);
    assertThat(infraMapping).isEqualTo(expectedInfraMapping);
  }

  private void assertConnectorValidationMessage(Infrastructure infrastructure, String message) {
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID).build();
    assertThatThrownBy(() -> infrastructureStep.validateConnector(infrastructure, ambiance))
        .hasMessageContaining(message);
  }

  private InstancesOutcome getInstancesOutcome() {
    return InstancesOutcome.builder()
        .instances(List.of(InstanceOutcome.builder().name("instanceName").hostName("instanceHostname").build()))
        .build();
  }
}