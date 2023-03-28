/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.filters;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilterCreatorMergeServiceTest extends PipelineServiceTestBase {
  private static String ACCOUNT_ID = "accountId";
  private static String PROJECT_ID = "projectId";
  private static String ORG_ID = "orgId";
  private static String IDENTIFIER = "pipeline";
  private static String gitConnectorRef = "gitConnector";

  private static final String pipelineYaml = "pipeline:\n"
      + "  identifier: p1\n"
      + "  name: pipeline1\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: managerDeployment\n"
      + "        type: deployment\n"
      + "        name: managerDeployment\n"
      + "        spec:\n"
      + "          service:\n"
      + "            identifier: manager\n"
      + "            name: manager\n"
      + "            serviceDefinition:\n"
      + "              type: k8s\n"
      + "              spec:\n"
      + "                field11: value1\n"
      + "                field12: value2\n"
      + "          infrastructure:\n"
      + "            environment:\n"
      + "              identifier: stagingInfra\n"
      + "              type: preProduction\n"
      + "              name: staging\n"
      + "            infrastructureDefinition:\n"
      + "              type: k8sDirect\n"
      + "              spec:\n"
      + "                connectorRef: pEIkEiNPSgSUsbWDDyjNKw\n"
      + "                namespace: harness\n"
      + "                releaseName: testingqa\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: managerCanary\n"
      + "                  type: k8sCanary\n"
      + "                  spec:\n"
      + "                    field11: value1\n"
      + "                    field12: value2\n"
      + "              - step:\n"
      + "                  identifier: managerVerify\n"
      + "                  type: appdVerify\n"
      + "                  spec:\n"
      + "                    field21: value1\n"
      + "                    field22: value2\n"
      + "              - step:\n"
      + "                  identifier: managerRolling\n"
      + "                  type: k8sRolling\n"
      + "                  spec:\n"
      + "                    field31: value1\n"
      + "                    field32: value2";

  PlanCreationServiceGrpc.PlanCreationServiceBlockingStub planCreationServiceBlockingStub;
  @Mock PmsSdkHelper pmsSdkHelper;
  @Mock PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock TriggeredByHelper triggeredByHelper;
  @Mock GitSyncSdkService gitSyncSdkService;
  FilterCreatorMergeService filterCreatorMergeService;

  @Before
  public void init() {
    filterCreatorMergeService = spy(new FilterCreatorMergeService(pmsSdkHelper, pipelineSetupUsageHelper,
        pmsGitSyncHelper, pmsPipelineTemplateHelper, gitSyncSdkService, principalInfoHelper, triggeredByHelper));
    when(
        pmsPipelineTemplateHelper.getTemplateReferencesForGivenYaml(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new ArrayList<>());
  }

  @After
  public void verifyInteractions() {
    verifyNoMoreInteractions(pmsSdkHelper);
    verifyNoMoreInteractions(pipelineSetupUsageHelper);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineInfo() throws IOException {
    Map<String, Set<String>> stepToSupportedTypes = new HashMap<>();
    stepToSupportedTypes.put("pipeline", Collections.singleton("__any__"));
    Map<String, PlanCreatorServiceInfo> sdkInstances = new HashMap<>();
    when(pmsSdkHelper.getServices()).thenReturn(sdkInstances);

    doReturn(
        FilterCreationBlobResponse.newBuilder().addReferredEntities(EntityDetailProtoDTO.newBuilder().build()).build())
        .when(filterCreatorMergeService)
        .obtainFiltersRecursively(any(), any(), any(), any());
    doNothing().when(pipelineSetupUsageHelper).deleteExistingSetupUsages(ACCOUNT_ID, ORG_ID, PROJECT_ID, IDENTIFIER);

    doReturn(ExecutionPrincipalInfo.newBuilder().build())
        .when(principalInfoHelper)
        .getPrincipalInfoFromSecurityContext();
    doReturn(TriggeredBy.newBuilder().build()).when(triggeredByHelper).getFromSecurityContext();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml(pipelineYaml)
                                        .accountId(ACCOUNT_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .identifier(IDENTIFIER)
                                        .build();
    doReturn(Collections.singletonList(EntityDetailProtoDTO.newBuilder().build()))
        .when(pmsPipelineTemplateHelper)
        .getTemplateReferencesForGivenYaml(anyString(), anyString(), anyString(), anyString());
    FilterCreatorMergeServiceResponse filterCreatorMergeServiceResponse =
        filterCreatorMergeService.getPipelineInfo(pipelineEntity);

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(pmsSdkHelper).getServices();
    verify(pipelineSetupUsageHelper).publishSetupUsageEvent(eq(pipelineEntity), listArgumentCaptor.capture());
    assertThat(listArgumentCaptor.getValue()).isNotNull().isNotEmpty().hasSize(1);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateFilterCreationBlobResponse() throws IOException {
    Map<String, YamlField> yamlFieldDependencies = new HashMap<>();
    yamlFieldDependencies.put("pipeline", YamlUtils.readTree(pipelineYaml));
    FilterCreationBlobResponse filterCreationBlobResponse =
        FilterCreationBlobResponse.newBuilder()
            .setDeps(DependenciesUtils.toDependenciesProto(yamlFieldDependencies))
            .build();
    assertThatThrownBy(() -> filterCreatorMergeService.validateFilterCreationBlobResponse(filterCreationBlobResponse))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainFiltersRecursivelyNoDependencies() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null));

    FilterCreationBlobResponse response = filterCreatorMergeService.obtainFiltersRecursively(
        services, Dependencies.newBuilder().build(), new HashMap<>(), SetupMetadata.newBuilder().build());

    assertThat(response).isEqualTo(
        FilterCreationBlobResponse.newBuilder().setDeps(Dependencies.newBuilder().build()).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainFiltersRecursivelyNoServices() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    FilterCreationBlobResponse response = filterCreatorMergeService.obtainFiltersRecursively(
        services, Dependencies.newBuilder().build(), new HashMap<>(), SetupMetadata.newBuilder().build());
    assertThat(response).isEqualTo(
        FilterCreationBlobResponse.newBuilder().setDeps(Dependencies.newBuilder().build()).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainFiltersRecursively() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null));
    String uuidYaml = YamlUtils.injectUuid(pipelineYaml);
    Map<String, YamlField> dependencies = new HashMap<>();
    dependencies.put("pipeline", YamlUtils.readTree(uuidYaml));

    Dependencies resDependencies = DependenciesUtils.toDependenciesProto(dependencies);
    Dependencies initialDependencies =
        Dependencies.newBuilder().putAllDependencies(resDependencies.getDependenciesMap()).setYaml(uuidYaml).build();
    doReturn(FilterCreationBlobResponse.newBuilder().setResolvedDeps(initialDependencies).build())
        .when(filterCreatorMergeService)
        .obtainFiltersPerIteration(any(), any(), any(), any());

    FilterCreationBlobResponse response = filterCreatorMergeService.obtainFiltersRecursively(
        services, initialDependencies, new HashMap<>(), SetupMetadata.newBuilder().build());

    assertThat(response.getResolvedDeps()).isEqualTo(resDependencies);

    verify(filterCreatorMergeService).obtainFiltersPerIteration(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainFiltersRecursivelyWithUnresolvedDependencies() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null));
    String uuidYaml = YamlUtils.injectUuid(pipelineYaml);
    Map<String, YamlField> dependencies = new HashMap<>();
    dependencies.put("pipeline", YamlUtils.extractPipelineField(uuidYaml));

    Dependencies resDependencies = DependenciesUtils.toDependenciesProto(dependencies);
    Dependencies initialDependencies =
        Dependencies.newBuilder().putAllDependencies(resDependencies.getDependenciesMap()).setYaml(uuidYaml).build();
    doReturn(FilterCreationBlobResponse.newBuilder().build())
        .when(filterCreatorMergeService)
        .obtainFiltersPerIteration(any(), any(), any(), any());

    assertThatThrownBy(()
                           -> filterCreatorMergeService.obtainFiltersRecursively(
                               services, initialDependencies, new HashMap<>(), SetupMetadata.newBuilder().build()))
        .isInstanceOf(RuntimeException.class);

    verify(filterCreatorMergeService).obtainFiltersPerIteration(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetPipelineInfoForOldGitSync() throws IOException {
    when(gitSyncSdkService.isGitSyncEnabled(anyString(), anyString(), anyString())).thenReturn(true);
    Map<String, Set<String>> stepToSupportedTypes = new HashMap<>();
    stepToSupportedTypes.put("pipeline", Collections.singleton("__any__"));
    Map<String, PlanCreatorServiceInfo> sdkInstances = new HashMap<>();
    when(pmsSdkHelper.getServices()).thenReturn(sdkInstances);

    doReturn(
        FilterCreationBlobResponse.newBuilder().addReferredEntities(EntityDetailProtoDTO.newBuilder().build()).build())
        .when(filterCreatorMergeService)
        .obtainFiltersRecursively(any(), any(), any(), any());
    doNothing().when(pipelineSetupUsageHelper).deleteExistingSetupUsages(ACCOUNT_ID, ORG_ID, PROJECT_ID, IDENTIFIER);

    doReturn(ExecutionPrincipalInfo.newBuilder().build())
        .when(principalInfoHelper)
        .getPrincipalInfoFromSecurityContext();
    doReturn(TriggeredBy.newBuilder().build()).when(triggeredByHelper).getFromSecurityContext();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .yaml(pipelineYaml)
                                        .accountId(ACCOUNT_ID)
                                        .projectIdentifier(PROJECT_ID)
                                        .orgIdentifier(ORG_ID)
                                        .identifier(IDENTIFIER)
                                        .build();
    doReturn(Collections.singletonList(EntityDetailProtoDTO.newBuilder().build()))
        .when(pmsPipelineTemplateHelper)
        .getTemplateReferencesForGivenYaml(anyString(), anyString(), anyString(), anyString());
    Assertions.assertDoesNotThrow(() -> filterCreatorMergeService.getPipelineInfo(pipelineEntity));

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(pmsSdkHelper).getServices();
    verify(pipelineSetupUsageHelper).publishSetupUsageEvent(eq(pipelineEntity), listArgumentCaptor.capture());
    assertThat(listArgumentCaptor.getValue()).isNotNull().isNotEmpty().hasSize(1);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testObtainFiltersWithTemplatePipelineYaml() throws IOException {
    String pipelineTemplateYaml = "pipeline:\n"
        + "  identifier: p2\n"
        + "  name: pipeline2\n"
        + "  template:\n"
        + "    templateRef: template1\n"
        + "    versionLabel: v1\n";
    String uuidYaml = YamlUtils.injectUuid(pipelineTemplateYaml);
    YamlField uuidYamlField = YamlUtils.extractPipelineField(uuidYaml);
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null));
    Map<String, String> dependencies = new HashMap<>();
    dependencies.put(uuidYamlField.getNode().getUuid(), uuidYamlField.getNode().getYamlPath());

    Dependencies initialDependencies =
        Dependencies.newBuilder().putAllDependencies(dependencies).setYaml(uuidYaml).build();

    FilterCreationBlobResponse response = filterCreatorMergeService.obtainFiltersRecursively(
        services, initialDependencies, new HashMap<>(), SetupMetadata.newBuilder().build());
    assertThat(response.getDeps()).isNotNull();
    assertThat(response.getDeps().getDependenciesMap()).isEmpty();
    verify(filterCreatorMergeService, never()).obtainFiltersPerIteration(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetGitConnectorReference() {
    GitSyncBranchContext gitSyncBranchContext =
        GitSyncBranchContext.builder()
            .gitBranchInfo(GitEntityInfo.builder().connectorRef(gitConnectorRef).storeType(StoreType.REMOTE).build())
            .build();
    GlobalContext context = new GlobalContext();
    context.setGlobalContextRecord(gitSyncBranchContext);
    GlobalContextManager.set(context);
    when(gitSyncSdkService.isGitSimplificationEnabled(anyString(), anyString(), anyString())).thenReturn(true);
    PipelineEntity pipelineEntity =
        PipelineEntity.builder().accountId(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).build();
    Optional<EntityDetailProtoDTO> entityDetailProtoDTO =
        filterCreatorMergeService.getGitConnectorReference(pipelineEntity);
    assertThat(entityDetailProtoDTO.isPresent()).isEqualTo(true);
    assertThat(entityDetailProtoDTO.get().getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
  }
}
