/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.delegate.TaskSelector;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class BambooBuildStepTest extends CDNGTestBase {
  @InjectMocks private BambooBuildStep bambooBuildStep;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private LogStreamingStepClientImpl logStreamingStepClient;
  @Mock private BambooBuildStepHelperService bambooBuildStepHelperService;

  private static final String CONNECTOR_REF = "connectorRef";
  private static final String DELEGATE_SELECTOR = "delegateSelector";
  private static final String DELEGATE_SELECTOR_2 = "delegateSelector2";
  private static final String PLAN_NAME = "planName";
  private static final String COMMAND_UNIT = "Execute";
  private static final String TIME_OUT = "timeOut";
  private static final String TASK_NAME = "Bamboo Task: Create Bamboo Build Task";
  private static final Ambiance AMBIANCE = Ambiance.newBuilder().build();
  private static final TaskRequest TASK_REQUEST = TaskRequest.newBuilder().build();
  private static final BambooBuildSpecParameters BAMBOO_BUILD_SPEC_PARAMETERS =
      BambooBuildSpecParameters.builder()
          .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
          .delegateSelectors(ParameterField.createValueField(
              Arrays.asList(new TaskSelectorYaml(DELEGATE_SELECTOR), new TaskSelectorYaml(DELEGATE_SELECTOR_2))))
          .planName(ParameterField.createValueField(PLAN_NAME))
          .build();
  private static final StepElementParameters STEP_ELEMENT_PARAMETERS =
      StepElementParameters.builder()
          .spec(BAMBOO_BUILD_SPEC_PARAMETERS)
          .timeout(ParameterField.createValueField(TIME_OUT))
          .build();
  private static final List<TaskSelector> TASK_SELECTOR_LIST =
      TaskSelectorYaml.toTaskSelector(BAMBOO_BUILD_SPEC_PARAMETERS.getDelegateSelectors());

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    doNothing().when(logStreamingStepClient).openStream(COMMAND_UNIT);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(AMBIANCE)).thenReturn(logStreamingStepClient);
    when(bambooBuildStepHelperService.prepareTaskRequest(
             any(), eq(AMBIANCE), eq(CONNECTOR_REF), eq(TIME_OUT), eq(TASK_NAME), eq(TASK_SELECTOR_LIST)))
        .thenReturn(TASK_REQUEST);

    TaskRequest taskRequest = bambooBuildStep.obtainTaskAfterRbac(AMBIANCE, STEP_ELEMENT_PARAMETERS, null);
    assertThat(taskRequest).isSameAs(TASK_REQUEST);

    verify(logStreamingStepClient).openStream(COMMAND_UNIT);
    verify(bambooBuildStepHelperService)
        .prepareTaskRequest(
            any(), eq(AMBIANCE), eq(CONNECTOR_REF), eq(TIME_OUT), eq(TASK_NAME), eq(TASK_SELECTOR_LIST));
    verify(logStreamingStepClientFactory).getLogStreamingStepClient(AMBIANCE);
  }
}