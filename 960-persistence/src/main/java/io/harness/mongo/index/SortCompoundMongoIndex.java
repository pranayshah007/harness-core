/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.atmosphere.annotation.AnnotationUtil.logger;

import io.harness.mongo.IndexCreator;
import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Preconditions;
import com.mongodb.BasicDBObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@Value
@Builder
@Slf4j
public class SortCompoundMongoIndex implements MongoIndex {
  private String name;
  private boolean unique;
  private boolean sparse;
  Collation collation;
  @Singular private List<String> fields;
  @Singular private List<String> sortFields;
  @Singular private List<String> rangeFields;

  @Override
  public IndexCreatorBuilder createBuilder(String id) {
    Preconditions.checkState(isNotEmpty(name), name);
    Preconditions.checkState(!sortFields.isEmpty() || !rangeFields.isEmpty(), name);
    Preconditions.checkState(!fields.isEmpty(), name);

    checks(logger);

    BasicDBObject keys = buildBasicDBObject(id);
    BasicDBObject options = buildBasicDBObject();
    if (Objects.nonNull(collation)) {
      io.harness.mongo.collation.Collation collation1 = io.harness.mongo.collation.Collation.builder()
                                                            .locale(this.getCollation().getLocale().getCode())
                                                            .strength(this.getCollation().getStrength().getCode())
                                                            .build();
      BasicDBObject basicDBObject = BasicDBObject.parse(JsonUtils.asJson(collation1));
      options.put("collation", basicDBObject);
    }

    if (isNotEmpty(getSortFields())) {
      for (String field : getSortFields()) {
        if (field.charAt(0) != '-') {
          keys.append(field, IndexType.ASC.toIndexValue());
        } else {
          keys.append(field.substring(1), IndexType.DESC.toIndexValue());
        }
      }
    }

    if (isNotEmpty(getRangeFields())) {
      for (String field : getRangeFields()) {
        if (field.charAt(0) != '-') {
          keys.append(field, IndexType.ASC.toIndexValue());
        } else {
          keys.append(field.substring(1), IndexType.DESC.toIndexValue());
        }
      }
    }
    return IndexCreator.builder().keys(keys).options(options);
  }

  @Override
  public BasicDBObject getHint() {
    Map<String, Object> options = new LinkedHashMap<>();
    for (String field : getFields()) {
      options.put(field.replace("-", ""), field.charAt(0) == '-' ? -1 : 1);
    }
    for (String field : getSortFields()) {
      options.put(field.replace("-", ""), field.charAt(0) == '-' ? -1 : 1);
    }
    for (String field : getRangeFields()) {
      options.put(field.replace("-", ""), field.charAt(0) == '-' ? -1 : 1);
    }
    return new BasicDBObject(options);
  }

  @Override
  public void checks(Logger log) {
    if ((getFields().size() + getSortFields().size() + getRangeFields().size() == 1)
        && !getFields().get(0).contains(".")) {
      log.error("Composite Sort index with only one field {}", getFields().get(0));
    }

    getFields().forEach(a -> {
      if (getFields().stream().filter(a::equals).count() > 1) {
        throw new Error(format("Index %s has field %s more than once", getName(), a));
      }
    });
    getSortFields().forEach(a -> {
      if (getFields().stream().filter(a::equals).count() > 1) {
        throw new Error(format("Index %s has field %s more than once", getName(), a));
      }
    });
    getRangeFields().forEach(a -> {
      if (getFields().stream().filter(a::equals).count() > 1) {
        throw new Error(format("Index %s has field %s more than once", getName(), a));
      }
    });
  }

  public static class SortCompoundMongoIndexBuilder {
    public SortCompoundMongoIndexBuilder ascSortField(String sortField) {
      return sortField(sortField);
    }

    public SortCompoundMongoIndexBuilder descSortField(String sortField) {
      return sortField("-" + sortField);
    }

    public SortCompoundMongoIndexBuilder ascRangeField(String rangeField) {
      return rangeField(rangeField);
    }

    public SortCompoundMongoIndexBuilder descRangeField(String rangeField) {
      return rangeField("-" + rangeField);
    }
  }
}
