/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.IndexManagerSession.NAME;
import static io.harness.mongo.IndexManagerSession.UNIQUE;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofDays;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.logging.AutoLogContext;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class IndexManagerCollectionSession {
  private List<DBObject> indexInfo;

  public static IndexManagerCollectionSession createCollectionSession(DBCollection collection) {
    return new IndexManagerCollectionSession(collection);
  }

  private IndexManagerCollectionSession(DBCollection collection) {
    reset(collection);
  }

  final void reset(DBCollection collection) {
    indexInfo = collection.getIndexInfo();
    if (log.isDebugEnabled()) {
      log.debug("collection {} has index info {}", collection.getName(), indexInfo.toString());
    }
  }

  boolean isOkToDropIndexes(Date tooNew, Map<String, IndexManagerSession.Accesses> accesses) {
    for (DBObject info : indexInfo) {
      String name = info.get(NAME).toString();
      // Note that we are aware that we checking the obsolete indexes as well. This is to play on the safe side.
      IndexManagerSession.Accesses access = accesses.get(name);
      if (access == null || access.getSince().compareTo(tooNew) > 0) {
        return false;
      }
    }
    return true;
  }

  List<String> obsoleteIndexes(Set<String> names) {
    return indexInfo.stream()
        .map(obj -> obj.get(NAME).toString())
        .filter(name -> !"_id_".equals(name))
        .filter(name -> !names.contains(name))
        .collect(toList());
  }

  DBObject findIndexByFields(IndexCreator indexCreator) {
    for (DBObject index : indexInfo) {
      BasicDBObject indexKeys = (BasicDBObject) index.get("key");
      if (indexCreator.sameKeysOrder(indexKeys)) {
        return index;
      }
    }
    return null;
  }

  DBObject findIndexByFieldsAndDirection(IndexCreator indexCreator) {
    for (DBObject index : indexInfo) {
      BasicDBObject indexKeys = (BasicDBObject) index.get("key");
      if (indexCreator.sameKeysOrderAndValues(indexKeys)) {
        return index;
      }
    }
    return null;
  }

  DBObject findIndexByName(String name) {
    for (DBObject index : indexInfo) {
      String indexName = (String) index.get(NAME);
      if (name.equals(indexName)) {
        return index;
      }
    }
    return null;
  }

  boolean isRebuildNeeded(IndexCreator indexCreator) {
    // first make sure that we need to rename the index
    String name = (String) indexCreator.getOptions().get(NAME);
    DBObject indexByName = findIndexByName(name);
    DBObject indexByFields = findIndexByFieldsAndDirection(indexCreator);

    // There is collision by name
    if (indexByName != null && indexByName != indexByFields) {
      if (log.isDebugEnabled()) {
        log.debug("index {} collides by name with {}", indexCreator.toString(), indexByName.toString());
      }
      return true;
    }

    // There is collision by fields
    if (indexByFields != null && indexByName != indexByFields) {
      if (log.isDebugEnabled()) {
        log.info("index {} collides by fields with {}", indexCreator.toString(), indexByFields.toString());
      }
      return true;
    }

    if (indexByName != null) {
      Boolean newUniqueFlag = (Boolean) indexCreator.getOptions().get(UNIQUE);

      if (newUniqueFlag == null || !newUniqueFlag.booleanValue()) {
        if (IndexCreator.isUniqueIndex(indexByName)) {
          return true;
        }
      } else {
        if (!IndexCreator.isUniqueIndex(indexByName)) {
          return true;
        }
      }
    }

    // else there is no collision or it is the same index
    return false;
  }

  boolean isCreateNeeded(IndexCreator indexCreator) {
    String name = (String) indexCreator.getOptions().get(NAME);
    DBObject indexByName = findIndexByName(name);
    DBObject indexByFields = findIndexByFieldsAndDirection(indexCreator);

    return indexByName == null || indexByName != indexByFields;
  }

  // This for checks for unused indexes. It utilize the indexStat provided from mongo.
  // A good article on the topic:
  // https://www.objectrocket.com/blog/mongodb/considerations-for-using-indexstats-to-find-unused-indexes-in-mongodb/
  // NOTE: This is work in progress. For the time being we are checking only for completely unused indexes.
  void checkForUnusedIndexes(Map<String, IndexManagerSession.Accesses> accesses) {
    long now = currentTimeMillis();
    Date tooNew = new Date(now - ofDays(7).toMillis());

    Set<String> uniqueIndexes = indexInfo.stream()
                                    .filter(obj -> {
                                      Object unique = obj.get(UNIQUE);
                                      return unique != null && unique.toString().equals("true");
                                    })
                                    .map(obj -> obj.get(NAME).toString())
                                    .collect(toSet());

    accesses.entrySet()
        .stream()
        .filter(entry -> entry.getValue().getOperations() == 0)
        .filter(entry -> entry.getValue().getSince().compareTo(tooNew) < 0)
        // Exclude the object id index. It is rare but it might be unused
        .filter(entry -> !entry.getKey().equals("_id_"))
        // Exclude ttl indexes, Ttl monitoring is not tracked as operations
        .filter(entry -> !entry.getKey().startsWith("validUntil"))
        // Exclude unique indexes. Adding items is not tracked as index operations
        .filter(entry -> !uniqueIndexes.contains(entry.getKey()))
        // Alert for every index that left:
        .forEach(entry -> {
          Duration passed = Duration.between(entry.getValue().getSince().toInstant(), ZonedDateTime.now().toInstant());
          try (AutoLogContext ignore = new IndexLogContext(entry.getKey(), OVERRIDE_ERROR)) {
            log.warn("Index is not used at for {} days", passed.toDays());
          }
        });
  }
}
