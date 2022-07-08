/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.evaluation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.jira.JiraIssueNG;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.evaluation.CriteriaEvaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class CriteriaEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testEvaluateJexlCriteria() {
    JiraIssueNG issue = prepareIssue();
    assertThatThrownBy(()
                           -> io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                               issue, JexlCriteriaSpecDTO.builder().expression("").build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                               issue, JexlCriteriaSpecDTO.builder().expression(null).build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                               issue, JexlCriteriaSpecDTO.builder().expression("5").build()))
        .isNotNull();

    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                   issue, JexlCriteriaSpecDTO.builder().expression("true").build()))
        .isTrue();
    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                   issue, JexlCriteriaSpecDTO.builder().expression("false").build()))
        .isFalse();

    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                   issue, JexlCriteriaSpecDTO.builder().expression("<+issue.a> == \"v1\"").build()))
        .isTrue();
    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(
                   issue, JexlCriteriaSpecDTO.builder().expression("<+issue.a> == \"v2\"").build()))
        .isFalse();
    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
                   JexlCriteriaSpecDTO.builder().expression("<+issue.a> == \"v1\" || <+issue.b> == \"v3\"").build()))
        .isTrue();
    assertThat(io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
                   JexlCriteriaSpecDTO.builder().expression("<+issue.a> == \"v1\" && <+issue.b> == \"v3\"").build()))
        .isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testEvaluateKeyValuesCriteria() {
    JiraIssueNG issue = prepareIssue();
    assertThatThrownBy(
        ()
            -> io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
                KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(true).conditions(Collections.emptyList()).build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
                               KeyValuesCriteriaSpecDTO.builder()
                                   .matchAnyCondition(false)
                                   .conditions(Collections.emptyList())
                                   .build()))
        .isNotNull();

    assertThat(
        io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v2, v3").build()))
                .build()))
        .isTrue();

    assertThat(
        io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isTrue();

    assertThat(
        io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v2").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isFalse();

    assertThat(
        io.harness.steps.approval.step.evaluation.CriteriaEvaluator.evaluateCriteria(issue,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v2, v3").build()))
                .build()))
        .isTrue();

    assertThat(
        CriteriaEvaluator.evaluateCriteria(issue,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isFalse();
  }

  private JiraIssueNG prepareIssue() {
    JiraIssueNG issue = new JiraIssueNG();
    issue.setUrl("url");
    issue.setId("id");
    issue.setKey("key");
    issue.setFields(new HashMap<>(ImmutableMap.of("a", "v1", "b", "v2")));
    return issue;
  }
}
