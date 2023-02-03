/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

@Slf4j
public class ChurnedAccountDeletionHelper {
  public List<ObjectId> getFileIdsMatchingParentEntity(
      List<String> parentIds, DBCollection fileCollection, String foreignKey) {
    List<ObjectId> fileIds = new ArrayList<>();
    BasicDBObject matchCondition = new BasicDBObject(foreignKey, new BasicDBObject("$in", parentIds));
    BasicDBObject projection = new BasicDBObject("_id", true);
    DBCursor configFiles = fileCollection.find(matchCondition, projection);
    while (configFiles.hasNext()) {
      DBObject record = configFiles.next();
      fileIds.add(new ObjectId(record.get("_id").toString()));
    }
    return fileIds;
  }
}
