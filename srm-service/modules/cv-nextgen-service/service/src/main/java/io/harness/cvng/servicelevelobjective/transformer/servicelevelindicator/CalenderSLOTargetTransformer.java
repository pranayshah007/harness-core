/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.MonthlyCalenderSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.CalenderSLOTargetSpec.WeeklyCalendarSpec;
import io.harness.cvng.servicelevelobjective.entities.CalenderSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.MonthlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.entities.QuarterlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.entities.WeeklyCalenderTarget;

public class CalenderSLOTargetTransformer implements SLOTargetTransformer<CalenderSLOTarget, CalenderSLOTargetSpec> {
  @Override
  public CalenderSLOTarget getSLOTarget(CalenderSLOTargetSpec spec) {
    switch (spec.getSpec().getType()) {
      case WEEKLY:
        return WeeklyCalenderTarget.builder().dayOfWeek(((WeeklyCalendarSpec) spec.getSpec()).getDayOfWeek()).build();
      case MONTHLY:
        return MonthlyCalenderTarget.builder()
            .windowEndDayOfMonth(((MonthlyCalenderSpec) spec.getSpec()).getDayOfMonth())
            .build();
      case QUARTERLY:
        return QuarterlyCalenderTarget.builder().build();
      default:
        throw new IllegalStateException("type: " + spec.getSpec().getType() + " is not handled");
    }
  }

  @Override
  public CalenderSLOTargetSpec getSLOTargetSpec(CalenderSLOTarget entity) {
    switch (entity.getCalenderType()) {
      case WEEKLY:
        return CalenderSLOTargetSpec.builder()
            .type(SLOCalenderType.WEEKLY)
            .spec(WeeklyCalendarSpec.builder().dayOfWeek(((WeeklyCalenderTarget) entity).getDayOfWeek()).build())
            .build();
      case MONTHLY:
        return CalenderSLOTargetSpec.builder()
            .type(SLOCalenderType.MONTHLY)
            .spec(MonthlyCalenderSpec.builder()
                      .dayOfMonth(((MonthlyCalenderTarget) entity).getWindowEndDayOfMonth())
                      .build())
            .build();
      case QUARTERLY:
        return CalenderSLOTargetSpec.builder()
            .type(SLOCalenderType.QUARTERLY)
            .spec(CalenderSLOTargetSpec.QuarterlyCalenderSpec.builder().build())
            .build();
      default:
        throw new IllegalStateException("type: " + entity.getCalenderType() + " is not handled");
    }
  }
}
