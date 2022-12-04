/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index;

import static java.lang.String.format;

import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.mongo.IndexManagerInspectException;

import com.mongodb.BasicDBObject;
import java.util.List;
import org.slf4j.Logger;

public interface MongoIndex {
  String NAME = "name";
  String UNIQUE = "unique";
  String SPARSE = "sparse";
  String BACKGROUND = "background";

  IndexCreatorBuilder createBuilder(String id);
  String getName();
  boolean isUnique();
  boolean isSparse();
  List<String> getFields();

  default void checks(Logger log) {
    getFields().forEach(a -> {
      if (getFields().stream().filter(b -> a.equals(b)).count() > 1) {
        throw new Error(format("Index %s has field %s more than once", getName(), a));
      }
    });
  }

  default BasicDBObject buildBasicDBObject(String id) {
    BasicDBObject keys = new BasicDBObject();

    for (String field : getFields()) {
      if (field.equals(id)) {
        throw new IndexManagerInspectException("There is no point of having collection key in a composite index."
            + "\nIf in the query there is a unique value it will always fetch exactly one item");
      }
      keys.append(field, IndexType.ASC.toIndexValue());
    }
    return keys;
  }

  default BasicDBObject buildBasicDBObject() {
    BasicDBObject options = new BasicDBObject();
    options.put(NAME, getName());
    if (isUnique()) {
      options.put(UNIQUE, Boolean.TRUE);
    } else {
      options.put(BACKGROUND, Boolean.TRUE);
    }
    if (isSparse()) {
      options.put(SPARSE, Boolean.TRUE);
    }
    return options;
  }
}
