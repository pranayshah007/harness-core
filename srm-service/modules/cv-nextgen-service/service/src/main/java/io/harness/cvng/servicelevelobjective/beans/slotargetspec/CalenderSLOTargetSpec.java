/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slotargetspec;

import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalenderSLOTargetSpec extends SLOTargetSpec {
  @JsonProperty("type") SLOCalenderType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  @NotNull
  CalenderSpec spec;

  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = WeeklyCalendarSpec.class, name = "Weekly")
    , @JsonSubTypes.Type(value = MonthlyCalenderSpec.class, name = "Monthly"),
        @JsonSubTypes.Type(value = QuarterlyCalenderSpec.class, name = "Quarterly"),
  })
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
  public abstract static class CalenderSpec {
    @JsonIgnore public abstract SLOCalenderType getType();
  }
  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class WeeklyCalendarSpec extends CalenderSpec {
    @NotNull private DayOfWeek dayOfWeek;
    @Override
    public SLOCalenderType getType() {
      return SLOCalenderType.WEEKLY;
    }
  }
  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MonthlyCalenderSpec extends CalenderSpec {
    @NotNull private int dayOfMonth;
    @Override
    public SLOCalenderType getType() {
      return SLOCalenderType.MONTHLY;
    }
  }
  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class QuarterlyCalenderSpec extends CalenderSpec {
    @Override
    public SLOCalenderType getType() {
      return SLOCalenderType.QUARTERLY;
    }
  }

  @Override
  public SLOTargetType getType() {
    return SLOTargetType.CALENDER;
  }

  @Override
  public boolean isErrorBudgetResetEnabled() {
    return true;
  }
}
