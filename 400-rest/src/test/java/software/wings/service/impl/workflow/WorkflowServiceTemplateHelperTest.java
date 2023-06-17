/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.workflow.StepSkipStrategy.Scope.SPECIFIC_STEPS;
import static software.wings.sm.StateType.HTTP;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStep.PhaseStepBuilder;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Variable;
import software.wings.beans.Variable.VariableBuilder;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.template.Template;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;
import software.wings.sm.StepType;
import software.wings.sm.states.HelmDeployState.HelmDeployStateKeys;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public class WorkflowServiceTemplateHelperTest extends WingsBaseTest {
  @Inject @InjectMocks private WorkflowServiceTemplateHelper workflowServiceTemplateHelper;
  @Mock private TemplateService templateService;

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testSaveWorkflowWithInvalidProperties() {
    Map<String, Object> properties = new HashMap<>();
    Integer timeout = WorkflowServiceTemplateHelper.MAXIMUM_TIMEOUT + 20;
    properties.put("timeoutMillis", timeout);

    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.APPROVAL.toString())
                                              .properties(properties)
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.validatePhaseStepsProperties(newPhaseStep);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testSaveWorkflowWithStringTimeout() {
    Map<String, Object> properties = new HashMap<>();
    Integer timeout = WorkflowServiceTemplateHelper.MAXIMUM_TIMEOUT + 20;
    properties.put("timeoutMillis", timeout.toString());

    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.APPROVAL.toString())
                                              .properties(properties)
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.validatePhaseStepsProperties(newPhaseStep);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSaveHelmDeployState() {
    Map<String, Object> newProperties = new HashMap<>();
    newProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "new-release-prod");
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(newProperties)
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .id("id1")
                                              .name("test-helm-new")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(Maps.newHashMap(newProperties))
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, null, false);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(
        newPhaseStep, PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY).build(), false);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSaveHelmDeployStateFromYaml() {
    Map<String, Object> newProperties = new HashMap<>();
    newProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "new-release-prod");
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(newProperties)
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .name("test-helm-new")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(Maps.newHashMap(newProperties))
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, null, true);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(
        newPhaseStep, PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY).build(), true);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateHelmStateReleaseName() {
    Map<String, Object> oldProperties = new HashMap<>();
    oldProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "release-prod");
    PhaseStep oldPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(oldProperties)
                                              .build())
                                 .build();

    Map<String, Object> newProperties = new HashMap<>();
    newProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "new-release-prod");
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(newProperties)
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .id("id1")
                                              .name("test-helm-new")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(Maps.newHashMap(newProperties))
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, oldPhaseStep, false);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateHelmStateReleaseNameFromYaml() {
    Map<String, Object> oldProperties = new HashMap<>();
    oldProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "release-prod");
    PhaseStep oldPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .id("id")
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(oldProperties)
                                              .build())
                                 .build();

    Map<String, Object> newProperties = new HashMap<>();
    newProperties.put(HelmDeployStateKeys.helmReleaseNamePrefix, "new-release-prod");
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.HELM_DEPLOY, "Helm Deploy")
                                 .addStep(GraphNode.builder()
                                              .name("test-helm")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(newProperties)
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .name("test-helm-new")
                                              .type(StepType.HELM_DEPLOY.toString())
                                              .properties(Maps.newHashMap(newProperties))
                                              .build())
                                 .build();

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, oldPhaseStep, true);
    assertThat(newPhaseStep.getSteps().get(0).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("release-prod");
    assertThat(newPhaseStep.getSteps().get(1).getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix))
        .isEqualTo("new-release-prod");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldSetTemplateVariablesGivenByUser() {
    Variable var1 = VariableBuilder.aVariable().name("variable").value("from-yaml").build();
    Variable var2 = VariableBuilder.aVariable().name("variable").value("from-template").build();
    Variable httpVar1 = VariableBuilder.aVariable().name("http-variable").value("http-from-yaml").build();
    Variable httpVar2 = VariableBuilder.aVariable().name("http-variable").value("http-from-template").build();
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.PRE_DEPLOYMENT, "Pre deployment")
                                 .addStep(GraphNode.builder()
                                              .name("test")
                                              .type(StepType.SHELL_SCRIPT.toString())
                                              .templateUuid("uuid")
                                              .templateVersion("latest")
                                              .templateVariables(Collections.singletonList(var1))
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .name("http-template-test")
                                              .type(StepType.HTTP.toString())
                                              .templateUuid("uuid-2")
                                              .templateVersion("latest")
                                              .templateVariables(Collections.singletonList(httpVar1))
                                              .build())
                                 .build();

    Template template = Template.builder().build();
    GraphNode graphNode = GraphNode.builder().templateVariables(Collections.singletonList(var2)).build();
    GraphNode httpGraphNode = GraphNode.builder().templateVariables(Collections.singletonList(httpVar2)).build();
    when(templateService.get(anyString(), anyString())).thenReturn(template);
    when(templateService.constructEntityFromTemplate(template, EntityType.WORKFLOW)).thenAnswer(new Answer() {
      private int count = 0;
      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return graphNode;
        }
        return httpGraphNode;
      }
    });

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, null, true);
    assertThat(newPhaseStep.getSteps()).isNotNull();
    assertThat(newPhaseStep.getSteps()).hasSize(2);
    assertThat(newPhaseStep.getSteps().get(0).getTemplateVariables()).isNotNull();
    assertThat(newPhaseStep.getSteps().get(0).getTemplateVariables().get(0).getValue()).isEqualTo("from-yaml");
    assertThat(newPhaseStep.getSteps().get(1).getTemplateVariables()).isNotNull();
    assertThat(newPhaseStep.getSteps().get(1).getTemplateVariables().get(0).getValue()).isEqualTo("http-from-yaml");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void ignoreExtraVariablesAddedFromTemplate() {
    Variable var1 = VariableBuilder.aVariable().name("variable").value("from-yaml").build();
    Variable var2 = VariableBuilder.aVariable().name("variable").value("from-template").build();
    Variable httpVar1 = VariableBuilder.aVariable().name("http-variable").value("http-from-yaml").build();
    Variable httpVar2 = VariableBuilder.aVariable().name("http-variable").value("http-from-template").build();
    PhaseStep newPhaseStep = PhaseStepBuilder.aPhaseStep(PhaseStepType.PRE_DEPLOYMENT, "Pre deployment")
                                 .addStep(GraphNode.builder()
                                              .name("test")
                                              .type(StepType.SHELL_SCRIPT.toString())
                                              .templateUuid("uuid")
                                              .templateVersion("latest")
                                              .templateVariables(asList(var1, httpVar1))
                                              .build())
                                 .addStep(GraphNode.builder()
                                              .name("http-template-test")
                                              .type(StepType.HTTP.toString())
                                              .templateUuid("uuid-2")
                                              .templateVersion("latest")
                                              .templateVariables(asList(httpVar1, var1))
                                              .build())
                                 .build();

    Template template = Template.builder().build();
    GraphNode graphNode = GraphNode.builder().templateVariables(Collections.singletonList(var2)).build();
    GraphNode httpGraphNode = GraphNode.builder().templateVariables(Collections.singletonList(httpVar2)).build();
    when(templateService.get(anyString(), anyString())).thenReturn(template);
    when(templateService.constructEntityFromTemplate(template, EntityType.WORKFLOW)).thenAnswer(new Answer() {
      private int count = 0;
      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return graphNode;
        }
        return httpGraphNode;
      }
    });

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(newPhaseStep, null, true);
    assertThat(newPhaseStep.getSteps()).isNotNull();
    assertThat(newPhaseStep.getSteps()).hasSize(2);
    assertThat(newPhaseStep.getSteps().get(0).getTemplateVariables()).isNotNull().hasSize(1);
    assertThat(newPhaseStep.getSteps().get(0).getTemplateVariables().get(0).getValue()).isEqualTo("from-yaml");
    assertThat(newPhaseStep.getSteps().get(1).getTemplateVariables()).isNotNull().hasSize(1);
    assertThat(newPhaseStep.getSteps().get(1).getTemplateVariables().get(0).getValue()).isEqualTo("http-from-yaml");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotChangePhaseStepUuidAndStepIdsOnWorkflowUpdate() {
    List<WorkflowPhase> oldWorkflowPhases = getWorkflowPhases("oldWorkflowPhase");
    List<WorkflowPhase> newWorkflowPhases = getWorkflowPhases("newWorkflowPhase");

    workflowServiceTemplateHelper.updateLinkedWorkflowPhases(newWorkflowPhases, oldWorkflowPhases, true);
    assertThat(newWorkflowPhases).isNotNull().hasSize(1);
    assertThat(oldWorkflowPhases).isNotNull().hasSize(1);
    List<PhaseStep> newPhaseSteps = newWorkflowPhases.get(0).getPhaseSteps();
    List<PhaseStep> oldPhaseSteps = oldWorkflowPhases.get(0).getPhaseSteps();
    assertThat(newPhaseSteps).isNotNull().hasSize(1);
    assertThat(oldPhaseSteps).isNotNull().hasSize(1);
    assertThat(newPhaseSteps.get(0).getUuid()).isEqualTo(oldPhaseSteps.get(0).getUuid());
    List<GraphNode> newSteps = newPhaseSteps.get(0).getSteps();
    List<GraphNode> oldSteps = oldPhaseSteps.get(0).getSteps();
    assertThat(newSteps).isNotNull().hasSize(2);
    assertThat(oldSteps).isNotNull().hasSize(2);
    assertThat(newSteps.get(0).getId()).isEqualTo(oldSteps.get(0).getId());
    assertThat(newSteps.get(1).getId()).isEqualTo(oldSteps.get(1).getId());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotChangeStepSkipAssertionsStepIdsOnWorkflowUpdate() {
    List<WorkflowPhase> oldWorkflowPhases = getWorkflowPhases("oldWorkflowPhase");
    List<WorkflowPhase> newWorkflowPhases = getWorkflowPhases("newWorkflowPhase");

    workflowServiceTemplateHelper.updateLinkedWorkflowPhases(newWorkflowPhases, oldWorkflowPhases, true);
    assertThat(newWorkflowPhases).isNotNull().hasSize(1);
    assertThat(oldWorkflowPhases).isNotNull().hasSize(1);
    List<PhaseStep> newPhaseSteps = newWorkflowPhases.get(0).getPhaseSteps();
    List<PhaseStep> oldPhaseSteps = oldWorkflowPhases.get(0).getPhaseSteps();
    assertThat(newPhaseSteps).isNotNull().hasSize(1);
    assertThat(oldPhaseSteps).isNotNull().hasSize(1);
    assertThat(newPhaseSteps.get(0).getUuid()).isEqualTo(oldPhaseSteps.get(0).getUuid());
    assertThat(newPhaseSteps.get(0).getStepSkipStrategies()).isNotNull().hasSize(1);
    assertThat(oldPhaseSteps.get(0).getStepSkipStrategies()).isNotNull().hasSize(1);
    List<String> newStepIds = newPhaseSteps.get(0).getStepSkipStrategies().get(0).getStepIds();
    List<String> oldStepIds = oldPhaseSteps.get(0).getStepSkipStrategies().get(0).getStepIds();
    assertThat(newStepIds).isNotNull().hasSize(2);
    assertThat(oldStepIds).isNotNull().hasSize(2);
    assertThat(newStepIds).isEqualTo(oldStepIds);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotChangePhaseStepIdsOnWorkflowUpdate() {
    PhaseStep oldPhaseStep = getPhaseStep();
    PhaseStep newPhaseStep = getPhaseStep();

    when(templateService.get(anyString(), anyString())).thenReturn(Template.builder().build());
    when(templateService.constructEntityFromTemplate(any(), any())).thenReturn(GraphNode.builder().build());

    workflowServiceTemplateHelper.updateLinkedPhaseStepTemplate(oldPhaseStep, newPhaseStep, true);

    assertThat(newPhaseStep.getUuid()).isEqualTo(oldPhaseStep.getUuid());
  }

  private PhaseStep getPhaseStep() {
    return PhaseStepBuilder.aPhaseStep(PhaseStepType.PRE_DEPLOYMENT, "Pre deployment")
        .addStep(GraphNode.builder()
                     .name("test")
                     .type(StepType.SHELL_SCRIPT.toString())
                     .templateUuid("uuid")
                     .templateVersion("latest")
                     .build())
        .addStep(GraphNode.builder()
                     .name("http-template-test")
                     .type(StepType.HTTP.toString())
                     .templateUuid("uuid-2")
                     .templateVersion("latest")
                     .build())
        .build();
  }

  private List<WorkflowPhase> getWorkflowPhases(String baseId) {
    int i = 0;
    GraphNode step = GraphNode.builder().id(baseId + i++).name("Ping Response").type(HTTP.name()).build();

    GraphNode step2 =
        GraphNode.builder().id(baseId + i++).name("Shell script").type(StateType.SHELL_SCRIPT.name()).build();

    PhaseStep phaseStep = PhaseStepBuilder.aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.VERIFY_SERVICE, baseId + i)
                              .addStep(step)
                              .addStep(step2)
                              .withStepSkipStrategies(Collections.singletonList(
                                  new StepSkipStrategy(SPECIFIC_STEPS, asList(baseId + "0", baseId + "1"), "1==1")))
                              .build();

    WorkflowPhase workflowPhase = aWorkflowPhase()
                                      .uuid(UUID)
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .infraDefinitionId(INFRA_DEFINITION_ID)
                                      .serviceId(SERVICE_ID)
                                      .deploymentType(SSH)
                                      .phaseSteps(Collections.singletonList(phaseStep))
                                      .build();

    return Collections.singletonList(workflowPhase);
  }
}
