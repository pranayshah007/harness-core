/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.dtos.mechanisms;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("OAUTH")
@OwnedBy(HarnessTeam.PL)
@Schema(description = "This contains the information about OAuth settings defined in Harness.")
public class OAuthSettings extends NGAuthSettings {
  @Schema(description = "Filter out the available OAuth providers.") private String filter;
  @Schema(description = "This is the list of OAuth Providers that are supported.")
  private Set<OauthProviderType> allowedProviders;

  public OAuthSettings(@JsonProperty("filter") String filter,
      @JsonProperty("allowedProviders") Set<OauthProviderType> allowedProviders) {
    super(AuthenticationMechanism.OAUTH);
    this.filter = filter;
    this.allowedProviders = allowedProviders;
  }

  @Override
  public AuthenticationMechanism getSettingsType() {
    return AuthenticationMechanism.OAUTH;
  }
}
