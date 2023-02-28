/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class UserMetadataMapper {
  public static UserMetadataDTO toDTO(UserInfo user) {
    return UserMetadataDTO.builder()
        .name(user.getName())
        .email(user.getEmail())
        .uuid(user.getUuid())
        .externallyManaged(user.isExternallyManaged())
        .disabled(user.isDisabled())
        .locked(user.isLocked())
        .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
        .build();
  }

  public static UserMetadataDTO toDTO(UserMetadata user) {
    return UserMetadataDTO.builder()
        .name(user.getName())
        .email(user.getEmail())
        .uuid(user.getUserId())
        .disabled(user.isDisabled())
        .externallyManaged(user.isExternallyManaged())
        .locked(Boolean.TRUE.equals(user.isLocked()))
        .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
        .build();
  }
}
