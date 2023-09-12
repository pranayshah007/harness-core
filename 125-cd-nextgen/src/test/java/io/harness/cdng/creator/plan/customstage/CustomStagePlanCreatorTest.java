/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.customstage;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CUSTOM;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.cdng.creator.plan.stage.CustomStagePlanCreator;
import io.harness.cdng.pipeline.beans.CustomStageSpecParams;
import io.harness.cdng.pipeline.steps.CustomStageStep;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class CustomStagePlanCreatorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks CustomStagePlanCreator customStagePlanCreator;

  private String SOURCE_PIPELINE_YAML;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String pipeline_yaml_filename = "customStage.yaml";
    SOURCE_PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
  }
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateSupportedStageTypes() {
    assertThat(customStagePlanCreator.getSupportedStageTypes()).isEqualTo(Collections.singleton(CUSTOM));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateGetStepType() {
    assertThat(customStagePlanCreator.getStepType(null)).isEqualTo(CustomStageStep.STEP_TYPE);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateSpecParameters() {
    String childNodeID = "temp";
    CustomStageSpecParams customStageSpecParams = CustomStageSpecParams.builder().childNodeID(childNodeID).build();
    assertThat(customStagePlanCreator.getSpecParameters(childNodeID, null, null)).isEqualTo(customStageSpecParams);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateCustomStagePlanFieldClass() {
    assertThat(customStagePlanCreator.getFieldClass()).isInstanceOf(Class.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateCustomStagePlanForChildrenNodes() throws IOException {
    String yamlWithUuid = YamlUtils.injectUuid(SOURCE_PIPELINE_YAML);
    YamlField fullYamlFieldWithUuiD = YamlUtils.injectUuidInYamlField(yamlWithUuid);

    PlanCreationContext ctx = PlanCreationContext.builder().yaml(yamlWithUuid).build();
    ctx.setCurrentField(fullYamlFieldWithUuiD);

    CustomStageNode customStageNode = new CustomStageNode();
    customStageNode.setUuid("tempid");
    doReturn("temp".getBytes()).when(kryoSerializer).asDeflatedBytes(any());

    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(fullYamlFieldWithUuiD.getNode());
    assertThat(customStagePlanCreator.createPlanForChildrenNodes(ctx, customStageNode)).isNotNull();
    mockSettings.close();
  }
}
