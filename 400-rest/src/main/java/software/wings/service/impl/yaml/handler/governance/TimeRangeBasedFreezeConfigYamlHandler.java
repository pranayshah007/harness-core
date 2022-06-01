/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.governance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.AllUserGroupFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.ApplicationFilterYaml;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomUserGroupFilter;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeBasedFreezeConfig.TimeRangeBasedFreezeConfigBuilder;
import io.harness.governance.TimeRangeBasedFreezeConfig.Yaml;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.governance.UserGroupFilter;
import io.harness.governance.UserGroupFilterYaml;
import io.harness.validation.Validator;

import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class TimeRangeBasedFreezeConfigYamlHandler
    extends GovernanceFreezeConfigYamlHandler<Yaml, TimeRangeBasedFreezeConfig> {
  @Inject YamlHelper yamlHelper;
  @Inject YamlHandlerFactory yamlHandlerFactory;
  @Inject EnvironmentService environmentService;
  @Inject UserGroupService userGroupService;
  @Inject GovernanceConfigService governanceConfigService;
  private static final long FREEZE_WINDOW_TIME = 157680000000L;

  @Override
  public Yaml toYaml(TimeRangeBasedFreezeConfig bean, String accountId) {
    ApplicationFilterYamlHandler applicationFilterYamlHandler;

    List<ApplicationFilterYaml> appFiltersYaml = new ArrayList<>();

    for (ApplicationFilter applicationFilter : bean.getAppSelections()) {
      applicationFilterYamlHandler =
          yamlHandlerFactory.getYamlHandler(YamlType.APPLICATION_FILTER, applicationFilter.getFilterType().name());
      appFiltersYaml.add(applicationFilterYamlHandler.toYaml(applicationFilter, accountId));
    }

    TimeRange.Yaml timeRangeYaml = bean.getTimeRange().toYaml();
    final UserGroupFilterYaml userGroupFilterYaml =
        buildUserGroupSelectionYaml(bean.getUserGroupSelection(), accountId);

    return Yaml.builder()
        .name(bean.getName())
        .type("TIME_RANGE_BASED_FREEZE_CONFIG")
        .description(bean.getDescription())
        .applicable(bean.isApplicable())
        .appSelections(appFiltersYaml)
        .userGroups(getUserGroupNames(bean.getUserGroups(), accountId))
        .userGroupSelection(userGroupFilterYaml)
        .timeRange(timeRangeYaml)
        .build();
  }

  private UserGroupFilterYaml buildUserGroupSelectionYaml(UserGroupFilter userGroupSelection, String accountId) {
    if (userGroupSelection == null) {
      return null;
    }
    if (BlackoutWindowFilterType.ALL.equals(userGroupSelection.getFilterType())) {
      return UserGroupFilterYaml.builder().filterType(BlackoutWindowFilterType.ALL).build();
    } else if (BlackoutWindowFilterType.CUSTOM.equals(userGroupSelection.getFilterType())) {
      return UserGroupFilterYaml.builder()
          .userGroupNames(getUserGroupNames(((CustomUserGroupFilter) userGroupSelection).getUserGroups(), accountId))
          .filterType(BlackoutWindowFilterType.CUSTOM)
          .build();
    } else {
      throw new InvalidRequestException("User group filter type can be CUSTOM or ALL");
    }
  }

  @Override
  public TimeRangeBasedFreezeConfig upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    TimeRangeBasedFreezeConfigBuilder timeRangeBasedFreezeConfig = TimeRangeBasedFreezeConfig.builder();
    toBean(timeRangeBasedFreezeConfig, changeContext, changeSetContext);
    final TimeRangeBasedFreezeConfig rangeBasedFreezeConfig = timeRangeBasedFreezeConfig.build();
    if (rangeBasedFreezeConfig.getUserGroupSelection() == null && isEmpty(rangeBasedFreezeConfig.getUserGroups())) {
      throw new InvalidRequestException("Both user group and user group selection can't be null");
    }

    if (rangeBasedFreezeConfig.getUserGroupSelection() != null && isNotEmpty(rangeBasedFreezeConfig.getUserGroups())) {
      throw new InvalidRequestException("Both user group and user group selection can't be selected");
    }
    return rangeBasedFreezeConfig;
  }

  @Override
  public Class getYamlClass() {
    return TimeRangeBasedFreezeConfig.Yaml.class;
  }

  private List<String> getUserGroupNames(List<String> userGroupList, String accountId) {
    if (isEmpty(userGroupList)) {
      return Collections.emptyList();
    }
    List<String> userGroupNames = new ArrayList<>();
    for (String userGroupId : userGroupList) {
      UserGroup userGroup = userGroupService.get(accountId, userGroupId);
      if (userGroup != null) {
        userGroupNames.add(userGroup.getName());
      }
    }
    return userGroupNames;
  }

  private List<String> getUserGroupIds(List<String> userGroupNames, String accountId, boolean isEmptyUserGroupAllowed) {
    if (isEmptyUserGroupAllowed) {
      return Collections.emptyList();
    }
    if (EmptyPredicate.isEmpty(userGroupNames)) {
      throw new InvalidRequestException("User Groups cannot be empty");
    }
    List<String> userGroupIds = new ArrayList<>();
    for (String userGroupName : userGroupNames) {
      UserGroup userGroup = userGroupService.getByName(accountId, userGroupName);
      if (userGroup == null) {
        throw new InvalidRequestException("Invalid User Group name: " + userGroupName);
      }
      userGroupIds.add(userGroup.getUuid());
    }
    return userGroupIds;
  }

  private void toBean(
      TimeRangeBasedFreezeConfigBuilder bean, ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String accountId = changeContext.getChange().getAccountId();
    Validator.notNullCheck("AccountId not present", accountId);

    TimeRangeBasedFreezeConfig.Yaml yaml = changeContext.getYaml();
    Validator.notNullCheck("Name is required", yaml.getName());
    TimeRange.Yaml timeRangeYaml = yaml.getTimeRange();

    bean.timeRange(validateTimeRangeYaml(timeRangeYaml));
    bean.name(yaml.getName());
    bean.applicable(yaml.isApplicable());
    bean.description(yaml.getDescription());
    bean.userGroups(getUserGroupIds(yaml.getUserGroups(), accountId, yaml.getUserGroupSelection() != null));
    bean.userGroupSelection(convertSelectionToBean(yaml.getUserGroupSelection(), accountId));

    ApplicationFilterYamlHandler applicationFilterYamlHandler;

    List<ApplicationFilter> applicationFilters = new ArrayList<>();
    for (ApplicationFilterYaml entry : yaml.getAppSelections()) {
      applicationFilterYamlHandler =
          yamlHandlerFactory.getYamlHandler(YamlType.APPLICATION_FILTER, entry.getFilterType().name());
      ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, entry);
      applicationFilters.add(applicationFilterYamlHandler.upsertFromYaml(clonedContext.build(), changeSetContext));
    }

    bean.appSelections(applicationFilters);
  }

  private UserGroupFilter convertSelectionToBean(UserGroupFilterYaml userGroupSelection, String accountId) {
    if (userGroupSelection == null) {
      return null;
    }
    if (BlackoutWindowFilterType.ALL.equals(userGroupSelection.getFilterType())) {
      return AllUserGroupFilter.builder().userGroupFilterType(BlackoutWindowFilterType.ALL).build();
    } else {
      return CustomUserGroupFilter.builder()
          .userGroupFilterType(BlackoutWindowFilterType.CUSTOM)
          .userGroups(getUserGroupIds(userGroupSelection.getUserGroupNames(), accountId, false))
          .build();
    }
  }

  private TimeRange validateTimeRangeYaml(TimeRange.Yaml timeRangeYaml) {
    Validator.notNullCheck("Time Range cannot be empty", timeRangeYaml);
    Validator.notNullCheck("From time cannot be empty", timeRangeYaml.getFrom());
    Validator.notNullCheck("Time zone cannot be empty", timeRangeYaml.getTimeZone());
    if (timeRangeYaml.isDurationBased()) {
      Validator.notNullCheck("Duration cannot be empty", timeRangeYaml.getDuration());
    } else {
      Validator.notNullCheck("To time cannot be empty", timeRangeYaml.getTo());
    }

    String timeZone = timeRangeYaml.getTimeZone();

    long from;
    long to;
    Long duration = null;
    Long endTime = null;
    boolean expires = true;
    TimeRangeOccurrence freezeOccurrence = null;
    boolean durationBased = timeRangeYaml.isDurationBased();
    try {
      from = Long.parseLong(timeRangeYaml.getFrom());
      if (durationBased) {
        duration = Long.parseLong(timeRangeYaml.getDuration());
        to = duration + from;
      } else {
        to = Long.parseLong(timeRangeYaml.getTo());
      }
    } catch (NumberFormatException exception) {
      throw new InvalidRequestException("Incorrect format for Time Range. Please enter epoch time");
    }

    if (isNotEmpty(timeRangeYaml.getEndTime()) && isNotEmpty(timeRangeYaml.getFreezeOccurrence())) {
      try {
        endTime = Long.parseLong(timeRangeYaml.getEndTime());
        freezeOccurrence = TimeRangeOccurrence.valueOf(timeRangeYaml.getFreezeOccurrence());
      } catch (NumberFormatException exception) {
        throw new InvalidRequestException("Incorrect format for Time Range. Please enter epoch time");
      } catch (IllegalArgumentException exception) {
        throw new InvalidRequestException(String.format(
            "Invalid occurrence for TimeRange. Please enter valid value: %s", TimeRangeOccurrence.values()));
      }
    }
    /*
     Setting the expires as false if the freeze window time is more than 5 years
     */
    if (timeRangeYaml.getEndTime() != null
        && Long.parseLong(timeRangeYaml.getEndTime()) - Long.parseLong(timeRangeYaml.getFrom()) >= FREEZE_WINDOW_TIME) {
      expires = false;
    } else if (timeRangeYaml.getFreezeOccurrence() == null) {
      expires = false;
    }
    // time zone from DB document
    return new TimeRange(from, to, timeZone, durationBased, duration, endTime, freezeOccurrence, expires);
  }
}
