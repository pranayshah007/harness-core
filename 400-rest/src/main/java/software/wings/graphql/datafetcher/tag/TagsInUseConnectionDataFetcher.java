/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.tag;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;

import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagLink.HarnessTagLinkKeys;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLTagsInUse;
import software.wings.graphql.schema.type.QLTagsInUseConnection;
import software.wings.graphql.schema.type.aggregation.QLEntityType;
import software.wings.graphql.schema.type.aggregation.QLEntityTypeFilter;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInUseFilter;
import software.wings.security.annotations.AuthRule;

import com.google.common.collect.Sets;
import com.mongodb.AggregationOptions;
import com.mongodb.BasicDBObject;
import com.mongodb.Cursor;
import com.mongodb.DBCollection;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class TagsInUseConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLTagInUseFilter, QLNoOpSortCriteria, QLTagsInUseConnection> {
  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLTagsInUseConnection fetchConnection(List<QLTagInUseFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    int offset = pageQueryParameters.getOffset();
    int limit = pageQueryParameters.getLimit();

    List<String> tagsKeyList = new ArrayList<>();
    Map<String, Set<String>> tagsInUseMap = new HashMap<>();
    populateTagsByUsingMongoAggregateAPI(filters, tagsKeyList, tagsInUseMap);

    int total = tagsKeyList.size();
    List<String> keysToBeReturned;
    if (total <= offset) {
      keysToBeReturned = new ArrayList<>();
    } else {
      int endIdx = Math.min(offset + limit, total);
      keysToBeReturned = tagsKeyList.subList(offset, endIdx);
    }

    List<QLTagsInUse> tagsInCurrentPage = new ArrayList<>();
    for (String key : keysToBeReturned) {
      tagsInCurrentPage.add(QLTagsInUse.builder().name(key).values(new ArrayList<>(tagsInUseMap.get(key))).build());
    }

    QLPageInfo pageInfo = QLPageInfo.builder().total(total).limit(limit).offset(offset).hasMore(total > offset).build();
    return QLTagsInUseConnection.builder().pageInfo(pageInfo).nodes(tagsInCurrentPage).build();
  }

  @Override
  protected void populateFilters(List<QLTagInUseFilter> filters, Query query) {
    setQuery(filters, query);
  }

  @Override
  protected QLTagInUseFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }

  private void populateTags(
      List<QLTagInUseFilter> filters, List<String> tagsKeyList, Map<String, Set<String>> tagsInUseMap) {
    try (HIterator<HarnessTagLink> iterator =
             new HIterator<HarnessTagLink>(populateFilters(wingsPersistence, filters, HarnessTagLink.class, true)
                                               .project(HarnessTagLinkKeys.key, true)
                                               .project(HarnessTagLinkKeys.value, true)
                                               .order(HarnessTagLinkKeys.key)
                                               .fetch())) {
      for (HarnessTagLink harnessTagLink : iterator) {
        String key = harnessTagLink.getKey();
        String value = harnessTagLink.getValue();

        if (!tagsInUseMap.containsKey(key)) {
          tagsKeyList.add(key);
        }
        tagsInUseMap.computeIfAbsent(key, set -> Sets.newHashSet()).add(value);
      }
    }
  }

  private void populateTagsByUsingMongoAggregateAPI(
      List<QLTagInUseFilter> filters, List<String> tagsKeyList, Map<String, Set<String>> tagsInUseMap) {
    BasicDBObject match = getMatchObject(filters);
    BasicDBObject group = new BasicDBObject().append("$group",
        new BasicDBObject().append("_id",
            new BasicDBObject()
                .append(HarnessTagLinkKeys.key, "$" + HarnessTagLinkKeys.key)
                .append(HarnessTagLinkKeys.value, "$" + HarnessTagLinkKeys.value)));
    BasicDBObject sort = new BasicDBObject().append("$sort", new BasicDBObject().append("_id.key", 1));

    List<BasicDBObject> pipeline = Arrays.asList(match, group, sort);
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "tagLinks");
    Cursor cursor = collection.aggregate(pipeline, AggregationOptions.builder().build());

    while (cursor.hasNext()) {
      BasicDBObject document = (BasicDBObject) cursor.next();
      String key = String.valueOf(((LinkedHashMap) document.get("_id")).get("key"));
      String value = String.valueOf(((LinkedHashMap) document.get("_id")).get("value"));

      if (!tagsInUseMap.containsKey(key)) {
        tagsKeyList.add(key);
      }
      tagsInUseMap.computeIfAbsent(key, set -> Sets.newHashSet()).add(value);
    }
  }

  private BasicDBObject getMatchObject(List<QLTagInUseFilter> filters) {
    String accountId = getAccountId();

    BasicDBObject basicDBObject = new BasicDBObject();
    basicDBObject.append(HarnessTagLinkKeys.accountId, accountId);

    filters.forEach(filter -> {
      if (filter == null || filter.getEntityType() == null) {
        return;
      }

      QLEntityTypeFilter entityTypeFilter = filter.getEntityType();
      QLEnumOperator operator = entityTypeFilter.getOperator();

      if (operator == null) {
        throw new WingsException("Operator cannot be null");
      }

      if (isEmpty(entityTypeFilter.getValues())) {
        throw new WingsException("Filter Values cannot be empty");
      }

      List<String> entityTypes =
          Arrays.stream(entityTypeFilter.getValues()).map(QLEntityType::getStringValue).collect(Collectors.toList());

      switch (operator) {
        case IN:
          basicDBObject.append(HarnessTagLinkKeys.entityType, new BasicDBObject("$in", entityTypes));
          break;

        case EQUALS:
          if (entityTypes.size() > 1) {
            throw new WingsException("Only one value needs to be inputted for operator EQUALS");
          }
          basicDBObject.append(HarnessTagLinkKeys.entityType, entityTypeFilter.getValues()[0].getStringValue());
          break;

        default:
          throw new WingsException("Unknown operator " + operator);
      }
    });

    return new BasicDBObject().append("$match", basicDBObject);
  }

  private void setQuery(List<QLTagInUseFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;

      if (filter.getEntityType() != null) {
        field = query.field("entityType");
        QLEntityTypeFilter entityTypeFilter = filter.getEntityType();
        utils.setEnumFilter(field, entityTypeFilter);
      }
    });
  }
}
