/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;

import static java.util.stream.Collectors.groupingBy;

import io.harness.account.AccountClient;
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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public class UsageMetricsEventPublisherImpl implements UsageMetricsEventPublisher {
  public static final String ACCOUNT_ID = "accountId";
  public static final String ORG_ID = "orgId";
  public static final String PROJECT_ID = "projectId";
  public static final String SERVICE_ID = "serviceId";
  private final Producer eventProducer;
  private AccountClient accountClient;

  @Inject
  public UsageMetricsEventPublisherImpl(@Named(INSTANCE_STATS) Producer eventProducer, AccountClient accountClient) {
    this.eventProducer = eventProducer;
    this.accountClient = accountClient;
  }

  @Override
  public void publishInstanceStatsTimeSeries(String accountId, long timestamp, List<InstanceDTO> instances) {
    if (EmptyPredicate.isEmpty(instances)) {
      return;
    }

    try {
      List<DataPoint> dataPoints = collectInstanceStats(accountId, instances);
      TimeseriesBatchEventInfo eventInfo = TimeseriesBatchEventInfo.newBuilder()
                                               .setAccountId(accountId)
                                               .setTimestamp(timestamp)
                                               .addAllDataPointList(dataPoints)
                                               .build();

      eventProducer.send(Message.newBuilder()
                             .putAllMetadata(ImmutableMap.of(
                                 ACCOUNT_ID, accountId, EventsFrameworkMetadataConstants.ENTITY_TYPE, INSTANCE_STATS))
                             .setData(eventInfo.toByteString())
                             .build());
      Map<String, String> dataMap = dataPoints.get(0).getDataMap();
      log.info("Event sent for service: {} (account: {}, org: {}, project: {})",
          dataMap.get(TimescaleConstants.SERVICE_ID.getKey()), accountId,
          dataMap.get(TimescaleConstants.ORG_ID.getKey()), dataMap.get(TimescaleConstants.PROJECT_ID.getKey()));
    } catch (Exception ex) {
      log.error("Error publishing instance stats for services of account {}", accountId, ex);
    }
  }

  private List<DataPoint> collectInstanceStats(String accountId, List<InstanceDTO> instances) {
    List<DataPoint> dataPointList = new ArrayList<>();
    if (EmptyPredicate.isEmpty(instances)) {
      return dataPointList;
    }

    // Add 1 data point for each env in the service
    Map<String, List<InstanceDTO>> instancesByEnv =
        instances.stream()
            .filter(instanceDTO -> StringUtils.isNotBlank(instanceDTO.getEnvIdentifier()))
            .collect(groupingBy(InstanceDTO::getEnvIdentifier));
    instancesByEnv.forEach((String envIdentifier, List<InstanceDTO> instancesForEnv) -> {
      try {
        int size = instancesForEnv.size();
        InstanceDTO instance = instancesForEnv.get(0);
        Map<String, String> data = populateInstanceData(instance, size);
        log.info("Adding instance record {} to the list", data);
        dataPointList.add(DataPoint.newBuilder().putAllData(data).build());
      } catch (Exception e) {
        log.error("Error adding instance record for service", e);
      }
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
    String connectorRef = instance.getConnectorRef();
    if (connectorRef == null) {
      connectorRef = "";
    }
    data.put(TimescaleConstants.CLOUDPROVIDER_ID.getKey(), connectorRef);
    return data;
  }
}
