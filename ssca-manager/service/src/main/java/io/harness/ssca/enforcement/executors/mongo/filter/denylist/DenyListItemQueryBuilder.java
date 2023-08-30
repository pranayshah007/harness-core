/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist;

import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.enforcement.executors.mongo.MongoOperators;
import io.harness.ssca.enforcement.executors.mongo.filter.QueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

@Builder
public class DenyListItemQueryBuilder implements QueryBuilder {
  String orchestrationId;
  List<Field> fields;
  DenyListItem denyListItem;
  Map<String, Object> filters;
  Query query;

  @Override
  public Query getQuery() {
    List<Document> documents = new ArrayList<>();
    documents.add(new Document(filters));
    for (Field a : fields) {
      if (a.isMatched(denyListItem)) {
        documents.add(a.getQueryDocument(denyListItem));
      }
    }
    return new BasicQuery(new Document(MongoOperators.MONGO_AND, documents));
  }
}
