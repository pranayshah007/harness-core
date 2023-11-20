/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataUnavailabilityInstancesHandlerService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SLIDataUnavailabilityInstancesHandlerServiceImpl implements SLIDataUnavailabilityInstancesHandlerService {
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Inject private DowntimeService downtimeService;
  @Override
  public List<SLIRecordParam> filterSLIRecordsToSkip(List<SLIRecordParam> sliRecordList, ProjectParams projectParams,
      Instant startTime, Instant endTime, String monitoredServiceIdentifier, String sliId) {
    // Adding downtime Instances
    List<EntityUnavailabilityStatusesDTO> failureInstances =
        entityUnavailabilityStatusesService.getAllMaintenanceWindowInstances(
            projectParams, startTime.getEpochSecond(), endTime.getEpochSecond());
    failureInstances = downtimeService.filterDowntimeInstancesOnMonitoredService(
        projectParams, failureInstances, monitoredServiceIdentifier);

    // Adding Data collection failure Instances
    failureInstances.addAll(entityUnavailabilityStatusesService.getAllDataCollectionFailureInstances(
        projectParams, sliId, startTime.getEpochSecond(), endTime.getEpochSecond()));

    Set<Instant> failureInstants = new HashSet<>();
    for (EntityUnavailabilityStatusesDTO failureInstance : failureInstances) {
      Instant startInstant = Instant.ofEpochSecond(failureInstance.getStartTime()).truncatedTo(ChronoUnit.MINUTES);
      Instant endInstant = Instant.ofEpochSecond(failureInstance.getEndTime()).truncatedTo(ChronoUnit.MINUTES);
      for (Instant instant = startInstant; !instant.isAfter(endInstant);
           instant = instant.plus(1, ChronoUnit.MINUTES)) {
        failureInstants.add(instant);
      }
    }
    List<SLIRecordParam> updatedRecords = new ArrayList<>();
    for (SLIRecordParam sliRecord : sliRecordList) {
      if (failureInstants.contains(sliRecord.getTimeStamp())) {
        sliRecord.setSliState(SLIState.SKIP_DATA);
        sliRecord.setBadEventCount(0l);
        sliRecord.setGoodEventCount(0l);
        sliRecord.setSkipEventCount(1l);
      }
      updatedRecords.add(sliRecord);
    }
    return updatedRecords;
  }
}
