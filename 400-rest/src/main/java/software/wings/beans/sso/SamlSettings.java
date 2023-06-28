/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.sso;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.settings.SettingVariableTypes.SSO_SAML;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.SAMLProviderType;
import io.harness.encryption.Encrypted;

import software.wings.annotation.EncryptableSetting;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SAML")
public class SamlSettings extends SSOSettings implements EncryptableSetting {
  @JsonIgnore @NotNull private String metaDataFile;
  @NotNull private String accountId;
  @NotNull private String origin;
  private String logoutUrl;
  private String groupMembershipAttr;
  private String userIdAttr;
  private String entityIdentifier;
  private SAMLProviderType samlProviderType;
  private String clientId;
  @Encrypted(fieldName = "clientSecret") private char[] clientSecret;
  @SchemaIgnore private String encryptedClientSecret;
  private String friendlySamlName;
  private boolean configuredFromNG;
  private boolean authenticationEnabled;
  private boolean jitEnabled;
  private String jitValidationKey;
  private String jitValidationValue;

  @JsonCreator
  @Builder
  public SamlSettings(@JsonProperty("type") SSOType ssoType, @JsonProperty("displayName") String displayName,
      @JsonProperty("url") String url, @JsonProperty("metaDataFile") String metaDataFile,
      @JsonProperty("accountId") String accountId, @JsonProperty("origin") String origin,
      @JsonProperty("groupMembershipAttr") String groupMembershipAttr, @JsonProperty("logoutUrl") String logoutUrl,
      @JsonProperty("entityIdentifier") String entityIdentifier,
      @JsonProperty("providerType") SAMLProviderType samlProviderType, @JsonProperty("clientId") String clientId,
      @JsonProperty("clientSecret") final char[] clientSecret,
      @JsonProperty("friendlySamlName") String friendlySamlName, @JsonProperty("jitEnabled") boolean jitEnabled,
      @JsonProperty("jitValidationKey") String jitValidationKey,
      @JsonProperty("jitValidationValue") String jitValidationValue) {
    super(SSOType.SAML, displayName, url);
    this.metaDataFile = metaDataFile;
    this.accountId = accountId;
    this.origin = origin;
    this.groupMembershipAttr = groupMembershipAttr;
    this.logoutUrl = logoutUrl;
    this.entityIdentifier = entityIdentifier;
    this.samlProviderType = samlProviderType;
    this.clientId = clientId;
    this.clientSecret = clientSecret == null ? null : clientSecret.clone();
    this.friendlySamlName = friendlySamlName;
    this.jitEnabled = jitEnabled;
    this.jitValidationKey = jitValidationKey;
    this.jitValidationValue = jitValidationValue;
  }

  @Override
  public SSOSettings getPublicSSOSettings() {
    return this;
  }

  @Override
  public SSOType getType() {
    return SSOType.SAML;
  }

  @JsonProperty
  public boolean isAuthorizationEnabled() {
    return isNotEmpty(groupMembershipAttr);
  }

  @Override
  public SettingVariableTypes getSettingType() {
    return SSO_SAML;
  }
}