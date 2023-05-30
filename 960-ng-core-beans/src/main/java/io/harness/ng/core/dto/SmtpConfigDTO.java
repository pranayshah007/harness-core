/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@JsonTypeName("smtp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"password"})
@EqualsAndHashCode
@Schema(name = "SmtpConfig", description = "This has the SMTP configuration details defined in Harness.")
public class SmtpConfigDTO {
  @Schema(description = "This is the host of the SMTP server.")
  @JsonProperty(value = "host", required = true)
  @NotNull
  @NotBlank
  private String host;
  @Schema(description = "This is the port of the SMTP server.")
  @JsonProperty(value = "port", required = true)
  @NotNull
  private int port;
  @Schema(description = "From address of the email that needs to be send.")
  @JsonProperty("fromAddress")
  private String fromAddress;
  @Schema(description = "Specify whether or not to use SSL certificate.")
  @JsonProperty("useSSL")
  private boolean useSSL;
  @Schema(description = "Specify whether or not to use TLS.") @JsonProperty("startTLS") private boolean startTLS;
  @Schema(description = "Username credential to authenticate with SMTP server.")
  @JsonProperty("username")
  private String username;
  @Schema(description = "Password credential to authenticate with SMTP server.")
  @JsonProperty("password")
  private char[] password;
  @Schema(description = "List of delegate selectors of delegates used by SMTP server as connectivity mode.")
  @JsonProperty("delegateSelectors")
  private Set<String> delegateSelectors;
}
