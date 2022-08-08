/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;

import software.wings.resources.stats.model.WeeklyRange;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonCreator;

@Getter
@ToString
@EqualsAndHashCode
@ParametersAreNonnullByDefault
@OwnedBy(HarnessTeam.CDC)
public class WeeklyFreezeConfig extends GovernanceFreezeConfig {
  // if freezeForAllApps=true, ignore appIds
  private WeeklyRange weeklyRange;

  public WeeklyRange getWeeklyRange() {
    return weeklyRange;
  }

  @JsonCreator
  public WeeklyFreezeConfig(@JsonProperty("freezeForAllApps") boolean freezeForAllApps,
      @JsonProperty("appIds") List<String> appIds,
      @JsonProperty("environmentTypes") List<EnvironmentType> environmentTypes,
      @JsonProperty("weeklyRange") WeeklyRange weeklyRange, @JsonProperty("name") String name,
      @JsonProperty("description") String description, @JsonProperty("applicable") boolean applicable,
      @JsonProperty("appSelections") List<ApplicationFilter> appSelections,
      @JsonProperty("userGroups") List<String> userGroups, @JsonProperty("uuid") String uuid,
      @JsonProperty("userGroupSelection") UserGroupFilter userGroupSelection) {
    super(freezeForAllApps, appIds, environmentTypes, name, description, applicable, appSelections, userGroups, uuid,
        userGroupSelection);
    this.weeklyRange = weeklyRange;
  }

  @Override
  public long fetchEndTime() {
    // TODO: return correct end time
    return 0;
  }
}
