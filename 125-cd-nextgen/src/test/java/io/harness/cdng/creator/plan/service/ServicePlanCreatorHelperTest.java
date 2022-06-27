/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode.StepType;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServicePlanCreatorHelperTest extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;
  @Mock ServiceEntityService serviceEntityService;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String SERVICE_IDENTIFIER = "service1";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDependenciesForServiceForV1() throws IOException {
    YamlField serviceField = getYamlFieldFromPath("cdng/plan/service.yml");

    String serviceNodeId = serviceField.getNode().getUuid();
    byte[] dummyValue = new byte[10];
    doReturn(dummyValue).when(kryoSerializer).asDeflatedBytes(any());
    Dependencies dependencies = ServicePlanCreatorHelper.getDependenciesForService(serviceField,
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .serviceConfig(ServiceConfig.builder().build())
                                       .infrastructure(PipelineInfrastructure.builder().build())
                                       .build())
            .build(),
        "environmentUuid", "infraSectionUuid", kryoSerializer);
    assertThat(dependencies).isNotEqualTo(null);
    assertThat(dependencies.getDependenciesMap().containsKey(serviceNodeId)).isEqualTo(true);
    assertThat(dependencies.getDependencyMetadataMap()
                   .get(serviceNodeId)
                   .containsMetadata(YamlTypes.INFRASTRUCTURE_STEP_PARAMETERS))
        .isEqualTo(true);
    assertThat(
        dependencies.getDependencyMetadataMap().get(serviceNodeId).containsMetadata(YamlTypes.ENVIRONMENT_NODE_ID))
        .isEqualTo(true);
  }

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetResolvedServiceFieldForV2() throws IOException {
    DeploymentStageNode stageNode =
        DeploymentStageNode.builder()
            .type(StepType.Deployment)
            .deploymentStageConfig(
                DeploymentStageConfig.builder()
                    .service(ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("service1")).build())
                    .build())
            .build();
    stageNode.setIdentifier("stage1");
    Map<String, PlanCreationContextValue> globalContext = new HashMap<>();
    globalContext.put("metadata",
        PlanCreationContextValue.newBuilder()
            .setAccountIdentifier(ACCOUNT_ID)
            .setOrgIdentifier(ORG_IDENTIFIER)
            .setProjectIdentifier(PROJ_IDENTIFIER)
            .build());
    PlanCreationContext context = PlanCreationContext.builder().globalContext(globalContext).build();

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("cdng/serviceV2.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();
    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();
    // Stage1 spec Node
    YamlField specField = stage1Node.getField("spec");

    String serviceYaml = "service:\n"
        + "    name: service1\n"
        + "    identifier: service1\n"
        + "    tags: {}\n"
        + "    gitOpsEnabled: false\n"
        + "    serviceDefinition:\n"
        + "        spec:\n"
        + "            variables:\n"
        + "                - name: var1\n"
        + "                  type: String\n"
        + "                  value: value1\n"
        + "                - name: var2\n"
        + "                  type: String\n"
        + "                  value: value2\n"
        + "        type: Kubernetes\n"
        + "    description: \"\"\n";

    ServiceEntity serviceEntity =
        ServiceEntity.builder().name(SERVICE_IDENTIFIER).name(SERVICE_IDENTIFIER).yaml(serviceYaml).build();
    when(serviceEntityService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(Optional.of(serviceEntity));
    YamlField serviceFieldForV2 =
        ServicePlanCreatorHelper.getResolvedServiceFieldForV2(stageNode, serviceEntityService, specField, context);
    assertThat(serviceFieldForV2).isNotNull();
    assertThat(serviceFieldForV2.getNode().getField(YamlTypes.SERVICE_DEFINITION)).isNotNull();
  }
}
