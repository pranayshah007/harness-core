/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.infra.InfraDefinitionTestConstants.INFRA_DEFINITION_NAME;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.sm.states.k8s.K8sResourcesSweepingOutput.K8S_RESOURCES_SWEEPING_OUTPUT;
import static software.wings.sm.states.k8s.K8sStateHelper.fetchTagsFromK8sCloudProvider;
import static software.wings.sm.states.k8s.K8sTestConstants.VALUES_YAML_WITH_ARTIFACT_REFERENCE;
import static software.wings.sm.states.k8s.K8sTestConstants.VALUES_YAML_WITH_COMMENTED_ARTIFACT_REFERENCE;
import static software.wings.sm.states.k8s.K8sTestConstants.VALUES_YAML_WITH_NO_ARTIFACT_REFERENCE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.K8sPodSyncException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.CommandExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class K8sStateHelperTest extends WingsBaseTest {
  @Mock private EnvironmentService environmentService;
  @Mock private DelegateService delegateService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ExecutionContext context;

  @Inject @InjectMocks private K8sStateHelper k8sStateHelper;

  @Inject KryoSerializer kryoSerializer;
  @Inject private HPersistence persistence;

  private static final String APPLICATION_MANIFEST_ID = "AppManifestId";

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDoManifestsUseArtifact() {
    ApplicationManifest applicationManifest = createApplicationManifest();
    ManifestFile manifestFile = createManifestFile();

    persistence.save(applicationManifest);
    persistence.save(manifestFile);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(createGCPInfraMapping());

    // Service K8S_MANIFEST
    boolean result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isTrue();

    manifestFile.setFileContent(VALUES_YAML_WITH_COMMENTED_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    manifestFile.setFileContent(VALUES_YAML_WITH_NO_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    // Service VALUES
    applicationManifest.setKind(AppManifestKind.VALUES);
    persistence.save(applicationManifest);
    manifestFile.setFileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isTrue();

    manifestFile.setFileContent(VALUES_YAML_WITH_COMMENTED_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    manifestFile.setFileContent(VALUES_YAML_WITH_NO_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    manifestFile.setFileContent(" ");
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    // Env VALUES
    applicationManifest.setServiceId(null);
    applicationManifest.setEnvId(ENV_ID);
    applicationManifest.setKind(AppManifestKind.VALUES);
    persistence.save(applicationManifest);
    manifestFile.setFileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isTrue();

    manifestFile.setFileContent(VALUES_YAML_WITH_COMMENTED_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    manifestFile.setFileContent(VALUES_YAML_WITH_NO_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    // Env Service VALUES
    applicationManifest.setServiceId(SERVICE_ID);
    persistence.save(applicationManifest);
    manifestFile.setFileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isTrue();

    manifestFile.setFileContent(VALUES_YAML_WITH_COMMENTED_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    manifestFile.setFileContent(VALUES_YAML_WITH_NO_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
    assertThat(result).isFalse();

    try {
      when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(null);
      k8sStateHelper.doManifestsUseArtifact(APP_ID, INFRA_MAPPING_ID);
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("Infra mapping not found for appId APP_ID infraMappingId INFRA_MAPPING_ID");
    }

    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().name(INFRA_DEFINITION_NAME).envId(ENV_ID).build();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);
    manifestFile.setFileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE);
    persistence.save(manifestFile);
    result = k8sStateHelper.doManifestsUseArtifact(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID);
    assertThat(result).isTrue();

    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(null);
    try {
      k8sStateHelper.doManifestsUseArtifact(APP_ID, SERVICE_ID, INFRA_DEFINITION_ID);
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("Infra definition not found for appId APP_ID infraDefinitionId INFRA_DEFINITION_ID");
    }
  }

  @NotNull
  private ApplicationManifest createApplicationManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .storeType(StoreType.Local)
                                                  .serviceId(SERVICE_ID)
                                                  .build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setUuid(APPLICATION_MANIFEST_ID);
    return applicationManifest;
  }

  private ManifestFile createManifestFile() {
    return ManifestFile.builder()
        .applicationManifestId(APPLICATION_MANIFEST_ID)
        .fileName(values_filename)
        .fileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE)
        .build();
  }

  private InfrastructureMapping createGCPInfraMapping() {
    return aGcpKubernetesInfrastructureMapping()
        .withNamespace("default")
        .withAppId(APP_ID)
        .withEnvId(ENV_ID)
        .withServiceId(SERVICE_ID)
        .withServiceTemplateId(TEMPLATE_ID)
        .withComputeProviderType(GCP.name())
        .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
        .withUuid(INFRA_MAPPING_ID)
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchTagsFromK8sCloudProvider() {
    List<String> tags = fetchTagsFromK8sCloudProvider(null);
    assertThat(tags).isEmpty();

    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder().build();
    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isEmpty();

    SettingAttribute settingAttribute =
        aSettingAttribute().withAccountId(ACCOUNT_ID).withValue(AwsConfig.builder().build()).build();
    containerServiceParams.setSettingAttribute(settingAttribute.toDTO());
    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isEmpty();

    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isEmpty();

    settingAttribute.setValue(KubernetesClusterConfig.builder().build());
    containerServiceParams.setSettingAttribute(settingAttribute.toDTO());
    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isEmpty();

    settingAttribute.setValue(KubernetesClusterConfig.builder().useKubernetesDelegate(true).build());
    containerServiceParams.setSettingAttribute(settingAttribute.toDTO());
    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isEmpty();

    settingAttribute.setValue(
        KubernetesClusterConfig.builder().useKubernetesDelegate(true).delegateName("delegateName").build());
    containerServiceParams.setSettingAttribute(settingAttribute.toDTO());
    tags = fetchTagsFromK8sCloudProvider(containerServiceParams);
    assertThat(tags).isNotEmpty();
    assertThat(tags).contains("delegateName");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchTagsFromK8sTaskParamsEmpty() {
    K8sTaskParameters nullK8sClusterConfig = K8sRollingDeployTaskParameters.builder().build();

    assertThat(K8sStateHelper.fetchTagsFromK8sTaskParams(null)).isEmpty();
    assertThat(K8sStateHelper.fetchTagsFromK8sTaskParams(nullK8sClusterConfig)).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateManifestsArtifactVariableNamesInfraDefinition() {
    ApplicationManifest applicationManifest = createApplicationManifest();
    ManifestFile manifestFile = createManifestFile();
    persistence.save(applicationManifest);
    persistence.save(manifestFile);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(createGCPInfraMapping());
    Set<String> serviceArtifactVariableNames = new HashSet<>();

    try {
      k8sStateHelper.updateManifestsArtifactVariableNamesInfraDefinition(
          APP_ID, INFRA_DEFINITION_ID, emptySet(), SERVICE_ID);
    } catch (Exception ex) {
      assertThatExceptionOfType(InvalidRequestException.class);
      assertThat(ex.getMessage())
          .isEqualTo("Infra Definition not found for appId APP_ID infraDefinitionId INFRA_DEFINITION_ID");
    }

    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID))
        .thenReturn(InfrastructureDefinition.builder().envId(ENV_ID).build());
    serviceArtifactVariableNames = new HashSet<>();
    k8sStateHelper.updateManifestsArtifactVariableNamesInfraDefinition(
        APP_ID, INFRA_DEFINITION_ID, serviceArtifactVariableNames, SERVICE_ID);
    assertThat(serviceArtifactVariableNames).contains("artifact");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTagsInGetPodList() throws Exception {
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().accountId(ACCOUNT_ID).build();
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);

    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder()
                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                            .k8sTaskResponse(K8sInstanceSyncResponse.builder().build())
                                            .build();
    when(delegateService.executeTaskV2(any())).thenReturn(response);
    Environment env = new Environment();
    env.setEnvironmentType(EnvironmentType.PROD);
    when(environmentService.get(nullable(String.class), nullable(String.class))).thenReturn(env);

    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(SETTING_ID)
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(AzureConfig.builder().build())
                                            .build();
    persistence.save(settingAttribute);

    k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).executeTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getTags()).isEmpty();

    k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).executeTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask.getTags()).isEmpty();

    doReturn(K8sClusterConfig.builder().cloudProvider(KubernetesClusterConfig.builder().build()).build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(), any());
    persistence.save(settingAttribute);
    k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(3)).executeTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask.getTags()).isEmpty();

    doReturn(K8sClusterConfig.builder()
                 .cloudProvider(KubernetesClusterConfig.builder()
                                    .useKubernetesDelegate(true)
                                    .delegateSelectors(new HashSet<>(Collections.singletonList("delegateSelectors")))
                                    .build())
                 .build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(), any());
    persistence.save(settingAttribute);
    k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(4)).executeTaskV2(captor.capture());
    delegateTask = captor.getValue();
    K8sInstanceSyncTaskParameters syncTaskParameters =
        (K8sInstanceSyncTaskParameters) delegateTask.getData().getParameters()[0];

    KubernetesClusterConfig clusterConfig =
        (KubernetesClusterConfig) syncTaskParameters.getK8sClusterConfig().getCloudProvider();
    assertThat(clusterConfig.getDelegateSelectors()).contains("delegateSelectors");

    when(delegateService.executeTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage("ErrorMessage").build());
    try {
      k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    } catch (Exception ex) {
      assertThatExceptionOfType(K8sPodSyncException.class);
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch PodList for release releaseName. Error: ErrorMessage");
    }

    when(delegateService.executeTaskV2(any()))
        .thenReturn(RemoteMethodReturnValueData.builder()
                        .returnValue("returnValue")
                        .exception(new K8sPodSyncException("k8sPodSyncException"))
                        .build());
    try {
      k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    } catch (Exception ex) {
      assertThatExceptionOfType(K8sPodSyncException.class);
      assertThat(ex.getMessage())
          .isEqualTo(
              "Failed to fetch PodList for release releaseName. Exception: RemoteMethodReturnValueData(returnValue=returnValue, exception=io.harness.exception.K8sPodSyncException: k8sPodSyncException)");
    }

    when(delegateService.executeTaskV2(any())).thenReturn(HelmValuesFetchTaskResponse.builder().build());
    try {
      k8sStateHelper.fetchPodList(infrastructureMapping, "default", "releaseName");
    } catch (Exception ex) {
      assertThatExceptionOfType(K8sPodSyncException.class);
      assertThat(ex.getMessage())
          .isEqualTo(
              "Failed to fetch PodList for release releaseName. Unknown return type software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse");
    }
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveResourcesToSweepingOutputWhenPreviousSweepingOutputInstanceFound() {
    List<KubernetesResource> resources = new ArrayList<>();
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    doReturn("APP_ID").when(context).getAppId();
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());
    doReturn(SweepingOutputInstance.builder().uuid("UUID").value(K8sResourcesSweepingOutput.builder().build()).build())
        .when(sweepingOutputService)
        .find(any());

    k8sStateHelper.saveResourcesToSweepingOutput(context, resources, "test");

    verify(sweepingOutputService, times(1)).deleteById("APP_ID", "UUID");
    verify(sweepingOutputService, times(1)).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo(K8S_RESOURCES_SWEEPING_OUTPUT);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveResourcesToSweepingOutputWhenNoPreviousSweepingOutputInstanceFound() {
    List<KubernetesResource> resources = new ArrayList<>();
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    doReturn("APP_ID").when(context).getAppId();
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());
    doReturn(null).when(sweepingOutputService).find(any());

    k8sStateHelper.saveResourcesToSweepingOutput(context, resources, "test");

    verify(sweepingOutputService, times(0)).deleteById("APP_ID", "UUID");
    verify(sweepingOutputService, times(1)).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo(K8S_RESOURCES_SWEEPING_OUTPUT);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetResourcesFromSweepingOutput() {
    List<KubernetesResource> resources = new ArrayList<>();
    resources.add(KubernetesResource.builder().build());

    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()
                 .uuid("UUID")
                 .value(K8sResourcesSweepingOutput.builder().stateType("test").resources(resources).build())

                 .build())
        .when(sweepingOutputService)
        .find(any());

    List<KubernetesResource> resourcesFromSweepingOutput =
        k8sStateHelper.getResourcesFromSweepingOutput(context, "test");
    assertThat(resourcesFromSweepingOutput).isEqualTo(resources);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetResourcesFromSweepingOutputWhenSweepingOutputInstanceIsNull() {
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(null).when(sweepingOutputService).find(any());

    List<KubernetesResource> resourcesFromSweepingOutput =
        k8sStateHelper.getResourcesFromSweepingOutput(context, "test");
    assertThat(resourcesFromSweepingOutput).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetResourcesFromSweepingOutputWhenK8sResourcesSweepingOutputIsNull() {
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder().uuid("UUID").value(null).build()).when(sweepingOutputService).find(any());

    List<KubernetesResource> resourcesFromSweepingOutput =
        k8sStateHelper.getResourcesFromSweepingOutput(context, "test");
    assertThat(resourcesFromSweepingOutput).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitListFeatureEnabled() {
    List<CommandUnit> commandUnits = k8sStateHelper.getCommandUnits(false, "accountId", false, false, true);
    assertThat(commandUnits).isNotEmpty();
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", false, false, true);
    assertThat(commandUnits).isNotEmpty();
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(commandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", false, true, true);
    assertThat(commandUnits.size()).isEqualTo(2);
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(commandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", true, false, true);
    assertThat(commandUnits).isNotEmpty();
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(commandUnits.get(commandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitListFeatureDisabled() {
    List<CommandUnit> commandUnits = k8sStateHelper.getCommandUnits(false, "accountId", false, false, true);
    assertThat(commandUnits.size()).isEqualTo(5);
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(commandUnits.get(commandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", false, false, true);
    assertThat(commandUnits.size()).isEqualTo(6);
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(commandUnits.get(commandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", false, true, true);
    assertThat(commandUnits.size()).isEqualTo(2);
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(commandUnits.get(commandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.Init);

    commandUnits = k8sStateHelper.getCommandUnits(true, "accountId", true, false, true);
    assertThat(commandUnits.size()).isEqualTo(5);
    assertThat(commandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(commandUnits.get(commandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);
  }
}
