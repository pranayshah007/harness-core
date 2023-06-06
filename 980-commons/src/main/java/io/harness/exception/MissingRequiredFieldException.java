/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Getter;

@OwnedBy(HarnessTeam.PIPELINE)
public class MissingRequiredFieldException extends RuntimeException {
  @Getter List<String> requiredFields;
  @Getter List<String> missingFields;

  public MissingRequiredFieldException(List<String> requiredFields, List<String> missingFields) {
    super(String.format("Missing Required Fields: [%s]", requiredFields));
    this.requiredFields = requiredFields;
    this.missingFields = missingFields;
  }
}
