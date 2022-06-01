/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.deploymentfreezewindow;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.governance.BlackoutWindowFilterType.ALL;
import static io.harness.governance.BlackoutWindowFilterType.CUSTOM;
import static io.harness.governance.EnvironmentFilter.EnvironmentFilterType;

import static java.lang.String.format;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.AllAppFilter;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.AllNonProdEnvFilter;
import io.harness.governance.AllProdEnvFilter;
import io.harness.governance.AllUserGroupFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.CustomEnvFilter;
import io.harness.governance.CustomUserGroupFilter;
import io.harness.governance.EnvironmentFilter;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.governance.TimeRangeOccurrence;
import io.harness.governance.UserGroupFilter;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLCreateDeploymentFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLServiceTypeFilterInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLSetupInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLUpdateDeploymentFreezeWindowInput;
import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLUserGroupFilterInput;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLDeploymentFreezeWindow;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLFreezeWindow;
import software.wings.graphql.schema.type.deploymentfreezewindow.QLSetup;
import software.wings.resources.stats.model.TimeRange;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeploymentFreezeWindowController {
  @Inject UserGroupService userGroupService;
  @Inject AppService appService;
  @Inject EnvironmentService environmentService;
  @Inject ServiceResourceService serviceResourceService;

  public TimeRangeBasedFreezeConfig populateDeploymentFreezeWindowEntity(
      QLCreateDeploymentFreezeWindowInput qlCreateDeploymentFreezeWindowInput) {
    // APP SELECTIONS POPULATED
    List<ApplicationFilter> appSelections =
        populateAppSelectionsEntity(qlCreateDeploymentFreezeWindowInput.getFreezeWindows());

    // TIME RANGE POPULATED
    TimeRange timeRange = populateTimeRangeEntity(qlCreateDeploymentFreezeWindowInput.getSetup());
    String uuid = null;

    final UserGroupFilter userGroupSelection =
        populateUserGroupSelection(qlCreateDeploymentFreezeWindowInput.getUserGroupSelection());

    return TimeRangeBasedFreezeConfig.builder()
        .name(qlCreateDeploymentFreezeWindowInput.getName().trim())
        .description(qlCreateDeploymentFreezeWindowInput.getDescription())
        .applicable(false)
        .appSelections(appSelections)
        .timeRange(timeRange)
        .userGroups(qlCreateDeploymentFreezeWindowInput.getNotifyTo())
        .uuid(uuid)
        .userGroupSelection(userGroupSelection)
        .build();
  }

  private QLUserGroupFilterInput populateUserGroupSelection(UserGroupFilter userGroupsToNotify) {
    if (userGroupsToNotify == null) {
      return null;
    }
    final BlackoutWindowFilterType userGroupFilterType = userGroupsToNotify.getFilterType();
    if (ALL.equals(userGroupFilterType)) {
      return QLUserGroupFilterInput.builder().userGroupFilterType(ALL).build();
    } else {
      return QLUserGroupFilterInput.builder()
          .userGroupFilterType(CUSTOM)
          .userGroupIds(((CustomUserGroupFilter) userGroupsToNotify).getUserGroups())
          .build();
    }
  }

  private UserGroupFilter populateUserGroupSelection(QLUserGroupFilterInput userGroupsToNotify) {
    if (userGroupsToNotify == null) {
      return null;
    }
    final BlackoutWindowFilterType userGroupFilterType = userGroupsToNotify.getUserGroupFilterType();
    if (ALL.equals(userGroupFilterType)) {
      return AllUserGroupFilter.builder().userGroupFilterType(ALL).build();
    } else {
      return CustomUserGroupFilter.builder()
          .userGroupFilterType(CUSTOM)
          .userGroups(userGroupsToNotify.getUserGroupIds())
          .build();
    }
  }

  private TimeRange populateTimeRangeEntity(QLSetupInput qlSetupInput) {
    Long from, to, duration = null, endTime = null;
    TimeRangeOccurrence freezeOccurrence = null;
    boolean durationBased = qlSetupInput.getIsDurationBased();
    String timeZone = qlSetupInput.getTimeZone();

    boolean expires = false;
    Long FIVE_YEARS_IN_MILLIS = 157784760000L;
    Long ONE_YEAR_IN_MILLIS = FIVE_YEARS_IN_MILLIS / 5;

    // DURATION-BASED DEPLOYMENT FREEZE WINDOWS
    if (durationBased) {
      // DURATION
      duration = qlSetupInput.getDuration();
      if (duration == null) {
        throw new InvalidRequestException(
            "Please provide 'duration' parameter for Duration-based deployment freeze windows");
      }

      // DURATION > 30 MIN CHECK
      if (duration < 1800000) {
        throw new InvalidRequestException(
            "'duration' parameter must be greater or equal to 30 minutes(1800000 ms). Please enter a valid duration");
      }

      // DURATION MAX. 1YEAR
      if (duration > ONE_YEAR_IN_MILLIS) {
        throw new InvalidRequestException("'duration' parameter cannot exceed one year.");
      }

      // FROM
      from = System.currentTimeMillis();

      // TO
      to = System.currentTimeMillis() + duration;

      // CHECK FOR INVALID INPUTS
      if (qlSetupInput.getTo() != null || qlSetupInput.getFrom() != null || qlSetupInput.getFreezeOccurrence() != null
          || qlSetupInput.getUntilForever() != null || qlSetupInput.getExpiryTime() != null) {
        throw new InvalidRequestException(
            "You have provided some unnecessary input(s) for Duration-Based deployment freeze window. Please provide only valid inputs as applicable to the Duration-Based deployment freeze windows.");
      }

    } else { // SCHEDULE-BASED DEPLOYMENT FREEZE WINDOWS

      // FROM
      from = qlSetupInput.getFrom();
      if (from == null) {
        throw new InvalidRequestException(
            "Please provide 'from' parameter for Schedule-based deployment freeze windows");
      }

      // TO
      to = qlSetupInput.getTo();
      if (to == null) {
        throw new InvalidRequestException("Please provide 'to' parameter for Schedule-based deployment freeze windows");
      }

      // TO MAX. 5 YEARS FROM NOW
      if (to > System.currentTimeMillis() + FIVE_YEARS_IN_MILLIS) {
        throw new InvalidRequestException("'to' parameter cannot be more than 5 years from now.");
      }

      // TO > FROM CHECK
      if (to < from) {
        throw new InvalidRequestException("'to' parameter should always be greater than 'from' parameter.");
      }

      // DURATION > 30 MIN CHECK
      if (to - from < 1800000) {
        throw new InvalidRequestException(
            "'to' parameter must be greater than 'from' parameter by atleast 30 minutes(1800000 ms). Please enter a valid duration.");
      }

      // DURATION MAX. 1YEAR
      if (to - from > ONE_YEAR_IN_MILLIS) {
        throw new InvalidRequestException("duration of the freeze cannot exceed one year.");
      }

      // FREEZE OCCURRENCE
      freezeOccurrence = qlSetupInput.getFreezeOccurrence();
      if (freezeOccurrence != null) {
        // EXPIRES(!UNTIL_FOREVER)
        if (qlSetupInput.getUntilForever() != null) {
          expires = !qlSetupInput.getUntilForever();

          if (expires) {
            // END_TIME(EXPIRY_TIME)
            endTime = qlSetupInput.getExpiryTime();

            if (endTime == null) {
              throw new InvalidRequestException(
                  "Please provide 'expiryTime' parameter for Schedule-based deployment freeze windows that expires");
            }

            if (endTime < to) {
              throw new InvalidRequestException("'endTime' parameter cannot be less than the 'to' parameter.");
            }
          } else {
            if (qlSetupInput.getExpiryTime() != null) {
              throw new InvalidRequestException(
                  "'expiryTime' parameter must not be provided for Schedule-based deployment freeze windows that do not expire");
            }

            // END_TIME -> 5 YEARS FOR UNTIL_FOREVER -> TRUE
            endTime = System.currentTimeMillis() + FIVE_YEARS_IN_MILLIS;
          }
        } else {
          throw new InvalidRequestException(
              "'untilForever' parameter cannot be null in a recurring deployment freeze window.");
        }
      } else {
        if (qlSetupInput.getUntilForever() != null) {
          throw new InvalidRequestException(
              "'untilForever' parameter must be null in a non-recurring deployment freeze window.");
        }

        if (qlSetupInput.getExpiryTime() != null) {
          throw new InvalidRequestException(
              "'expiryTime' parameter must be null in a non-recurring deployment freeze window.");
        }
      }

      // INVALID INPUTS FOR SCHEDULED-BASED DFW
      if (qlSetupInput.getDuration() != null) {
        throw new InvalidRequestException(
            "You have provided some unnecessary input(s) for Scheduled-Based deployment freeze window. Please provide only valid inputs as applicable to the Scheduled-Based deployment freeze windows.");
      }
    }

    // SETUP CREATED
    return new TimeRange(from, to, timeZone, durationBased, duration, endTime, freezeOccurrence, expires);
  }

  private List<ApplicationFilter> populateAppSelectionsEntity(List<QLFreezeWindowInput> qlFreezeWindowInputList) {
    List<ApplicationFilter> applicationFilterList = new ArrayList<>();

    // ITERATING THE APP_SELECTIONS
    for (QLFreezeWindowInput qlFreezeWindowInput : qlFreezeWindowInputList) {
      // APP_FILTER -> ALL
      if (qlFreezeWindowInput.getAppFilter() == ALL) {
        BlackoutWindowFilterType filterType = ALL;

        if (qlFreezeWindowInput.getAppIds() != null || qlFreezeWindowInput.getEnvIds() != null
            || qlFreezeWindowInput.getServIds() != null) {
          throw new InvalidRequestException(
              "'appIds', 'envIds' and 'servIds' must not be given when 'appFilter' is selected as 'ALL'.");
        }
        QLServiceTypeFilterInput serviceTypeFilter = qlFreezeWindowInput.getServiceTypeFilter();
        if (!QLServiceTypeFilterInput.ALL.equals(serviceTypeFilter)) {
          throw new InvalidRequestException(
              "Invalid filter type. 'Custom' service filter type is applicable only for a single application. You have selected all applications.");
        }

        ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

        switch (qlFreezeWindowInput.getEnvTypeFilter()) {
          // ENV_FILTER_TYPE -> ALL
          case ALL: {
            EnvironmentFilter envSelection = new AllEnvFilter(EnvironmentFilterType.ALL);

            ApplicationFilter applicationFilter = new AllAppFilter(filterType, envSelection, serviceFilter);
            applicationFilterList.add(applicationFilter);
            break;
          }

          // ENV_FILTER_TYPE -> ALL_PROD
          case ALL_PROD: {
            EnvironmentFilter envSelection = new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD);
            ApplicationFilter applicationFilter = new AllAppFilter(filterType, envSelection, serviceFilter);
            applicationFilterList.add(applicationFilter);
            break;
          }

          // ENV_FILTER_TYPE -> ALL_NON_PROD
          case ALL_NON_PROD: {
            EnvironmentFilter envSelection = new AllNonProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD);
            ApplicationFilter applicationFilter = new AllAppFilter(filterType, envSelection, serviceFilter);
            applicationFilterList.add(applicationFilter);
            break;
          }

          // ENV_FILTER_TYPE -> CUSTOM
          case CUSTOM: {
            throw new InvalidRequestException(
                "Invalid filter type. 'Custom' environment filter type is applicable only for a single application. You have selected all applications.");
          }
          default:
        }
      } else { // APP_FILTER -> CUSTOM
        BlackoutWindowFilterType filterType = CUSTOM;

        // APP_IDS
        List<String> appIds = qlFreezeWindowInput.getAppIds();
        if (appIds == null) {
          throw new InvalidRequestException(
              "'appIds' cannot be empty. Please enter the application ids applicable to the deployment freeze window.");
        }

        QLServiceTypeFilterInput serviceTypeFilter = qlFreezeWindowInput.getServiceTypeFilter();
        ServiceFilter serviceFilter = null;
        switch (serviceTypeFilter) {
          case ALL: {
            if (qlFreezeWindowInput.getServIds() != null) {
              throw new InvalidRequestException(
                  "'servIds' must not be given when 'serviceTypeFilter' is given as 'ALL'.");
            }
            serviceFilter = new ServiceFilter(ServiceFilterType.ALL, Collections.emptyList());
            break;
          }
          case CUSTOM: {
            if (isEmpty(qlFreezeWindowInput.getServIds())) {
              throw new InvalidRequestException(
                  "'servIds' cannot be empty. Please enter the service Ids applicable to the deployment freeze window.");
            }
            serviceFilter = new ServiceFilter(ServiceFilterType.CUSTOM, qlFreezeWindowInput.getServIds());
            break;
          }
          default:
            serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
        }
        switch (qlFreezeWindowInput.getEnvTypeFilter()) {
          // ENV_FILTER_TYPE -> ALL
          case ALL: {
            EnvironmentFilter envSelection = new AllEnvFilter(EnvironmentFilterType.ALL);
            ApplicationFilter applicationFilter = new CustomAppFilter(filterType, envSelection, appIds, serviceFilter);
            applicationFilterList.add(applicationFilter);
            if (qlFreezeWindowInput.getEnvIds() != null) {
              throw new InvalidRequestException("'envIds' must not be given when 'envTypeFilter' is given as 'ALL'.");
            }
            break;
          }

          // ENV_FILTER_TYPE -> ALL_PROD
          case ALL_PROD: {
            EnvironmentFilter envSelection = new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD);
            ApplicationFilter applicationFilter = new CustomAppFilter(filterType, envSelection, appIds, serviceFilter);
            applicationFilterList.add(applicationFilter);
            if (qlFreezeWindowInput.getEnvIds() != null) {
              throw new InvalidRequestException(
                  "'envIds' must not be given when 'envTypeFilter' is given as 'ALL_PROD'.");
            }
            break;
          }

          // ENV_FILTER_TYPE -> ALL_NON_PROD
          case ALL_NON_PROD: {
            EnvironmentFilter envSelection = new AllNonProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD);
            ApplicationFilter applicationFilter = new CustomAppFilter(filterType, envSelection, appIds, serviceFilter);
            applicationFilterList.add(applicationFilter);
            if (qlFreezeWindowInput.getEnvIds() != null) {
              throw new InvalidRequestException(
                  "'envIds' must not be given when 'envTypeFilter' is given as 'ALL_NON_PROD'.");
            }
            break;
          }

          // ENV_FILTER_TYPE -> CUSTOM
          case CUSTOM: {
            if (appIds.size() == 1) {
              // ENV_IDS
              List<String> environments = qlFreezeWindowInput.getEnvIds();
              if (environments == null) {
                throw new InvalidRequestException(
                    "'envIds' cannot be empty. Please enter the environment ids applicable to the deployment freeze window.");
              }
              EnvironmentFilter envSelection = new CustomEnvFilter(EnvironmentFilterType.CUSTOM, environments);
              ApplicationFilter applicationFilter =
                  new CustomAppFilter(filterType, envSelection, appIds, serviceFilter);
              applicationFilterList.add(applicationFilter);
              break;
            } else {
              throw new InvalidRequestException(
                  "Invalid filter type. 'Custom' environment filter type is applicable only for a single application. You have selected multiple applications.");
            }
          }
          default:
        }
      }
    }
    return applicationFilterList;
  }

  public TimeRangeBasedFreezeConfig updateDeploymentFreezeWindowEntity(
      QLUpdateDeploymentFreezeWindowInput parameter, TimeRangeBasedFreezeConfig existingFreezeWindow) {
    // BLOCK UPDATE FOR 'ENABLED' DEPLOYMENT FREEZE WINDOWS
    if (existingFreezeWindow.isApplicable()) {
      throw new InvalidRequestException(
          "The deployment freeze window is enabled and cannot be updated. Please disable it using the 'toggleDeploymentFreezeWindow' api before updating any parameters.");
    } else {
      // UPDATE -> NAME
      existingFreezeWindow.setName(parameter.getName().trim());

      // UPDATE -> DESCRIPTION
      existingFreezeWindow.setDescription(parameter.getDescription());

      // UPDATE -> USER GROUPS
      existingFreezeWindow.setUserGroups(parameter.getNotifyTo());

      // UPDATE -> SETUP(TIME_RANGE)
      TimeRange updatedTimeRange = populateTimeRangeEntity(parameter.getSetup());
      existingFreezeWindow.setTimeRange(updatedTimeRange);

      // UPDATE -> FREEZE_WINDOWS(APP_SELECTIONS)
      List<ApplicationFilter> updatedAppSelections = populateAppSelectionsEntity(parameter.getFreezeWindows());
      existingFreezeWindow.setAppSelections(updatedAppSelections);
    }
    return existingFreezeWindow;
  }

  public QLDeploymentFreezeWindow populateDeploymentFreezeWindowPayload(
      TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig) {
    // POPULATING APP SELECTIONS FOR PAYLOAD
    List<QLFreezeWindow> qlFreezeWindowList = new ArrayList<>();
    for (ApplicationFilter applicationFilter : timeRangeBasedFreezeConfig.getAppSelections()) {
      List<String> appIds = new ArrayList<>();
      if (applicationFilter.getFilterType().equals(CUSTOM)) {
        appIds = ((CustomAppFilter) applicationFilter).getApps();
      }

      List<String> envIds = new ArrayList<>();
      if (appIds.size() == 1
          && applicationFilter.getEnvSelection().getFilterType().equals(EnvironmentFilterType.CUSTOM)) {
        envIds = ((CustomEnvFilter) applicationFilter.getEnvSelection()).getEnvironments();
      }

      List<String> servIds = new ArrayList<>();
      if (appIds.size() == 1
          && applicationFilter.getServiceSelection().getFilterType().equals(ServiceFilterType.CUSTOM)) {
        servIds = applicationFilter.getServiceSelection().getServices();
      }

      QLFreezeWindow qlFreezeWindow = QLFreezeWindow.builder()
                                          .appFilter(applicationFilter.getFilterType())
                                          .envFilterType(applicationFilter.getEnvSelection().getFilterType())
                                          .appIds(appIds)
                                          .envIds(envIds)
                                          .servIds(servIds)
                                          .servFilterType(applicationFilter.getServiceSelection().getFilterType())
                                          .build();

      qlFreezeWindowList.add(qlFreezeWindow);
    }

    // POPULATING TIME RANGE FOR PAYLOAD
    TimeRange timeRange = timeRangeBasedFreezeConfig.getTimeRange();
    QLSetup qlSetup = QLSetup.builder()
                          .isDurationBased(timeRange.isDurationBased())
                          .from(timeRange.getFrom())
                          .to(timeRange.getTo())
                          .timeZone(timeRange.getTimeZone())
                          .duration(timeRange.getDuration())
                          .endTime(timeRange.getEndTime())
                          .freezeOccurrence(timeRange.getFreezeOccurrence())
                          .untilForever(!timeRange.isExpires())
                          .build();

    // DEPLOYMENT FREEZE WINDOW PAYLOAD
    return QLDeploymentFreezeWindow.builder()
        .id(timeRangeBasedFreezeConfig.getUuid())
        .name(timeRangeBasedFreezeConfig.getName())
        .description(timeRangeBasedFreezeConfig.getDescription())
        .applicable(timeRangeBasedFreezeConfig.isApplicable())
        .notifyTo(timeRangeBasedFreezeConfig.getUserGroups())
        .freezeWindows(qlFreezeWindowList)
        .setup(qlSetup)
        .userGroupSelection(populateUserGroupSelection(timeRangeBasedFreezeConfig.getUserGroupSelection()))
        .build();
  }

  public void validateDeploymentFreezeWindowInput(
      TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig, String accountId) {
    // VALIDATE_NAME
    validateName(timeRangeBasedFreezeConfig);

    // VALIDATE_USER_GROUPS
    validateUserGroups(timeRangeBasedFreezeConfig, accountId);

    // VALIDATE_APP_SELECTIONS
    validateAppSelections(timeRangeBasedFreezeConfig, accountId);
  }

  private void validateAppSelections(TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig, String accountId) {
    List<ApplicationFilter> appSelections = timeRangeBasedFreezeConfig.getAppSelections();

    for (ApplicationFilter applicationFilter : appSelections) {
      // APP_IDS VALIDATION
      List<String> appIds = new ArrayList<>();
      if (applicationFilter.getFilterType().equals(CUSTOM)) {
        appIds = ((CustomAppFilter) applicationFilter).getApps();
        for (String appId : appIds) {
          if (EmptyPredicate.isEmpty(appId)) {
            throw new InvalidRequestException("'appId' cannot be an empty string. Please insert a valid appId.");
          }

          Application application = appService.get(appId);
          if (application == null) {
            throw new InvalidRequestException(format("Invalid Application Id: %s", appId));
          }
        }
      }

      // ENV_IDS_VALIDATION
      if (appIds.size() == 1
          && applicationFilter.getEnvSelection().getFilterType().equals(EnvironmentFilterType.CUSTOM)) {
        List<String> envIds = ((CustomEnvFilter) applicationFilter.getEnvSelection()).getEnvironments();
        for (String envId : envIds) {
          if (EmptyPredicate.isEmpty(envId)) {
            throw new InvalidRequestException("'envId' cannot be an empty string. Please insert a valid envId.");
          }

          Environment environment = environmentService.get(appIds.get(0), envId);
          if (environment == null) {
            throw new InvalidRequestException(
                format("Invalid Environment Id: %s for the given Application Id: %s", envId, appIds.get(0)));
          }
        }
      }

      // Service Ids
      if (appIds.size() == 1
          && applicationFilter.getServiceSelection().getFilterType().equals(ServiceFilterType.CUSTOM)) {
        List<String> serviceIds = applicationFilter.getServiceSelection().getServices();
        for (String serviceId : serviceIds) {
          if (EmptyPredicate.isEmpty(serviceId)) {
            throw new InvalidRequestException(
                "'serviceId' cannot be an empty string. Please insert a valid serviceId.");
          }

          Service service = serviceResourceService.get(appIds.get(0), serviceId);
          if (service == null) {
            throw new InvalidRequestException(
                format("Invalid Service Id: %s for the given Application Id: %s", serviceId, appIds.get(0)));
          }
        }
      }
    }
  }

  private void validateUserGroups(TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig, String accountId) {
    List<String> userGroups = timeRangeBasedFreezeConfig.getUserGroups();
    if (isEmpty(userGroups) && timeRangeBasedFreezeConfig.getUserGroupSelection() == null) {
      throw new InvalidRequestException("User Groups cannot be empty");
    }
    if (timeRangeBasedFreezeConfig.getUserGroupSelection() != null) {
      if (CUSTOM.equals(timeRangeBasedFreezeConfig.getUserGroupSelection().getFilterType())) {
        final CustomUserGroupFilter userGroupSelection =
            (CustomUserGroupFilter) timeRangeBasedFreezeConfig.getUserGroupSelection();
        if (isEmpty(userGroupSelection.getUserGroups())) {
          throw new InvalidRequestException("User Groups cannot be empty in custom filter");
        }
        validateUserGroups(accountId, userGroupSelection.getUserGroups());
      }
    } else {
      validateUserGroups(accountId, userGroups);
    }
  }

  private void validateUserGroups(String accountId, List<String> userGroups) {
    for (String userGroupId : userGroups) {
      if (userGroupId.isEmpty()) {
        throw new InvalidRequestException("User group Id cannot be empty");
      }
      UserGroup userGroup = userGroupService.get(accountId, userGroupId);
      if (userGroup == null) {
        throw new InvalidRequestException(format("Invalid UserGroup Id: %s", userGroupId));
      }
    }
  }

  private void validateName(TimeRangeBasedFreezeConfig timeRangeBasedFreezeConfig) {
    String name = timeRangeBasedFreezeConfig.getName();

    if (EmptyPredicate.isEmpty(name)) {
      throw new InvalidRequestException("Name cannot be empty for a freeze window");
    }
  }
}
