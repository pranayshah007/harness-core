/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira.create;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.jira.beans.JiraField;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class JiraCreateStepInfoTest extends OrchestrationStepsTestBase {
  JiraCreateStepInfo jiraCreateStepInfo = JiraCreateStepInfo.builder()
                                              .connectorRef(ParameterField.createValueField("conn1"))
                                              .projectKey(ParameterField.createValueField("projectKey"))
                                              .issueType(ParameterField.createValueField("issue"))
                                              .fields(List.of(JiraField.builder().build()))
                                              .delegateSelectors(ParameterField.createValueField(List.of()))
                                              .build();
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertEquals(jiraCreateStepInfo.getStepType(), StepSpecTypeConstants.JIRA_CREATE_STEP_TYPE);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    assertEquals(jiraCreateStepInfo.getFacilitatorType(), OrchestrationFacilitatorType.TASK);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    JiraCreateSpecParameters jiraCreateSpecParameters =
        (JiraCreateSpecParameters) jiraCreateStepInfo.getSpecParameters();
    assertEquals(jiraCreateSpecParameters.getConnectorRef().getValue(), "conn1");
    assertEquals(jiraCreateSpecParameters.getDelegateSelectors().getValue().size(), 0);
    assertEquals(jiraCreateSpecParameters.getIssueType().getValue(), "issue");
    assertEquals(jiraCreateSpecParameters.getProjectKey().getValue(), "projectKey");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    assertEquals(
        jiraCreateStepInfo.extractConnectorRefs().get(YAMLFieldNameConstants.CONNECTOR_REF).getValue(), "conn1");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchDelegateSelectors() {
    assertEquals(jiraCreateStepInfo.fetchDelegateSelectors().getValue().size(), 0);
  }
}
