/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(PL)
@Data
@Builder
@AllArgsConstructor
@Schema(description = "This contains the information about the session timeout for this account in Harness.")
public class SessionTimeoutSettings {
  @Getter
  @Setter
  @Schema(
      description = "Any user of this account will be logged out if there is no activity for this number of minutes")
  @Min(value = 30)
  @NotNull
  private Integer sessionTimeOutInMinutes;
}
