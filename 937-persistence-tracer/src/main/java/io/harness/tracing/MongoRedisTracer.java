/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tracing;

import static io.harness.mongo.tracing.TracerConstants.QUERY_HASH;
import static io.harness.mongo.tracing.TracerConstants.SERVICE_ID;
import static io.harness.version.VersionConstants.MAJOR_VERSION_KEY;
import static io.harness.version.VersionConstants.VERSION_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.mongo.tracing.Tracer;
import io.harness.ng.persistence.tracer.NgTracer;
import io.harness.persistence.HQuery;
import io.harness.serializer.JsonUtils;
import io.harness.tracing.shapedetector.QueryShapeDetector;
import io.harness.version.VersionInfoManager;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.mongodb.DBObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class MongoRedisTracer implements Tracer, NgTracer {
  private static final int SAMPLE_SIZE = 120; // Consider only 1 sample out of 120 invocations

  @Inject @Named(PersistenceTracerConstants.TRACING_THREAD_POOL) private ExecutorService executorService;
  @Inject @Named(PersistenceTracerConstants.QUERY_ANALYSIS_PRODUCER) private Producer producer;
  @Inject @Named(SERVICE_ID) private String serviceId;
  @Inject private VersionInfoManager versionInfoManager;

  private final ConcurrentHashMap<String, Long> queryStatsCache = new ConcurrentHashMap<>();

  @Override
  public void traceSpringQuery(Query query, Class<?> entityClass, MongoTemplate mongoTemplate) {
    try {
      executorService.execute(() -> {
        try {
          traceSpringQueryInternal(query, entityClass, mongoTemplate);
        } catch (Exception ex) {
          log.error(String.format("Unable to trace spring query: %s", query.getQueryObject().toJson()), ex);
        }
      });
    } catch (Exception ex) {
      log.error("Unable to submit spring trace query task", ex);
    }
  }

  @Override
  public void traceMorphiaQuery(HQuery<?> query) {
    try {
      executorService.execute(() -> {
        try {
          traceMorphiaQueryInternal(query);
        } catch (Exception ex) {
          log.error(String.format("Unable to trace morphia query: %s", query.getQueryObject().toString()), ex);
        }
      });
    } catch (Exception ex) {
      log.error("Unable to submit morphia trace query task", ex);
    }
  }

  private void traceSpringQueryInternal(Query query, Class<?> entityClass, MongoTemplate mongoTemplate) {
    String collectionName = mongoTemplate.getCollectionName(entityClass);
    MongoConverter mongoConverter = mongoTemplate.getConverter();
    MongoPersistentEntity<?> entity = mongoConverter.getMappingContext().getPersistentEntity(entityClass);
    QueryMapper queryMapper = new QueryMapper(mongoConverter);
    Document queryDoc = queryMapper.getMappedObject(nonNullDocument(query.getQueryObject()), entity);
    Document sortDoc = queryMapper.getMappedSort(nonNullDocument(query.getSortObject()), entity);
    String qHash = QueryShapeDetector.getQueryHash(collectionName, queryDoc, sortDoc);
    if (skipSample(qHash)) {
      return;
    }

    log.info("Sampling spring query for collection {}...", collectionName);

    Document explainDocument = new Document();
    explainDocument.put("find", collectionName);
    explainDocument.put("filter", queryDoc);
    explainDocument.put("sort", sortDoc);

    Document command = new Document();
    command.put("explain", explainDocument);

    Document explainResult = mongoTemplate.getDb().runCommand(command);
    log.debug(String.format("Explain Results: %s", explainResult.toJson()));
    producer.send(Message.newBuilder()
                      .putMetadata(VERSION_KEY, versionInfoManager.getVersionInfo().getVersion())
                      .putMetadata(SERVICE_ID, serviceId)
                      .putMetadata(MAJOR_VERSION_KEY, getMajorVersionFromFullVersion())
                      .putMetadata(QUERY_HASH, qHash)
                      .setData(ByteString.copyFromUtf8(explainResult.toJson()))
                      .build());
  }

  private void traceMorphiaQueryInternal(HQuery<?> query) {
    String collectionName = query.getCollection().getName();
    Document queryDoc = toDocument(query.getQueryObject());
    Document sortDoc = toDocument(query.getSortObject());
    String qHash = QueryShapeDetector.getQueryHash(collectionName, queryDoc, sortDoc);
    if (skipSample(qHash)) {
      return;
    }

    String explainResult = JsonUtils.asJson(query.explain());
    log.debug(String.format("Explain Results: %s", explainResult));
    producer.send(Message.newBuilder()
                      .putMetadata(VERSION_KEY, versionInfoManager.getVersionInfo().getVersion())
                      .putMetadata(MAJOR_VERSION_KEY, getMajorVersionFromFullVersion())
                      .putMetadata(SERVICE_ID, serviceId)
                      .putMetadata(QUERY_HASH, qHash)
                      .setData(ByteString.copyFromUtf8(explainResult))
                      .build());
  }

  private String getMajorVersionFromFullVersion() {
    String buildNo = versionInfoManager.getVersionInfo().getBuildNo();
    String replaceBuildNumber = buildNo.substring(0, buildNo.length() - 2) + "xx";
    return versionInfoManager.getVersionInfo().getVersion().replace(buildNo, replaceBuildNumber);
  }

  private boolean skipSample(String qHash) {
    // Here we first increment the counter corresponding to a qHash by 1 and the  check is it should be sampled.
    //
    // Value of a qHash in queryStatsCache is in the range [0, SAMPLE_SIZE-1] inclusive. We only consider a sample for
    // processing when the new value after incrementing the counter is 1. So if we see a new qHash, the initial value
    // after the increment is 1, hence we process the sample. Then we skip the next (SAMPLE_SIZE-1) samples.
    long newCount = queryStatsCache.compute(qHash, (k, v) -> v == null ? 1 : ((v + 1) % SAMPLE_SIZE));
    return newCount != 1;
  }

  private static Document nonNullDocument(Document doc) {
    return doc == null ? new Document() : doc;
  }

  private static Document toDocument(DBObject dbObject) {
    if (dbObject == null) {
      return new Document();
    }
    return new Document(dbObject.toMap());
  }
}
