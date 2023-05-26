/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.ActivityBucket;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeEventMetadata;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class ChangeEventMetaDataTransformer<E extends Activity, M extends ChangeEventMetadata> {
  @Inject NextGenService nextGenService;
  @Inject private MonitoredServiceService monitoredServiceService;

  public abstract E getEntity(ChangeEventDTO changeEventDTO);

  public final ChangeEventDTO getDTO(E activity) {
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builder()
            .accountIdentifier(activity.getAccountId())
            .orgIdentifier(activity.getOrgIdentifier())
            .projectIdentifier(activity.getProjectIdentifier())
            .monitoredServiceIdentifier(activity.getMonitoredServiceIdentifier())
            .build());
    ServiceResponseDTO serviceResponseDTO = nextGenService.getService(activity.getAccountId(),
        activity.getOrgIdentifier(), activity.getProjectIdentifier(), monitoredService.getServiceIdentifier());
    EnvironmentResponseDTO environmentResponseDTO =
        nextGenService.getEnvironment(activity.getAccountId(), activity.getOrgIdentifier(),
            activity.getProjectIdentifier(), monitoredService.getEnvironmentIdentifier()); // TODO: move to a env list
    String serviceName = serviceResponseDTO != null ? serviceResponseDTO.getName() : null;
    String environmentName = environmentResponseDTO != null ? environmentResponseDTO.getName() : null;
    return ChangeEventDTO.builder()
        .id(activity.getUuid())
        .accountId(activity.getAccountId())
        .orgIdentifier(activity.getOrgIdentifier())
        .projectIdentifier(activity.getProjectIdentifier())
        .serviceIdentifier(monitoredService.getServiceIdentifier())
        .serviceName(serviceName)
        .changeSourceIdentifier(activity.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(activity.getMonitoredServiceIdentifier())
        .envIdentifier(monitoredService.getEnvironmentIdentifier())
        .environmentName(environmentName)
        .name(activity.getActivityName())
        .eventTime(activity.getEventTime().toEpochMilli())
        .type(ChangeSourceType.ofActivityType(activity.getType()))
        .metadata(getMetadata(activity))
        .build();
  }

  public final ActivityBucket getActivityBucket(E activity) {
    Instant eventTime =
        Objects.isNull(activity.getEventTime()) ? activity.getActivityStartTime() : activity.getEventTime();
    return ActivityBucket.builder()
        .accountId(activity.getAccountId())
        .orgIdentifier(activity.getOrgIdentifier())
        .projectIdentifier(activity.getProjectIdentifier())
        .monitoredServiceIdentifiers(getMonitoredServiceIdentifiers(activity))
        .bucketTime(DateTimeUtils.roundDownTo5MinBoundary(eventTime))
        .type(activity.getType())
        .build();
  }
  protected List<String> getMonitoredServiceIdentifiers(E activity) {
    return Collections.singletonList(activity.getMonitoredServiceIdentifier());
  }

  protected abstract M getMetadata(E activity);
}
