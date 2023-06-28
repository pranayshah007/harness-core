/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf.apprenaming;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public interface AppRenamingOperator {
  enum NamingTransition {
    VERSION_TO_NON_VERSION,
    NON_VERSION_TO_NON_VERSION,
    NON_VERSION_TO_VERSION,
    ROLLBACK_OPERATOR
  }

  CfInBuiltVariablesUpdateValues renameApp(CfRouteUpdateRequestConfigData cfRouteUpdateConfigData,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, CfDeploymentManager pcfDeploymentManager,
      PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper) throws PivotalClientApiException;

  static AppRenamingOperator of(NamingTransition transition) throws PivotalClientApiException {
    switch (transition) {
      case VERSION_TO_NON_VERSION:
        return new VersionToNonVersionOperator();
      case NON_VERSION_TO_VERSION:
        return new NonVersionToVersionOperator();
      case NON_VERSION_TO_NON_VERSION:
        return new NonVersionToNonVersionOperator();
      case ROLLBACK_OPERATOR:
        return new AppRenamingRollbackOperator();
      default:
        throw new PivotalClientApiException(String.format("%s is not supported", transition.name()));
    }
  }

  default boolean isValidAppDetails(CfAppSetupTimeDetails appDetails, String appGuid) {
    return appDetails != null && isNotEmpty(appDetails.getApplicationGuid())
        && isNotEmpty(appDetails.getApplicationName()) && appGuid.equalsIgnoreCase(appDetails.getApplicationGuid());
  }

  default AppRenamingData populateRenamingData(
      ApplicationSummary appSummary, String appGuid, String currentName, String newName) {
    return AppRenamingData.builder()
        .newName(newName)
        .guid(appGuid)
        .currentName(currentName)
        .appSummary(appSummary)
        .build();
  }

  default TreeMap<AppType, AppRenamingData> getAppsInTheRenamingOrder(
      CfRouteUpdateRequestConfigData cfRouteUpdateConfigData, List<ApplicationSummary> allReleases) {
    CfAppSetupTimeDetails inActiveAppBeforeThisDeployment =
        cfRouteUpdateConfigData.getExistingInActiveApplicationDetails();
    CfAppSetupTimeDetails activeAppBeforeThisDeployment =
        isNotEmpty(cfRouteUpdateConfigData.getExistingApplicationDetails())
        ? cfRouteUpdateConfigData.getExistingApplicationDetails().get(0)
        : CfAppSetupTimeDetails.builder().build();
    CfAppSetupTimeDetails newApplicationDetails = cfRouteUpdateConfigData.getNewApplicationDetails();

    TreeMap<AppType, AppRenamingData> appTypeApplicationSummaryMap = new TreeMap<>(getComparatorForRenamingOrder());

    populateAppDetailsForRenaming(inActiveAppBeforeThisDeployment, activeAppBeforeThisDeployment, newApplicationDetails,
        allReleases, appTypeApplicationSummaryMap, cfRouteUpdateConfigData.getCfAppNamePrefix());

    return appTypeApplicationSummaryMap;
  }

  default void populateAppDetailsForRenaming(CfAppSetupTimeDetails inActiveApp, CfAppSetupTimeDetails activeApp,
      CfAppSetupTimeDetails newApp, List<ApplicationSummary> allReleases,
      TreeMap<AppType, AppRenamingData> appTypeApplicationSummaryMap, String cfAppNamePrefix) {
    for (ApplicationSummary appSummary : allReleases) {
      String appGuid = appSummary.getId();
      if (isValidAppDetails(activeApp, appGuid)) {
        appTypeApplicationSummaryMap.put(
            AppType.ACTIVE, populateRenamingData(appSummary, appGuid, appSummary.getName(), null));

      } else if (isValidAppDetails(newApp, appGuid)) {
        appTypeApplicationSummaryMap.put(
            AppType.NEW, populateRenamingData(appSummary, appGuid, appSummary.getName(), null));
      }
    }
  }

  default Comparator<AppType> getComparatorForRenamingOrder() {
    return Comparator.comparingInt(Enum::ordinal);
  }

  default void renameApp(ApplicationSummary app, PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, @NotNull String newName, Logger log)
      throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.renameApp(app, cfRequestConfig, executionLogCallback, newName);
  }

  default void renameApp(ApplicationSummary app, PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, @NotNull String newName,
      @NotNull String oldName, Logger log) throws PivotalClientApiException {
    pcfCommandTaskBaseHelper.renameApp(app, cfRequestConfig, executionLogCallback, newName, oldName);
  }
}
