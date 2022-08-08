/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.applicationmanifest;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.service.intfc.ownership.OwnedByApplicationManifest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface HelmChartService extends OwnedByApplicationManifest {
  HelmChart create(@Valid HelmChart helmChart);

  PageResponse<HelmChart> listHelmChartsForService(PageRequest<HelmChart> pageRequest);

  HelmChart get(String appId, String helmChartId);

  Map<String, List<HelmChart>> listHelmChartsForService(String appId, String serviceId, String manifestSearchString,
      PageRequest<HelmChart> PageRequest, boolean showHelmChartsForDisabledCollection);

  HelmChart getLastCollectedManifest(String accountId, String applicationManifestUuid);

  List<HelmChart> listByIds(String accountId, List<String> helmChartIds);

  void deleteByAppManifest(String appId, @NotNull String applicationManifestId);

  List<HelmChart> listHelmChartsForAppManifest(@NotNull String accountId, @NotNull String appManifestId);

  boolean deleteHelmChartsByVersions(String accountId, String appManifestId, Set<String> toBeDeletedVersions);

  boolean addCollectedHelmCharts(String accountId, String appManifestId, @NotNull List<HelmChart> manifestsCollected);

  HelmChart getLastCollectedManifestMatchingRegex(String accountId, String appManifestId, String versionRegex);

  HelmChart getManifestByVersionNumber(String accountId, String appManifestId, String versionNumber);

  HelmChart getByChartVersion(String appId, String serviceId, String appManifestName, String chartVersion);

  HelmChart fetchByChartVersion(
      String accountId, String appId, String serviceId, String appManifestName, String chartVersion);

  List<HelmChart> fetchChartsFromRepo(
      String accountId, String appId, String serviceId, String appManifestName, boolean collectUsingDelegate);

  HelmChart createHelmChartWithVersionForAppManifest(ApplicationManifest appManifest, String versionNumber);

  HelmChart createOrUpdateAppVersion(@Valid HelmChart helmChart);
}
