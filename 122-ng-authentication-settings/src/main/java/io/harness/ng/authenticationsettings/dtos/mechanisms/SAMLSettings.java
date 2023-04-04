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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("SAML")
@OwnedBy(HarnessTeam.PL)
public class SAMLSettings extends NGAuthSettings {
  @NotNull private String origin;
  @NotNull private String identifier;
  private String logoutUrl;
  private String groupMembershipAttr;
  private String displayName;
  private Boolean authorizationEnabled;
  private String entityIdentifier;
  private String samlProviderType;
  private String clientId;
  private String clientSecret;
  private String friendlySamlName;

  public SAMLSettings(@JsonProperty("origin") String origin, @JsonProperty("identifier") String identifier,
      @JsonProperty("logoutUrl") String logoutUrl, @JsonProperty("groupMembershipAttr") String groupMembershipAttr,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("authorizationEnabled") Boolean authorizationEnabled,
      @JsonProperty("entityIdentifier") String entityIdentifier,
      @JsonProperty("samlProviderType") String samlProviderType, @JsonProperty("clientId") String clientId,
      @JsonProperty("clientSecret") String clientSecret, @JsonProperty("friendlySamlName") String friendlySamlName) {
    super(AuthenticationMechanism.SAML);
    this.identifier = identifier;
    this.displayName = displayName;
    this.origin = origin;
    this.logoutUrl = logoutUrl;
    this.groupMembershipAttr = groupMembershipAttr;
    this.authorizationEnabled = authorizationEnabled;
    this.entityIdentifier = entityIdentifier;
    this.samlProviderType = samlProviderType;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.friendlySamlName = friendlySamlName;
  }

  @Override
  public AuthenticationMechanism getSettingsType() {
    return AuthenticationMechanism.SAML;
  }
}
