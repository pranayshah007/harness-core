/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretManagerConfig.SecretManagerConfigKeys;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerConnection;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerConnection.QLSecretManagerConnectionBuilder;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class SecretManagersDataFetcher
    extends AbstractConnectionV2DataFetcher<QLSecretManagerFilter, QLNoOpSortCriteria, QLSecretManagerConnection> {
  @Inject protected DataFetcherUtils dataFetcherUtils;
  @Inject private SecretManagerController secretManagerController;

  @Override
  protected QLSecretManagerFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return QLSecretManagerFilter.builder().build();
  }

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLSecretManagerConnection fetchConnection(List<QLSecretManagerFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<SecretManagerConfig> query = populateFilters(wingsPersistence, filters, SecretManagerConfig.class, false);
    query.field(SecretManagerConfigKeys.ngMetadata).equal(null);
    query.or(query.criteria(SettingAttributeKeys.accountId).equal(getAccountId()),
        query.criteria(SettingAttributeKeys.accountId).equal(GLOBAL_ACCOUNT_ID));
    query.order(Sort.descending(SecretManagerConfigKeys.createdAt));
    QLSecretManagerConnectionBuilder connectionBuilder = QLSecretManagerConnection.builder();
    connectionBuilder.pageInfo(dataFetcherUtils.populate(pageQueryParameters, query, secretManager -> {
      QLSecretManager qlSecretManager = secretManagerController.convertToQLSecretManager(secretManager);
      connectionBuilder.node(qlSecretManager);
    }));
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLSecretManagerFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }
    filters.forEach(filter -> {
      FieldEnd<? extends Query<SettingAttribute>> field;
      if (filter.getSecretManager() != null) {
        field = query.field("_id");
        QLIdFilter secretManagerFilter = filter.getSecretManager();
        dataFetcherUtils.setIdFilter(field, secretManagerFilter);
      }
      if (filter.getType() != null) {
        QLSecretManagerTypeFilter entityTypeFilter = filter.getType();
        field = query.field(SecretManagerConfigKeys.encryptionType);
        dataFetcherUtils.setEnumFilter(field, entityTypeFilter);
      }
    });
  }
}
