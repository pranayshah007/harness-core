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

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapUserSettings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("LDAP")
@OwnedBy(HarnessTeam.PL)
@Schema(description = "This has the details of LDAP Settings supported in NG.")
public class LDAPSettings extends NGAuthSettings {
  @Schema(description = "This is the LDAP connection setting.")
  @NotNull
  @Valid
  LdapConnectionSettings connectionSettings;
  @Schema(description = "This is the LDAP setting identifier.") @NotNull private String identifier;

  @Schema(description = "This is the user settings list in LDAP setting.")
  @Valid
  List<LdapUserSettings> userSettingsList;

  @Schema(description = "This is the group settings list in LDAP setting.")
  @Valid
  List<LdapGroupSettings> groupSettingsList;

  @Schema(description = "This is the LDAP setting display name.") @NotNull private String displayName;
  @Schema(description = "This is the cron expression in LDAP Settings.") private String cronExpression;
  @Schema(description = "This is the list of iterations for next LDAP sync job.") private List<Long> nextIterations;

  public LDAPSettings(@JsonProperty("connectionSettings") LdapConnectionSettings connectionSettings,
      @JsonProperty("identifier") String identifier,
      @JsonProperty("userSettingsList") List<LdapUserSettings> userSettingsList,
      @JsonProperty("groupSettingsList") List<LdapGroupSettings> groupSettingsList,
      @JsonProperty("displayName") String displayName, @JsonProperty("cronExpression") String cronExpression,
      @JsonProperty("nextIterations") List<Long> nextIterations) {
    super(AuthenticationMechanism.LDAP);
    this.connectionSettings = connectionSettings;
    this.userSettingsList = userSettingsList;
    this.groupSettingsList = groupSettingsList;
    this.identifier = identifier;
    this.displayName = displayName;
    this.cronExpression = cronExpression == null ? "" : cronExpression;
    this.nextIterations = nextIterations == null ? new ArrayList<>() : nextIterations;
  }

  @Override
  public AuthenticationMechanism getSettingsType() {
    return AuthenticationMechanism.LDAP;
  }
}
