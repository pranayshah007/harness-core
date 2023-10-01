/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ssca.beans.AllowList.AllowListItem;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.enforcement.EnforcementListItem;
import io.harness.ssca.enforcement.constants.PolicyType;
import io.harness.ssca.enforcement.executors.mongo.filter.QueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.allowlist.AllowLicenseQueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.allowlist.PurlQueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.allowlist.SupplierQueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.DenyListItemQueryBuilder;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.LicenseField;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.PurlField;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.SupplierField;
import io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields.VersionField;
import io.harness.ssca.enforcement.rule.IRuleInterpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.mongodb.core.query.Query;

public class MongoInterpreter implements IRuleInterpreter {
  @Override
  public Query interpretRules(EnforcementListItem item, String orchestrationId, AllowListRuleType type) {
    QueryBuilder queryBuilder;
    Map<String, Object> filter = new HashMap<>();
    filter.put("orchestrationid", orchestrationId);

    if (item.getType() == PolicyType.DENY_LIST) {
      if (item.getPackageName() != null) {
        filter.put("packagename", item.getPackageName());
      }

      queryBuilder =
          DenyListItemQueryBuilder.builder()
              .orchestrationId(orchestrationId)
              .denyListItem((DenyListItem) item)
              .filters(filter)
              .fields(Arrays.asList(new LicenseField(), new SupplierField(), new PurlField(), new VersionField()))
              .build();
    } else if (type == AllowListRuleType.ALLOW_LICENSE_ITEM) {
      queryBuilder = AllowLicenseQueryBuilder.builder()
                         .orchestrationId(orchestrationId)
                         .licenses(((AllowListItem) item).getLicenses())
                         .allowLicenses(new ArrayList<>())
                         .ignorePackages(new ArrayList<>())
                         .filters(new HashMap<>())
                         .build();
    } else if (type == AllowListRuleType.ALLOW_SUPPLIER_ITEM) {
      queryBuilder = SupplierQueryBuilder.builder()
                         .orchestrationId(orchestrationId)
                         .suppliers(((AllowListItem) item).getSuppliers())
                         .allowSuppliers(new ArrayList<>())
                         .ignorePackages(new ArrayList<>())
                         .filters(new HashMap<>())
                         .build();
    } else if (type == AllowListRuleType.ALLOW_PURL_ITEM) {
      queryBuilder = PurlQueryBuilder.builder()
                         .orchestrationId(orchestrationId)
                         .purls(((AllowListItem) item).getPurls())
                         .filters(new HashMap<>())
                         .build();
    } else {
      throw new InvalidArgumentsException(String.format("Invalid value of type: %s", type.name()));
    }
    return queryBuilder.getQuery();
  }
}
