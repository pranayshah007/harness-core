/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment;
import software.wings.beans.instance.dashboard.InstanceStatsByService;
import software.wings.beans.instance.dashboard.InstanceSummaryStats;
import software.wings.beans.instance.dashboard.InstanceSummaryStatsByService;
import software.wings.beans.instance.dashboard.service.ServiceInstanceDashboard;
import software.wings.service.impl.instance.CompareEnvironmentAggregationResponseInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Serves all the service and infrastructure dashboard related statistics
 * @author rktummala on 08/13/17
 */
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(DX)
public interface DashboardStatisticsService {
  /**
   * Gets the total instance summary stats for the given apps.
   * The results are grouped by the given entity types.
   *
   * @param accountId
   * @param appIds application ids
   * @param groupByEntityTypes the entity types user wants to group by
   * @param timestamp point in time
   * @return instance summary statistics
   */
  InstanceSummaryStats getAppInstanceSummaryStats(
      @NotEmpty String accountId, List<String> appIds, List<String> groupByEntityTypes, long timestamp);

  long getTotalInstancesForAccount(String accountId, List<String> appIds);

  /**
   * Gets the total instance summary stats for the given service.
   * The results are grouped by the given entity types.
   *
   * @param accountId
   * @param serviceId service id
   * @param groupByEntityTypes the entity types user wants to group by
   * @param timestamp point in time
   * @return instance summary statistics
   */
  InstanceSummaryStats getServiceInstanceSummaryStats(
      @NotEmpty String accountId, @NotEmpty String serviceId, List<String> groupByEntityTypes, long timestamp);

  /**
   * Gets the total instance stats for the given apps.
   * @param accountId
   * @param appIds application ids
   * @param timestamp point in time
   * @return instance summary statistics
   */
  List<InstanceStatsByService> getAppInstanceStatsByService(
      @NotEmpty String accountId, List<String> appIds, long timestamp);

  /**
   * Gets the instances for the given account. This api is used by the stats cron job.
   * @param accountId account id
   * @param timestamp point in time
   * @return set of instances
   */
  @Nonnull List<Instance> getAppInstancesForAccount(@NotEmpty String accountId, long timestamp);

  /**
   * Search instances for the given account. This API is used by the stats cron job.
   *
   * @return total of retrieved instances
   */
  int consumeAppInstancesForAccount(@NotEmpty String accountId, long timestamp, Consumer<Instance> instanceConsumer);

  List<InstanceStatsByEnvironment> getServiceInstances(String accountId, String serviceId, long timestamp);

  PageResponse<InstanceSummaryStatsByService> getAppInstanceSummaryStatsByService(
      String accountId, List<String> appIds, long timestamp, int offset, int limit);

  /**
   * Gets the detailed information about the instances provisioned, deployments and pipelines for the given service.
   * @param appId app id
   * @param serviceId service id
   * @param envId
   * @return service dashboard with cloud instance info
   */
  ServiceInstanceDashboard getServiceInstanceDashboard(@NotEmpty String accountId, @NotEmpty String appId,
      @NotEmpty String serviceId, PageRequest<WorkflowExecution> pageRequest);

  /**
   * Gets the instance detailed information including the metadata
   * @param instanceId instance id
   * @return instance info with the metadata
   */
  Instance getInstanceDetails(String instanceId);

  Set<String> getDeletedAppIds(String accountId, long timestamp);

  Set<String> getDeletedAppIds(String accountId, long fromTimestamp, long toTimestamp);

  PageResponse<CompareEnvironmentAggregationResponseInfo> getCompareServicesByEnvironment(
      String accountId, String appId, String envId1, String envId2, int offset, int limit);

  ServiceInstanceDashboard getServiceInstanceDashboardFiltered(
      String accountId, String appId, String serviceId, PageRequest<WorkflowExecution> pageRequest);

  boolean isInstanceConsumerEnabled(String accountId);
}
