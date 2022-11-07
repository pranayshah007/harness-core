package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOResetService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class CompositeSLOResetServiceImpl implements CompositeSLOResetService {
  @Inject SideKickService sideKickService;

  @Inject Clock clock;

  @Override
  public void reset(CompositeServiceLevelObjective compositeServiceLevelObjective) {
    compositeServiceLevelObjective.setStartedAt(System.currentTimeMillis());

    String sloId = compositeServiceLevelObjective.getUuid();
    int sloVersion = compositeServiceLevelObjective.getVersion();
    long startTime = TimeUnit.MILLISECONDS.toMinutes(compositeServiceLevelObjective
                                                         .getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(),
                                                             compositeServiceLevelObjective.getZoneOffset()))
                                                         .getStartTime()
                                                         .toInstant(ZoneOffset.UTC)
                                                         .toEpochMilli());
    sideKickService.schedule(CompositeSLORecordsCleanupSideKickData.builder()
                                 .sloVersion(sloVersion)
                                 .sloId(sloId)
                                 .afterStartTime(startTime)
                                 .build(),
        clock.instant());
  }
}
