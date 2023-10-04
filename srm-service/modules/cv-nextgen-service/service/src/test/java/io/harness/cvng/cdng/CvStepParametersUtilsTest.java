/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng;

import static io.harness.rule.OwnerRule.ANSHIKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cvng.cdng.beans.CVNGAbstractStepNode;
import io.harness.plancreator.policy.PolicyConfig;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import io.harness.utils.TimeoutUtils;
import io.harness.when.beans.StepWhenCondition;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;

@OwnedBy(HarnessTeam.CV)
public class CvStepParametersUtilsTest extends CvNextGenTestBase {
    @Mock private CVNGAbstractStepNode stepNode;
    private static final String STEP_PARAMETER_NAME = "TestStepName";
    private static final String STEP_PARAMETER_IDENTIFIER = "TestStepIdentifier";
    private static final ParameterField<List<String>> STEP_PARAMETER_DELEGATESELECTORS = new ParameterField<>();
    private static final String STEP_PARAMETER_DESCRIPTION = "TestDescription";
    private static final ParameterField<String> STEP_PARAMETER_SKIPCONDITION = new ParameterField<>();
    private static final String STEP_PARAMETER_TYPE = "TestType";
    private static final String STEP_PARAMETER_UUID = "TestUuid";
    private static final ParameterField<List<FailureStrategyConfig>> STEP_PARAMETER_FAILURESTRATEGIES = new ParameterField<>();
    private static final ParameterField<Timeout> STEP_PARAMETER_TIMEOUT = new ParameterField<>();
    private static final ParameterField<StepWhenCondition> STEP_PARAMETER_WHEN_GET = new ParameterField<>();
    private static final PolicyConfig STEP_PARAMETER_ENFORCE = PolicyConfig.builder().build();

    @Before
    public void setup() throws IllegalAccessException {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @Owner(developers = ANSHIKA)
    @Category(UnitTests.class)
    public void testGetStepParameters() {
        when(stepNode.getName()).thenReturn(STEP_PARAMETER_NAME);
        when(stepNode.getIdentifier()).thenReturn(STEP_PARAMETER_IDENTIFIER);
        when(stepNode.getDelegateSelectors()).thenReturn(STEP_PARAMETER_DELEGATESELECTORS);
        when(stepNode.getDescription()).thenReturn(STEP_PARAMETER_DESCRIPTION);
        when(stepNode.getSkipCondition()).thenReturn(STEP_PARAMETER_SKIPCONDITION);
        when(stepNode.getFailureStrategies()).thenReturn(STEP_PARAMETER_FAILURESTRATEGIES);
        when(stepNode.getTimeout()).thenReturn(STEP_PARAMETER_TIMEOUT);
        when(stepNode.getWhen()).thenReturn(STEP_PARAMETER_WHEN_GET);
        when(stepNode.getType()).thenReturn(STEP_PARAMETER_TYPE);
        when(stepNode.getUuid()).thenReturn(STEP_PARAMETER_UUID);
        when(stepNode.getEnforce()).thenReturn(STEP_PARAMETER_ENFORCE);
        StepElementParametersBuilder expectedStepBuilder = StepElementParameters.builder()
                .name(STEP_PARAMETER_NAME)
                .identifier(STEP_PARAMETER_IDENTIFIER)
                .description(STEP_PARAMETER_DESCRIPTION)
                .delegateSelectors(STEP_PARAMETER_DELEGATESELECTORS)
                .skipCondition(STEP_PARAMETER_SKIPCONDITION)
                .failureStrategies(null)
                .timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(STEP_PARAMETER_TIMEOUT)))
                .when(null)
                .type(STEP_PARAMETER_TYPE)
                .uuid(STEP_PARAMETER_UUID)
                .enforce(STEP_PARAMETER_ENFORCE);
        StepElementParametersBuilder result = CvStepParametersUtils.getStepParameters(stepNode);
        assertThat(result.build()).isEqualTo(expectedStepBuilder.build());
    }
}
