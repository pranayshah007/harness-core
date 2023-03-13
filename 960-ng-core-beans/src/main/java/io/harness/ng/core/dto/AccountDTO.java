/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@ApiModel(value = "AccountDTO")
@OwnedBy(PL)
@Schema(name = "Account", description = "Account details defined in Harness.")
public class AccountDTO {
  @Schema(description = "Account Identifier.")
  @EntityIdentifier(allowBlank = false)
  @VariableExpression(skipVariableExpression = true)
  String identifier;
  @Schema(description = "Name of the Account.") @NGEntityName String name;
  @Schema(description = "Name of the Company.") String companyName;
  @Schema(description = "Name of the cluster associated with this Account.")
  @VariableExpression(skipVariableExpression = true)
  String cluster;
  @Schema(description = "Default experience of the Account.")
  @VariableExpression(skipVariableExpression = true)
  DefaultExperience defaultExperience;
  @Schema(description = "Specifies weather access to other generation is allowed or not")
  @VariableExpression(skipVariableExpression = true)
  boolean isCrossGenerationAccessEnabled;
  @Schema(description = "Authentication mechanism associated with the account.")
  @VariableExpression(skipVariableExpression = true)
  AuthenticationMechanism authenticationMechanism;
  @Schema(description = "Service Account configuration associated with this Account.")
  @VariableExpression(skipVariableExpression = true)
  ServiceAccountConfig serviceAccountConfig;
  @Schema(description = "Specifies if NextGen is enabled for this Account.")
  @VariableExpression(skipVariableExpression = true)
  boolean isNextGenEnabled;
  @Schema(description = "Specifies if Account is product-let.")
  @VariableExpression(skipVariableExpression = true)
  boolean isProductLed;
  @Schema(description = "Specifies if Account has two factor authentication enforced.")
  @VariableExpression(skipVariableExpression = true)
  boolean isTwoFactorAdminEnforced;
  @Schema(description = "Account creation time in epoch")
  @VariableExpression(skipVariableExpression = true)
  long createdAt;

  @Schema(description = "Specifies delegate ring version for account")
  @VariableExpression(skipVariableExpression = true)
  private String ringName;

  @Builder
  public AccountDTO(String identifier, String name, String companyName, String cluster,
      DefaultExperience defaultExperience, boolean isCrossGenerationAccessEnabled,
      AuthenticationMechanism authenticationMechanism, ServiceAccountConfig serviceAccountConfig,
      boolean isNextGenEnabled, boolean isProductLed, boolean isTwoFactorAdminEnforced, long createdAt,
      String ringName) {
    this.identifier = identifier;
    this.name = name;
    this.companyName = companyName;
    this.cluster = cluster;
    this.defaultExperience = defaultExperience;
    this.isCrossGenerationAccessEnabled = isCrossGenerationAccessEnabled;
    this.authenticationMechanism = authenticationMechanism;
    this.isNextGenEnabled = isNextGenEnabled;
    this.serviceAccountConfig = serviceAccountConfig;
    this.isProductLed = isProductLed;
    this.isTwoFactorAdminEnforced = isTwoFactorAdminEnforced;
    this.ringName = ringName;
    this.createdAt = createdAt;
  }
}
