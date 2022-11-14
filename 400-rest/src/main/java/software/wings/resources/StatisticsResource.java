/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.rest.RestResponse;

import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.ServiceInstanceStatistics;
import software.wings.beans.stats.TopConsumer;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.StatisticsService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 8/15/16.
 */
@Api("/statistics")
@Path("/statistics")
@Produces("application/json")
@Scope(ResourceType.APPLICATION)
@Slf4j
public class StatisticsResource {
  @Inject private StatisticsService statisticsService;
  @Inject private ExecutorService executorService;
  @Inject private FeatureFlagService featureFlagService;

  @GET
  @Path("deployment-stats")
  @Timed
  @ExceptionMetered
  public RestResponse<DeploymentStatistics> deploymentStats(@QueryParam("accountId") String accountId,
      @DefaultValue("30") @QueryParam("numOfDays") Integer numOfDays, @QueryParam("appId") List<String> appIds) {
    DeploymentStatistics deploymentStatistics;
    if (featureFlagService.isNotEnabled(FeatureName.SPG_DASHBOARD_STATS_OPTIMIZE_DEPLOYMENTS, accountId)) {
      deploymentStatistics = statisticsService.getDeploymentStatistics(accountId, appIds, numOfDays);
      DeploymentStatistics finalDeploymentStatistics = deploymentStatistics;
      if (featureFlagService.isEnabled(FeatureName.SPG_LIVE_DASHBOARD_STATS_DEBUGGING, accountId)) {
        executorService.submit(() -> dataComparisionLogging(accountId, numOfDays, appIds, finalDeploymentStatistics));
      }
    } else {
      deploymentStatistics = statisticsService.getDeploymentStatisticsNew(accountId, appIds, numOfDays);
    }
    return new RestResponse<>(deploymentStatistics);
  }

  private void dataComparisionLogging(
      String accountId, Integer numOfDays, List<String> appIds, DeploymentStatistics finalDeploymentStatistics) {
    DeploymentStatistics deploymentStatisticsNew =
        statisticsService.getDeploymentStatisticsNew(accountId, appIds, numOfDays);
    if (finalDeploymentStatistics != null && !finalDeploymentStatistics.equals(deploymentStatisticsNew)) {
      try (AutoLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_NESTS)) {
        log.error("DEBUG LOG: old way deployment stats: [{}], new way deployment stats:[{}]", finalDeploymentStatistics,
            deploymentStatisticsNew);
      }
    }
  }

  @GET
  @Path("service-instance-stats")
  @Timed
  @ExceptionMetered
  public RestResponse<ServiceInstanceStatistics> instanceStats(@QueryParam("accountId") String accountId,
      @DefaultValue("30") @QueryParam("numOfDays") Integer numOfDays, @QueryParam("appId") List<String> appIds) {
    ServiceInstanceStatistics serviceInstanceStatistics;
    if (featureFlagService.isNotEnabled(FeatureName.SPG_DASHBOARD_STATS_OPTIMIZE_ACTIVE_SERVICES, accountId)) {
      serviceInstanceStatistics = statisticsService.getServiceInstanceStatistics(accountId, appIds, numOfDays);
      if (featureFlagService.isEnabled(FeatureName.SPG_LIVE_DASHBOARD_STATS_DEBUGGING, accountId)) {
        executorService.submit(() -> dataComparisionLogging(accountId, numOfDays, appIds, serviceInstanceStatistics));
      }
    } else {
      serviceInstanceStatistics = statisticsService.getServiceInstanceStatisticsNew(accountId, appIds, numOfDays);
    }
    return new RestResponse<>(serviceInstanceStatistics);
  }

  private void dataComparisionLogging(
      String accountId, Integer numOfDays, List<String> appIds, ServiceInstanceStatistics finalDeploymentStatistics) {
    ServiceInstanceStatistics serviceInstanceStatistics =
        statisticsService.getServiceInstanceStatisticsNew(accountId, appIds, numOfDays);
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OverrideBehavior.OVERRIDE_NESTS)) {
      if (finalDeploymentStatistics != null && !finalDeploymentStatistics.equals(serviceInstanceStatistics)) {
        serviceInstanceStatistics.getStatsMap().get(EnvironmentType.ALL).forEach(s -> {
          Optional<TopConsumer> first = finalDeploymentStatistics.getStatsMap()
                                            .get(EnvironmentType.ALL)
                                            .stream()
                                            .filter(t -> t.equals(s))
                                            .findFirst();
          if (!first.isPresent()) {
            log.error("DEBUG LOG: first unmatching {}", s);
          }
        });
        if (finalDeploymentStatistics.getStatsMap().get(EnvironmentType.ALL).size()
            != serviceInstanceStatistics.getStatsMap().get(EnvironmentType.ALL).size()) {
          log.error("DEBUG LOG: size doesnt match: oldway {}, new way{}", finalDeploymentStatistics,
              serviceInstanceStatistics);
        }
      }
    }
  }
}