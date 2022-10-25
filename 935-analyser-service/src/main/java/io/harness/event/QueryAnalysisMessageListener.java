/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.mongo.tracing.TracerConstants.QUERY_HASH;
import static io.harness.mongo.tracing.TracerConstants.SERVICE_ID;
import static io.harness.version.VersionConstants.MAJOR_VERSION_KEY;
import static io.harness.version.VersionConstants.VERSION_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;
import io.harness.repositories.QueryRecordsRepository;
import io.harness.serializer.JsonUtils;
import io.harness.serviceinfo.ServiceInfoService;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class QueryAnalysisMessageListener implements MessageListener {
  @Inject private QueryRecordsRepository queryRecordsRepository;
  @Inject private ServiceInfoService serviceInfoService;

  @Override
  public boolean handleMessage(Message message) {
    log.info("Message data : {}", message.getMessage().getData().toStringUtf8());
    String data = message.getMessage().getData().toStringUtf8();
    String queryResult = "{ \"queryExplainResult\":" + data + "}";
    QueryExplainResult queryExplainResult = JsonUtils.asObject(queryResult, QueryExplainResult.class);
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    QueryRecordEntity queryRecordEntity = QueryRecordEntity.builder()
                                              .data(ByteString.copyFromUtf8(data).toByteArray())
                                              .explainResult(queryExplainResult)
                                              .hash(metadataMap.get(QUERY_HASH))
                                              .fullVersion(metadataMap.get(VERSION_KEY))
                                              .majorVersion(metadataMap.get(MAJOR_VERSION_KEY))
                                              .serviceName(metadataMap.get(SERVICE_ID))
                                              .createdAt(System.currentTimeMillis())
                                              .parsedQuery(queryExplainResult.getQueryPlanner().getParsedQuery())
                                              .collectionName(queryExplainResult.getQueryPlanner().getNamespace())
                                              .build();
    queryRecordsRepository.save(queryRecordEntity);
    serviceInfoService.updateLatest(metadataMap.get(SERVICE_ID), metadataMap.get(MAJOR_VERSION_KEY));
    return true;
  }
}
