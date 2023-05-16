/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.api.PhaseStepExecutionData.PhaseStepExecutionDataBuilder.aPhaseStepExecutionData;
import static software.wings.service.impl.instance.InstanceSyncFlow.ITERATOR;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ORGANIZATION;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_APP_GUID_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_APP_GUID_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_0;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PCF_INSTANCE_INDEX_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ROUTE1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ROUTE2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SPACE;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.PcfAppNotFoundException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PcfDeploymentInfo;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.api.pcf.PcfDeployExecutionSummary;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.key.PcfInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.PcfDeploymentKey;
import software.wings.service.impl.PcfHelperService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.sm.PhaseStepExecutionSummary;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._441_CG_INSTANCE_SYNC)
public class PcfInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private InstanceService instanceService;
  @Mock private PcfHelperService pcfHelperService;
  @Mock private AppService appService;
  @Mock private SettingsService settingsService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock DeploymentService deploymentService;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Inject PcfInstanceHandler pcfInstanceHandler;

  @Before
  public void setUp() {
    doReturn(PcfInfrastructureMapping.builder()
                 .organization(ORGANIZATION)
                 .space(SPACE)
                 .routeMaps(asList(ROUTE1))
                 .tempRouteMap(asList(ROUTE2))
                 .computeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .uuid(INFRA_MAPPING_ID)
                 .envId(ENV_ID)
                 .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                 .serviceId(SERVICE_ID)
                 .accountId(ACCOUNT_ID)
                 .appId(APP_ID)
                 .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                 .computeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .build())
        .when(infraMappingService)
        .get(any(), any());

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(any());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build()).when(serviceResourceService).getWithDetails(any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().build()).build())
        .when(settingsService)
        .get(any());
  }

  // 2 existing PCF instances,
  // expected 1 Delete, 1 Update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances() throws Exception {
    final List<Instance> instances = getInstancesForAppAndInframapping();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    List<PcfInstanceInfo> pcfInstanceInfos = getPcfInstanceInfos();
    doReturn(pcfInstanceInfos).when(pcfHelperService).getApplicationDetails(any(), any(), any(), any(), any());

    pcfInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(INSTANCE_2_ID)).isTrue();
  }

  private List<Instance> getInstancesForAppAndInframapping() {
    return asList(Instance.builder()
                      .uuid(INSTANCE_1_ID)
                      .accountId(ACCOUNT_ID)
                      .appId(APP_ID)
                      .computeProviderId(COMPUTE_PROVIDER_NAME)
                      .appName(APP_NAME)
                      .envId(ENV_ID)
                      .envName(ENV_NAME)
                      .envType(EnvironmentType.PROD)
                      .infraMappingId(INFRA_MAPPING_ID)
                      .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
                      .pcfInstanceKey(PcfInstanceKey.builder().id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0).build())
                      .instanceType(InstanceType.PCF_INSTANCE)
                      .instanceInfo(PcfInstanceInfo.builder()
                                        .organization(ORGANIZATION)
                                        .space(SPACE)
                                        .pcfApplicationName(APP_NAME_1)
                                        .pcfApplicationGuid(PCF_APP_GUID_1)
                                        .instanceIndex(PCF_INSTANCE_INDEX_0)
                                        .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                                        .build())
                      .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.PCF_PCF.getName())
            .pcfInstanceKey(PcfInstanceKey.builder().id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1).build())
            .instanceType(InstanceType.PCF_INSTANCE)
            .instanceInfo(PcfInstanceInfo.builder()
                              .organization(ORGANIZATION)
                              .space(SPACE)
                              .pcfApplicationName(APP_NAME_1)
                              .pcfApplicationGuid(PCF_APP_GUID_1)
                              .instanceIndex(PCF_INSTANCE_INDEX_1)
                              .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1)
                              .build())
            .build());
  }

  private List<PcfInstanceInfo> getPcfInstanceInfos() {
    return asList(PcfInstanceInfo.builder()
                      .organization(ORGANIZATION)
                      .space(SPACE)
                      .pcfApplicationName(APP_NAME_1)
                      .pcfApplicationGuid(PCF_APP_GUID_1)
                      .instanceIndex(PCF_INSTANCE_INDEX_0)
                      .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                      .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_1)
            .pcfApplicationGuid(PCF_APP_GUID_1)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_2)
            .build());
  }

  /**
   * Add 3 new instance for new deployment
   * @throws Exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_2() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());

    doReturn(pageResponse).when(instanceService).list(any());

    List<PcfInstanceInfo> pcfInstanceInfos = asList(PcfInstanceInfo.builder()
                                                        .organization(ORGANIZATION)
                                                        .space(SPACE)
                                                        .pcfApplicationName(APP_NAME_2)
                                                        .pcfApplicationGuid(PCF_APP_GUID_2)
                                                        .instanceIndex(PCF_INSTANCE_INDEX_0)
                                                        .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0)
                                                        .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_1)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1)
            .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2)
            .build());

    doReturn(pcfInstanceInfos).when(pcfHelperService).getApplicationDetails(any(), any(), any(), any(), any());

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    pcfInstanceHandler.handleNewDeployment(
        asList(DeploymentSummary.builder()
                   .deploymentInfo(
                       PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                   .accountId(ACCOUNT_ID)
                   .infraMappingId(INFRA_MAPPING_ID)
                   .workflowExecutionId("workfloeExecution_1")
                   .stateExecutionInstanceId("stateExecutionInstanceId")
                   .artifactName("new")
                   .artifactBuildNum("1")
                   .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    Set<String> expectedKeys = new HashSet<>();
    expectedKeys.addAll(asList(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0, PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1,
        PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2));

    assertThat(expectedKeys.contains(capturedInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");

    assertThat(expectedKeys.contains(capturedInstances.get(1).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(1).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(1).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");

    assertThat(expectedKeys.contains(capturedInstances.get(2).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(2).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(2).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("new");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_rollback() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());

    doReturn(pageResponse).when(instanceService).list(any());

    List<PcfInstanceInfo> pcfInstanceInfos = asList(PcfInstanceInfo.builder()
                                                        .organization(ORGANIZATION)
                                                        .space(SPACE)
                                                        .pcfApplicationName(APP_NAME_2)
                                                        .pcfApplicationGuid(PCF_APP_GUID_2)
                                                        .instanceIndex(PCF_INSTANCE_INDEX_0)
                                                        .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0)
                                                        .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_1)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1)
            .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_2)
            .pcfApplicationGuid(PCF_APP_GUID_2)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2)
            .build());

    doReturn(pcfInstanceInfos).when(pcfHelperService).getApplicationDetails(any(), any(), any(), any(), any());

    doReturn(
        Optional.of(DeploymentSummary.builder()
                        .deploymentInfo(
                            PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                        .accountId(ACCOUNT_ID)
                        .infraMappingId(INFRA_MAPPING_ID)
                        .workflowExecutionId("workfloeExecution_1")
                        .stateExecutionInstanceId("stateExecutionInstanceId")
                        .artifactBuildNum("1")
                        .artifactName("old")
                        .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();

    pcfInstanceHandler.handleNewDeployment(
        asList(DeploymentSummary.builder()
                   .deploymentInfo(
                       PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_2).build())
                   .accountId(ACCOUNT_ID)
                   .infraMappingId(INFRA_MAPPING_ID)
                   .workflowExecutionId("workfloeExecution_1")
                   .stateExecutionInstanceId("stateExecutionInstanceId")
                   .artifactBuildNum("2")
                   .artifactName("new")
                   .build()),
        true, onDemandRollbackInfo);

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    Set<String> expectedKeys = new HashSet<>();
    expectedKeys.addAll(asList(PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_0, PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_1,
        PCF_APP_GUID_2 + ":" + PCF_INSTANCE_INDEX_2));

    assertThat(expectedKeys.contains(capturedInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(0).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("old");

    assertThat(expectedKeys.contains(capturedInstances.get(1).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(1).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(1).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(1).getLastArtifactName()).isEqualTo("old");

    assertThat(expectedKeys.contains(capturedInstances.get(2).getPcfInstanceKey().getId())).isTrue();
    assertThat(capturedInstances.get(2).getInstanceType()).isEqualTo(InstanceType.PCF_INSTANCE);
    assertThat(capturedInstances.get(2).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(2).getLastArtifactName()).isEqualTo("old");
  }

  // 2 instances will be updated in DB and 1 new instance will be added in DB
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleOnDemandRollbackDeployment() throws PcfAppNotFoundException {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());
    String rollbackExecutionId = "rollbackExecutionId";
    String stateExecutionInstanceId = "stateExecutionInstanceId";
    String workflowExecutionId = "workfloeExecution_1";

    doReturn(pageResponse).when(instanceService).list(any());
    doReturn(getPcfInstanceInfosForNewDeployment())
        .when(pcfHelperService)
        .getApplicationDetails(any(), any(), any(), any(), any());
    doReturn(getInstancesForAppAndInframapping()).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    OnDemandRollbackInfo onDemandRollbackInfo =
        OnDemandRollbackInfo.builder().onDemandRollback(true).rollbackExecutionId(rollbackExecutionId).build();

    pcfInstanceHandler.handleNewDeployment(
        Collections.singletonList(
            DeploymentSummary.builder()
                .deploymentInfo(
                    PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_1).build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId(workflowExecutionId)
                .stateExecutionInstanceId(stateExecutionInstanceId)
                .artifactName("new")
                .artifactBuildNum("1")
                .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> updatedCaptorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(2)).saveOrUpdate(updatedCaptorInstance.capture());

    ArgumentCaptor<Instance> savedCaptorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).save(savedCaptorInstance.capture());

    Set<String> updatedExpectedKeys =
        new HashSet<>(asList(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0, PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1));
    Set<String> savedExpectedKeys =
        new HashSet<>(Collections.singletonList(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_2));

    List<Instance> updatedInstances = updatedCaptorInstance.getAllValues();
    assertThat(updatedExpectedKeys.contains(updatedInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(updatedExpectedKeys.contains(updatedInstances.get(1).getPcfInstanceKey().getId())).isTrue();

    List<Instance> saveInstances = savedCaptorInstance.getAllValues();
    assertThat(savedExpectedKeys.contains(saveInstances.get(0).getPcfInstanceKey().getId())).isTrue();
  }

  // 3 new instances will be added in DB
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleOnDemandRollbackDeploymentWithOnlyNewInstances() throws PcfAppNotFoundException {
    PageResponse<Instance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList());
    String rollbackExecutionId = "rollbackExecutionId";
    String stateExecutionInstanceId = "stateExecutionInstanceId";
    String workflowExecutionId = "workfloeExecution_1";

    doReturn(pageResponse).when(instanceService).list(any());
    doReturn(getPcfInstanceInfosForNewDeployment())
        .when(pcfHelperService)
        .getApplicationDetails(any(), any(), any(), any(), any());

    OnDemandRollbackInfo onDemandRollbackInfo =
        OnDemandRollbackInfo.builder().onDemandRollback(true).rollbackExecutionId(rollbackExecutionId).build();

    pcfInstanceHandler.handleNewDeployment(
        Collections.singletonList(
            DeploymentSummary.builder()
                .deploymentInfo(
                    PcfDeploymentInfo.builder().applicationGuild("GUID").applicationName(APP_NAME_1).build())
                .accountId(ACCOUNT_ID)
                .infraMappingId(INFRA_MAPPING_ID)
                .workflowExecutionId(workflowExecutionId)
                .stateExecutionInstanceId(stateExecutionInstanceId)
                .artifactName("new")
                .artifactBuildNum("1")
                .build()),
        false, onDemandRollbackInfo);

    ArgumentCaptor<Instance> savedCaptorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(3)).save(savedCaptorInstance.capture());

    Set<String> savedExpectedKeys = new HashSet<>(asList(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0,
        PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1, PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_2));

    List<Instance> saveInstances = savedCaptorInstance.getAllValues();
    assertThat(savedExpectedKeys.contains(saveInstances.get(0).getPcfInstanceKey().getId())).isTrue();
    assertThat(savedExpectedKeys.contains(saveInstances.get(1).getPcfInstanceKey().getId())).isTrue();
    assertThat(savedExpectedKeys.contains(saveInstances.get(2).getPcfInstanceKey().getId())).isTrue();
  }

  private List<PcfInstanceInfo> getPcfInstanceInfosForNewDeployment() {
    return asList(PcfInstanceInfo.builder()
                      .organization(ORGANIZATION)
                      .space(SPACE)
                      .pcfApplicationName(APP_NAME_1)
                      .pcfApplicationGuid(PCF_APP_GUID_1)
                      .instanceIndex(PCF_INSTANCE_INDEX_0)
                      .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_0)
                      .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_1)
            .pcfApplicationGuid(PCF_APP_GUID_1)
            .instanceIndex(PCF_INSTANCE_INDEX_1)
            .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_1)
            .build(),
        PcfInstanceInfo.builder()
            .organization(ORGANIZATION)
            .space(SPACE)
            .pcfApplicationName(APP_NAME_1)
            .pcfApplicationGuid(PCF_APP_GUID_1)
            .instanceIndex(PCF_INSTANCE_INDEX_2)
            .id(PCF_APP_GUID_1 + ":" + PCF_INSTANCE_INDEX_2)
            .build());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetStatusWhenSuccessResponse() {
    InfrastructureMapping infrastructureMapping = mock(InfrastructureMapping.class);
    when(infrastructureMapping.getUuid()).thenReturn("ID");

    CfCommandExecutionResponse cfCommandExecutionResponse = getPcfCommandExecutionResponse(SUCCESS);
    when(pcfHelperService.getInstanceCount(cfCommandExecutionResponse)).thenReturn(1);

    Status status = pcfInstanceHandler.getStatus(infrastructureMapping, cfCommandExecutionResponse);
    assertTrue(status.isSuccess());
    assertTrue(status.isRetryable());
    assertTrue(isEmpty(status.getErrorMessage()));

    when(pcfHelperService.getInstanceCount(cfCommandExecutionResponse)).thenReturn(0);
    status = pcfInstanceHandler.getStatus(infrastructureMapping, cfCommandExecutionResponse);
    assertTrue(status.isSuccess());
    assertFalse(status.isRetryable());
    assertTrue(isEmpty(status.getErrorMessage()));
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testGetStatusWhenSuccessFailure() throws Exception {
    InfrastructureMapping infrastructureMapping = mock(InfrastructureMapping.class);
    when(infrastructureMapping.getUuid()).thenReturn("ID");

    CfCommandExecutionResponse cfCommandExecutionResponse = getPcfCommandExecutionResponse(FAILURE);

    Status status = pcfInstanceHandler.getStatus(infrastructureMapping, cfCommandExecutionResponse);
    assertFalse(status.isSuccess());
    assertTrue(status.isRetryable());

    when(pcfHelperService.validatePcfInstanceSyncResponse(any(), any(), any(), any()))
        .thenThrow(PcfAppNotFoundException.class);
    status = pcfInstanceHandler.getStatus(infrastructureMapping, cfCommandExecutionResponse);
    assertFalse(status.isSuccess());
    assertFalse(status.isRetryable());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetApplicationInfoFailure() throws PcfAppNotFoundException {
    String accountId = "kmpySmUISimoRrJL6NL73w";
    String appId = "7PicE_WxSs6s1W04zxzEEQ";
    String appName = "TestApp";

    Instance instance1 = Instance.builder()
                             .accountId(accountId)
                             .appId(appId)
                             .appName(appName)
                             .instanceInfo(PcfInstanceInfo.builder().pcfApplicationName(appName).build())
                             .build();
    Instance instance2 = Instance.builder()
                             .accountId(accountId)
                             .appId(appId)
                             .appName(appName)
                             .instanceInfo(PcfInstanceInfo.builder().pcfApplicationName(appName).build())
                             .build();
    List<Instance> instances = asList(instance1, instance2);
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    doThrow(new PcfAppNotFoundException("App not found"))
        .when(pcfHelperService)
        .getApplicationDetails(any(), any(), any(), any(), any());

    assertThatThrownBy(() -> pcfInstanceHandler.syncInstances("appId", "infraMappingId", ITERATOR))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Application not found for PCF application Name: [TestApp] with InfraMappingId: [infraMappingId], Failed to perform instance sync for this PCF Application with Exception: [PcfAppNotFoundException: App not found]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    String appName = "appName";
    String appId = "appId";
    String stateExecutionInstanceId = "instanceId";
    PhaseStepExecutionData phaseStepExecutionData = null;
    Optional<List<DeploymentInfo>> deploymentInfoList;

    // PhaseStepExecutionSummary is null
    phaseStepExecutionData = aPhaseStepExecutionData().withPhaseStepExecutionSummary(null).build();
    deploymentInfoList =
        pcfInstanceHandler.getDeploymentInfo(null, phaseStepExecutionData, null, null, stateExecutionInstanceId, null);
    assertThat(deploymentInfoList.isPresent()).isFalse();

    // StepExecutionSummaryList is null
    phaseStepExecutionData =
        aPhaseStepExecutionData().withPhaseStepExecutionSummary(getPhaseStepExecutionSummary(null)).build();
    deploymentInfoList =
        pcfInstanceHandler.getDeploymentInfo(null, phaseStepExecutionData, null, null, stateExecutionInstanceId, null);
    assertThat(deploymentInfoList.isPresent()).isFalse();

    // PhaseStepExecutionSummary populated with instances data
    phaseStepExecutionData =
        aPhaseStepExecutionData()
            .withPhaseStepExecutionSummary(getPhaseStepExecutionSummary(getPcfDeployExecutionSummary(appName, appId)))
            .build();

    deploymentInfoList =
        pcfInstanceHandler.getDeploymentInfo(null, phaseStepExecutionData, null, null, stateExecutionInstanceId, null);

    boolean present = deploymentInfoList.isPresent();
    assertThat(present).isTrue();

    List<DeploymentInfo> deploymentInfos = deploymentInfoList.get();
    assertThat(deploymentInfos.size()).isEqualTo(1);

    DeploymentInfo deploymentInfo = deploymentInfos.get(0);
    assertThat(deploymentInfo instanceof PcfDeploymentInfo).isTrue();
    PcfDeploymentInfo pcfDeploymentInfo = (PcfDeploymentInfo) deploymentInfo;
    assertThat(pcfDeploymentInfo).isNotNull();
    assertThat(pcfDeploymentInfo.getApplicationGuild()).isEqualTo(appId);
    assertThat(pcfDeploymentInfo.getApplicationName()).isEqualTo(appName);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGenerateDeploymentKey() {
    String appName = "appName";
    String appId = "appId";
    PcfDeploymentInfo deploymentInfo =
        PcfDeploymentInfo.builder().applicationGuild(appId).applicationName(appName).build();
    DeploymentKey deploymentKey = pcfInstanceHandler.generateDeploymentKey(deploymentInfo);

    assertThat(deploymentKey).isNotNull();
    assertThat(deploymentKey instanceof PcfDeploymentKey).isTrue();
    PcfDeploymentKey pcfDeploymentKey = (PcfDeploymentKey) deploymentKey;
    assertThat(pcfDeploymentKey.getApplicationName()).isEqualTo(appName);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResponseFromPerpetualTask() throws PcfAppNotFoundException {
    CfCommandExecutionResponse cfCommandExecutionResponse = CfCommandExecutionResponse.builder().build();
    cfCommandExecutionResponse.setPcfCommandResponse(CfInstanceSyncResponse.builder().name(APP_NAME_1).build());
    PcfInfrastructureMapping infraMapping =
        PcfInfrastructureMapping.builder().appId(APP_ID).uuid(INFRA_MAPPING_ID).build();

    final List<Instance> instances = getInstancesForAppAndInframapping();
    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    List<PcfInstanceInfo> pcfInstanceInfos = getPcfInstanceInfos();
    doReturn(pcfInstanceInfos).when(pcfHelperService).getApplicationDetails(any(), any(), any(), any(), any());
    doReturn(true).when(featureFlagService).isEnabled(any(), any());

    pcfInstanceHandler.processInstanceSyncResponseFromPerpetualTask(infraMapping, cfCommandExecutionResponse);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(INSTANCE_2_ID)).isTrue();
  }

  private PhaseStepExecutionSummary getPhaseStepExecutionSummary(PcfDeployExecutionSummary stepExecutionSummary) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = new PhaseStepExecutionSummary();
    phaseStepExecutionSummary.setStepExecutionSummaryList(Collections.singletonList(stepExecutionSummary));
    return phaseStepExecutionSummary;
  }

  private PcfDeployExecutionSummary getPcfDeployExecutionSummary(String appName, String appId) {
    return PcfDeployExecutionSummary.builder()
        .instaceData(Collections.singletonList(CfServiceData.builder()
                                                   .name(appName)
                                                   .id(appId)
                                                   .previousCount(2)
                                                   .desiredCount(4)
                                                   .disableAutoscalarPerformed(true)
                                                   .build()))
        .releaseName("releaseName")
        .build();
  }

  private CfCommandExecutionResponse getPcfCommandExecutionResponse(CommandExecutionStatus commandExecutionStatus) {
    CfInstanceSyncResponse cfInstanceSyncResponse =
        CfInstanceSyncResponse.builder().commandExecutionStatus(commandExecutionStatus).build();

    return CfCommandExecutionResponse.builder()
        .pcfCommandResponse(cfInstanceSyncResponse)
        .commandExecutionStatus(commandExecutionStatus)
        .build();
  }
}
