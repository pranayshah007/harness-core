/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;

import java.util.EnumSet;
import lombok.Getter;

@OwnedBy(HarnessTeam.CDC)
@Getter
public class NGTemplateResolveException extends NGTemplateException {
  private final TemplateInputsErrorMetadataDTO errorResponseDTO;

  public NGTemplateResolveException(
      String message, EnumSet<ReportTarget> reportTarget, TemplateInputsErrorMetadataDTO errorResponseDTO) {
    super(message, reportTarget, errorResponseDTO);
    this.errorResponseDTO = errorResponseDTO;
  }
}
