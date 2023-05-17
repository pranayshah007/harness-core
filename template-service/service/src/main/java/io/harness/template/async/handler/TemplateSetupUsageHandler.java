/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.async.handler;

import io.harness.template.async.beans.SetupUsageEventStatus;
import io.harness.template.async.beans.TemplateSetupUsageEvent;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.services.TemplateAsyncSetupUsageService;

public class TemplateSetupUsageHandler implements Runnable {
  private final TemplateSetupUsageEvent templateSetupUsageEvent;

  private final TemplateAsyncSetupUsageService templateAsyncSetupUsageService;

  private final TemplateReferenceHelper referenceHelper;

  public TemplateSetupUsageHandler(TemplateSetupUsageEvent templateSetupUsageEvent,
      TemplateAsyncSetupUsageService templateAsyncSetupUsageService, TemplateReferenceHelper referenceHelper) {
    this.templateSetupUsageEvent = templateSetupUsageEvent;
    this.templateAsyncSetupUsageService = templateAsyncSetupUsageService;
    this.referenceHelper = referenceHelper;
  }

  @Override
  public void run() {
    templateAsyncSetupUsageService.updateEvent(templateSetupUsageEvent.getUuid(), SetupUsageEventStatus.IN_PROGRESS);
    try {
      TemplateEntity templateEntity = templateSetupUsageEvent.getParams().getTemplateEntity();
      referenceHelper.populateTemplateReferences(templateEntity);
      templateAsyncSetupUsageService.updateEvent(templateSetupUsageEvent.getUuid(), SetupUsageEventStatus.SUCCESS);
    } catch (Exception e) {
      templateAsyncSetupUsageService.updateEvent(templateSetupUsageEvent.getUuid(), SetupUsageEventStatus.FAILURE);
    }
  }
}
