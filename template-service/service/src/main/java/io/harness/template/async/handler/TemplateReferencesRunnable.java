/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.async.handler;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.template.async.beans.SetupUsageParams;
import io.harness.template.helpers.TemplateReferenceHelper;

import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Slf4j
public class TemplateReferencesRunnable implements Runnable {
  SetupUsageParams setupUsageParams;

  private final TemplateReferenceHelper referenceHelper;

  public TemplateReferencesRunnable(SetupUsageParams setupUsageParams, TemplateReferenceHelper referenceHelper) {
    this.setupUsageParams = setupUsageParams;
    this.referenceHelper = referenceHelper;
  }

  @Override
  public void run() {
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder("TemplateReferencesRunnable BG Task")) {
      String templateIdentifier = setupUsageParams.getTemplateEntity().getIdentifier();
      try {
        log.info(String.format(
            "Calculating template references in the background for templateIdentifier: %s", templateIdentifier));
        referenceHelper.populateTemplateReferences(setupUsageParams);
        log.info("Successfully calculated template references and updated entitySetupUsage db.");
      } catch (Exception exception) {
        log.error(
            "Exception while calculating reference for template {} in BG THREAD : ", templateIdentifier, exception);
      }
    }
  }
}
