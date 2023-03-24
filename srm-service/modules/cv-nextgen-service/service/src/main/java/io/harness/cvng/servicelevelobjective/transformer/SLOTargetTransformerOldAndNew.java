/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer;

import io.harness.cvng.servicelevelobjective.entities.CalenderSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.MonthlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.entities.QuarterlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.entities.RollingSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.SLOTarget;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.WeeklyCalenderTarget;

public class SLOTargetTransformerOldAndNew {
  public static SLOTarget getNewSLOtargetFromOldSLOtarget(ServiceLevelObjective.SLOTarget sloTarget) {
    switch (sloTarget.getType()) {
      case ROLLING:
        ServiceLevelObjective.RollingSLOTarget rollingSLOTarget = (ServiceLevelObjective.RollingSLOTarget) sloTarget;
        return RollingSLOTarget.builder().periodLengthDays(rollingSLOTarget.getPeriodLengthDays()).build();
      case CALENDER:
        ServiceLevelObjective.CalenderSLOTarget calenderSLOTarget = (ServiceLevelObjective.CalenderSLOTarget) sloTarget;
        switch (calenderSLOTarget.getCalenderType()) {
          case WEEKLY:
            ServiceLevelObjective.WeeklyCalenderTarget weeklyCalenderTarget =
                (ServiceLevelObjective.WeeklyCalenderTarget) sloTarget;
            return WeeklyCalenderTarget.builder().dayOfWeek(weeklyCalenderTarget.getDayOfWeek()).build();
          case MONTHLY:
            ServiceLevelObjective.MonthlyCalenderTarget monthlyCalenderTarget =
                (ServiceLevelObjective.MonthlyCalenderTarget) sloTarget;
            return MonthlyCalenderTarget.builder()
                .windowEndDayOfMonth(monthlyCalenderTarget.getWindowEndDayOfMonth())
                .build();
          case QUARTERLY:
            return QuarterlyCalenderTarget.builder().build();
          default:
            throw new IllegalStateException("No such calendar type found " + calenderSLOTarget.getCalenderType());
        }
      default:
        throw new IllegalStateException("No such slo target type found " + sloTarget.getType());
    }
  }

  public static ServiceLevelObjective.SLOTarget getOldSLOtargetFromNewSLOtarget(SLOTarget sloTarget) {
    switch (sloTarget.getType()) {
      case ROLLING:
        RollingSLOTarget rollingSLOTarget = (RollingSLOTarget) sloTarget;
        return ServiceLevelObjective.RollingSLOTarget.builder()
            .periodLengthDays(rollingSLOTarget.getPeriodLengthDays())
            .build();
      case CALENDER:
        CalenderSLOTarget calenderSLOTarget = (CalenderSLOTarget) sloTarget;
        switch (calenderSLOTarget.getCalenderType()) {
          case WEEKLY:
            WeeklyCalenderTarget weeklyCalenderTarget = (WeeklyCalenderTarget) sloTarget;
            return ServiceLevelObjective.WeeklyCalenderTarget.builder()
                .dayOfWeek(weeklyCalenderTarget.getDayOfWeek())
                .build();
          case MONTHLY:
            MonthlyCalenderTarget monthlyCalenderTarget = (MonthlyCalenderTarget) sloTarget;
            return ServiceLevelObjective.MonthlyCalenderTarget.builder()
                .windowEndDayOfMonth(monthlyCalenderTarget.getWindowEndDayOfMonth())
                .build();
          case QUARTERLY:
            return ServiceLevelObjective.QuarterlyCalenderTarget.builder().build();
          default:
            throw new IllegalStateException("No such calendar type found " + calenderSLOTarget.getCalenderType());
        }
      default:
        throw new IllegalStateException("No such slo target type found " + sloTarget.getType());
    }
  }
}
