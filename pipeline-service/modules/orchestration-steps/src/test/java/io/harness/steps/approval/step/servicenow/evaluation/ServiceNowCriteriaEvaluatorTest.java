/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.evaluation;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ApprovalStepNGException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpecDTO;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceNowCriteriaEvaluatorTest extends CategoryTest {
  private NGLogCallback logCallback;
  private static final String ticketNumber = "TICKET_NUMBER";
  private static final String connectorRef = "CONNECTOR_REF";
  private static final String changeWindowStartField = "CHANGE_WINDOW_START_FIELD";
  private static final String changeWindowEndField = "CHANGE_WINDOW_END_FIELD";
  private static final String ticketType = "TICKET_TYPE";
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  // display value depends on timezone set in SNOW account
  private static final String endTimeDisplayValue = "END_TIME_DISPLAY_VALUE";
  private static final String startTimeDisplayValue = "START_TIME_DISPLAY_VALUE";

  @Before
  public void setup() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    logCallback = mock(NGLogCallback.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testEvaluateJexlCriteria() {
    ServiceNowTicketNG ticket = prepareTicket(
        new HashMap<>(ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
            ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build())));
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
    ServiceNowTicketNG ticket = prepareTicket(
        new HashMap<>(ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
            ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build())));
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

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenTicketNull() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(null, prepareInstance(null), logCallback);
      fail("Expected failure as ticket provided is null");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch ticket. Ticket number might be invalid");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenTicketFieldsNull() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(prepareTicket(null), prepareInstance(null), logCallback);
      fail("Expected failure as provided ticket's field is null");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch ticket. Ticket number might be invalid");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenTicketFieldsEmpty() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>()), prepareInstance(null), logCallback);
      fail("Expected failure as provided ticket's fields are empty");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to fetch ticket. Ticket number might be invalid");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowIsNull() {
    assertThat(ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
                   prepareTicket(new HashMap<>(
                       ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(),
                           "b", ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
                   prepareInstance(null), logCallback))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldIsNull() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(
              ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
                  ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(
              ServiceNowChangeWindowSpecDTO.builder().startField(null).endField(changeWindowEndField).build()),
          logCallback);
      fail("Expected failure as change window start field is null");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Change window start field can't be empty or blank");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldIsBlank() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(
              ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
                  ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(
              ServiceNowChangeWindowSpecDTO.builder().startField("  ").endField(changeWindowEndField).build()),
          logCallback);
      fail("Expected failure as change window start field is blank");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Change window start field can't be empty or blank");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldNotPresentInTicket() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(
              ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
                  ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(ServiceNowChangeWindowSpecDTO.builder()
                              .startField(changeWindowStartField)
                              .endField(changeWindowEndField)
                              .build()),
          logCallback);
      fail("Expected failure as change window start field not present in ticket");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Field CHANGE_WINDOW_START_FIELD doesn't exist in ticket");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldValueInTicketIsNull() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(
              ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value(null).displayValue("d1").build(), "b",
                  ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(
              ServiceNowChangeWindowSpecDTO.builder().startField("a").endField(changeWindowEndField).build()),
          logCallback);
      fail("Expected failure as change window start field value in ticket is null");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Value of change window start field in the ticket can't be blank");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldValueInTicketIsBlank() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(
              new HashMap<>(ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value(" ").displayValue("d1").build(),
                  "b", ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(
              ServiceNowChangeWindowSpecDTO.builder().startField("a").endField(changeWindowEndField).build()),
          logCallback);
      fail("Expected failure as change window start field value in ticket is blank");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage()).isEqualTo("Value of change window start field in the ticket can't be blank");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateWithinChangeWindowWhenInstanceChangeWindowFieldsValueInTicketResultsInParsingErrorAndCheckTimeAdded() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(
              ImmutableMap.of("a", ServiceNowFieldValueNG.builder().value("v1").displayValue("d1").build(), "b",
                  ServiceNowFieldValueNG.builder().value("v2").displayValue("d2").build()))),
          prepareInstance(ServiceNowChangeWindowSpecDTO.builder().startField("a").endField("b").build()), logCallback);
      fail("Expected failure as change window start and end field values in ticket should result in parsing error");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Invalid approval Change window values in ServiceNow : Unparseable date: \"v1 00:00:00\"");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateWithinChangeWindowWhenInstanceChangeWindowFieldsValueInTicketCorrectButStartWindowGrThanEndWindow() {
    try {
      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(ImmutableMap.of("a",
              ServiceNowFieldValueNG.builder().value("2022-07-29 08:01:30").displayValue("2022-07-29 01:01:30").build(),
              "b",
              ServiceNowFieldValueNG.builder()
                  .value("2022-07-26 08:01:30")
                  .displayValue("2022-07-26 01:01:30")
                  .build()))),
          prepareInstance(ServiceNowChangeWindowSpecDTO.builder().startField("a").endField("b").build()), logCallback);
      fail("Expected failure as change window start field value greater than end field value in ticket");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage())
          .isEqualTo(
              "Start window time {2022-07-29 01:01:30} must be earlier than end window time {2022-07-26 01:01:30}");
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateWithinChangeWindowWhenInstanceChangeWindowFieldsValueInTicketCorrectButCurrentTimeGrThanEndWindow() {
    try {
      String endTime = dateFormat.format(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
      String startTime = dateFormat.format(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));

      ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
          prepareTicket(new HashMap<>(ImmutableMap.of("a",
              ServiceNowFieldValueNG.builder().value(startTime).displayValue(startTimeDisplayValue).build(), "b",
              ServiceNowFieldValueNG.builder().value(endTime).displayValue(endTimeDisplayValue).build()))),
          prepareInstance(ServiceNowChangeWindowSpecDTO.builder().startField("a").endField("b").build()), logCallback);
      fail("Expected failure as current time greater than end field value in ticket");
    } catch (ApprovalStepNGException ex) {
      assertThat(ex.getMessage())
          .isEqualTo(String.format("End window time {%s} must be greater than current time", endTimeDisplayValue));
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldsValueInTicketCorrectButNotSatisfied() {
    String endTime = dateFormat.format(Date.from(Instant.now().plus(2, ChronoUnit.DAYS)));
    String startTime = dateFormat.format(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    assertThat(
        ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
            prepareTicket(new HashMap<>(ImmutableMap.of("a",
                ServiceNowFieldValueNG.builder().value(startTime).displayValue(startTimeDisplayValue).build(), "b",
                ServiceNowFieldValueNG.builder().value(endTime).displayValue(endTimeDisplayValue).build()))),
            prepareInstance(ServiceNowChangeWindowSpecDTO.builder().startField("a").endField("b").build()),
            logCallback))
        .isFalse();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateWithinChangeWindowWhenInstanceChangeWindowFieldsValueInTicketCorrectButSatisfied() {
    String endTime = dateFormat.format(Date.from(Instant.now().plus(2, ChronoUnit.DAYS)));
    String startTime = dateFormat.format(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
    assertThat(
        ServiceNowCriteriaEvaluator.validateWithinChangeWindow(
            prepareTicket(new HashMap<>(ImmutableMap.of("a",
                ServiceNowFieldValueNG.builder().value(startTime).displayValue(startTimeDisplayValue).build(), "b",
                ServiceNowFieldValueNG.builder().value(endTime).displayValue(endTimeDisplayValue).build()))),
            prepareInstance(ServiceNowChangeWindowSpecDTO.builder().startField("a").endField("b").build()),
            logCallback))
        .isTrue();
  }

  private ServiceNowApprovalInstance prepareInstance(ServiceNowChangeWindowSpecDTO serviceNowChangeWindowSpecDTO) {
    return ServiceNowApprovalInstance.builder()
        .ticketNumber(ticketNumber)
        .connectorRef(connectorRef)
        .ticketType(ticketType)
        .changeWindow(serviceNowChangeWindowSpecDTO)
        .build();
  }

  private ServiceNowTicketNG prepareTicket(Map<String, ServiceNowFieldValueNG> fields) {
    ServiceNowTicketNG ticketNG = new ServiceNowTicketNG();
    ticketNG.setUrl("url");
    ticketNG.setNumber("number");
    ticketNG.setFields(fields);
    return ticketNG;
  }
}
