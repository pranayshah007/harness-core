/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLPageInfo.QLPageInfoBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.connector.QLConnectorFilter;
import software.wings.graphql.schema.type.connector.QLConnector;
import software.wings.graphql.schema.type.connector.QLConnectorsConnection;
import software.wings.graphql.schema.type.connector.QLConnectorsConnection.QLConnectorsConnectionBuilder;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.SettingsService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ConnectorConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLConnectorFilter, QLNoOpSortCriteria, QLConnectorsConnection> {
  @Inject private SettingsService settingsService;
  @Inject ConnectorQueryHelper connectorQueryHelper;
  @Inject ConnectorsController connectorsController;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLConnectorsConnection fetchConnection(List<QLConnectorFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    final Iterable<SettingCategory> settingValues =
        Lists.newArrayList(SettingCategory.CONNECTOR, SettingCategory.HELM_REPO);
    Query<SettingAttribute> query = populateFilters(wingsPersistence, filters, SettingAttribute.class, true);
    query = query.field(SettingAttributeKeys.category).in(settingValues);

    final List<SettingAttribute> settingAttributes = query.asList();

    int offset = pageQueryParameters.getOffset();
    int limit = pageQueryParameters.getLimit();

    List<SettingAttribute> filteredSettingAttributes =
        settingsService.getFilteredSettingAttributes(settingAttributes, null, null, false);

    QLConnectorsConnectionBuilder connectorsConnectionBuilder = QLConnectorsConnection.builder();

    QLPageInfoBuilder pageInfoBuilder = QLPageInfo.builder();

    if (filteredSettingAttributes == null) {
      filteredSettingAttributes = new ArrayList<>();
    }

    List<SettingAttribute> resp;
    int total = filteredSettingAttributes.size();
    if (total <= offset) {
      resp = new ArrayList<>();
    } else {
      int endIdx = Math.min(offset + limit, total);
      resp = filteredSettingAttributes.subList(offset, endIdx);
    }

    List<QLConnector> nodes = new ArrayList<>();
    for (SettingAttribute settingAttribute : resp) {
      nodes.add(connectorsController
                    .populateConnector(settingAttribute, connectorsController.getConnectorBuilder(settingAttribute))
                    .build());
    }

    QLPageInfo pageInfo = pageInfoBuilder.total(total).limit(limit).offset(offset).hasMore(total > offset).build();
    connectorsConnectionBuilder.pageInfo(pageInfo).nodes(nodes);
    return connectorsConnectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLConnectorFilter> filters, Query query) {
    connectorQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLConnectorFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    if (NameService.connector.equals(key)) {
      return QLConnectorFilter.builder()
          .connector(QLIdFilter.builder()
                         .operator(QLIdOperator.EQUALS)
                         .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                         .build())
          .build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
