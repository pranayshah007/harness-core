/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.evaluation;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceNowCriteriaEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testEvaluateJexlCriteria() {
    ServiceNowTicketNG ticket = prepareTicket();
    assertThatThrownBy(()
                           -> ServiceNowCriteriaEvaluator.evaluateCriteria(
                               ticket, JexlCriteriaSpecDTO.builder().expression("").build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> ServiceNowCriteriaEvaluator.evaluateCriteria(
                               ticket, JexlCriteriaSpecDTO.builder().expression(null).build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> ServiceNowCriteriaEvaluator.evaluateCriteria(
                               ticket, JexlCriteriaSpecDTO.builder().expression("5").build()))
        .isNotNull();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket, JexlCriteriaSpecDTO.builder().expression("true").build()))
        .isTrue();
    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket, JexlCriteriaSpecDTO.builder().expression("false").build()))
        .isFalse();

    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(
                   ticket, JexlCriteriaSpecDTO.builder().expression("<+ticket.a.value> == \"v1\"").build()))
        .isTrue();
    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(
                   ticket, JexlCriteriaSpecDTO.builder().expression("<+ticket.a.value> == \"v2\"").build()))
        .isFalse();
    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                   JexlCriteriaSpecDTO.builder()
                       .expression("<+ticket.a.value> == \"v1\" || <+ticket.b.value> == \"v3\"")
                       .build()))
        .isTrue();
    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                   JexlCriteriaSpecDTO.builder()
                       .expression("<+ticket.a.value> == \"v1\" && <+ticket.b.value> == \"v3\"")
                       .build()))
        .isFalse();

    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(
                   ticket, JexlCriteriaSpecDTO.builder().expression("<+ticket.a.displayValue> == \"d1\"").build()))
        .isTrue();
    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                   JexlCriteriaSpecDTO.builder()
                       .expression("<+ticket.a.value> == \"v1\" && <+ticket.b.displayValue> == \"d2\"")
                       .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testEvaluateKeyValuesCriteria() {
    ServiceNowTicketNG ticket = prepareTicket();
    assertThatThrownBy(
        ()
            -> ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(true).conditions(Collections.emptyList()).build()))
        .isNotNull();
    assertThatThrownBy(()
                           -> ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                               KeyValuesCriteriaSpecDTO.builder()
                                   .matchAnyCondition(false)
                                   .conditions(Collections.emptyList())
                                   .build()))
        .isNotNull();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v2, v3").build()))
                .build()))
        .isTrue();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isTrue();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v2").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isFalse();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v2, v3").build()))
                .build()))
        .isTrue();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("v1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("v1").build()))
                .build()))
        .isFalse();

    assertThat(
        ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(ImmutableList.of(ConditionDTO.builder().key("a").operator(Operator.EQ).value("d1").build(),
                    ConditionDTO.builder().key("b").operator(Operator.IN).value("d2, d3").build()))
                .build()))
        .isTrue();

    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                   KeyValuesCriteriaSpecDTO.builder()
                       .matchAnyCondition(false)
                       .conditions(ImmutableList.of(
                           ConditionDTO.builder().key("a").operator(Operator.NOT_EQ).value("d1").build()))
                       .build()))
        .isFalse();

    assertThat(ServiceNowCriteriaEvaluator.evaluateCriteria(ticket,
                   KeyValuesCriteriaSpecDTO.builder()
                       .matchAnyCondition(false)
                       .conditions(ImmutableList.of(
                           ConditionDTO.builder().key("b").operator(Operator.NOT_IN).value("d2, v1").build()))
                       .build()))
        .isFalse();
  }

  private ServiceNowTicketNG prepareTicket() {
    ServiceNowTicketNG ticketNG = new ServiceNowTicketNG();
    ticketNG.setUrl("url");
    ticketNG.setNumber("number");
    ticketNG.setFields(
        new HashMap<>(ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
            ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build())));
    return ticketNG;
  }
}
