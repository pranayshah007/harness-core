/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira.update;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.OrchestrationStepsTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.jira.JiraStepHelperService;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class JiraUpdateStepTest extends OrchestrationStepsTestBase {
  @InjectMocks JiraUpdateStep jiraUpdateStep;
  @Mock private JiraStepHelperService jiraStepHelperService;

  private static final String PROJECT = "project";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String TIMEOUT = "timeOut";
  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String ISSUE_KEY = "issueKey";
  private static final String DELEGATE_SELECTOR = "delegateSelector";
  private static final String DELEGATE_SELECTOR_2 = "delegateSelector2";

  private static final ParameterField DELEGATE_SELECTORS = ParameterField.createValueField(
      List.of(new TaskSelectorYaml(DELEGATE_SELECTOR), new TaskSelectorYaml(DELEGATE_SELECTOR_2)));

  private static final List<TaskSelector> TASK_SELECTORS = TaskSelectorYaml.toTaskSelector(DELEGATE_SELECTORS);

  private static final Ambiance AMBIANCE = Ambiance.newBuilder()
                                               .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT)
                                               .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG)
                                               .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT)
                                               .build();

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    JiraUpdateSpecParameters jiraUpdateSpecParameters =
        JiraUpdateSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
            .delegateSelectors(DELEGATE_SELECTORS)
            .issueKey(ParameterField.createValueField(ISSUE_KEY))
            .build();
    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .timeout(ParameterField.createValueField(TIMEOUT))
                                               .spec(jiraUpdateSpecParameters)
                                               .build();
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    Mockito
        .when(jiraStepHelperService.prepareTaskRequest(
            any(), eq(AMBIANCE), eq(CONNECTOR_REF), eq(TIMEOUT), eq("Jira Task: Update Issue"), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);
    TaskRequest taskRequest1 = jiraUpdateStep.obtainTaskAfterRbac(AMBIANCE, stepParameters, null);
    assertThat(taskRequest1).isSameAs(taskRequest);
  }
}