/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class HealthSourceHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    return null;
  }

  @Override
  public List<Map<String, String>> getColumnValueMappings(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    List<Map<String, String>> columnValueMappings = new ArrayList<>();
    DBObject dbObject = changeEvent.getFullDocument();

    String verificationJobInstanceId = dbObject.get("_id").toString();
    log.info("Handling change event: {} for VerificationJobInstance _id: {}", changeEvent.getUuid(),
        verificationJobInstanceId);

    BasicDBObject verificationJob = (BasicDBObject) dbObject.get(VerificationJobInstanceKeys.resolvedJob);
    List<BasicDBObject> cvConfigs = (List<BasicDBObject>) verificationJob.get(VerificationJobKeys.cvConfigs);
    Map<String, List<BasicDBObject>> healthSourceMap =
        cvConfigs.stream().collect(Collectors.groupingBy(cvConfig -> cvConfig.get(CVConfigKeys.identifier).toString()));
    for (Map.Entry<String, List<BasicDBObject>> healthSource : healthSourceMap.entrySet()) {
      Map<String, String> columnValueMapping =
          getColumnValueMappingForSingleHealthSource(verificationJobInstanceId, healthSource);
      columnValueMappings.add(columnValueMapping);
    }
    log.info("Handled change event: {} for VerificationJobInstance _id: {}", changeEvent.getUuid(),
        verificationJobInstanceId);
    return columnValueMappings;
  }

  private static Map<String, String> getColumnValueMappingForSingleHealthSource(
      String verificationJobInstanceId, Map.Entry<String, List<BasicDBObject>> healthSource) {
    String healthSourceIdentifier = healthSource.getKey().substring(healthSource.getKey().indexOf("/") + 1);
    BasicDBObject cvConfigAsDBObject = healthSource.getValue().get(0);
    String healthSourceName = cvConfigAsDBObject.getString(CVConfigKeys.monitoringSourceName);
    String type = null;
    if (Objects.nonNull(cvConfigAsDBObject.get(CVConfigKeys.dataSourceName))) {
      type = cvConfigAsDBObject.getString(CVConfigKeys.dataSourceName);
    }
    String providerType = null;
    int numberOfManualQueries = 0;
    if (Objects.nonNull(cvConfigAsDBObject.get(CVConfigKeys.verificationType))) {
      providerType = cvConfigAsDBObject.getString(CVConfigKeys.verificationType);
      numberOfManualQueries = getNumberOfManualQueries(healthSource.getValue());
    }
    String accountId = cvConfigAsDBObject.getString(CVConfigKeys.accountId);
    String orgIdentifier = cvConfigAsDBObject.getString(CVConfigKeys.orgIdentifier);
    String projectIdentifier = cvConfigAsDBObject.getString(CVConfigKeys.projectIdentifier);

    Map<String, String> columnValueMapping = new HashMap<>();
    String uniqueHealthSourceIdentifier = verificationJobInstanceId + healthSourceIdentifier;
    columnValueMapping.put(
        "id", UUID.nameUUIDFromBytes(uniqueHealthSourceIdentifier.getBytes(StandardCharsets.UTF_8)).toString());
    columnValueMapping.put("healthSourceIdentifier", healthSourceIdentifier);
    columnValueMapping.put("accountId", accountId);
    columnValueMapping.put("orgIdentifier", orgIdentifier);
    columnValueMapping.put("projectIdentifier", projectIdentifier);
    columnValueMapping.put("name", healthSourceName);
    if (Objects.nonNull(providerType)) {
      columnValueMapping.put("providerType", providerType);
    }
    if (Objects.nonNull(type)) {
      columnValueMapping.put("type", type);
    }
    columnValueMapping.put("numberOfManualQueries", String.valueOf(numberOfManualQueries));
    columnValueMapping.put("verificationJobInstanceId", verificationJobInstanceId);
    return columnValueMapping;
  }

  private static int getNumberOfManualQueries(List<BasicDBObject> cvConfigs) {
    int numberOfManualQueries = 0;
    for (BasicDBObject cvConfigAsDBObject : cvConfigs) {
      if (cvConfigAsDBObject.getString(CVConfigKeys.verificationType).equals(VerificationType.TIME_SERIES.toString())) {
        int numberOfManualQueriesInCurrentCvConfig = 0;
        if (Objects.nonNull(cvConfigAsDBObject.get("metricInfos"))) {
          numberOfManualQueriesInCurrentCvConfig = ((List<?>) cvConfigAsDBObject.get("metricInfos")).size();
        } else if (Objects.nonNull(cvConfigAsDBObject.get("metricInfoList"))) {
          numberOfManualQueriesInCurrentCvConfig = ((List<?>) cvConfigAsDBObject.get("metricInfoList")).size();
        }
        numberOfManualQueries += numberOfManualQueriesInCurrentCvConfig;
      } else {
        numberOfManualQueries++;
      }
    }
    return numberOfManualQueries;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of("id");
  }
}
