/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.allowlist;

import io.harness.ssca.beans.AllowLicense;
import io.harness.ssca.enforcement.executors.mongo.MongoOperators;
import io.harness.ssca.enforcement.executors.mongo.filter.QueryBuilder;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

@Builder
public class AllowLicenseQueryBuilder implements QueryBuilder {
  List<AllowLicense> licenses;
  String orchestrationId;
  List<String> ignorePackages;
  List<AllowLicense> allowLicenses;
  Map<String, Object> filters;

  @Override
  public Query getQuery() {
    ignorePackages = new ArrayList<>();
    List<Document> result = new ArrayList<>();
    for (AllowLicense license : licenses) {
      if (license.getName() != null) {
        Document uniquePackageFilter = license.uniquePackageFilter();
        result.add(new Document(MongoOperators.MONGO_AND, Arrays.asList(new Document(filters), uniquePackageFilter)));
        ignorePackages.add(license.getName());
      } else {
        allowLicenses.add(license);
      }
    }
    if (allowLicenses.size() != 0) {
      Document allowLicenseFilter = getAllowLicenseFilter(orchestrationId, allowLicenses, ignorePackages);
      result.add(new Document(MongoOperators.MONGO_AND, Arrays.asList(new Document(filters), allowLicenseFilter)));
    }

    return new BasicQuery(new Document(MongoOperators.MONGO_AND, result));
  }

  private Document getAllowLicenseFilter(
      String orchestrationId, List<AllowLicense> allowLicenses, List<String> ignorePackages) {
    List<Document> filters = new ArrayList<>();
    for (AllowLicense license : allowLicenses) {
      filters.add(new Document(NormalizedSBOMEntityKeys.packageLicense.toLowerCase(),
          new Document(MongoOperators.MONGO_REGEX, license.getLicense())));
    }
    if (filters.isEmpty()) {
      return null;
    }

    Document ignorePackagesFilter = new Document(
        NormalizedSBOMEntityKeys.packageName.toLowerCase(), new Document(MongoOperators.MONGO_NOT_IN, ignorePackages));
    Document licenseFilter = new Document(MongoOperators.MONGO_NOR, filters);
    Document orchestrationIdFilter =
        new Document(NormalizedSBOMEntityKeys.orchestrationId.toLowerCase(), orchestrationId);

    return new Document(
        MongoOperators.MONGO_AND, Arrays.asList(ignorePackagesFilter, licenseFilter, orchestrationIdFilter));
  }
}
