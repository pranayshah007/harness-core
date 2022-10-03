/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dtos.InstanceDTO;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;
import io.harness.ng.core.infrastructure.InfrastructureKind;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public class UsageMetricsEventPublisherImpl implements UsageMetricsEventPublisher {
  public static final String ACCOUNT_ID = "accountId";
  private final Producer eventProducer;

  @Inject
  public UsageMetricsEventPublisherImpl(@Named(INSTANCE_STATS) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public void publishInstanceStatsTimeSeries(String accountId, long timestamp, List<InstanceDTO> instances) {
    if (EmptyPredicate.isEmpty(instances)) {
      return;
    }

    List<DataPoint> dataPointList = new ArrayList<>();

    try {
      Map<String, List<InstanceDTO>> orgInstancesMap =
          instances.stream().collect(groupingBy(InstanceDTO::getOrgIdentifier));
      for (String orgIdentifier : orgInstancesMap.keySet()) {
        try {
          Map<String, List<InstanceDTO>> projectInstancesMap =
              orgInstancesMap.get(orgIdentifier).stream().collect(groupingBy(InstanceDTO::getProjectIdentifier));
          for (String projectIdentifier : projectInstancesMap.keySet()) {
            dataPointList.addAll(collectInstanceStats(projectInstancesMap.get(projectIdentifier)));
            dataPointList.addAll(collectInstanceStatsForGitOps(projectInstancesMap.get(projectIdentifier)));
          }
        } catch (Exception ex) {
          log.error("Error publishing instance stats for org {} (account {})", orgIdentifier, accountId, ex);
        }
      }
    } catch (Exception ex) {
      log.error("Error publishing instance stats for account {}", accountId, ex);
    }

    TimeseriesBatchEventInfo eventInfo = TimeseriesBatchEventInfo.newBuilder()
                                             .setAccountId(accountId)
                                             .setTimestamp(timestamp)
                                             .addAllDataPointList(dataPointList)
                                             .build();

    try {
      eventProducer.send(Message.newBuilder()
                             .putAllMetadata(ImmutableMap.of(
                                 ACCOUNT_ID, accountId, EventsFrameworkMetadataConstants.ENTITY_TYPE, INSTANCE_STATS))
                             .setData(eventInfo.toByteString())
                             .build());
    } catch (Exception ex) {
      log.error(
          String.format("Failed to publish instance stats event for Account %s via event framework.", accountId), ex);
    }
  }

  private List<DataPoint> collectInstanceStatsForGitOps(List<InstanceDTO> instances) {
    // Handle GitOps instances separately as they don't have infrastructureMappingId
    List<DataPoint> dataPointList = new ArrayList<>();
    Map<String, List<InstanceDTO>> serviceInstancesMap =
        instances.stream()
            .filter(instanceDTO -> instanceDTO.getInfrastructureKind().equals(InfrastructureKind.GITOPS))
            .collect(groupingBy(InstanceDTO::getServiceIdentifier));

    serviceInstancesMap.values().forEach(instanceList -> {
      if (EmptyPredicate.isEmpty(instanceList)) {
        return;
      }

      int size = instanceList.size();
      InstanceDTO instance = instanceList.get(0);
      Map<String, String> data = populateInstanceData(instance, size);
      log.info("Adding the GitOps instance record {} to the list", data);
      dataPointList.add(DataPoint.newBuilder().putAllData(data).build());
    });
    return dataPointList;
  }

  private List<DataPoint> collectInstanceStats(List<InstanceDTO> instances) {
    List<DataPoint> dataPointList = new ArrayList<>();
    // key - infraMappingId, value - Set<InstanceDTO>
    Map<String, List<InstanceDTO>> infraMappingInstancesMap =
        instances.stream()
            .filter(instanceDTO -> !instanceDTO.getInfrastructureKind().equals(InfrastructureKind.GITOPS))
            .collect(groupingBy(InstanceDTO::getInfrastructureMappingId));

    infraMappingInstancesMap.values().forEach(instanceList -> {
      if (EmptyPredicate.isEmpty(instanceList)) {
        return;
      }

      int size = instanceList.size();
      InstanceDTO instance = instanceList.get(0);
      Map<String, String> data = populateInstanceData(instance, size);
      data.put(TimescaleConstants.CLOUDPROVIDER_ID.getKey(), instance.getConnectorRef());
      log.info("Adding the instance record {} to the list", data);
      dataPointList.add(DataPoint.newBuilder().putAllData(data).build());
    });
    return dataPointList;
  }

  private Map<String, String> populateInstanceData(InstanceDTO instance, int size) {
    Map<String, String> data = new HashMap<>();
    data.put(TimescaleConstants.ACCOUNT_ID.getKey(), instance.getAccountIdentifier());
    data.put(TimescaleConstants.ORG_ID.getKey(), instance.getOrgIdentifier());
    data.put(TimescaleConstants.PROJECT_ID.getKey(), instance.getProjectIdentifier());
    data.put(TimescaleConstants.SERVICE_ID.getKey(), instance.getServiceIdentifier());
    data.put(TimescaleConstants.ENV_ID.getKey(), instance.getEnvIdentifier());
    data.put(TimescaleConstants.INSTANCE_TYPE.getKey(), instance.getInstanceType().name());
    data.put(TimescaleConstants.INSTANCECOUNT.getKey(), String.valueOf(size));
    return data;
  }
}
