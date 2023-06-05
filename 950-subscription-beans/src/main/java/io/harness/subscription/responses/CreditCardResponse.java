/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.responses;

import static io.harness.annotations.dev.HarnessTeam.PLG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.subscription.dto.CreditCardDTO;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PLG)
@Data
@Builder
public class CreditCardResponse {
  @NotNull CreditCardDTO creditCardDTO;
  private Long createdAt;
  private Long lastUpdatedAt;
}
