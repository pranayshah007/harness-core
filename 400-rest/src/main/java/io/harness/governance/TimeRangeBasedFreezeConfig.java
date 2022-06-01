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
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.resources.stats.model.TimeRange;
import software.wings.service.impl.yaml.handler.governance.GovernanceFreezeConfigYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Objects;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.annotate.JsonCreator;
import org.mongodb.morphia.annotations.Transient;

@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
@Slf4j
@ParametersAreNonnullByDefault
@OwnedBy(HarnessTeam.CDC)
public class TimeRangeBasedFreezeConfig extends GovernanceFreezeConfig {
  // if freezeForAllApps=true, ignore appIds
  @Setter private TimeRange timeRange;

  public enum FreezeWindowStateType { ACTIVE, INACTIVE }
  // this field is used for proper UI rendering
  @Transient @EqualsAndHashCode.Exclude private FreezeWindowStateType freezeWindowState;

  public TimeRange getTimeRange() {
    return timeRange;
  }

  @Builder
  @JsonCreator
  public TimeRangeBasedFreezeConfig(@JsonProperty("freezeForAllApps") boolean freezeForAllApps,
      @JsonProperty("appIds") List<String> appIds,
      @JsonProperty("environmentTypes") List<EnvironmentType> environmentTypes,
      @JsonProperty("timeRange") TimeRange timeRange, @JsonProperty("name") String name,
      @JsonProperty("description") String description, @JsonProperty("applicable") boolean applicable,
      @JsonProperty("appSelections") List<ApplicationFilter> appSelections,
      @JsonProperty("userGroups") List<String> userGroups, @JsonProperty("uuid") String uuid,
      @JsonProperty("userGroupSelections") UserGroupFilter userGroupSelection) {
    super(freezeForAllApps, appIds, environmentTypes, name, description, applicable, appSelections, userGroups, uuid,
        userGroupSelection);
    this.timeRange = Objects.requireNonNull(timeRange, "time-range not provided for deployment freeze");

    if (timeRange.getFrom() > timeRange.getTo()) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Start time of time-range must be strictly less than end time.");
    }
  }

  public boolean checkIfActive() {
    if (!isApplicable()) {
      return false;
    }
    long currentTime = System.currentTimeMillis();
    log.info("Window id: {}, Current time: {}, from: {}, to: {}", getUuid(), currentTime, getTimeRange().getFrom(),
        getTimeRange().getTo());
    if (timeRange == null) {
      log.warn("Time range is null for deployment freeze window: " + getUuid());
      return false;
    }
    if (timeRange.getFreezeOccurrence() != null) {
      return timeRange.isInRange();
    }
    return currentTime <= getTimeRange().getTo() && currentTime >= getTimeRange().getFrom();
  }

  public FreezeWindowStateType getFreezeWindowState() {
    if (!isApplicable()) {
      return FreezeWindowStateType.INACTIVE;
    }
    long currentTime = System.currentTimeMillis();
    log.info("Window id: {}, Current time: {}, from: {}, to: {}", getUuid(), currentTime, getTimeRange().getFrom(),
        getTimeRange().getTo());
    if (timeRange == null) {
      log.warn("Time range is null for deployment freeze window: " + getUuid());
      return FreezeWindowStateType.INACTIVE;
    }
    if (timeRange.getFreezeOccurrence() != null) {
      return timeRange.isInRange() ? FreezeWindowStateType.ACTIVE : FreezeWindowStateType.INACTIVE;
    }
    return currentTime <= getTimeRange().getTo() && currentTime >= getTimeRange().getFrom()
        ? FreezeWindowStateType.ACTIVE
        : FreezeWindowStateType.INACTIVE;
  }

  public void toggleExpiredWindowsOff() {
    if (checkWindowExpired()) {
      setApplicable(false);
    }
  }

  public boolean checkWindowExpired() {
    long currentTime = System.currentTimeMillis();
    if (timeRange != null) {
      // After all iterations of scheduled windows are done, toggle the window
      if (timeRange.getFreezeOccurrence() != null && currentTime > timeRange.getEndTime()) {
        return true;
        // toggle scheduled NEVER reoccurring windows and start now windows
      } else if (timeRange.getFreezeOccurrence() == null && currentTime > timeRange.getTo()) {
        return true;
      }
    }
    return false;
  }

  public void recalculateFreezeWindowState() {
    this.freezeWindowState = getFreezeWindowState();
  }

  @Override
  public long fetchEndTime() {
    return getTimeRange().getTo();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("TIME_RANGE_BASED_FREEZE_CONFIG")
  public static final class Yaml extends GovernanceFreezeConfigYaml {
    private String name;
    private String description;
    private boolean applicable;
    private List<String> userGroups;
    private List<ApplicationFilterYaml> appSelections;
    private TimeRange.Yaml timeRange;
    private UserGroupFilterYaml userGroupSelection;

    @Builder
    public Yaml(String type, String name, String description, boolean applicable, List<String> userGroups,
        List<ApplicationFilterYaml> appSelections, TimeRange.Yaml timeRange, UserGroupFilterYaml userGroupSelection) {
      super(type);
      setName(name);
      setDescription(description);
      setApplicable(applicable);
      setUserGroups(userGroups);
      setAppSelections(appSelections);
      setTimeRange(timeRange);
      setUserGroupSelection(userGroupSelection);
    }

    public Yaml() {
      super("TIME_RANGE_BASED_FREEZE_CONFIG");
    }
  }
}
