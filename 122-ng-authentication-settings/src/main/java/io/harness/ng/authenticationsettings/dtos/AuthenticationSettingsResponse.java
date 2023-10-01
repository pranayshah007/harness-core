/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.authenticationsettings.dtos.mechanisms.NGAuthSettings;
import io.harness.ng.core.account.AuthenticationMechanism;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.PL)
@Schema(description = "This contains information on the Authentication Settings defined in Harness.")
public class AuthenticationSettingsResponse {
  @Schema(description = "List of Auth Settings configured for an Account.") List<NGAuthSettings> ngAuthSettings;
  @Schema(description = "List of the whitelisted domains.") Set<String> whitelistedDomains;
  @Schema(description = "Indicates if the Authentication Mechanism is SSO or NON-SSO.")
  AuthenticationMechanism authenticationMechanism;
  @Schema(description = "If Two Factor Authentication is enabled, this value is true. Otherwise, it is false.")
  boolean twoFactorEnabled;
  @Schema(
      description = "Any user of this account will be logged out if there is no activity for this number of minutes")
  Integer sessionTimeoutInMinutes;
  @Schema(description = "If public access is enabled, this value is true. Otherwise, it is false.")
  boolean publicAccessEnabled;
}
