/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.index;

import com.mongodb.BasicDBObject;
import java.util.List;
import javax.ws.rs.NotFoundException;

public class BasicDBUtils {
  public static BasicDBObject getIndexObject(List<MongoIndex> mongoIndexList, String indexName) {
    return mongoIndexList.stream()
        .filter(index -> index.getName().equals(indexName))
        .findFirst()
        .orElseThrow(() -> { throw new NotFoundException("index not found: " + indexName); })
        .getHint();
  }
}
