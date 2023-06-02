/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(PL)
public class UserInfo {
  @EqualsAndHashCode.Include @ApiModelProperty(required = true) String uuid;
  String name;
  String email;
  String token;
  String defaultAccountId;
  String intent;
  List<GatewayAccountRequestDTO> accounts;
  boolean admin;
  boolean twoFactorAuthenticationEnabled;
  boolean emailVerified;
  @Getter(value = AccessLevel.PRIVATE) boolean locked;
  boolean disabled;
  String signupAction;
  String edition;
  String billingFrequency;
  UtmInfo utmInfo;
  boolean externallyManaged;
  String givenName;
  String familyName;
  String externalId;
  long createdAt;
  long lastUpdatedAt;

  public boolean isLocked() {
    return this.locked;
  }
}
