/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;

import java.util.EnumSet;
import lombok.Getter;

@OwnedBy(HarnessTeam.CDC)
@Getter
public class NGTemplateResolveExceptionV2 extends NGTemplateException {
  ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO;

  public NGTemplateResolveExceptionV2(String message, EnumSet<ReportTarget> reportTarget,
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO) {
    super(message, reportTarget, validateTemplateInputsResponseDTO);
  }
}
