/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.allowlist;

import io.harness.ssca.enforcement.executors.mongo.MongoOperators;
import io.harness.ssca.enforcement.executors.mongo.filter.QueryBuilder;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Builder;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

@Builder
public class PurlQueryBuilder implements QueryBuilder {
  String orchestrationId;
  List<String> purls;
  Map<String, Object> filters;

  // Sample Purl -> pkg:apk/alpine/alpine-baselayout@3.4.3-r1?arch=x86_64&distro=alpine-3.18.2
  @Override
  public Query getQuery() {
    List<Document> filterList = new ArrayList<>();
    for (String purl : purls) {
      String[] splitPurl = purl.split(":");
      if (splitPurl.length != 2) {
        break;
      }
      splitPurl = splitPurl[1].split("@");
      if (splitPurl.length != 2) {
        break;
      }
      String version = splitPurl[1];
      splitPurl = splitPurl[0].split("/");
      String packageName;
      String packageManager;
      String packageNamespace = null;
      if (splitPurl.length == 2) {
        packageManager = splitPurl[0];
        packageName = splitPurl[1];
      } else {
        packageManager = splitPurl[0];
        packageNamespace = splitPurl[1];
        packageName = splitPurl[2];
      }
      filterList.add(new Document(filters));
      filterList.add(new Document(NormalizedSBOMEntityKeys.packageName.toLowerCase(), packageName));

      List<Document> innerFilters = new ArrayList<>();
      Pattern regex = Pattern.compile(version, Pattern.CASE_INSENSITIVE);
      Document packageVersionFilter = new Document(NormalizedSBOMEntityKeys.packageVersion.toLowerCase(),
          new Document(
              MongoOperators.MONGO_NOT, new Document(MongoOperators.MONGO_IN, Collections.singletonList(regex))));

      regex = Pattern.compile(packageManager, Pattern.CASE_INSENSITIVE);
      Document packageManagerFilter = new Document(NormalizedSBOMEntityKeys.packageManager.toLowerCase(),
          new Document(
              MongoOperators.MONGO_NOT, new Document(MongoOperators.MONGO_IN, Collections.singletonList(regex))));
      innerFilters.add(
          new Document(MongoOperators.MONGO_AND, Arrays.asList(packageVersionFilter, packageManagerFilter)));

      if (packageNamespace != null) {
        regex = Pattern.compile(packageNamespace, Pattern.CASE_INSENSITIVE);
        Document packageNamespaceFilter = new Document(NormalizedSBOMEntityKeys.packageNamespace.toLowerCase(),
            new Document(
                MongoOperators.MONGO_NOT, new Document(MongoOperators.MONGO_IN, Collections.singletonList(regex))));
        innerFilters.add(packageNamespaceFilter);
      }

      filterList.add(new Document(MongoOperators.MONGO_OR, innerFilters));
    }
    return new BasicQuery(new Document(MongoOperators.MONGO_AND, filterList));
  }
}
