/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeDurationBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.OnetimeDowntimeDetails;
import io.harness.cvng.downtime.utils.DowntimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class OnetimeDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<OnetimeDowntimeDetails, OnetimeDowntimeSpec> {
  @Inject Clock clock;
  @Override
  public OnetimeDowntimeDetails getDowntimeDetails(OnetimeDowntimeSpec spec) {
    switch (spec.getSpec().getType()) {
      case DURATION:
        return Downtime.OnetimeDurationBased.builder()
            .startTime(spec.getStartTime())
            .downtimeDuration(((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration())
            .build();
      case END_TIME:
        return Downtime.EndTimeBased.builder()
            .startTime(spec.getStartTime())
            .endTime(((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime())
            .build();
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }

  @Override
  public OnetimeDowntimeSpec getDowntimeSpec(OnetimeDowntimeDetails entity) {
    switch (entity.getOnetimeDowntimeType()) {
      case DURATION:
        return OnetimeDowntimeSpec.builder()
            .type(OnetimeDowntimeType.DURATION)
            .spec(OnetimeDowntimeSpec.OnetimeDurationBasedSpec.builder()
                      .downtimeDuration(((Downtime.OnetimeDurationBased) entity).getDowntimeDuration())
                      .build())
            .startTime(entity.getStartTime())
            .build();
      case END_TIME:
        return OnetimeDowntimeSpec.builder()
            .type(OnetimeDowntimeType.END_TIME)
            .spec(OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec.builder()
                      .endTime(((Downtime.EndTimeBased) entity).getEndTime())
                      .build())
            .startTime(entity.getStartTime())
            .build();
      default:
        throw new IllegalStateException("type: " + entity.getOnetimeDowntimeType() + " is not handled");
    }
  }

  @Override
  public DowntimeDuration getDowntimeDuration(OnetimeDowntimeDetails entity) {
    switch (entity.getOnetimeDowntimeType()) {
      case DURATION:
        return ((Downtime.OnetimeDurationBased) entity).getDowntimeDuration();
      case END_TIME:
        long durationInSec = ((Downtime.EndTimeBased) entity).getEndTime() - entity.getStartTime();
        return DowntimeUtils.getDowntimeDurationFromSeconds(durationInSec);
      default:
        throw new IllegalStateException("type: " + entity.getOnetimeDowntimeType() + " is not handled");
    }
  }

  @Override
  public boolean isPastDowntime(OnetimeDowntimeDetails entity) {
    switch (entity.getOnetimeDowntimeType()) {
      case DURATION:
        return getEndTime(entity.getStartTime(), ((Downtime.OnetimeDurationBased) entity).getDowntimeDuration())
            < clock.millis() / 1000;
      case END_TIME:
        return ((Downtime.EndTimeBased) entity).getEndTime() < clock.millis() / 1000;
      default:
        throw new IllegalStateException("type: " + entity.getOnetimeDowntimeType() + " is not handled");
    }
  }

  @Override
  public List<Pair<Long, Long>> getStartAndEndTimesForFutureInstances(OnetimeDowntimeSpec spec) {
    if (spec.getStartTime() < clock.millis() / 1000) {
      return Collections.emptyList();
    }
    switch (spec.getSpec().getType()) {
      case DURATION:
        return Collections.singletonList(Pair.of(spec.getStartTime(),
            getEndTime(spec.getStartTime(), ((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration())));
      case END_TIME:
        return Collections.singletonList(
            Pair.of(spec.getStartTime(), ((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime()));
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }
}
