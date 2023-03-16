/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.infrastructure;

import static io.harness.cdng.creator.plan.infrastructure.InfraRollbackPMSPlanCreator.INFRA_ROLLBACK_NODE_ID_SUFFIX;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class InfraRollbackPMSPlanCreatorTest extends CDNGTestBase {
  ObjectMapper mapper = new ObjectMapper();

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackPlanForV1InfraProvisioner() {
    DeploymentStageNode node = getDeploymentStageConfigProvisionerV1();
    node.setFailureStrategies(
        ParameterField.createValueField(List.of(FailureStrategyConfig.builder()
                                                    .onFailure(OnFailureConfig.builder()
                                                                   .errors(List.of(NGFailureType.ALL_ERRORS))
                                                                   .action(AbortFailureActionConfig.builder().build())
                                                                   .build())
                                                    .build())));

    JsonNode jsonNode = mapper.valueToTree(node);
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
                                  .currentField(new YamlField(new YamlNode("spec", jsonNode)))
                                  .build();

    PlanCreationResponse planCreationResponse = InfraRollbackPMSPlanCreator.createInfraRollbackPlan(
        ctx.getCurrentField().getNode().getField("spec").getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE));

    assertThat(planCreationResponse).isNotNull();
    assertThat(planCreationResponse.getNodes().size()).isEqualTo(1);
    assertThat(planCreationResponse.getNodes()).containsKey("pipelineinfra" + INFRA_ROLLBACK_NODE_ID_SUFFIX);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRollbackPlanForV2InfraProvisioner() {
    DeploymentStageNode node = getDeploymentStageConfigProvisionerV2();
    node.setFailureStrategies(
        ParameterField.createValueField(List.of(FailureStrategyConfig.builder()
                                                    .onFailure(OnFailureConfig.builder()
                                                                   .errors(List.of(NGFailureType.ALL_ERRORS))
                                                                   .action(AbortFailureActionConfig.builder().build())
                                                                   .build())
                                                    .build())));

    JsonNode jsonNode = mapper.valueToTree(node);
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
                                  .currentField(new YamlField(new YamlNode("spec", jsonNode)))
                                  .build();

    PlanCreationResponse planCreationResponse = InfraRollbackPMSPlanCreator.createProvisionerRollbackPlan(
        ctx.getCurrentField().getNode().getField("spec").getNode().getField(YamlTypes.ENVIRONMENT_YAML));

    assertThat(planCreationResponse).isNotNull();
    assertThat(planCreationResponse.getNodes().size()).isEqualTo(1);
    assertThat(planCreationResponse.getNodes()).containsKey("v2env" + INFRA_ROLLBACK_NODE_ID_SUFFIX);
  }

  private DeploymentStageNode getDeploymentStageConfigProvisionerV1() {
    Map<String, Object> step = Map.of("name", "testprovisionstep");
    Map<String, Object> rollbackstep = Map.of("name", "testprovisionrollbackstep");
    final DeploymentStageConfig deploymentStageConfig =
        DeploymentStageConfig.builder()
            .uuid("uuid")
            .infrastructure(
                PipelineInfrastructure.builder()
                    .uuid("pipelineinfra")
                    .infrastructureDefinition(
                        InfrastructureDef.builder()
                            .provisioner(
                                ExecutionElementConfig.builder()
                                    .uuid("provisioner")
                                    .steps(List.of(
                                        ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                                    .rollbackSteps(List.of(ExecutionWrapperConfig.builder()
                                                               .step(mapper.valueToTree(rollbackstep))
                                                               .build()))
                                    .build())
                            .build())
                    .build())
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .build();
    return buildNode(deploymentStageConfig);
  }

  private DeploymentStageNode getDeploymentStageConfigProvisionerV2() {
    Map<String, Object> step = Map.of("name", "testprovisionstep");
    Map<String, Object> rollbackstep = Map.of("name", "testprovisionrollbackstep");
    final DeploymentStageConfig deploymentStageConfig =
        DeploymentStageConfig.builder()
            .uuid("uuid")
            .infrastructure(
                PipelineInfrastructure.builder().infrastructureDefinition(InfrastructureDef.builder().build()).build())
            .environment(
                EnvironmentYamlV2.builder()
                    .uuid("v2env")
                    .provisioner(
                        ExecutionElementConfig.builder()
                            .uuid("provisioner")
                            .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                            .rollbackSteps(List.of(
                                ExecutionWrapperConfig.builder().step(mapper.valueToTree(rollbackstep)).build()))
                            .build())
                    .build())
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .build();
    return buildNode(deploymentStageConfig);
  }

  private DeploymentStageNode buildNode(DeploymentStageConfig config) {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setUuid("nodeuuid");
    node.setDeploymentStageConfig(config);
    return node;
  }
}
