/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@Data
@Builder
@Schema(name = "NgSmtp", description = "This is the view of the NgSmtp entity defined in Harness")
public class NgSmtpDTO {
  @Schema(description = "Identifier of the SMTP config.") private String uuid;
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotEmpty String accountId;
  @Schema(description = "Name of the SMTP config.")
  @NotNull
  @NotBlank
  @Trimmed(message = "The name must not have trailing spaces.")
  @NGEntityName
  private String name;
  @Valid @NotNull private SmtpConfigDTO value;
}
