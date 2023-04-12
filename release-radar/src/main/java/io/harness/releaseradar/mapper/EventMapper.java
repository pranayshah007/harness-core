/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.mapper;

import io.harness.releaseradar.dto.EventRequestDTO;
import io.harness.releaseradar.dto.EventResponseDTO;
import io.harness.releaseradar.entities.EventEntity;
import io.harness.releaseradar.mapper.release.IReleaseVersionMapper;
import io.harness.releaseradar.mapper.release.ReleaseVersionMapperFactory;
import lombok.experimental.UtilityClass;

import java.text.SimpleDateFormat;
import java.util.Date;

@UtilityClass
public class EventMapper {
  private final ReleaseVersionMapperFactory releaseVersionMapperFactory = new ReleaseVersionMapperFactory();
  public static EventEntity toEntity(EventRequestDTO dto) {
    IReleaseVersionMapper releaseVersionMapper =
        releaseVersionMapperFactory.getReleaseVersionMapper(dto.getVersioningScheme());
    String parsedRelease = releaseVersionMapper.parseReleaseVersion(dto.getBuildVersion());

    return EventEntity.builder()
        .eventType(dto.getEventType())
        .buildVersion(dto.getBuildVersion())
        .release(parsedRelease)
        .environment(dto.getEnvironment())
        .epoch(System.currentTimeMillis())
        .serviceName(dto.getServiceName())
        .versioningScheme(dto.getVersioningScheme())
            .createdAt(System.currentTimeMillis())
        .build();
  }

  public static EventResponseDTO toEntity(EventEntity entity) {
    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    return EventResponseDTO.builder()
        .eventType(entity.getEventType())
        .buildVersion(entity.getBuildVersion())
        .release(entity.getRelease())
        .environment(entity.getEnvironment())
        .simpleDateFormat(sdf.format(new Date(entity.getEpoch())))
        .epoch(entity.getEpoch())
        .serviceName(entity.getServiceName())
        .versioningScheme(entity.getVersioningScheme())
        .build();
  }
}
