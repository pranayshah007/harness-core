/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GenericStepPMSFilterJsonCreatorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testWithConnectorRef() throws IOException {
    DummyGenericStepFilterCreator dummyGenericStepFilterCreator = new DummyGenericStepFilterCreator();
    TestObjectWithConnectorRef testObjectWithConnectorRef = TestObjectWithConnectorRef.builder().build();
    StepElementConfig stepElementConfig = StepElementConfig.builder().stepSpecType(testObjectWithConnectorRef).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-test.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    YamlNode stepsNode =
        stage1Node.getField("spec").getNode().getField("execution").getNode().getField("steps").getNode();

    YamlNode step1Node = stepsNode.asArray().get(0).getField("step").getNode();

    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .currentField(new YamlField("test", step1Node))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
            .build();
    FilterCreationResponse filterCreationResponse =
        dummyGenericStepFilterCreator.handleNode(filterCreationContext, stepElementConfig);
    assertThat(filterCreationResponse.getReferredEntities()).isNotEmpty();
    assertThat(filterCreationResponse.getReferredEntities().get(0))
        .isEqualTo(FilterCreatorHelper.convertToEntityDetailProtoDTO(ACCOUNT_ID, ORG_ID, PROJECT_ID,
            "pipeline.stages.managerDeployment.spec.execution.steps.managerCanary.spec.connectorRef",
            ParameterField.createValueField("connectorRef"), EntityTypeProtoEnum.CONNECTORS));
  }
}
