/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.mongo.IndexCreator;
import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.mongo.IndexManagerInspectException;

import com.google.common.base.Preconditions;
import com.mongodb.BasicDBObject;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class CompoundTextMongoIndex implements MongoIndex {
  String name;
  boolean sparse;
  @Singular private List<String> fields;
  @Singular private List<String> textFields;
  @Singular private List<String> sortFields;
  @Singular private List<String> rangeFields;

  @Override
  public IndexCreatorBuilder createBuilder(String id) {
    checks(log);
    Preconditions.checkState(isNotEmpty(name), name);
    Preconditions.checkState(
        !sortFields.isEmpty() || !rangeFields.isEmpty() || !textFields.isEmpty() || !fields.isEmpty(), name);
    Preconditions.checkState(!textFields.isEmpty(), name);

    BasicDBObject keys = buildBasicDBObject(id);
    BasicDBObject options = buildBasicDBObject();

    if (isNotEmpty(getSortFields())) {
      for (String field : getSortFields()) {
        if (field.equals(id)) {
          throw new IndexManagerInspectException("There is no point of having collection key in a composite index."
              + "\nIf in the query there is a unique value it will always fetch exactly one item");
        }
        if (field.charAt(0) != '-') {
          keys.append(field, IndexType.ASC.toIndexValue());
        } else {
          keys.append(field.substring(1), IndexType.DESC.toIndexValue());
        }
      }
    }

    if (isNotEmpty(getTextFields())) {
      for (String field : getTextFields()) {
        if (field.equals(id)) {
          throw new IndexManagerInspectException("There is no point of having collection key in a composite index."
              + "\nIf in the query there is a unique value it will always fetch exactly one item");
        }
        keys.append(field, IndexType.TEXT.toIndexValue());
      }
    }
    if (isNotEmpty(getRangeFields())) {
      for (String field : getRangeFields()) {
        if (field.equals(id)) {
          throw new IndexManagerInspectException("There is no point of having collection key in a composite index."
              + "\nIf in the query there is a unique value it will always fetch exactly one item");
        }
        keys.append(field, IndexType.ASC.toIndexValue());
      }
    }
    return IndexCreator.builder().keys(keys).options(options);
  }

  @Override
  public boolean isUnique() {
    return false;
  }

  public static class CompoundTextMongoIndexBuilder {
    public CompoundTextMongoIndexBuilder ascSortField(String sortField) {
      return sortField(sortField);
    }

    public CompoundTextMongoIndexBuilder descSortField(String sortField) {
      return sortField("-" + sortField);
    }

    public CompoundTextMongoIndexBuilder ascRangeField(String rangeField) {
      return rangeField(rangeField);
    }

    public CompoundTextMongoIndexBuilder descRangeField(String rangeField) {
      return rangeField("-" + rangeField);
    }
  }
}
