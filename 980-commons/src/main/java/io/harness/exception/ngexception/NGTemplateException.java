/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;
import static io.harness.eraro.ErrorCode.TEMPLATE_EXCEPTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.HasResponseMessages;
import io.harness.exception.WingsException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
public class NGTemplateException extends WingsException implements HasResponseMessages {
  private static final String MESSAGE_ARG = "message";
  private List<ResponseMessage> responseMessages = new ArrayList<>();

  public NGTemplateException(String message, EnumSet<ReportTarget> reportTarget, ErrorMetadataDTO metadata) {
    super(message, null, TEMPLATE_EXCEPTION, Level.ERROR, reportTarget, null, metadata);
    super.param(MESSAGE_ARG, message);
  }

  public NGTemplateException(String message) {
    super(message, null, TEMPLATE_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public NGTemplateException(String message, Throwable cause) {
    super(message, cause, TEMPLATE_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public NGTemplateException(String message, List<ResponseMessage> responseMessages) {
    super(message, null, TEMPLATE_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
    this.responseMessages = responseMessages;
  }

  @Override
  public List<ResponseMessage> getResponseMessages() {
    if (EmptyPredicate.isEmpty(responseMessages)) {
      return new ArrayList<>(0);
    }
    return responseMessages;
  }
}
