/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.deploydetective.mapper;

import io.harness.deploydetective.dto.EventRequestDTO;
import io.harness.deploydetective.dto.EventResponseDTO;
import io.harness.deploydetective.entities.EventEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EventMapper {
  public static EventEntity toDTO(EventRequestDTO dto) {
    return EventEntity.builder()
        .eventType(dto.getEventType())
        .buildVersion(dto.getBuildVersion())
        .environment(dto.getEnvironment())
        .epoch(dto.getEpoch())
        .serviceName(dto.getServiceName())
        .versioningScheme(dto.getVersioningScheme())
        .build();
  }

  public static EventResponseDTO toDTO(EventEntity entity) {
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    return EventResponseDTO.builder()
        .eventType(entity.getEventType())
        .buildVersion(entity.getBuildVersion())
        .environment(entity.getEnvironment())
        .simpleDateFormat(sdf.format(new Date(entity.getEpoch())))
        .serviceName(entity.getServiceName())
        .versioningScheme(entity.getVersioningScheme())
        .build();
  }
}
