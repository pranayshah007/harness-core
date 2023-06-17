/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import static java.lang.Math.max;

import io.harness.cvng.downtime.beans.DowntimeDuration;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeDurationBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.OnetimeDowntimeDetails;
import io.harness.cvng.downtime.utils.DateTimeUtils;
import io.harness.cvng.downtime.utils.DowntimeUtils;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class OnetimeDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<OnetimeDowntimeDetails, OnetimeDowntimeSpec> {
  @Inject Clock clock;
  @Override
  public OnetimeDowntimeDetails getDowntimeDetails(OnetimeDowntimeSpec spec) {
    Optional<String> startDateTime = Optional.ofNullable(spec.getStartDateTime());
    long startTime = startDateTime.isPresent()
        ? DateTimeUtils.getEpochValueFromDateString(startDateTime.get(), spec.getTimezone())
        : spec.getStartTime();
    switch (spec.getSpec().getType()) {
      case DURATION:
        return Downtime.OnetimeDurationBased.builder()
            .startTime(startTime)
            .downtimeDuration(((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration())
            .build();
      case END_TIME:
        Optional<String> endDateTime = Optional.ofNullable(((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndDateTime());
        return Downtime.EndTimeBased.builder()
            .startTime(startTime)
            .endTime(endDateTime.isPresent()
                    ? DateTimeUtils.getEpochValueFromDateString(endDateTime.get(), spec.getTimezone())
                    : ((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime())
            .build();
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }

  @Override
  public OnetimeDowntimeSpec getDowntimeSpec(OnetimeDowntimeDetails entity, String timeZone) {
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
                      .endDateTime(
                          DateTimeUtils.getDateStringFromEpoch(((Downtime.EndTimeBased) entity).getEndTime(), timeZone))
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
    Optional<String> startDateTime = Optional.ofNullable(spec.getStartDateTime());
    long startTime = startDateTime.isPresent()
        ? DateTimeUtils.getEpochValueFromDateString(spec.getStartDateTime(), spec.getTimezone())
        : spec.getStartTime();
    long endTime;
    long currentTime = clock.millis() / 1000;
    switch (spec.getSpec().getType()) {
      case DURATION:
        endTime = getEndTime(startTime, ((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration());
        if (endTime >= startTime && endTime >= currentTime) {
          return Collections.singletonList(Pair.of(max(startTime, currentTime), endTime));
        }
        return Collections.emptyList();
      case END_TIME:
        Optional<String> endDateTime = Optional.ofNullable(((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndDateTime());
        endTime = endDateTime.isPresent()
            ? DateTimeUtils.getEpochValueFromDateString(endDateTime.get(), spec.getTimezone())
            : ((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime();
        if (endTime >= startTime && endTime >= currentTime) {
          return Collections.singletonList(Pair.of(max(startTime, currentTime), endTime));
        }
        return Collections.emptyList();
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }
}
