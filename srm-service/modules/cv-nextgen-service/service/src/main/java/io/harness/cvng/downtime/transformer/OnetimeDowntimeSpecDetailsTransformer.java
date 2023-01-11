/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.OnetimeDowntimeSpecDetails;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpecDetails.OnetimeDurationBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpecDetails.OnetimeEndTimeBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.OnetimeDowntimeDetails;
import io.harness.cvng.utils.DateTimeParser;

public class OnetimeDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<OnetimeDowntimeDetails, OnetimeDowntimeSpecDetails> {
  @Override
  public OnetimeDowntimeDetails getDowntimeDetails(OnetimeDowntimeSpecDetails spec) {
    switch (spec.getSpec().getType()) {
      case DURATION:
        return Downtime.OnetimeDurationBased.builder()
            .downtimeDuration(((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration())
            .build();
      case END_TIME:
        return Downtime.EndTimeBased.builder()
            .endTime(DateTimeParser.getEpochSecond(
                ((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime(), spec.getTimezone()))
            .build();
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }

  @Override
  public OnetimeDowntimeSpecDetails getDowntimeSpec(OnetimeDowntimeDetails entity) {
    switch (entity.getOnetimeDowntimeType()) {
      case DURATION:
        return OnetimeDowntimeSpecDetails.builder()
            .type(OnetimeDowntimeType.DURATION)
            .spec(OnetimeDowntimeSpecDetails.OnetimeDurationBasedSpec.builder()
                      .downtimeDuration(((Downtime.OnetimeDurationBased) entity).getDowntimeDuration())
                      .build())
            .build();
      case END_TIME:
        return OnetimeDowntimeSpecDetails.builder()
            .type(OnetimeDowntimeType.END_TIME)
            .spec(OnetimeDowntimeSpecDetails.OnetimeEndTimeBasedSpec.builder()
                      .endTime(DateTimeParser.getDateTime(
                          ((Downtime.EndTimeBased) entity).getEndTime(), entity.getTimezone()))
                      .build())
            .build();
      default:
        throw new IllegalStateException("type: " + entity.getOnetimeDowntimeType() + " is not handled");
    }
  }
}
