/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CV)
@Value
public class ErrorBudgetBurnDownResponse {
  @NotNull @JsonProperty("errorBudgetBurnDown") private ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO;
  private Long createdAt;
  private Long lastModifiedAt;

  @Builder
  public ErrorBudgetBurnDownResponse(
      ErrorBudgetBurnDownDTO errorBudgetBurnDownDTO, Long createdAt, Long lastModifiedAt) {
    this.errorBudgetBurnDownDTO = errorBudgetBurnDownDTO;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
  }
}
