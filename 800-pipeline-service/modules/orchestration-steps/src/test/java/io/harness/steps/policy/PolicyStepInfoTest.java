/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.policy.custom.CustomPolicyStepSpec;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PolicyStepInfoTest extends CategoryTest {
  String basicStepYaml;
  String policySetRuntimeInput;
  String payloadRuntimeInput;
  String policySetExpression;
  String payloadExpression;

  @Before
  public void setUp() {
    basicStepYaml = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets:\n"
        + "  - acc.ps1\n"
        + "  - org.ps1\n"
        + "  - ps1\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: |\n"
        + "      {\n"
        + "        \"this\" : \"that\"\n"
        + "      }";
    policySetRuntimeInput = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets: <+input>\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: |\n"
        + "      {\n"
        + "        \"this\" : \"that\"\n"
        + "      }";
    payloadRuntimeInput = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets: <+input>\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: <+input>\n";
    policySetExpression = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets:\n"
        + "  - acc.ps1\n"
        + "  - <+step.name>\n"
        + "  - ps1\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: |\n"
        + "      {\n"
        + "        \"this\" : \"that\"\n"
        + "      }";
    payloadExpression = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets: <+input>\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: |\n"
        + "      {\n"
        + "        \"this\" : \"<+step.name>>\"\n"
        + "      }";
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeserializeToPolicyStepNode() throws IOException {
    PolicyStepNode policyStepNode = YamlUtils.read(basicStepYaml, PolicyStepNode.class);
    assertThat(policyStepNode).isNotNull();
    policyStepNode = YamlUtils.read(policySetRuntimeInput, PolicyStepNode.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(policyStepNode.getPolicyStepInfo().getPolicySets().getExpressionValue()).isEqualTo("<+input>");
    policyStepNode = YamlUtils.read(payloadRuntimeInput, PolicyStepNode.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(
        ((CustomPolicyStepSpec) policyStepNode.getPolicyStepInfo().getPolicySpec()).getPayload().getExpressionValue())
        .isEqualTo("<+input>");
    policyStepNode = YamlUtils.read(policySetExpression, PolicyStepNode.class);
    assertThat(policyStepNode).isNotNull();
    policyStepNode = YamlUtils.read(payloadExpression, PolicyStepNode.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(((CustomPolicyStepSpec) policyStepNode.getPolicyStepInfo().getPolicySpec()).getPayload().isExpression())
        .isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeserializeToStepElementConfig() throws IOException {
    StepElementConfig policyStepNode = YamlUtils.read(basicStepYaml, StepElementConfig.class);
    assertThat(policyStepNode).isNotNull();
    policyStepNode = YamlUtils.read(policySetRuntimeInput, StepElementConfig.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(((PolicyStepInfo) policyStepNode.getStepSpecType()).getPolicySets().getExpressionValue())
        .isEqualTo("<+input>");
    policyStepNode = YamlUtils.read(payloadRuntimeInput, StepElementConfig.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(((CustomPolicyStepSpec) ((PolicyStepInfo) policyStepNode.getStepSpecType()).getPolicySpec())
                   .getPayload()
                   .getExpressionValue())
        .isEqualTo("<+input>");
    policyStepNode = YamlUtils.read(policySetExpression, StepElementConfig.class);
    assertThat(policyStepNode).isNotNull();
    policyStepNode = YamlUtils.read(payloadExpression, StepElementConfig.class);
    assertThat(policyStepNode).isNotNull();
    assertThat(((CustomPolicyStepSpec) ((PolicyStepInfo) policyStepNode.getStepSpecType()).getPolicySpec())
                   .getPayload()
                   .isExpression())
        .isTrue();
  }
}