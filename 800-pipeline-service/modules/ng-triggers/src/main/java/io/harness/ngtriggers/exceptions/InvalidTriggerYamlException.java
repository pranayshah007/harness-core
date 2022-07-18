/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.exceptions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidYamlException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;

import java.util.Map;
import lombok.Getter;

@OwnedBy(HarnessTeam.CI)
@Getter
public class InvalidTriggerYamlException extends InvalidYamlException {
  private final Map<String, Map<String, String>> errors;
  private final TriggerDetails triggerDetails;
  public InvalidTriggerYamlException(
      String message, Map<String, Map<String, String>> errors, TriggerDetails triggerDetails, Throwable cause) {
    super(message, cause);
    this.triggerDetails = triggerDetails;
    this.errors = errors;
  }
}
