/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.ng.migration.AddServiceTagsToTagsInfo.DEBUG_LINE;
import static io.harness.threading.Morpheus.sleep;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NGCoreDataMigrationHelper {
  @Inject TimeScaleDBService timeScaleDBService;

  public void migrateServiceTag(
      DBCollection collection, BasicDBObject projection, BasicDBObject filter, int batchLimit, String query) {
    DBCursor dataRecords =
        collection.find(filter, projection).sort(new BasicDBObject(ServiceEntityKeys.createdAt, -1)).limit(batchLimit);

    int recordAdded = 0;
    int totalRecord = 0;
    List<DBObject> serviceTagDetails = new ArrayList<>();

    try {
      while (dataRecords.hasNext()) {
        DBObject record = dataRecords.next();
        totalRecord++;

        if (EmptyPredicate.isNotEmpty((List<DBObject>) record.get(ServiceEntityKeys.tags))) {
          serviceTagDetails.add(record);
          recordAdded++;
        }

        if (totalRecord != 0 && totalRecord % batchLimit == 0) {
          executeAdditionQuery(query, serviceTagDetails);

          sleep(Duration.ofMillis(100));

          dataRecords = collection.find(filter, projection)
                            .sort(new BasicDBObject(ServiceEntityKeys.createdAt, -1))
                            .skip(totalRecord)
                            .limit(batchLimit);

          log.info(DEBUG_LINE + "{} records added to tags_info class", recordAdded);
        }
      }

      if (totalRecord % batchLimit != 0) {
        executeAdditionQuery(query, serviceTagDetails);

        log.info(DEBUG_LINE + "{} records added to tags_info class", recordAdded);
      }

    } catch (Exception e) {
      log.error(DEBUG_LINE + "Exception occurred migrating service tags to tags_info class", e);
    } finally {
      dataRecords.close();
    }
  }

  private void executeAdditionQuery(String query, List<DBObject> serviceTagDetails) throws SQLException {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement additionStatement = connection.prepareStatement(query)) {
      for (DBObject serviceTagInfo : serviceTagDetails) {
        String identifier = serviceTagInfo.get(ServiceEntityKeys.identifier).toString();
        additionStatement.setString(1, identifier);

        List<BasicDBObject> tagObjects = (List<BasicDBObject>) serviceTagInfo.get(ServiceEntityKeys.tags);
        List<String> tagList = getTagList(tagObjects);
        additionStatement.setArray(2, connection.createArrayOf("text", tagList.toArray()));
        additionStatement.addBatch();
      }
      additionStatement.executeBatch();
    }
  }

  private List<String> getTagList(List<BasicDBObject> tagObjects) {
    List<String> tagList = new ArrayList<>();

    for (BasicDBObject tagObject : tagObjects) {
      String tagKey = tagObject.get(NGTagKeys.key).toString();
      String tagValue = tagObject.get(NGTagKeys.value).toString();
      tagValue = tagValue == null ? "" : tagValue;
      tagList.add(tagKey + ':' + tagValue);
    }

    return tagList;
  }
}
