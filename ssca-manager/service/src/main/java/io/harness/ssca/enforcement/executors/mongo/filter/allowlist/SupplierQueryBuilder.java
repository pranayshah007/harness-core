/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.allowlist;

import io.harness.ssca.beans.Supplier;
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
public class SupplierQueryBuilder implements QueryBuilder {
  List<Supplier> suppliers;
  String orchestrationId;
  List<String> ignorePackages;
  List<Supplier> allowSuppliers;
  Map<String, Object> filters;

  @Override
  public Query getQuery() {
    ignorePackages = new ArrayList<>();
    List<Document> result = new ArrayList<>();
    for (Supplier supplier : suppliers) {
      if (supplier.getName() != null) {
        Document uniquePackageFilter = supplier.uniquePackageFilter();
        result.add(new Document(MongoOperators.MONGO_AND, Arrays.asList(new Document(filters), uniquePackageFilter)));
        ignorePackages.add(supplier.getName());
      } else {
        allowSuppliers.add(supplier);
      }
    }
    if (allowSuppliers.size() != 0) {
      Document allowLicenseFilter = getAllowSupplierFilter(orchestrationId, allowSuppliers, ignorePackages);
      result.add(new Document(MongoOperators.MONGO_AND, Arrays.asList(new Document(filters), allowLicenseFilter)));
    }

    return new BasicQuery(new Document(MongoOperators.MONGO_AND, result));
  }

  private Document getAllowSupplierFilter(
      String orchestrationId, List<Supplier> suppliers, List<String> ignorePackages) {
    List<Document> filters = new ArrayList<>();
    for (Supplier supplier : suppliers) {
      filters.add(new Document(NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase(),
          new Document(MongoOperators.MONGO_NOT, new Document(MongoOperators.MONGO_REGEX, supplier.getSupplier()))));
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
