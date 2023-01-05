/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import dev.morphia.AdvancedDatastore;
import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class QuartzCleaner {
  public static void cleanup(AdvancedDatastore datastore, String prefix) {
    cleanupJobs(datastore, prefix);
    cleanupTriggers(datastore, prefix);
  }

  private static void cleanupJobs(AdvancedDatastore datastore, String prefix) {
    DBCollection collection = datastore.getDB().getCollection(prefix + "_jobs");
    if (collection == null) {
      return;
    }

    Set<String> classes = new HashSet<>();

    DBCursor dbObjects = null;
    try {
      dbObjects = collection.find(null);

      for (DBObject job : dbObjects) {
        String jobClass = job.get("jobClass").toString();
        if (classes.contains(jobClass)) {
          continue;
        }

        try {
          QuartzCleaner.class.getClassLoader().loadClass(jobClass);
        } catch (ClassNotFoundException e) {
          Object id = job.get("_id");
          collection.remove(new BasicDBObject("_id", id));
          log.warn("Removing invalid job: {}, class: {}", id.toString(), jobClass);
          continue;
        }

        classes.add(jobClass);
      }

    } finally {
      if (dbObjects != null) {
        dbObjects.close();
      }
    }
  }

  private static void cleanupTriggers(AdvancedDatastore datastore, String prefix) {
    DBCollection collection = datastore.getDB().getCollection(prefix + "_triggers");
    if (collection == null) {
      return;
    }

    DBCollection jobsCollection = datastore.getDB().getCollection(prefix + "_jobs");

    DBCursor dbObjects = null;
    try {
      dbObjects = collection.find(null);

      for (DBObject trigger : dbObjects) {
        DBObject job = jobsCollection.findOne(new BasicDBObject("_id", trigger.get("jobId")));
        if (job == null) {
          Object id = trigger.get("_id");
          collection.remove(new BasicDBObject("_id", id));
          log.warn("Removing invalid trigger: {}", id.toString());
        }
      }
    } finally {
      if (dbObjects != null) {
        dbObjects.close();
      }
    }
  }
}
