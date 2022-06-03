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

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServicePlanCreatorHelperTest extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;

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
}
