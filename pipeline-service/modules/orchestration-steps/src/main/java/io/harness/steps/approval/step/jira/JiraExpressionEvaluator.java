/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.jira.JiraIssueNG;

@OwnedBy(CDC)
public class JiraExpressionEvaluator extends EngineExpressionEvaluator {
  public static final String ISSUE_IDENTIFIER = "issue";

  private final JiraIssueNG jiraIssueNG;

  public JiraExpressionEvaluator(JiraIssueNG jiraIssueNG) {
    super(null);
    this.jiraIssueNG = jiraIssueNG;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext(ISSUE_IDENTIFIER, jiraIssueNG.getFields());
  }
}
