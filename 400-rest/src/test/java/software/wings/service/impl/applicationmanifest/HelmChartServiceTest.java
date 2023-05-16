/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.TaskType.HELM_COLLECT_CHART;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.HelmChart.HelmChartKeys;
import software.wings.beans.appmanifest.StoreType;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams.HelmChartCollectionType;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDP)
public class HelmChartServiceTest extends WingsBaseTest {
  private String APPLICATION_MANIFEST_ID = "APPLICATION_MANIFEST_ID";
  @Inject private HPersistence persistence;

  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private DelegateService delegateService;
  @Mock ManifestCollectionUtils manifestCollectionUtils;
  @Inject @InjectMocks private HelmChartService helmChartService;

  private HelmChart helmChart = generateHelmChartWithVersion("1.0");

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldCreateHelmChart() {
    HelmChart outHelmChart = helmChartService.create(helmChart);
    assertThat(outHelmChart).isEqualTo(helmChart);
  }

  private HelmChart generateHelmChartWithVersion(String version) {
    return HelmChart.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(UUID + version)
        .applicationManifestId(APPLICATION_MANIFEST_ID)
        .serviceId(SERVICE_ID)
        .version(version)
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testListHelmChartsForService() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = HelmChart.builder()
                                 .accountId(ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .uuid("uuid")
                                 .applicationManifestId(APPLICATION_MANIFEST_ID)
                                 .serviceId("serviceId")
                                 .build();

    helmChartService.create(newHelmChart);
    HelmChart newHelmChart2 = HelmChart.builder()
                                  .accountId(ACCOUNT_ID)
                                  .appId(APP_ID)
                                  .uuid("uuid2")
                                  .applicationManifestId(APPLICATION_MANIFEST_ID + 2)
                                  .serviceId(SERVICE_ID)
                                  .build();

    helmChartService.create(newHelmChart2);
    List<HelmChart> helmCharts =
        helmChartService.listHelmChartsForService(aPageRequest()
                                                      .addFilter(HelmChartKeys.appId, EQ, APP_ID)
                                                      .addFilter(HelmChartKeys.serviceId, EQ, SERVICE_ID)
                                                      .build());
    assertThat(helmCharts).containsExactlyInAnyOrder(helmChart, newHelmChart2);

    Mockito.when(applicationManifestService.getNamesForIds(eq(APP_ID), anySet()))
        .thenReturn(ImmutableMap.of(
            APPLICATION_MANIFEST_ID, APP_MANIFEST_NAME, APPLICATION_MANIFEST_ID + 2, APP_MANIFEST_NAME + 2));
    Map<String, List<HelmChart>> appManifestHelmChartMap =
        helmChartService.listHelmChartsForService(APP_ID, SERVICE_ID, null, new PageRequest<>(), true);

    assertThat(appManifestHelmChartMap.get(APP_MANIFEST_NAME)).containsOnly(helmChart);
    assertThat(appManifestHelmChartMap.get(APP_MANIFEST_NAME + 2)).containsOnly(newHelmChart2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldGetHelmChart() {
    helmChartService.create(helmChart);
    HelmChart outHelmChart = helmChartService.get(APP_ID, UUID + "1.0");
    assertThat(outHelmChart).isEqualTo(helmChart);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldPruneByApplicationManifest() {
    helmChartService.create(helmChart);
    helmChartService.pruneByApplicationManifest(APP_ID, APPLICATION_MANIFEST_ID);
    assertThat(helmChartService.get(APP_ID, UUID)).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetLastCollectedHelmChart() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = generateHelmChartWithVersion("2.0");
    helmChartService.create(newHelmChart);
    HelmChart outHelmChart = helmChartService.getLastCollectedManifest(ACCOUNT_ID, APPLICATION_MANIFEST_ID);
    assertThat(outHelmChart).isEqualTo(newHelmChart);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testListHelmChartsForApplicationManifest() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = HelmChart.builder()
                                 .accountId(ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .uuid(UUID + 2)
                                 .applicationManifestId(APPLICATION_MANIFEST_ID + 2)
                                 .serviceId(SERVICE_ID)
                                 .build();
    helmChartService.create(newHelmChart);
    List<HelmChart> helmCharts = helmChartService.listHelmChartsForAppManifest(ACCOUNT_ID, APPLICATION_MANIFEST_ID);
    assertThat(helmCharts).containsOnly(helmChart);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldListByIds() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = generateHelmChartWithVersion("2.0");
    HelmChart newHelmChart2 = generateHelmChartWithVersion("3.0");
    helmChartService.create(newHelmChart);
    helmChartService.create(newHelmChart2);
    List<HelmChart> outHelmCharts = helmChartService.listByIds(ACCOUNT_ID, asList("UUID1.0", "UUID2.0"));
    assertThat(outHelmCharts).containsExactlyInAnyOrder(helmChart, newHelmChart);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testDeleteHelmCharts() {
    List<HelmChart> helmCharts =
        asList(helmChart, generateHelmChartWithVersion("2.0"), generateHelmChartWithVersion("3.0"));
    helmCharts.forEach(helmChart -> helmChartService.create(helmChart));
    assertThat(helmChartService.deleteHelmChartsByVersions(
                   ACCOUNT_ID, APPLICATION_MANIFEST_ID, new HashSet(asList("2.0", "3.0"))))
        .isTrue();

    List<HelmChart> finalHelmCharts =
        helmChartService.listHelmChartsForAppManifest(ACCOUNT_ID, APPLICATION_MANIFEST_ID);
    assertThat(finalHelmCharts).containsOnly(helmChart);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testAddHelmCharts() {
    helmChartService.create(helmChart);
    HelmChart helmChart2 = generateHelmChartWithVersion("2.0");
    HelmChart helmChart3 = generateHelmChartWithVersion("3.0");
    List<HelmChart> helmCharts = asList(helmChart, helmChart2, helmChart3);
    assertThat(helmChartService.addCollectedHelmCharts(ACCOUNT_ID, APPLICATION_MANIFEST_ID, helmCharts)).isTrue();

    List<HelmChart> finalHelmCharts =
        helmChartService.listHelmChartsForAppManifest(ACCOUNT_ID, APPLICATION_MANIFEST_ID);
    assertThat(finalHelmCharts.size()).isEqualTo(3);
    assertThat(finalHelmCharts).containsExactlyInAnyOrder(helmChart, helmChart2, helmChart3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetHelmChartsWithVersion() {
    helmChartService.create(helmChart);
    HelmChart helmChart2 = generateHelmChartWithVersion("2.0");
    helmChartService.addCollectedHelmCharts(ACCOUNT_ID, APPLICATION_MANIFEST_ID, asList(helmChart, helmChart2));
    HelmChart helmChartWithVersion =
        helmChartService.getManifestByVersionNumber(ACCOUNT_ID, APPLICATION_MANIFEST_ID, "2.0");
    assertThat(helmChartWithVersion).isEqualTo(helmChart2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetHelmChartsMatchingRegex() {
    helmChartService.create(helmChart);
    HelmChart helmChart2 = generateHelmChartWithVersion("2.1");
    HelmChart helmChart3 = generateHelmChartWithVersion("1.1");
    helmChartService.addCollectedHelmCharts(
        ACCOUNT_ID, APPLICATION_MANIFEST_ID, asList(helmChart, helmChart2, helmChart3));
    HelmChart helmChartWithVersion =
        helmChartService.getLastCollectedManifestMatchingRegex(ACCOUNT_ID, APPLICATION_MANIFEST_ID, "2\\.*");
    assertThat(helmChartWithVersion).isEqualTo(helmChart2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchChartsFromRepo() throws InterruptedException {
    HelmChart helmChart2 = generateHelmChartWithVersion("2.1");
    when(delegateService.executeTaskV2(any()))
        .thenReturn(
            HelmCollectChartResponse.builder().helmCharts(asList(helmChart.toDto(), helmChart2.toDto())).build());
    when(manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(
             APPLICATION_MANIFEST_ID, APP_ID, HelmChartCollectionType.ALL, null))
        .thenReturn(HelmChartCollectionParams.builder()
                        .accountId(ACCOUNT_ID)
                        .appManifestId(APPLICATION_MANIFEST_ID)
                        .collectionType(HelmChartCollectionType.ALL)
                        .build());
    List<HelmChart> helmCharts =
        helmChartService.fetchChartsFromRepo(ACCOUNT_ID, APP_ID, SERVICE_ID, APPLICATION_MANIFEST_ID, true);
    assertThat(helmCharts).containsExactly(helmChart, helmChart2);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).executeTaskV2(delegateTaskArgumentCaptor.capture());

    assertThat(delegateTaskArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    TaskData taskData = delegateTaskArgumentCaptor.getValue().getData();
    assertThat(taskData.getTaskType()).isEqualTo(HELM_COLLECT_CHART.name());
    assertThat(taskData.getParameters()).hasSize(1);
    ManifestCollectionParams collectionParams = (ManifestCollectionParams) taskData.getParameters()[0];
    assertThat(collectionParams.getAppManifestId()).isEqualTo(APPLICATION_MANIFEST_ID);
    assertThat(((HelmChartCollectionParams) collectionParams).getCollectionType())
        .isEqualTo(HelmChartCollectionType.ALL);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchChartsEmptyFromRepo() throws InterruptedException {
    when(delegateService.executeTaskV2(any())).thenReturn(null);
    when(manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(
             APPLICATION_MANIFEST_ID, APP_ID, HelmChartCollectionType.ALL, null))
        .thenReturn(HelmChartCollectionParams.builder()
                        .accountId(ACCOUNT_ID)
                        .appManifestId(APPLICATION_MANIFEST_ID)
                        .collectionType(HelmChartCollectionType.ALL)
                        .build());
    List<HelmChart> helmCharts =
        helmChartService.fetchChartsFromRepo(ACCOUNT_ID, APP_ID, SERVICE_ID, APPLICATION_MANIFEST_ID, true);
    assertThat(helmCharts).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchChartByVersion() throws InterruptedException {
    when(delegateService.executeTaskV2(any()))
        .thenReturn(HelmCollectChartResponse.builder().helmCharts(asList(helmChart.toDto())).build());
    when(manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(
             APPLICATION_MANIFEST_ID, APP_ID, HelmChartCollectionType.SPECIFIC_VERSION, helmChart.getVersion()))
        .thenReturn(HelmChartCollectionParams.builder()
                        .accountId(ACCOUNT_ID)
                        .appManifestId(APPLICATION_MANIFEST_ID)
                        .collectionType(HelmChartCollectionType.SPECIFIC_VERSION)
                        .build());
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build();
    applicationManifest.setUuid(APPLICATION_MANIFEST_ID);
    when(applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID, APPLICATION_MANIFEST_ID))
        .thenReturn(applicationManifest);
    HelmChart helmChartReturned = helmChartService.fetchByChartVersion(
        ACCOUNT_ID, APP_ID, SERVICE_ID, APPLICATION_MANIFEST_ID, helmChart.getVersion());
    // since now dto is passed in delegateService.executeTask in this test, createdAt and lastUpdatedAt will not be set
    helmChart.setCreatedAt(helmChartReturned.getCreatedAt());
    helmChart.setLastUpdatedAt(helmChartReturned.getLastUpdatedAt());
    assertThat(helmChartReturned).isEqualTo(helmChart);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).executeTaskV2(delegateTaskArgumentCaptor.capture());

    assertThat(delegateTaskArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    TaskData taskData = delegateTaskArgumentCaptor.getValue().getData();
    assertThat(taskData.getTaskType()).isEqualTo(HELM_COLLECT_CHART.name());
    assertThat(taskData.getParameters()).hasSize(1);
    ManifestCollectionParams collectionParams = (ManifestCollectionParams) taskData.getParameters()[0];
    assertThat(collectionParams.getAppManifestId()).isEqualTo(APPLICATION_MANIFEST_ID);
    assertThat(((HelmChartCollectionParams) collectionParams).getCollectionType())
        .isEqualTo(HelmChartCollectionType.SPECIFIC_VERSION);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchChartsEmptyByVersion() throws InterruptedException {
    when(delegateService.executeTaskV2(any())).thenReturn(null);
    when(manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(
             APPLICATION_MANIFEST_ID, APP_ID, HelmChartCollectionType.ALL, null))
        .thenReturn(HelmChartCollectionParams.builder()
                        .accountId(ACCOUNT_ID)
                        .appManifestId(APPLICATION_MANIFEST_ID)
                        .collectionType(HelmChartCollectionType.SPECIFIC_VERSION)
                        .build());
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build();
    applicationManifest.setUuid(APPLICATION_MANIFEST_ID);
    when(applicationManifestService.getAppManifestByName(APP_ID, null, SERVICE_ID, APPLICATION_MANIFEST_ID))
        .thenReturn(applicationManifest);

    HelmChart helmChart1 = helmChartService.fetchByChartVersion(
        ACCOUNT_ID, APP_ID, SERVICE_ID, APPLICATION_MANIFEST_ID, helmChart.getVersion());
    assertThat(helmChart1).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFetchChartsFromRepo_withCollectUsingDelegateFalseAndManifestCollectionEnabled() {
    HelmChart helmChart2 = generateHelmChartWithVersion("2.1");
    helmChartService.create(helmChart);
    helmChartService.create(helmChart2);
    when(applicationManifestService.getById(APP_ID, APPLICATION_MANIFEST_ID))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.Local).enableCollection(true).build());

    List<HelmChart> helmCharts =
        helmChartService.fetchChartsFromRepo(ACCOUNT_ID, APP_ID, SERVICE_ID, APPLICATION_MANIFEST_ID, false);
    assertThat(helmCharts).containsExactly(helmChart, helmChart2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testListHelmChartsForService_withShowHelmChartsForDisabledCollectionFalse() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = HelmChart.builder()
                                 .accountId(ACCOUNT_ID)
                                 .appId(APP_ID)
                                 .uuid("uuid")
                                 .applicationManifestId(APPLICATION_MANIFEST_ID)
                                 .serviceId("serviceId")
                                 .build();

    helmChartService.create(newHelmChart);
    HelmChart newHelmChart2 = HelmChart.builder()
                                  .accountId(ACCOUNT_ID)
                                  .appId(APP_ID)
                                  .uuid("uuid2")
                                  .applicationManifestId(APPLICATION_MANIFEST_ID + 2)
                                  .serviceId(SERVICE_ID)
                                  .build();

    helmChartService.create(newHelmChart2);

    HelmChart newHelmChart3 = HelmChart.builder()
                                  .accountId(ACCOUNT_ID)
                                  .appId(APP_ID)
                                  .uuid("uuid3")
                                  .applicationManifestId(APPLICATION_MANIFEST_ID + 3)
                                  .serviceId(SERVICE_ID)
                                  .build();

    helmChartService.create(newHelmChart3);
    List<HelmChart> helmCharts =
        helmChartService.listHelmChartsForService(aPageRequest()
                                                      .addFilter(HelmChartKeys.appId, EQ, APP_ID)
                                                      .addFilter(HelmChartKeys.serviceId, EQ, SERVICE_ID)
                                                      .build());
    assertThat(helmCharts).containsExactlyInAnyOrder(helmChart, newHelmChart2, newHelmChart3);

    Mockito.when(applicationManifestService.getApplicationManifestByIds(eq(APP_ID), anySet()))
        .thenReturn(asList(
            generateAppManifestWithCollectionEnabled(APPLICATION_MANIFEST_ID, APP_MANIFEST_NAME, Boolean.TRUE),
            generateAppManifestWithCollectionEnabled(APPLICATION_MANIFEST_ID + 2, APP_MANIFEST_NAME + 2, Boolean.FALSE),
            generateAppManifestWithCollectionEnabled(APPLICATION_MANIFEST_ID + 3, APP_MANIFEST_NAME + 3, null)));
    Map<String, List<HelmChart>> appManifestHelmChartMap =
        helmChartService.listHelmChartsForService(APP_ID, SERVICE_ID, null, new PageRequest<>(), false);

    assertThat(appManifestHelmChartMap.get(APP_MANIFEST_NAME)).containsOnly(helmChart);
    assertThat(appManifestHelmChartMap.get(APP_MANIFEST_NAME + 2)).isNull();
    assertThat(appManifestHelmChartMap.get(APP_MANIFEST_NAME + 3)).containsOnly(newHelmChart3);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCreateHelmChartWithAppVersion() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = generateHelmChartWithVersion("2.0");
    newHelmChart.setAppVersion("1.0.0");
    HelmChart outHelmChart = helmChartService.createOrUpdateAppVersion(newHelmChart);
    assertThat(outHelmChart.getVersion()).isEqualTo("2.0");
    assertThat(outHelmChart.getAppVersion()).isEqualTo("1.0.0");
    assertThat(outHelmChart.getUuid()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateHelmChartWithAppVersion() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = generateHelmChartWithVersion("1.0");
    newHelmChart.setAppVersion("1.0.0");
    HelmChart outHelmChart = helmChartService.createOrUpdateAppVersion(newHelmChart);
    assertThat(outHelmChart.getVersion()).isEqualTo("1.0");
    assertThat(outHelmChart.getAppVersion()).isEqualTo("1.0.0");
    assertThat(outHelmChart.getUuid()).isEqualTo(newHelmChart.getUuid());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotUpdateHelmChartWithNullAppVersion() {
    helmChartService.create(helmChart);
    HelmChart newHelmChart = generateHelmChartWithVersion("1.0");
    HelmChart outHelmChart = helmChartService.createOrUpdateAppVersion(newHelmChart);
    assertThat(outHelmChart.getVersion()).isEqualTo("1.0");
    assertThat(outHelmChart.getUuid()).isEqualTo(helmChart.getUuid());
  }

  private ApplicationManifest generateAppManifestWithCollectionEnabled(
      String id, String name, Boolean collectionEnabled) {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().name(name).enableCollection(collectionEnabled).storeType(StoreType.Local).build();
    applicationManifest.setUuid(id);
    return applicationManifest;
  }
}
