/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeDurationBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec;
import io.harness.cvng.downtime.beans.OnetimeDowntimeType;
import io.harness.cvng.downtime.entities.Downtime;
import io.harness.cvng.downtime.entities.Downtime.OnetimeDowntimeDetails;

public class OnetimeDowntimeSpecDetailsTransformer
    implements DowntimeSpecDetailsTransformer<OnetimeDowntimeDetails, OnetimeDowntimeSpec> {
  @Override
  public OnetimeDowntimeDetails getDowntimeDetails(OnetimeDowntimeSpec spec) {
    switch (spec.getSpec().getType()) {
      case DURATION:
        return Downtime.OnetimeDurationBased.builder()
            .downtimeDuration(((OnetimeDurationBasedSpec) spec.getSpec()).getDowntimeDuration())
            .build();
      case END_TIME:
        return Downtime.EndTimeBased.builder().endTime(((OnetimeEndTimeBasedSpec) spec.getSpec()).getEndTime()).build();
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
            .build();
      case END_TIME:
        return OnetimeDowntimeSpec.builder()
            .type(OnetimeDowntimeType.END_TIME)
            .spec(OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec.builder()
                      .endTime(((Downtime.EndTimeBased) entity).getEndTime())
                      .build())
            .build();
      default:
        throw new IllegalStateException("type: " + entity.getOnetimeDowntimeType() + " is not handled");
    }
  }
}
