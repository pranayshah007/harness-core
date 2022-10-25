/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index;

import static org.atmosphere.annotation.AnnotationUtil.logger;

import io.harness.mongo.IndexCreator;
import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.serializer.JsonUtils;

import com.mongodb.BasicDBObject;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class CompoundMongoIndex implements MongoIndex {
  String name;
  boolean unique;
  boolean sparse;
  Collation collation;
  @Singular private List<String> fields;

  @Override
  public IndexCreatorBuilder createBuilder(String id) {
    checks(logger);

    BasicDBObject keys = buildBasicDBObject(id);
    BasicDBObject options = buildBasicDBObject();
    if (Objects.nonNull(collation)) {
      io.harness.mongo.Collation collation1 = io.harness.mongo.Collation.builder()
                                                  .locale(this.getCollation().getLocale().getCode())
                                                  .strength(this.getCollation().getStrength().getCode())
                                                  .build();
      BasicDBObject basicDBObject = BasicDBObject.parse(JsonUtils.asJson(collation1));
      options.put("collation", basicDBObject);
    }
    return IndexCreator.builder().keys(keys).options(options);
  }
}
