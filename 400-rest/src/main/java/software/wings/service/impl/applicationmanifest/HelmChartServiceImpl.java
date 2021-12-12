package software.wings.service.impl.applicationmanifest;

import static io.harness.beans.SearchFilter.Operator.CONTAINS;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.TaskType.HELM_COLLECT_CHART;

import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.HelmChart.HelmChartKeys;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HelmChartServiceImpl implements HelmChartService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ManifestCollectionUtils manifestCollectionUtils;
  @Inject private DelegateServiceImpl delegateService;
  @Inject private HelmChartService helmChartService;

  @Override
  public HelmChart create(HelmChart helmChart) {
    String key = wingsPersistence.save(helmChart);
    return wingsPersistence.get(HelmChart.class, key);
  }

  @Override
  public PageResponse<HelmChart> listHelmChartsForService(PageRequest<HelmChart> pageRequest) {
    return wingsPersistence.query(HelmChart.class, pageRequest);
  }

  @Override
  public Map<String, List<HelmChart>> listHelmChartsForService(
      String appId, String serviceId, String manifestSearchString, PageRequest<HelmChart> pageRequest) {
    if (isNotBlank(appId)) {
      pageRequest.addFilter(HelmChartKeys.appId, EQ, appId);
    }
    if (isNotBlank(serviceId)) {
      pageRequest.addFilter(HelmChartKeys.serviceId, EQ, serviceId);
    }
    if (isNotBlank(manifestSearchString)) {
      pageRequest.addFilter(HelmChartKeys.displayName, CONTAINS, manifestSearchString);
    }
    pageRequest.addOrder(HelmChartKeys.createdAt, SortOrder.OrderType.DESC);
    List<HelmChart> helmCharts = listHelmChartsForService(pageRequest);
    Map<String, String> appManifestIdNameMap = applicationManifestService.getNamesForIds(
        appId, helmCharts.stream().map(HelmChart::getApplicationManifestId).collect(Collectors.toSet()));
    return helmCharts.stream().collect(
        groupingBy(helmChart -> appManifestIdNameMap.get(helmChart.getApplicationManifestId())));
  }

  @Override
  public HelmChart getLastCollectedManifest(String accountId, String applicationManifestUuid) {
    return wingsPersistence.createQuery(HelmChart.class)
        .filter(HelmChartKeys.accountId, accountId)
        .filter(HelmChartKeys.applicationManifestId, applicationManifestUuid)
        .order(Sort.descending(HelmChartKeys.createdAt))
        .get();
  }

  @Override
  public List<HelmChart> listByIds(String accountId, List<String> helmChartIds) {
    return wingsPersistence.createQuery(HelmChart.class)
        .filter(HelmChartKeys.accountId, accountId)
        .field(HelmChartKeys.uuid)
        .in(helmChartIds)
        .asList();
  }

  @Override
  public HelmChart get(String appId, String helmChartId) {
    return wingsPersistence.getWithAppId(HelmChart.class, appId, helmChartId);
  }
  public List<HelmChart> listHelmChartsForAppManifest(String accountId, String appManifestId) {
    return wingsPersistence.createQuery(HelmChart.class)
        .filter(HelmChartKeys.accountId, accountId)
        .filter(HelmChartKeys.applicationManifestId, appManifestId)
        .asList();
  }

  @Override
  public void deleteByAppManifest(String appId, String applicationManifestId) {
    Query<HelmChart> query = wingsPersistence.createQuery(HelmChart.class)
                                 .filter(HelmChartKeys.appId, appId)
                                 .filter(HelmChartKeys.applicationManifestId, applicationManifestId);
    wingsPersistence.delete(query);
  }

  @Override
  public boolean deleteHelmChartsByVersions(String accountId, String appManifestId, Set<String> toBeDeletedVersions) {
    Query<HelmChart> query = wingsPersistence.createQuery(HelmChart.class)
                                 .filter(HelmChartKeys.accountId, accountId)
                                 .filter(HelmChartKeys.applicationManifestId, appManifestId)
                                 .field(HelmChartKeys.version)
                                 .in(toBeDeletedVersions);
    return wingsPersistence.delete(query);
  }

  @Override
  public boolean addCollectedHelmCharts(String accountId, String appManifestId, List<HelmChart> manifestsCollected) {
    List<HelmChart> helmCharts = wingsPersistence.createQuery(HelmChart.class)
                                     .filter(HelmChartKeys.accountId, accountId)
                                     .filter(HelmChartKeys.applicationManifestId, appManifestId)
                                     .project(HelmChartKeys.version, true)
                                     .asList();

    List<String> versionsPresent = helmCharts.stream().map(HelmChart::getVersion).collect(toList());

    List<HelmChart> newHelmCharts = manifestsCollected.stream()
                                        .filter(helmChart -> !versionsPresent.contains(helmChart.getVersion()))
                                        .collect(toList());
    if (EmptyPredicate.isEmpty(newHelmCharts)) {
      return true;
    }

    List<String> savedHelmCharts = wingsPersistence.save(newHelmCharts);
    return isNotEmpty(savedHelmCharts);
  }

  @Override
  public HelmChart getLastCollectedManifestMatchingRegex(String accountId, String appManifestId, String versionRegex) {
    Query<HelmChart> helmChartQuery = wingsPersistence.createQuery(HelmChart.class)
                                          .filter(HelmChartKeys.accountId, accountId)
                                          .filter(HelmChartKeys.applicationManifestId, appManifestId);

    return helmChartQuery.filter(HelmChartKeys.version, compile(versionRegex))
        .order("-createdAt")
        .disableValidation()
        .get();
  }

  @Override
  public HelmChart getManifestByVersionNumber(String accountId, String appManifestId, String versionNumber) {
    return wingsPersistence.createQuery(HelmChart.class)
        .filter(HelmChartKeys.accountId, accountId)
        .filter(HelmChartKeys.applicationManifestId, appManifestId)
        .filter(HelmChartKeys.version, versionNumber)
        .get();
  }

  @Override
  public void pruneByApplicationManifest(String appId, String applicationManifestId) {
    deleteByAppManifest(appId, applicationManifestId);
  }

  @Override
  public HelmChart getByChartVersion(String appId, String serviceId, String appManifestName, String chartVersion) {
    ApplicationManifest applicationManifest =
        applicationManifestService.getAppManifestByName(appId, null, serviceId, appManifestName);
    notNullCheck("App manifest with name " + appManifestName + " doesn't belong to the given app and service",
        applicationManifest);
    Query<HelmChart> query = wingsPersistence.createQuery(HelmChart.class)
                                 .filter(HelmChartKeys.appId, appId)
                                 .filter(HelmChartKeys.serviceId, serviceId)
                                 .filter(HelmChartKeys.applicationManifestId, applicationManifest.getUuid())
                                 .filter(HelmChartKeys.version, chartVersion);
    return query.get();
  }

  @Override
  public HelmChart fetchByChartVersion(
      String accountId, String appId, String serviceId, String appManifestName, String chartVersion) {
    ApplicationManifest applicationManifest =
        applicationManifestService.getAppManifestByName(appId, null, serviceId, appManifestName);
    notNullCheck("App manifest with name " + appManifestName + " doesn't belong to the given app and service",
        applicationManifest);

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(HELM_COLLECT_CHART.name())
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .parameters(new Object[] {manifestCollectionUtils.prepareCollectTaskParamsWithChartVersion(
                          applicationManifest.getUuid(), appId, chartVersion)})
                      .build())
            .build();

    HelmCollectChartResponse helmCollectChartResponse = null;
    try {
      helmCollectChartResponse = delegateService.executeTask(delegateTask);
    } catch (InterruptedException e) {
      log.error("Delegate Service execute task : fetchChartVersion" + e);
    }

    HelmChart helmChart = helmCollectChartResponse == null ? null : helmCollectChartResponse.getHelmChart();

    if (helmChart != null) {
      addCollectedHelmCharts(accountId, applicationManifest.getUuid(), Collections.singletonList(helmChart));
    }
    return helmChart;
  }
}
