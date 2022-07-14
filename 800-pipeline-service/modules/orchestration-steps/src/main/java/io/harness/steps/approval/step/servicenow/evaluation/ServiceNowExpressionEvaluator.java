/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.evaluation;

import io.harness.expression.EngineExpressionEvaluator;
import io.harness.servicenow.ServiceNowTicketNG;

public class ServiceNowExpressionEvaluator extends EngineExpressionEvaluator {
  public static final String TICKET_IDENTIFIER = "ticket";

  private final ServiceNowTicketNG serviceNowTicketNG;

  public ServiceNowExpressionEvaluator(ServiceNowTicketNG serviceNowTicketNG) {
    super(null);
    this.serviceNowTicketNG = serviceNowTicketNG;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext(TICKET_IDENTIFIER, serviceNowTicketNG.getFields());
  }
}
