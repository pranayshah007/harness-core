/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.DHRUV;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.APPDYNAMICS_APPID;
import static software.wings.beans.EntityType.APPDYNAMICS_CONFIGID;
import static software.wings.beans.EntityType.APPDYNAMICS_TIERID;
import static software.wings.beans.EntityType.ELK_CONFIGID;
import static software.wings.beans.EntityType.ELK_INDICES;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.NEWRELIC_APPID;
import static software.wings.beans.EntityType.NEWRELIC_CONFIGID;
import static software.wings.beans.EntityType.NEWRELIC_MARKER_APPID;
import static software.wings.beans.EntityType.NEWRELIC_MARKER_CONFIGID;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SPLUNK_CONFIGID;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.ManifestVariable;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;
import software.wings.sm.states.ApprovalState.ApprovalStateKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest(PipelineServiceImpl.class)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
@OwnedBy(CDC)
public class PipelineServiceImplTest extends WingsBaseTest {
  @Mock AppService mockAppService;
  @Mock WingsPersistence mockWingsPersistence;
  @Mock YamlPushService mockYamlPushService;
  @Mock WorkflowService mockWorkflowService;
  @Mock private LimitCheckerFactory mockLimitCheckerFactory;
  @Mock protected FeatureFlagService featureFlagService;
  @Inject @InjectMocks private PipelineServiceImpl pipelineServiceImpl;

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testPopulateParentFields() {
    List<Variable> workflowVariables = asList(
        aVariable()
            .entityType(SERVICE)
            .name("Service")
            .value("Service 1")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "ServiceInfra_ECS", Variable.ENTITY_TYPE, SERVICE))
            .build(),
        aVariable()
            .entityType(ENVIRONMENT)
            .name("Environment")
            .value("env 1")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "ServiceInfra_ECS", Variable.ENTITY_TYPE, ENVIRONMENT))
            .build(),
        aVariable().entityType(INFRASTRUCTURE_MAPPING).name("ServiceInfra_ECS").value("${Infra}").build(),
        aVariable()
            .entityType(APPDYNAMICS_APPID)
            .name("AppdAppId")
            .value("AppD app")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "AppdTierId", Variable.ENTITY_TYPE, APPDYNAMICS_APPID))
            .build(),
        aVariable()
            .entityType(APPDYNAMICS_CONFIGID)
            .name("AppdConfigId")
            .value("AppD config")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "AppdTierId", Variable.ENTITY_TYPE, APPDYNAMICS_CONFIGID))
            .build(),
        aVariable()
            .entityType(ELK_CONFIGID)
            .name("ElkConfigId")
            .value("elk config")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "ElkIndices", Variable.ENTITY_TYPE, ELK_CONFIGID))
            .build(),
        aVariable().entityType(ELK_INDICES).name("ElkIndices").value("elk indices").build(),
        aVariable().entityType(NEWRELIC_APPID).name("NewRelicAppId").value("NewRelic appId").build(),
        aVariable()
            .entityType(NEWRELIC_CONFIGID)
            .name("NewRelicConfigId")
            .value("NewRelic configId")
            .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "NewRelicAppId", Variable.ENTITY_TYPE, NEWRELIC_CONFIGID))
            .build(),
        aVariable().entityType(SPLUNK_CONFIGID).name("SplunkConfigId").value("Splunk configId").build(),
        aVariable()
            .entityType(NEWRELIC_MARKER_APPID)
            .name("NewRelicMarkerAppId")
            .value("NewRelic Marker appId")
            .build(),
        aVariable()
            .entityType(NEWRELIC_MARKER_CONFIGID)
            .name("NewRelicMarkerConfigId")
            .value("NewRelic Marker configId")
            .metadata(ImmutableMap.of(
                Variable.RELATED_FIELD, "NewRelicMarkerAppId", Variable.ENTITY_TYPE, NEWRELIC_MARKER_CONFIGID))
            .build(),
        aVariable().entityType(APPDYNAMICS_TIERID).name("AppdTierId").value("${Tier}").build(),
        aVariable().type(VariableType.TEXT).name("test").value("test").build());

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("Service", "Service 2");
    pseWorkflowVariables.put("Environment", "Environment 2");
    pseWorkflowVariables.put("ServiceInfra_ECS", "{$infra}");
    pseWorkflowVariables.put("AppdAppId", "AppD app 1");
    pseWorkflowVariables.put("AppdTierId", "AppD tier");
    pseWorkflowVariables.put("AppdConfigId", "AppD config 2");
    pseWorkflowVariables.put("NewRelicAppId", "NewRelicAppId2");
    pseWorkflowVariables.put("NewRelicMarkerAppId", "${app}");
    pseWorkflowVariables.put("ElkIndices", "elk");
    pseWorkflowVariables.put("ElkConfigId", "elkconfig");
    pseWorkflowVariables.put("NewRelicMarkerConfigId", "newRelicMarkerconfigId");
    pseWorkflowVariables.put("NewRelicConfigId", "newRelicConfigId");

    // Infra mapping variable populate test
    Map<String, Object> metadataMapInfra = new HashMap<>();
    metadataMapInfra.put("entityType", INFRASTRUCTURE_MAPPING);
    Variable infraMappingPipelineVar = aVariable()
                                           .name("Infra")
                                           .description("Variable for Service Infra-structure entity")
                                           .type(VariableType.ENTITY)
                                           .mandatory(true)
                                           .fixed(false)
                                           .metadata(metadataMapInfra)
                                           .build();
    pipelineServiceImpl.populateParentFields(
        infraMappingPipelineVar, INFRASTRUCTURE_MAPPING, workflowVariables, "ServiceInfra_ECS", pseWorkflowVariables);
    assertThat(infraMappingPipelineVar.getMetadata().get(Variable.ENV_ID)).isNotNull();
    assertThat(infraMappingPipelineVar.getMetadata().get(Variable.ENV_ID)).isEqualTo("Environment 2");

    assertThat(infraMappingPipelineVar.getMetadata().get(Variable.SERVICE_ID)).isNotNull();
    assertThat(infraMappingPipelineVar.getMetadata().get(Variable.SERVICE_ID)).isEqualTo("Service 2");

    // Infra mapping definition populate test
    Map<String, Object> metadataMapInfraDef = new HashMap<>();
    metadataMapInfra.put("entityType", INFRASTRUCTURE_DEFINITION);
    Variable infraDefPipelineVar = aVariable()
                                       .name("Infra")
                                       .description("Variable for Service Infra-structure entity")
                                       .type(VariableType.ENTITY)
                                       .mandatory(true)
                                       .fixed(false)
                                       .metadata(metadataMapInfraDef)
                                       .build();

    pipelineServiceImpl.populateParentFields(
        infraDefPipelineVar, INFRASTRUCTURE_DEFINITION, workflowVariables, "ServiceInfra_ECS", pseWorkflowVariables);
    assertThat(infraDefPipelineVar.getMetadata().get(Variable.ENV_ID)).isNotNull();
    assertThat(infraDefPipelineVar.getMetadata().get(Variable.ENV_ID)).isEqualTo("Environment 2");

    assertThat(infraDefPipelineVar.getMetadata().get(Variable.SERVICE_ID)).isNotNull();
    assertThat(infraDefPipelineVar.getMetadata().get(Variable.SERVICE_ID)).isEqualTo("Service 2");

    // Appdynamics tierId populate test

    Map<String, Object> metadataMapAppdTier = new HashMap<>();
    metadataMapAppdTier.put("entityType", APPDYNAMICS_TIERID);
    Variable appdTierPipelineVar = aVariable()
                                       .name("Tier")
                                       .description("Variable for Appd Tier")
                                       .type(VariableType.ENTITY)
                                       .mandatory(true)
                                       .fixed(false)
                                       .metadata(metadataMapAppdTier)
                                       .build();

    pipelineServiceImpl.populateParentFields(
        appdTierPipelineVar, APPDYNAMICS_TIERID, workflowVariables, "AppdTierId", pseWorkflowVariables);
    assertThat(appdTierPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
    Map<String, String> parents = (Map<String, String>) appdTierPipelineVar.getMetadata().get(Variable.PARENT_FIELDS);
    assertThat(parents.get("applicationId")).isEqualTo("AppD app 1");
    assertThat(parents.get("analysisServerConfigId")).isEqualTo("AppD config 2");

    // Appdynamics appId populate test
    Map<String, Object> metadataMapAppdApp = new HashMap<>();
    metadataMapAppdApp.put("entityType", APPDYNAMICS_APPID);
    Variable appdAppPipelineVar = aVariable()
                                      .name("App")
                                      .description("Variable for Appd App")
                                      .type(VariableType.ENTITY)
                                      .mandatory(true)
                                      .fixed(false)
                                      .metadata(metadataMapAppdApp)
                                      .build();

    pipelineServiceImpl.populateParentFields(
        appdAppPipelineVar, APPDYNAMICS_APPID, workflowVariables, "AppdTierId", pseWorkflowVariables);
    assertThat(appdAppPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
    parents = (Map<String, String>) appdAppPipelineVar.getMetadata().get(Variable.PARENT_FIELDS);
    assertThat(parents.get("analysisServerConfigId")).isEqualTo("AppD config 2");

    // Elk indices populate test
    Map<String, Object> metadataMapElkIndices = new HashMap<>();
    metadataMapElkIndices.put("entityType", ELK_INDICES);
    Variable elkIndicesPipelineVar = aVariable()
                                         .name("Elk")
                                         .description("Variable for elk type")
                                         .type(VariableType.ENTITY)
                                         .mandatory(true)
                                         .fixed(false)
                                         .metadata(metadataMapElkIndices)
                                         .build();

    pipelineServiceImpl.populateParentFields(
        elkIndicesPipelineVar, ELK_INDICES, workflowVariables, "ElkIndices", pseWorkflowVariables);
    assertThat(elkIndicesPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
    parents = (Map<String, String>) elkIndicesPipelineVar.getMetadata().get(Variable.PARENT_FIELDS);
    assertThat(parents.get("analysisServerConfigId")).isEqualTo("elkconfig");

    // New relic populate test
    Map<String, Object> metadataMapNewRelicIndices = new HashMap<>();
    metadataMapNewRelicIndices.put("entityType", NEWRELIC_APPID);
    Variable newRelicPipelineVar = aVariable()
                                       .name("newRelic")
                                       .description("Variable for newrelic type")
                                       .type(VariableType.ENTITY)
                                       .mandatory(true)
                                       .fixed(false)
                                       .metadata(metadataMapNewRelicIndices)
                                       .build();

    pipelineServiceImpl.populateParentFields(
        newRelicPipelineVar, NEWRELIC_APPID, workflowVariables, "NewRelicAppId", pseWorkflowVariables);
    assertThat(newRelicPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
    parents = (Map<String, String>) newRelicPipelineVar.getMetadata().get(Variable.PARENT_FIELDS);
    assertThat(parents.get("analysisServerConfigId")).isEqualTo("newRelicConfigId");

    // New relic marker populate test
    Map<String, Object> metadataMapNewRelicMarkerIndices = new HashMap<>();
    metadataMapNewRelicMarkerIndices.put("entityType", NEWRELIC_MARKER_APPID);
    Variable newRelicMarkerPipelineVar = aVariable()
                                             .name("newRelic")
                                             .description("Variable for newrelic type")
                                             .type(VariableType.ENTITY)
                                             .mandatory(true)
                                             .fixed(false)
                                             .metadata(metadataMapNewRelicMarkerIndices)
                                             .build();

    pipelineServiceImpl.populateParentFields(newRelicMarkerPipelineVar, NEWRELIC_MARKER_APPID, workflowVariables,
        "NewRelicMarkerAppId", pseWorkflowVariables);
    assertThat(newRelicMarkerPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
    parents = (Map<String, String>) newRelicMarkerPipelineVar.getMetadata().get(Variable.PARENT_FIELDS);
    assertThat(parents.get("analysisServerConfigId")).isEqualTo("newRelicMarkerconfigId");

    pipelineServiceImpl.populateParentFields(
        newRelicMarkerPipelineVar, NEWRELIC_MARKER_APPID, null, "NewRelicMarkerAppId", pseWorkflowVariables);
    assertThat(newRelicMarkerPipelineVar.getMetadata().get(Variable.PARENT_FIELDS)).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testUniquePipelineName() throws Exception {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder().name("test").build();
    PipelineStageElement pipelineStageElement1 = PipelineStageElement.builder().name("test_1").build();

    PipelineStage pipelineStage =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement)).build();
    Pipeline pipeline = Pipeline.builder().pipelineStages(Arrays.asList(pipelineStage, pipelineStage)).build();

    assertThatThrownBy(() -> pipelineServiceImpl.checkUniquePipelineStepName(pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Duplicate step name test");

    PipelineStage pipelineStage1 =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement1)).build();
    Pipeline pipeline1 = Pipeline.builder().pipelineStages(Arrays.asList(pipelineStage1, pipelineStage)).build();

    pipelineServiceImpl.checkUniquePipelineStepName(pipeline1);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testHandlePipelineStageDeletion() throws Exception {
    PipelineStageElement pipelineStageElement = PipelineStageElement.builder().name("test").parallelIndex(0).build();
    PipelineStageElement pipelineStageElement1 = PipelineStageElement.builder().name("test_1").parallelIndex(0).build();
    PipelineStageElement pipelineStageElement2 = PipelineStageElement.builder().name("test_2").parallelIndex(1).build();
    PipelineStageElement pipelineStageElement3 = PipelineStageElement.builder().name("test_3").parallelIndex(1).build();
    PipelineStageElement pipelineStageElement4 = PipelineStageElement.builder().name("test_4").parallelIndex(1).build();

    PipelineStage pipelineStage =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement)).parallel(false).build();
    PipelineStage pipelineStage1 =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement1)).parallel(true).build();
    PipelineStage pipelineStage2 =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement2)).parallel(false).build();
    PipelineStage pipelineStage3 =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement3)).parallel(true).build();
    PipelineStage pipelineStage4 =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pipelineStageElement4)).parallel(true).build();

    Pipeline pipeline =
        Pipeline.builder()
            .pipelineStages(Arrays.asList(pipelineStage, pipelineStage1, pipelineStage3, pipelineStage4))
            .build();
    Pipeline savedPipeline = Pipeline.builder()
                                 .pipelineStages(Arrays.asList(
                                     pipelineStage, pipelineStage1, pipelineStage2, pipelineStage3, pipelineStage4))
                                 .build();

    pipelineServiceImpl.handlePipelineStageDeletion(pipeline, savedPipeline, false);

    assertThat(pipeline.getPipelineStages().get(2).isParallel()).isEqualTo(false);
    assertThat(pipeline.getPipelineStages().get(2).getPipelineStageElements().get(0).getName()).isEqualTo("test_3");

    pipeline = Pipeline.builder()
                   .pipelineStages(Arrays.asList(pipelineStage, pipelineStage1, pipelineStage2, pipelineStage4))
                   .build();
    savedPipeline = Pipeline.builder()
                        .pipelineStages(Arrays.asList(
                            pipelineStage, pipelineStage1, pipelineStage2, pipelineStage3, pipelineStage4))
                        .build();

    pipelineServiceImpl.handlePipelineStageDeletion(pipeline, savedPipeline, false);

    assertThat(pipeline.getPipelineStages().get(3).isParallel()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotAllowSamePublishedVariable() throws Exception {
    Map<String, Object> approvalProperties = new HashMap<>();
    approvalProperties.put(ApprovalStateKeys.sweepingOutputName, "Info");
    Pipeline pipeline =
        Pipeline.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .name("test")
            .pipelineStages(asList(PipelineStage.builder()
                                       .pipelineStageElements(asList(PipelineStageElement.builder()
                                                                         .name("approval_state_1")
                                                                         .type(StateType.APPROVAL.name())
                                                                         .properties(approvalProperties)
                                                                         .build()))
                                       .build(),
                PipelineStage.builder()
                    .pipelineStageElements(asList(PipelineStageElement.builder()
                                                      .name("approval_state_2")
                                                      .type(StateType.APPROVAL.name())
                                                      .properties(approvalProperties)
                                                      .build()))
                    .build()))
            .build();

    setupQueryMocks();

    assertThatThrownBy(() -> pipelineServiceImpl.save(pipeline))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("You cannot use the same Publish Variable Name");
  }

  private void setupQueryMocks() throws Exception {
    PowerMockito.doReturn(ACCOUNT_ID).when(mockAppService).getAccountIdByAppId(APP_ID);
    Query mockQuery = PowerMockito.mock(Query.class);
    PowerMockito.doReturn(mockChecker()).when(mockLimitCheckerFactory).getInstance(Mockito.any());
    PowerMockito.doReturn(null).when(mockWingsPersistence).save(any(Pipeline.class));
    PowerMockito.doReturn(mockQuery).when(mockWingsPersistence).createQuery(Pipeline.class);
    PowerMockito.doReturn(mockQuery).when(mockQuery).filter(anyString(), any());
    PowerMockito.doReturn(mockQuery).when(mockQuery).filter(anyString(), any());
    PowerMockito.doReturn(Collections.emptyMap()).when(mockWorkflowService).stencilMap(anyString());
    PowerMockito.whenNew(StateMachine.class).withAnyArguments().thenReturn(null);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateMetadtaWhenSameEnvironmentVariableUsedAgain() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.RELATED_FIELD, "infra1");
    metadataStored.put(Variable.ENTITY_TYPE, ENVIRONMENT);

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable envVarStored = aVariable().entityType(ENVIRONMENT).name("env").metadata(metadataStored).build();
    pipelineVariables.add(envVarStored);

    List<Variable> workflowVariables = new ArrayList<>();
    Variable envWorkflowVariable = aVariable()
                                       .entityType(ENVIRONMENT)
                                       .name("Environment")
                                       .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "InfraDefinition_KUBERNETES",
                                           Variable.ENTITY_TYPE, ENVIRONMENT))
                                       .build();
    Variable infraWorkflowVariable = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("Infrastructure_KUBERNETES")
                                         .metadata(ImmutableMap.of(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();
    workflowVariables.add(envWorkflowVariable);
    workflowVariables.add(infraWorkflowVariable);

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("Environment", "${env}");
    pseWorkflowVariables.put("Infrastructure_KUBERNETES", "${infra2}");
    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, workflowVariables, pseWorkflowVariables, envWorkflowVariable, "env", false, false);
    assertThat(envVarStored.getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("infra1,infra2");
  }

  // When Service and infra both templatised in both phases, with same service var, but different infra var
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateMetadataServiceVarTC_1() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.RELATED_FIELD, "infra1");
    metadataStored.put(Variable.ENTITY_TYPE, SERVICE);
    metadataStored.put(Variable.ARTIFACT_TYPE, "DOCKER");

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable serviceVarStored = aVariable().entityType(SERVICE).name("srv").metadata(metadataStored).build();
    pipelineVariables.add(serviceVarStored);
    List<Variable> workflowVariables = new ArrayList<>();
    Variable serviceWorkflowVariable = aVariable()
                                           .entityType(SERVICE)
                                           .name("Service")
                                           .metadata(ImmutableMap.of(Variable.ARTIFACT_TYPE, "DOCKER",
                                               Variable.RELATED_FIELD, "infra2", Variable.ENTITY_TYPE, SERVICE))
                                           .build();
    Variable infraWorkflowVariable = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("Infrastructure_KUBERNETES")
                                         .metadata(ImmutableMap.of(Variable.RELATED_FIELD, "Service",
                                             Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();
    workflowVariables.add(serviceWorkflowVariable);
    workflowVariables.add(infraWorkflowVariable);

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("Service", "${srv}");
    pseWorkflowVariables.put("Infrastructure_KUBERNETES", "${infra2}");
    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, workflowVariables, pseWorkflowVariables, serviceWorkflowVariable, "srv", false, false);
    assertThat(serviceVarStored.getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("infra1,infra2");
  }

  // When Service templatised in both phases, with same service var, infra templatised in 1, concrete in other
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateMetadataServiceVarTC_2() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.INFRA_ID, "infra_id_1");
    metadataStored.put(Variable.ENTITY_TYPE, SERVICE);
    metadataStored.put(Variable.ARTIFACT_TYPE, "DOCKER");

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable serviceVarStored = aVariable().entityType(SERVICE).name("srv").metadata(metadataStored).build();
    pipelineVariables.add(serviceVarStored);

    Variable serviceVariableNewPhase = aVariable()
                                           .entityType(SERVICE)
                                           .name("Service")
                                           .metadata(ImmutableMap.of(Variable.ARTIFACT_TYPE, "DOCKER",
                                               Variable.RELATED_FIELD, "infra2", Variable.ENTITY_TYPE, SERVICE))
                                           .build();

    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, new ArrayList<>(), new HashMap<>(), serviceVariableNewPhase, "srv", false, false);
    assertThat(serviceVarStored.getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("infra2");
    assertThat(serviceVarStored.getMetadata().get(Variable.INFRA_ID)).isEqualTo("infra_id_1");
  }

  // When Service templatised in both phases, with same service var, infra concrete in both phases
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateMetadataServiceVarTC_3() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.INFRA_ID, "infra_id_1");
    metadataStored.put(Variable.ENTITY_TYPE, SERVICE);
    metadataStored.put(Variable.ARTIFACT_TYPE, "DOCKER");

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable serviceVarStored = aVariable().entityType(SERVICE).name("srv").metadata(metadataStored).build();
    pipelineVariables.add(serviceVarStored);

    Variable serviceVariableNewPhase = aVariable()
                                           .entityType(SERVICE)
                                           .name("Service")
                                           .metadata(ImmutableMap.of(Variable.ARTIFACT_TYPE, "DOCKER",
                                               Variable.INFRA_ID, "infra_id_2", Variable.ENTITY_TYPE, SERVICE))
                                           .build();
    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, new ArrayList<>(), new HashMap<>(), serviceVariableNewPhase, "srv", false, false);
    assertThat(serviceVarStored.getMetadata().get(Variable.INFRA_ID)).isEqualTo("infra_id_1,infra_id_2");
  }

  // When Service templatised in both phases, but different artifact type workflows
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateMetadataServiceVarTC_4() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.INFRA_ID, "infra_id_1");
    metadataStored.put(Variable.ENTITY_TYPE, SERVICE);
    metadataStored.put(Variable.ARTIFACT_TYPE, "DOCKER");

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable serviceVarStored = aVariable().entityType(SERVICE).name("srv").metadata(metadataStored).build();
    pipelineVariables.add(serviceVarStored);
    Variable serviceVariableNewPhase = aVariable()
                                           .entityType(SERVICE)
                                           .name("Service")
                                           .metadata(ImmutableMap.of(Variable.ARTIFACT_TYPE, "WAR",
                                               Variable.RELATED_FIELD, "infra2", Variable.ENTITY_TYPE, SERVICE))
                                           .build();

    assertThatThrownBy(()
                           -> pipelineServiceImpl.updateStoredVariable(pipelineVariables, new ArrayList<>(),
                               new HashMap<>(), serviceVariableNewPhase, "srv", false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "The same Workflow variable name srv cannot be used for Services using different Artifact types. Change the name of the variable in one or more Workflow.");
  }

  // When Infra templatised in both phases, but different envId selected
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateStoredVariableInfraTC_1() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.ENV_ID, "env_id_1");
    metadataStored.put(Variable.SERVICE_ID, "s1");
    metadataStored.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    List<Variable> pipelineVariables = new ArrayList<>();
    Variable serviceVarStored =
        aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra").metadata(metadataStored).build();
    pipelineVariables.add(serviceVarStored);
    Variable infraVariableNewPhase = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("infra")
                                         .metadata(ImmutableMap.of(Variable.ENV_ID, "env_id_2", Variable.SERVICE_ID,
                                             "s2", Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();

    assertThatThrownBy(()
                           -> pipelineServiceImpl.updateStoredVariable(pipelineVariables, new ArrayList<>(),
                               new HashMap<>(), infraVariableNewPhase, "infra", false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "The same Workflow variable name infra cannot be used for InfraDefinitions using different Environment. Change the name of the variable in one or more Workflow.");
  }

  // When Infra templatised in both phases, with same var,service templatised in both phases
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateStoredVariableInfraTC_2() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.ENV_ID, "env_id_1");
    metadataStored.put(Variable.RELATED_FIELD, "srv1");
    metadataStored.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    List<Variable> pipelineVariables = new ArrayList<>();
    Variable infraVarStored = aVariable()
                                  .entityType(INFRASTRUCTURE_DEFINITION)
                                  .name("infra")
                                  .metadata(metadataStored)
                                  .allowMultipleValues(false)
                                  .build();
    pipelineVariables.add(infraVarStored);
    Variable infraVariableNewPhase = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("infra")
                                         .metadata(ImmutableMap.of(Variable.ENV_ID, "env_id_1", Variable.RELATED_FIELD,
                                             "srv2", Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();

    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, new ArrayList<>(), new HashMap<>(), infraVariableNewPhase, "infra", true, false);
    assertThat(infraVarStored.getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("srv1,srv2");
    assertThat(infraVarStored.isAllowMultipleValues()).isFalse();
  }

  // When Infra templatised in both phases, with same var,service templatised in one phase, concrete in another
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateStoredVariableInfraTC_3() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.ENV_ID, "env_id_1");
    metadataStored.put(Variable.RELATED_FIELD, "srv1");
    metadataStored.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    List<Variable> pipelineVariables = new ArrayList<>();
    Variable infraVarStored = aVariable()
                                  .entityType(INFRASTRUCTURE_DEFINITION)
                                  .name("infra")
                                  .metadata(metadataStored)
                                  .allowMultipleValues(true)
                                  .build();
    pipelineVariables.add(infraVarStored);
    Variable infraVariableNewPhase = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("infra")
                                         .metadata(ImmutableMap.of(Variable.ENV_ID, "env_id_1", Variable.SERVICE_ID,
                                             "service_id_2", Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();

    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, new ArrayList<>(), new HashMap<>(), infraVariableNewPhase, "infra", false, false);
    assertThat(infraVarStored.getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("srv1");
    assertThat(infraVarStored.getMetadata().get(Variable.SERVICE_ID)).isEqualTo("service_id_2");
    assertThat(infraVarStored.isAllowMultipleValues()).isFalse();
  }

  // When Infra templatised in both phases, with same var,service concrete in both
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void updateStoredVariableInfraTC_4() {
    HashMap<String, Object> metadataStored = new HashMap<>();
    metadataStored.put(Variable.ENV_ID, "env_id_1");
    metadataStored.put(Variable.SERVICE_ID, "serviceID_1,serviceID_2");
    metadataStored.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    List<Variable> pipelineVariables = new ArrayList<>();
    Variable infraVarStored =
        aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("infra").metadata(metadataStored).build();
    pipelineVariables.add(infraVarStored);
    Variable infraVariableNewPhase = aVariable()
                                         .entityType(INFRASTRUCTURE_DEFINITION)
                                         .name("infra")
                                         .metadata(ImmutableMap.of(Variable.ENV_ID, "env_id_1", Variable.SERVICE_ID,
                                             "serviceID_2", Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION))
                                         .build();

    pipelineServiceImpl.updateStoredVariable(
        pipelineVariables, new ArrayList<>(), new HashMap<>(), infraVariableNewPhase, "infra", true, false);
    assertThat(infraVarStored.getMetadata().get(Variable.SERVICE_ID)).isEqualTo("serviceID_1,serviceID_2");
  }

  // When two phase has same env variables, Service non templatised, and infra variable with different vars
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void setPipelineVariablesTC_1() {
    HashMap<String, Object> metadataEnv = new HashMap<>();
    metadataEnv.put(Variable.RELATED_FIELD, "InfraDefinition_ECS");
    metadataEnv.put(Variable.ENTITY_TYPE, ENVIRONMENT);

    Variable envVarWorkflow = aVariable().entityType(ENVIRONMENT).name("Environment").metadata(metadataEnv).build();

    HashMap<String, Object> metadataInfra = new HashMap<>();
    metadataInfra.put(Variable.SERVICE_ID, "s1");
    metadataInfra.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    Variable infraVarWorkflow =
        aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("InfraDefinition_ECS").metadata(metadataInfra).build();
    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(envVarWorkflow);
    userVariables.add(infraVarWorkflow);

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .accountId("ACCOUNT_ID")
            .build();

    List<Variable> pipelineVariables = new ArrayList<>();

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("Environment", "${env}");
    pseWorkflowVariables.put("InfraDefinition_ECS", "${infra1}");

    PipelineStageElement pse =
        PipelineStageElement.builder().workflowVariables(pseWorkflowVariables).type("ENV_STATE").build();

    Map<String, String> pse2WorkflowVariables = new HashMap<>();
    pse2WorkflowVariables.put("Environment", "${env}");
    pse2WorkflowVariables.put("InfraDefinition_ECS", "${infra2}");
    PipelineStageElement pse2 =
        PipelineStageElement.builder().workflowVariables(pse2WorkflowVariables).type("ENV_STATE").build();

    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    pipelineServiceImpl.setPipelineVariables(workflow, pse2, pipelineVariables, false);

    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("env");
    assertThat(pipelineVariables.get(0).getMetadata().get(Variable.RELATED_FIELD)).isEqualTo("infra1,infra2");
    assertThat(pipelineVariables.get(1).isAllowMultipleValues()).isTrue();
    assertThat(pipelineVariables.get(2).isAllowMultipleValues()).isTrue();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void setPipelineVariablesNonEntityVars() {
    Variable customeVarWorkflow = aVariable().type(VariableType.TEXT).name("customVar").build();
    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(customeVarWorkflow);
    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .accountId("ACCOUNT_ID")
            .build();
    List<Variable> pipelineVariables = new ArrayList<>();
    Map<String, String> pseWorkflowVariables = new HashMap<>();
    PipelineStageElement pse =
        PipelineStageElement.builder().workflowVariables(pseWorkflowVariables).type("ENV_STATE").build();

    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("customVar");
    assertThat(pipelineVariables.get(0).getType()).isEqualTo(VariableType.TEXT);

    pseWorkflowVariables.put("customVar", "val1");
    pipelineVariables = new ArrayList<>();
    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    assertThat(pipelineVariables).isEmpty();

    pseWorkflowVariables.put("customVar", "${service.name}");
    pipelineVariables = new ArrayList<>();
    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    assertThat(pipelineVariables).isEmpty();

    pseWorkflowVariables.put("customVar", "${abc}");
    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("abc");
    assertThat(pipelineVariables.get(0).getType()).isEqualTo(VariableType.TEXT);

    pseWorkflowVariables.remove("customVar");
    pipelineVariables = new ArrayList<>();
    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("customVar");
    assertThat(pipelineVariables.get(0).getType()).isEqualTo(VariableType.TEXT);
  }

  // When two phase has diff env id, same service var, and diff infra id. FF on
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void setPipelineVariablesTC_2() {
    HashMap<String, Object> metadataEnv = new HashMap<>();
    metadataEnv.put(Variable.RELATED_FIELD, "InfraDefinition_ECS");
    metadataEnv.put(Variable.ENTITY_TYPE, ENVIRONMENT);

    Variable envVarWorkflow = aVariable().entityType(ENVIRONMENT).name("Environment").metadata(metadataEnv).build();

    HashMap<String, Object> metadataInfra = new HashMap<>();
    metadataInfra.put(Variable.RELATED_FIELD, "Service");
    metadataInfra.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    metadataInfra.put(Variable.DEPLOYMENT_TYPE, DeploymentType.SSH.name());
    Variable infraVarWorkflow =
        aVariable().entityType(INFRASTRUCTURE_DEFINITION).name("InfraDefinition_ECS").metadata(metadataInfra).build();

    HashMap<String, Object> metadataSrv = new HashMap<>();
    metadataSrv.put(Variable.RELATED_FIELD, "InfraDefinition_ECS");
    metadataSrv.put(Variable.ENTITY_TYPE, SERVICE);
    metadataSrv.put(Variable.DEPLOYMENT_TYPE, DeploymentType.SSH.name());
    Variable srvVarWorkflow = aVariable().entityType(SERVICE).name("Service").metadata(metadataSrv).build();

    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(envVarWorkflow);
    userVariables.add(infraVarWorkflow);
    userVariables.add(srvVarWorkflow);

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .accountId("ACCOUNT_ID")
            .build();

    List<Variable> pipelineVariables = new ArrayList<>();

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("Environment", "E1");
    pseWorkflowVariables.put("InfraDefinition_ECS", "I1");
    pseWorkflowVariables.put("Service", "${srv}");

    PipelineStageElement pse =
        PipelineStageElement.builder().workflowVariables(pseWorkflowVariables).type("ENV_STATE").build();

    Map<String, String> pse2WorkflowVariables = new HashMap<>();
    pse2WorkflowVariables.put("Environment", "E2");
    pse2WorkflowVariables.put("InfraDefinition_ECS", "I2");
    pse2WorkflowVariables.put("Service", "${srv}");
    PipelineStageElement pse2 =
        PipelineStageElement.builder().workflowVariables(pse2WorkflowVariables).type("ENV_STATE").build();

    pipelineServiceImpl.setPipelineVariables(workflow, pse, pipelineVariables, false);
    pipelineServiceImpl.setPipelineVariables(workflow, pse2, pipelineVariables, false);

    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("srv");
    assertThat(pipelineVariables.get(0).getMetadata().get(Variable.INFRA_ID)).isEqualTo("I1,I2");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void

  checkPipelineWithWorkflowVariables() {
    HashMap<String, Object> metadataInfra = new HashMap<>();
    metadataInfra.put(Variable.RELATED_FIELD, "Service");
    metadataInfra.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION);
    metadataInfra.put(Variable.DEPLOYMENT_TYPE, DeploymentType.SSH.name());
    Variable infraVarWorkflow = aVariable()
                                    .entityType(INFRASTRUCTURE_DEFINITION)
                                    .name("InfraDefinition_ECS")
                                    .metadata(metadataInfra)
                                    .mandatory(true)
                                    .type(VariableType.ENTITY)
                                    .build();

    HashMap<String, Object> metadataSrv = new HashMap<>();
    metadataSrv.put(Variable.RELATED_FIELD, "InfraDefinition_ECS");
    metadataSrv.put(Variable.ENTITY_TYPE, SERVICE);
    metadataSrv.put(Variable.DEPLOYMENT_TYPE, DeploymentType.SSH.name());
    Variable srvVarWorkflow = aVariable()
                                  .entityType(SERVICE)
                                  .name("Service")
                                  .metadata(metadataSrv)
                                  .mandatory(true)
                                  .type(VariableType.ENTITY)
                                  .build();

    List<Variable> userVariables = new ArrayList<>();
    userVariables.add(infraVarWorkflow);
    userVariables.add(srvVarWorkflow);

    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .accountId("ACCOUNT_ID")
            .name("Complete workflow")
            .build();

    Map<String, String> pseWorkflowVariables = new HashMap<>();
    pseWorkflowVariables.put("InfraDefinition_ECS", "ID1");
    pseWorkflowVariables.put("Service", "${srv}");

    PipelineStageElement pse = PipelineStageElement.builder()
                                   .name("Pipeline Step 1")
                                   .workflowVariables(pseWorkflowVariables)
                                   .valid(true)
                                   .build();
    PipelineStage pipelineStage =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pse)).valid(true).name("Pipeline Stage 1").build();

    Set<String> invalidWorkflows = new HashSet<>();
    pipelineServiceImpl.validateWorkflowVariables(workflow, pse, pipelineStage, invalidWorkflows);
    assertThat(invalidWorkflows).isEmpty();
    assertThat(pse.isValid()).isTrue();

    PipelineStageElement pse2 = PipelineStageElement.builder().name("Pipeline Step 2").build();
    PipelineStage pipelineStage2 =
        PipelineStage.builder().name("Pipeline Stage 2").pipelineStageElements(Arrays.asList(pse)).build();

    pipelineServiceImpl.validateWorkflowVariables(workflow, pse2, pipelineStage2, invalidWorkflows);
    assertThat(invalidWorkflows).containsExactly("Pipeline Step 2");
    assertThat(pse2.isValid()).isFalse();
    assertThat(pipelineStage2.isValid()).isFalse();

    Workflow workflow2 = aWorkflow()
                             .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                             .accountId("ACCOUNT_ID")
                             .name("Incomplete workflow")
                             .build();

    invalidWorkflows = new HashSet<>();
    pipelineServiceImpl.validateWorkflowVariables(workflow2, pse, pipelineStage, invalidWorkflows);
    assertThat(invalidWorkflows).containsExactly("Pipeline Step 1");
    assertThat(pse.isValid()).isFalse();
    assertThat(pipelineStage.isValid()).isFalse();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotPopulateErrorForMissingOptionalVariables() {
    Variable optionalVariable = aVariable().name("test").mandatory(false).build();
    Variable requiredVariable = aVariable().name("testReq").mandatory(true).build();
    Workflow workflow =
        aWorkflow()
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                       .withUserVariables(Arrays.asList(optionalVariable, requiredVariable))
                                       .build())
            .accountId("ACCOUNT_ID")
            .name("Complete workflow")
            .build();

    PipelineStageElement pse = PipelineStageElement.builder()
                                   .workflowVariables(Collections.singletonMap("testReq", "value"))
                                   .valid(true)
                                   .build();
    PipelineStage pipelineStage = PipelineStage.builder().pipelineStageElements(Arrays.asList(pse)).valid(true).build();
    Set<String> invalidWorkflows = new HashSet<>();
    pipelineServiceImpl.validateWorkflowVariables(workflow, pse, pipelineStage, invalidWorkflows);
    assertThat(invalidWorkflows).isEmpty();
    assertThat(pse.isValid()).isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldDetectInvalidWorkflowsInPipelines() {
    Workflow workflow = aWorkflow()
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .accountId("ACCOUNT_ID")
                            .uuid(WORKFLOW_ID)
                            .name(WORKFLOW_NAME)
                            .build();

    workflow.getOrchestrationWorkflow().setValid(false);

    PipelineStageElement pse = PipelineStageElement.builder()
                                   .workflowVariables(Collections.singletonMap("testReq", "value"))
                                   .type("ENV_STATE")
                                   .name("TEST_STEP")
                                   .valid(true)
                                   .properties(Collections.singletonMap("workflowId", WORKFLOW_ID))
                                   .build();
    PipelineStage pipelineStage =
        PipelineStage.builder().pipelineStageElements(Arrays.asList(pse)).valid(true).name("TEST_STAGE").build();
    Pipeline pipeline = Pipeline.builder().pipelineStages(Collections.singletonList(pipelineStage)).build();
    pipeline.setWorkflowIds(Collections.singletonList(WORKFLOW_ID));

    doReturn(workflow).when(mockWorkflowService).readWorkflowWithoutServices(any(), eq(WORKFLOW_ID));
    pipelineServiceImpl.setServicesAndPipelineVariables(pipeline);
    assertThat(pipeline.isValid()).isFalse();
    assertThat(pipelineStage.isValid()).isFalse();
    assertThat(pipeline.getValidationMessage()).isEqualTo("Some steps [TEST_STEP] are found to be invalid/incomplete.");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldDetectClassCastWhenSetPipelineVariablesApprovalState() {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("templateExpressions", "");
    PipelineStageElement pse = PipelineStageElement.builder().type("APPROVAL").properties(properties).build();
    PipelineStage pipelineStage = PipelineStage.builder().name("Approval").build();
    List<Variable> pipelineVariables = new ArrayList<>();

    pipelineServiceImpl.setPipelineVariablesApproval(pse, pipelineVariables, pipelineStage.getName());

    assertThat(pipelineVariables).isEmpty();
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void setPipelineVariablesApprovalState() {
    HashMap<String, Object> metadata = new HashMap<>();
    metadata.put("entityType", "USER_GROUP");
    metadata.put("relatedField", "");

    HashMap<String, Object> expression = new HashMap<>();

    expression.put("metadata", metadata);
    expression.put("expression", "${User_Group}");
    expression.put("fieldName", "userGroups");

    List<Map<String, Object>> templateExpressions = new ArrayList<>();
    templateExpressions.add(expression);

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("templateExpressions", templateExpressions);
    PipelineStageElement pse = PipelineStageElement.builder().type("APPROVAL").properties(properties).build();
    PipelineStage pipelineStage = PipelineStage.builder().name("Approval").build();
    List<Variable> pipelineVariables = new ArrayList<>();

    pipelineServiceImpl.setPipelineVariablesApproval(pse, pipelineVariables, pipelineStage.getName());

    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0).getName()).isEqualTo("User_Group");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testValidateMultipleValues() {
    Pipeline pipeline = Pipeline.builder().name(PIPELINE_NAME).uuid(PIPELINE_ID).build();
    List<Variable> pipelineVariables = new ArrayList<>();
    Variable infraVar =
        aVariable().name("infraVar").entityType(INFRASTRUCTURE_DEFINITION).allowMultipleValues(false).build();
    pipelineVariables.add(infraVar);
    Map<String, String> values = ImmutableMap.of("infraVar", "pcf, pcf2");
    pipeline.setPipelineVariables(pipelineVariables);
    assertThatCode(() -> pipelineServiceImpl.validateMultipleValuesAllowed(pipeline, values))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("variable infraVar cannot take multiple values");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testValidateMultipleValuesValid() {
    Pipeline pipeline = Pipeline.builder().name(PIPELINE_NAME).uuid(PIPELINE_ID).build();
    pipeline.setPipelineVariables(new ArrayList<>());
    pipelineServiceImpl.validateMultipleValuesAllowed(pipeline, new HashMap<>());

    List<Variable> pipelineVariables = new ArrayList<>();
    Variable infraVar =
        aVariable().name("infraVar").entityType(INFRASTRUCTURE_DEFINITION).allowMultipleValues(true).build();
    pipelineVariables.add(infraVar);
    Map<String, String> values = ImmutableMap.of("infraVar", "pcf, pcf2");
    pipeline.setPipelineVariables(pipelineVariables);
    pipelineServiceImpl.validateMultipleValuesAllowed(pipeline, values);
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testRuntimeInputNewVars() {
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(ImmutableMap.of("nonEntity", "${nonEntityVal}", "entity", "${entityVal}"))
            .runtimeInputsConfig(
                RuntimeInputsConfig.builder().runtimeInputVariables(asList("nonEntity", "entity")).build())
            .build();

    List<Variable> userVariables = new ArrayList<>();
    Variable nonEntityVar = aVariable().name("nonEntity").build();
    Variable entittyVar = aVariable().name("entity").entityType(ENVIRONMENT).build();
    userVariables.add(nonEntityVar);
    userVariables.add(entittyVar);
    Workflow workflow =
        aWorkflow()
            .accountId(ACCOUNT_ID)
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .build();

    List<Variable> pipelineVars = new ArrayList<>();
    pipelineServiceImpl.setPipelineVariables(workflow, pipelineStageElement, pipelineVars, false);
    assertThat(pipelineVars).isNotEmpty();
    assertThat(pipelineVars.get(0).getRuntimeInput()).isTrue();
    assertThat(pipelineVars.get(1).getRuntimeInput()).isTrue();
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testRuntimeInputExistingVarsNOnEntityException() {
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(ImmutableMap.of("nonEntity", "${nonEntityVal}", "entity", "${entityVal}"))
            .runtimeInputsConfig(
                RuntimeInputsConfig.builder().runtimeInputVariables(asList("nonEntity", "entity")).build())
            .build();

    List<Variable> userVariables = new ArrayList<>();
    Variable nonEntityVar = aVariable().name("nonEntity").build();
    Variable entittyVar = aVariable().name("entity").entityType(ENVIRONMENT).build();
    userVariables.add(nonEntityVar);
    userVariables.add(entittyVar);
    Workflow workflow =
        aWorkflow()
            .accountId(ACCOUNT_ID)
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .build();

    List<Variable> pipelineVars = new ArrayList<>();
    Variable existNonEntityVar = aVariable().name("nonEntityVal").build();
    existNonEntityVar.setRuntimeInput(false);
    Variable existEntityVar = aVariable().name("entityVal").entityType(ENVIRONMENT).build();
    existEntityVar.setRuntimeInput(true);
    pipelineVars.add(existNonEntityVar);
    pipelineVars.add(existEntityVar);
    assertThatThrownBy(
        () -> pipelineServiceImpl.setPipelineVariables(workflow, pipelineStageElement, pipelineVars, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Variable nonEntityVal is not marked as runtime in all pipeline stages");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testRuntimeInputExistingVarsEntityException() {
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder()
            .workflowVariables(ImmutableMap.of("nonEntity", "${nonEntityVal}", "entity", "${entityVal}"))
            .runtimeInputsConfig(
                RuntimeInputsConfig.builder().runtimeInputVariables(asList("nonEntity", "entity")).build())
            .build();

    List<Variable> userVariables = new ArrayList<>();
    Variable nonEntityVar = aVariable().name("nonEntity").build();
    Variable entittyVar = aVariable().name("entity").entityType(ENVIRONMENT).build();
    userVariables.add(nonEntityVar);
    userVariables.add(entittyVar);
    Workflow workflow =
        aWorkflow()
            .accountId(ACCOUNT_ID)
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(userVariables).build())
            .build();

    List<Variable> pipelineVars = new ArrayList<>();
    Variable existNonEntityVar = aVariable().name("nonEntityVal").build();
    existNonEntityVar.setRuntimeInput(true);
    Variable existEntityVar = aVariable().name("entityVal").entityType(ENVIRONMENT).build();
    existEntityVar.setRuntimeInput(false);
    pipelineVars.add(existNonEntityVar);
    pipelineVars.add(existEntityVar);
    assertThatThrownBy(
        () -> pipelineServiceImpl.setPipelineVariables(workflow, pipelineStageElement, pipelineVars, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Variable entityVal is not marked as runtime in all pipeline stages");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testMergeManifestVariables() {
    Workflow workflow1 =
        aWorkflow()
            .accountId(ACCOUNT_ID)
            .uuid(WORKFLOW_ID)
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow().withOrchestrationWorkflowType(OrchestrationWorkflowType.BASIC).build())
            .build();
    Workflow workflow2 =
        aWorkflow()
            .accountId(ACCOUNT_ID)
            .uuid(WORKFLOW_ID + 2)
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow().withOrchestrationWorkflowType(OrchestrationWorkflowType.BASIC).build())
            .build();

    DeploymentMetadata deploymentMetadata =
        DeploymentMetadata.builder()
            .manifestRequiredServiceIds(asList(SERVICE_ID, SERVICE_ID + 2))
            .manifestVariables(asList(ManifestVariable.builder().serviceId(SERVICE_ID).build(),
                ManifestVariable.builder().serviceId(SERVICE_ID + 2).build()))
            .build();
    DeploymentMetadata deploymentMetadata2 =
        DeploymentMetadata.builder()
            .manifestRequiredServiceIds(asList(SERVICE_ID))
            .manifestVariables(asList(ManifestVariable.builder().serviceId(SERVICE_ID).build()))
            .build();
    PipelineStage ps1 =
        PipelineStage.builder()
            .pipelineStageElements(asList(PipelineStageElement.builder()
                                              .type("ENV_STATE")
                                              .disableAssertion("false")
                                              .properties(Collections.singletonMap("workflowId", WORKFLOW_ID))
                                              .build()))
            .build();
    PipelineStage ps2 =
        PipelineStage.builder()
            .pipelineStageElements(asList(PipelineStageElement.builder()
                                              .type("ENV_STATE")
                                              .disableAssertion("false")
                                              .properties(Collections.singletonMap("workflowId", WORKFLOW_ID + 2))
                                              .build()))
            .build();
    Pipeline pipeline = Pipeline.builder().appId(APP_ID).pipelineStages(asList(ps1, ps2)).build();

    when(mockWorkflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID)).thenReturn(workflow1);
    when(mockWorkflowService.readWorkflowWithoutServices(APP_ID, WORKFLOW_ID + 2)).thenReturn(workflow2);
    when(mockWorkflowService.fetchDeploymentMetadata(eq(APP_ID), eq(workflow1), any(), any(), any(), eq(false), any(),
             eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(deploymentMetadata);
    when(mockWorkflowService.fetchDeploymentMetadata(eq(APP_ID), eq(workflow2), any(), any(), any(), eq(false), any(),
             eq(DeploymentMetadata.Include.ARTIFACT_SERVICE)))
        .thenReturn(deploymentMetadata2);
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);

    DeploymentMetadata pipelineDeploymentMetadata = pipelineServiceImpl.fetchDeploymentMetadata(
        APP_ID, pipeline, Collections.EMPTY_LIST, Collections.EMPTY_LIST, DeploymentMetadata.Include.ARTIFACT_SERVICE);
    assertThat(pipelineDeploymentMetadata).isNotNull();
    assertThat(pipelineDeploymentMetadata.getManifestRequiredServiceIds()).containsExactly(SERVICE_ID, SERVICE_ID + 2);
    assertThat(pipelineDeploymentMetadata.getManifestVariables()).hasSize(2);
    assertThat(pipelineDeploymentMetadata.getManifestVariables().get(0).getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(pipelineDeploymentMetadata.getManifestVariables().get(0).getWorkflowIds())
        .containsExactly(WORKFLOW_ID, WORKFLOW_ID + 2);
    assertThat(pipelineDeploymentMetadata.getManifestVariables().get(1).getServiceId()).isEqualTo(SERVICE_ID + 2);
    assertThat(pipelineDeploymentMetadata.getManifestVariables().get(1).getWorkflowIds()).containsExactly(WORKFLOW_ID);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesRequired() {
    Variable var1 = aVariable().name("var1").mandatory(true).build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 = aVariable().name("var1").mandatory(false).build();
    pipelineVars.add(existVar1);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1");
    assertThat(pipelineVars.size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getName()).isEqualTo("var1");
    assertThat(pipelineVars.get(0).isMandatory()).isEqualTo(true);

    Variable var2 = aVariable().name("var2").mandatory(false).build();
    List<Variable> pipelineVars1 = new ArrayList<>();
    Variable existVar2 = aVariable().name("var2").mandatory(true).build();
    pipelineVars1.add(existVar2);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var2, false, pipelineVars1, var2.getName(), "var2");
    assertThat(pipelineVars1.size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getName()).isEqualTo("var2");
    assertThat(pipelineVars1.get(0).isMandatory()).isEqualTo(true);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesDefaultValue() {
    Variable var1 = aVariable().name("var1").value("value1").build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 = aVariable().name("var1").value("value2").build();
    pipelineVars.add(existVar1);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1");
    assertThat(pipelineVars.size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getName()).isEqualTo("var1");
    assertThat(pipelineVars.get(0).getValue()).isEqualTo("value1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesAllowedValues() {
    Variable var1 =
        aVariable().name("var1").allowedList(asList("value2", "value3")).allowedValues("value2,value3").build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 = aVariable()
                             .name("var1")
                             .allowedList(asList("value1", "value2", "value3"))
                             .allowedValues("value1,value2,value3")
                             .build();
    pipelineVars.add(existVar1);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1");
    assertThat(pipelineVars.size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getName()).isEqualTo("var1");
    assertThat(pipelineVars.get(0).getAllowedList().size()).isEqualTo(2);
    assertThat(pipelineVars.get(0).getAllowedValues()).isEqualTo("value2,value3");

    Variable var2 =
        aVariable().name("var2").allowedList(asList("value2", "value3")).allowedValues("value2,value3").build();
    List<Variable> pipelineVars1 = new ArrayList<>();
    Variable existVar2 = aVariable().name("var2").build();
    pipelineVars1.add(existVar2);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var2, false, pipelineVars1, var2.getName(), "var2");
    assertThat(pipelineVars1.size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getName()).isEqualTo("var2");
    assertThat(pipelineVars1.get(0).getAllowedList().size()).isEqualTo(2);
    assertThat(pipelineVars1.get(0).getAllowedValues()).isEqualTo("value2,value3");

    Variable var3 = aVariable().name("var3").build();
    List<Variable> pipelineVars2 = new ArrayList<>();
    Variable existVar3 = aVariable()
                             .name("var3")
                             .allowedList(asList("value1", "value2", "value3"))
                             .allowedValues("value1,value2,value3")
                             .build();
    pipelineVars2.add(existVar3);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var3, false, pipelineVars2, var3.getName(), "var3");
    assertThat(pipelineVars2.size()).isEqualTo(1);
    assertThat(pipelineVars2.get(0).getName()).isEqualTo("var3");
    assertThat(pipelineVars2.get(0).getAllowedList().size()).isEqualTo(3);
    assertThat(pipelineVars2.get(0).getAllowedValues()).isEqualTo("value1,value2,value3");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesAllowedValuesException() {
    Variable var1 =
        aVariable().name("var1").allowedList(asList("value2", "value3")).allowedValues("value2,value3").build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 = aVariable()
                             .name("var1")
                             .allowedList(asList("value1", "value4", "value5"))
                             .allowedValues("value1,value4,value5")
                             .build();
    pipelineVars.add(existVar1);
    assertThatThrownBy(
        () -> pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Variable var1 does not have any common allowed values between all stages");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesDefaultValueFalseOverride1() {
    Variable var1 = aVariable()
                        .name("var1")
                        .allowedList(asList("value2", "value3"))
                        .allowedValues("value2,value3")
                        .value("value3")
                        .build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 =
        aVariable().name("var1").allowedList(asList("value1", "value2")).allowedValues("value1,value2").build();
    pipelineVars.add(existVar1);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1");
    assertThat(pipelineVars.size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getName()).isEqualTo("var1");
    assertThat(pipelineVars.get(0).getAllowedList().size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getAllowedValues()).isEqualTo("value2");
    assertThat(pipelineVars.get(0).getValue()).isEqualTo(null);

    Variable var2 = aVariable()
                        .name("var2")
                        .allowedList(asList("value2", "value3"))
                        .allowedValues("value2,value3")
                        .value("value3")
                        .build();
    List<Variable> pipelineVars1 = new ArrayList<>();
    Variable existVar2 = aVariable()
                             .name("var2")
                             .allowedList(asList("value1", "value2"))
                             .allowedValues("value1,value2")
                             .value("value2")
                             .build();
    pipelineVars1.add(existVar2);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var2, false, pipelineVars1, var2.getName(), "var2");
    assertThat(pipelineVars1.size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getName()).isEqualTo("var2");
    assertThat(pipelineVars1.get(0).getAllowedList().size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getAllowedValues()).isEqualTo("value2");
    assertThat(pipelineVars1.get(0).getValue()).isEqualTo("value2");

    Variable var3 = aVariable()
                        .name("var3")
                        .allowedList(asList("value2", "value3", "value4"))
                        .allowedValues("value2,value3,value4")
                        .value("value3")
                        .build();
    List<Variable> pipelineVars2 = new ArrayList<>();
    Variable existVar3 = aVariable()
                             .name("var3")
                             .allowedList(asList("value1", "value2", "value3"))
                             .allowedValues("value1,value2,value3")
                             .value("value2")
                             .build();
    pipelineVars2.add(existVar3);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var3, false, pipelineVars2, var3.getName(), "var3");
    assertThat(pipelineVars2.size()).isEqualTo(1);
    assertThat(pipelineVars2.get(0).getName()).isEqualTo("var3");
    assertThat(pipelineVars2.get(0).getAllowedList().size()).isEqualTo(2);
    assertThat(pipelineVars2.get(0).getAllowedValues()).isEqualTo("value2,value3");
    assertThat(pipelineVars2.get(0).getValue()).isEqualTo("value3");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_mergeNonEntityPipelineVariablesDefaultValueFalseOverride2() {
    Variable var1 =
        aVariable().name("var1").allowedList(asList("value2", "value3")).allowedValues("value2,value3").build();
    List<Variable> pipelineVars = new ArrayList<>();
    Variable existVar1 = aVariable()
                             .name("var1")
                             .allowedList(asList("value1", "value2"))
                             .allowedValues("value1,value2")
                             .value("value1")
                             .build();
    pipelineVars.add(existVar1);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var1, false, pipelineVars, var1.getName(), "var1");
    assertThat(pipelineVars.size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getName()).isEqualTo("var1");
    assertThat(pipelineVars.get(0).getAllowedList().size()).isEqualTo(1);
    assertThat(pipelineVars.get(0).getAllowedValues()).isEqualTo("value2");
    assertThat(pipelineVars.get(0).getValue()).isEqualTo(null);

    Variable var2 = aVariable()
                        .name("var2")
                        .allowedList(asList("value2", "value3"))
                        .allowedValues("value2,value3")
                        .value("value2")
                        .build();
    List<Variable> pipelineVars1 = new ArrayList<>();
    Variable existVar2 = aVariable()
                             .name("var2")
                             .allowedList(asList("value1", "value2"))
                             .allowedValues("value1,value2")
                             .value("value1")
                             .build();
    pipelineVars1.add(existVar2);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var2, false, pipelineVars1, var2.getName(), "var2");
    assertThat(pipelineVars1.size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getName()).isEqualTo("var2");
    assertThat(pipelineVars1.get(0).getAllowedList().size()).isEqualTo(1);
    assertThat(pipelineVars1.get(0).getAllowedValues()).isEqualTo("value2");
    assertThat(pipelineVars1.get(0).getValue()).isEqualTo("value2");

    Variable var3 = aVariable()
                        .name("var3")
                        .allowedList(asList("value2", "value3", "value4"))
                        .allowedValues("value2,value3,value4")
                        .value("value3")
                        .build();
    List<Variable> pipelineVars2 = new ArrayList<>();
    Variable existVar3 = aVariable()
                             .name("var3")
                             .allowedList(asList("value1", "value2", "value5"))
                             .allowedValues("value1,value2,value5")
                             .value("value5")
                             .build();
    pipelineVars2.add(existVar3);
    pipelineServiceImpl.mergeNonEntityPipelineVariables(var3, false, pipelineVars2, var3.getName(), "var3");
    assertThat(pipelineVars2.size()).isEqualTo(1);
    assertThat(pipelineVars2.get(0).getName()).isEqualTo("var3");
    assertThat(pipelineVars2.get(0).getAllowedList().size()).isEqualTo(1);
    assertThat(pipelineVars2.get(0).getAllowedValues()).isEqualTo("value2");
    assertThat(pipelineVars2.get(0).getValue()).isEqualTo(null);
  }
}
