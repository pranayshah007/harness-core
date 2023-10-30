/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.graphRendering;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.POOJA;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.INFRASTRUCTURE_NODE;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.SELECT_NODE_NAME;
import static software.wings.sm.StateType.DC_NODE_SELECT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.ff.FeatureFlagService;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.WorkflowGenerator;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GraphGroup;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.execution.StateExecutionElement;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.StateType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.GenericType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class GraphRenderingFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private FeatureFlagService featureFlagService;

  private final Seed seed = new Seed(0);

  private Owners owners;
  private Application application;
  private Environment environment;
  private InfrastructureDefinition infrastructureDefinition;
  private Service service;

  @Before
  public void setUp() {
    owners = ownerManager.create();

    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();

    service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    assertThat(service).isNotNull();

    environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    assertThat(environment).isNotNull();

    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.PHYSICAL_SSH_TEST);
    assertThat(infrastructureDefinition).isNotNull();
  }

  @Test
  @Owner(developers = POOJA)
  @Category({FunctionalTests.class})
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testGraphWithoutAggregation() {
    resetCache(application.getAccountId());
    Workflow workflowNoAggregation = createNodeAggregationWorkflow(5);
    workflowNoAggregation =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(),
            workflowNoAggregation); // workflowGenerator.ensureWorkflow(seed, owners, workflowNoAggregation);

    assertThat(workflowNoAggregation.getUuid()).isNotNull();

    resetCache(application.getAccountId());

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getAppId(), environment.getUuid(),
        workflowNoAggregation.getUuid(), Collections.emptyList());
    assertThat(workflowExecution).isNotNull();

    workflowExecution =
        workflowExecutionService.getExecutionDetails(application.getUuid(), workflowExecution.getUuid(), true, false);
    assertThat(workflowExecution.getExecutionNode()).isNotNull();
    GraphNode executionGraph = workflowExecution.getExecutionNode();
    GraphNode phase1Graph = executionGraph.getNext();

    assertThat(phase1Graph).isNotNull();

    GraphGroup phase1Group = phase1Graph.getGroup();
    List<GraphNode> phase1Elements = phase1Group.getElements();

    assertThat(phase1Elements.size()).isEqualTo(1);
    GraphNode prepareInfraGraph = phase1Elements.get(0);
    GraphNode deployServiceGraph = prepareInfraGraph.getNext();
    assertThat(deployServiceGraph).isNotNull();
    GraphGroup deployServiceGroup = deployServiceGraph.getGroup();
    GraphNode repeatStateGraph = deployServiceGroup.getElements().get(0);
    assertThat(repeatStateGraph.getElementStatusSummary().size()).isEqualTo(5);

    // No aggregation so should have 5 child nodes
    assertThat(repeatStateGraph.getGroup().getElements().size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = POOJA, intermittent = true)
  @Category({FunctionalTests.class})
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testGraphWithAggregation() {
    resetCache(application.getAccountId());
    Workflow workflowNoAggregation = createNodeAggregationWorkflow(20);
    workflowNoAggregation =
        WorkflowRestUtils.createWorkflow(bearerToken, application.getAccountId(), application.getUuid(),
            workflowNoAggregation); // workflowGenerator.ensureWorkflow(seed, owners, workflowNoAggregation);

    assertThat(workflowNoAggregation.getUuid()).isNotNull();

    resetCache(application.getAccountId());

    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getAppId(), environment.getUuid(),
        workflowNoAggregation.getUuid(), Collections.emptyList());
    assertThat(workflowExecution).isNotNull();

    workflowExecution =
        workflowExecutionService.getExecutionDetails(application.getUuid(), workflowExecution.getUuid(), true, false);
    assertThat(workflowExecution.getExecutionNode()).isNotNull();
    GraphNode executionGraph = workflowExecution.getExecutionNode();
    GraphNode phase1Graph = executionGraph.getNext();

    assertThat(phase1Graph).isNotNull();

    GraphGroup phase1Group = phase1Graph.getGroup();
    List<GraphNode> phase1Elements = phase1Group.getElements();

    assertThat(phase1Elements.size()).isEqualTo(1);
    GraphNode prepareInfraGraph = phase1Elements.get(0);
    GraphNode deployServiceGraph = prepareInfraGraph.getNext();
    assertThat(deployServiceGraph).isNotNull();
    GraphGroup deployServiceGroup = deployServiceGraph.getGroup();
    GraphNode repeatStateGraph = deployServiceGroup.getElements().get(0);
    assertThat(repeatStateGraph.getElementStatusSummary().size()).isEqualTo(20);

    //    //With aggregation so should have 1 child nodes
    assertThat(repeatStateGraph.getGroup().getElements().size()).isEqualTo(1);
    GraphNode aggregateElement = repeatStateGraph.getGroup().getElements().get(0);
    assertThat(aggregateElement.getName()).isEqualTo("20 instances");

    // validate elements api response
    GenericType<RestResponse<List<StateExecutionElement>>> type =
        new GenericType<RestResponse<List<StateExecutionElement>>>() {};

    RestResponse<List<StateExecutionElement>> elementsListResponse =
        Setup.portal()
            .auth()
            .oauth2(bearerToken)
            .queryParam("accountId", application.getAccountId())
            .queryParam("appId", application.getUuid())
            .pathParam("workflowExecutionId", workflowExecution.getUuid())
            .pathParam("stateExecutionInstanceId", repeatStateGraph.getId())
            .contentType(ContentType.JSON)
            .get("/executions/{workflowExecutionId}/element/{stateExecutionInstanceId}")
            .as(type.getType());

    List<StateExecutionElement> elementList = elementsListResponse.getResource();
    assertThat(elementList).isNotNull();
    assertThat(elementList.size()).isEqualTo(20);

    Map<String, List<String>> body = new HashMap<>();
    body.put(repeatStateGraph.getId(), Collections.singletonList(elementList.get(0).getExecutionContextElementId()));
    // validate subgraph api respone
    GenericType<RestResponse<Map<String, GraphGroup>>> nodeSubGraphType =
        new GenericType<RestResponse<Map<String, GraphGroup>>>() {};
    RestResponse<Map<String, GraphGroup>> subGraphResponse =
        Setup.portal()
            .auth()
            .oauth2(bearerToken)
            .queryParam("accountId", application.getAccountId())
            .queryParam("appId", application.getUuid())
            .pathParam("workflowExecutionId", workflowExecution.getUuid())
            .body(body)
            .contentType(ContentType.JSON)
            .post("/executions/nodeSubGraphs/{workflowExecutionId}/")
            .as(nodeSubGraphType.getType());

    Map<String, GraphGroup> subGraph = subGraphResponse.getResource();
    assertThat(subGraph).isNotNull();
    assertThat(subGraph.get(repeatStateGraph.getId()).getElements().size()).isEqualTo(1);
    assertThat(subGraph.get(repeatStateGraph.getId()).getElements().get(0).getId().contains(repeatStateGraph.getId()))
        .isTrue();
    assertThat(subGraph.get(repeatStateGraph.getId()).getElements().get(0).getName()).isEqualTo("host13");
  }

  private Workflow createNodeAggregationWorkflow(int numberOfHosts) {
    List<PhaseStep> phaseSteps = new ArrayList<>();

    Map<String, Object> selectNodeProperties = new HashMap<>();
    selectNodeProperties.put("specificHosts", false);
    selectNodeProperties.put("instanceCount", numberOfHosts);
    selectNodeProperties.put("excludeSelectedHostsFromFuturePhases", false);

    phaseSteps.add(aPhaseStep(INFRASTRUCTURE_NODE, WorkflowServiceHelper.INFRASTRUCTURE_NODE_NAME)
                       .withPhaseStepType(INFRASTRUCTURE_NODE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name(SELECT_NODE_NAME)
                                    .type(DC_NODE_SELECT.name())
                                    .properties(selectNodeProperties)
                                    .build())
                       .build());
    phaseSteps.add(aPhaseStep(PhaseStepType.DEPLOY_SERVICE, WorkflowServiceHelper.DEPLOY_SERVICE)
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .name("Shell script_1")
                                    .type(StateType.SHELL_SCRIPT.toString())
                                    .properties(ImmutableMap.<String, Object>builder()
                                                    .put("scriptType", "BASH")
                                                    .put("scriptString", "echo ${instance.hostName}")
                                                    .put("timeoutMillis", 60000)
                                                    .put("executeOnDelegate", true)
                                                    .build())
                                    .build())
                       .build());

    return aWorkflow()
        .name("Node Aggregation Test" + numberOfHosts + System.currentTimeMillis())
        .workflowType(WorkflowType.ORCHESTRATION)
        .appId(service.getAppId())
        .envId(infrastructureDefinition.getEnvId())
        .infraDefinitionId(infrastructureDefinition.getUuid())
        .serviceId(service.getUuid())
        .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                   .addWorkflowPhase(aWorkflowPhase()
                                                         .serviceId(service.getUuid())
                                                         .deploymentType(DeploymentType.SSH)
                                                         .daemonSet(false)
                                                         .infraDefinitionId(infrastructureDefinition.getUuid())
                                                         .infraDefinitionName(infrastructureDefinition.getName())
                                                         .phaseSteps(phaseSteps)
                                                         .build())
                                   .build())
        .build();
  }
}
