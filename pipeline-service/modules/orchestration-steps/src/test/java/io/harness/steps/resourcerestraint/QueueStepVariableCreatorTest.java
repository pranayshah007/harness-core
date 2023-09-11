/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.resourceconstraint.QueueStepNode;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class QueueStepVariableCreatorTest {
  private QueueStepVariableCreator variableCreator;

  @Before
  public void setup() {
    variableCreator = new QueueStepVariableCreator();
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateSupportedStepTypes() {
    Set<String> supported = variableCreator.getSupportedStepTypes();
    assertThat(supported).hasSize(1);
    assertThat(supported).containsOnly(StepSpecTypeConstants.QUEUE);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateExtraPropertiesWithResourceRestraintOutcome() {
    YamlExtraProperties extraProperties =
        variableCreator.getStepExtraProperties("fqn", "localName", new QueueStepNode());
    List<YamlProperties> outputProperties = extraProperties.getOutputPropertiesList();
    assertThat(outputProperties).hasSize(1);
    assertYamlProperties(outputProperties.get(0), "fqn.output.resourceUnit", "localName.output.resourceUnit", true);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateExtraPropertiesWithSuperExtraProperties() {
    YamlExtraProperties extraProperties =
        variableCreator.getStepExtraProperties("fqn", "localName", new QueueStepNode());
    List<YamlProperties> properties = extraProperties.getPropertiesList();
    assertThat(properties).hasSize(3);
    assertYamlProperties(properties.get(0), "fqn.startTs", "localName.startTs", false);
    assertYamlProperties(properties.get(1), "fqn.endTs", "localName.endTs", false);
    assertYamlProperties(properties.get(2), "fqn.status", "localName.status", false);
  }

  private void assertYamlProperties(YamlProperties yaml, String fqn, String localName, boolean visible) {
    assertThat(yaml).isNotNull();
    assertThat(yaml.getFqn()).isEqualTo(fqn);
    assertThat(yaml.getLocalName()).isEqualTo(localName);
    assertThat(yaml.getVisible()).isEqualTo(visible);
  }
}
