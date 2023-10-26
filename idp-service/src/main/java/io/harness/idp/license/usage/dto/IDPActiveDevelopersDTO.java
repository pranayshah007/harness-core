/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.idp.common.DateUtils.milliSecondsToDateWithFormat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.licensing.usage.beans.LicenseUsageDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@OwnedBy(HarnessTeam.IDP)
public class IDPActiveDevelopersDTO extends LicenseUsageDTO {
  @NotNull String identifier;
  @NotNull String email;
  @NotNull String name;
  @NotNull String lastAccessedAt;

  public static IDPActiveDevelopersDTO fromActiveDevelopersEntity(ActiveDevelopersEntity activeDevelopersEntity) {
    return IDPActiveDevelopersDTO.builder()
        .identifier(activeDevelopersEntity.getUserIdentifier())
        .email(activeDevelopersEntity.getEmail())
        .name(activeDevelopersEntity.getUserName())
        .lastAccessedAt(milliSecondsToDateWithFormat(activeDevelopersEntity.getLastAccessedAt(), "MM-dd-yyyy"))
        .build();
  }
}
