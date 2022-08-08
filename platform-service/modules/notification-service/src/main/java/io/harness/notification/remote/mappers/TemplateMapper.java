/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.mappers;

import io.harness.notification.entities.NotificationTemplate;
import io.harness.notification.remote.dto.TemplateDTO;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplateMapper {
  public static Optional<TemplateDTO> toDTO(NotificationTemplate template) {
    if (!Optional.ofNullable(template).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(TemplateDTO.builder()
                           .team(template.getTeam())
                           .identifier(template.getIdentifier())
                           .createdAt(template.getCreatedAt())
                           .lastModifiedAt(template.getLastUpdatedAt())
                           .file(template.getFile())
                           .build());
  }
}
