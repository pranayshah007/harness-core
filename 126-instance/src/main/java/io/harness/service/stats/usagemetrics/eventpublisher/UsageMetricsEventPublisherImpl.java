/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventpublisher;

import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.DataPoint;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.mappers.InstanceMapper;
import io.harness.models.constants.TimescaleConstants;
import io.harness.ng.core.entities.Project;
import io.harness.service.stats.model.InstanceCountByServiceAndEnv;

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
  public static final String ORG_ID = "orgId";
  public static final String PROJECT_ID = "projectId";
  public static final String SERVICE_ID = "serviceId";
  private final Producer eventProducer;

  @Inject
  public UsageMetricsEventPublisherImpl(@Named(INSTANCE_STATS) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  @Override
  public void publishInstanceStatsTimeSeries(
      Project project, long timestamp, List<InstanceCountByServiceAndEnv> instancesByServiceAndEnv) {
    if (instancesByServiceAndEnv.isEmpty()) {
      return;
    }

    try {
      List<DataPoint> dataPoints = collectInstanceStats(instancesByServiceAndEnv);
      TimeseriesBatchEventInfo eventInfo = TimeseriesBatchEventInfo.newBuilder()
                                               .setAccountId(project.getAccountIdentifier())
                                               .setTimestamp(timestamp)
                                               .addAllDataPointList(dataPoints)
                                               .build();

      eventProducer.send(Message.newBuilder()
                             .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, project.getAccountIdentifier(),
                                 EventsFrameworkMetadataConstants.ENTITY_TYPE, INSTANCE_STATS))
                             .setData(eventInfo.toByteString())
                             .build());
      Map<String, String> dataMap = dataPoints.get(0).getDataMap();
      log.info("Event sent for project: {} (account: {}, org: {})", dataMap.get(TimescaleConstants.PROJECT_ID.getKey()),
          dataMap.get(TimescaleConstants.ACCOUNT_ID.getKey()), dataMap.get(TimescaleConstants.ORG_ID.getKey()));
    } catch (Exception ex) {
      log.error("Error publishing instance stats for services of account {}", project, ex);
    }
  }

  private List<DataPoint> collectInstanceStats(List<InstanceCountByServiceAndEnv> envInstanceCounts) {
    List<DataPoint> dataPointList = new ArrayList<>();

    for (InstanceCountByServiceAndEnv instanceCountByServiceAndEnv : envInstanceCounts) {
      if (instanceCountByServiceAndEnv.getCount() > 0) {
        try {
          int size = instanceCountByServiceAndEnv.getCount();
          InstanceDTO instance = InstanceMapper.toDTO(instanceCountByServiceAndEnv.getFirstDocument());
          Map<String, String> data = populateInstanceData(instance, size);
          log.info("Adding instance record {} to the list", data);
          dataPointList.add(DataPoint.newBuilder().putAllData(data).build());
        } catch (Exception e) {
          log.error("Error adding instance record for service", e);
        }
      }
    }
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
