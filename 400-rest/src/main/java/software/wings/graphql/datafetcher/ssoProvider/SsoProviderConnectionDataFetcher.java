/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.ssoProvider;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLPageInfo.QLPageInfoBuilder;
import software.wings.graphql.schema.type.QLSSOProvider;
import software.wings.graphql.schema.type.QLSSOProvider.QLSSOProviderBuilder;
import software.wings.graphql.schema.type.QLSSOProviderConnection;
import software.wings.graphql.schema.type.QLSSOProviderConnection.QLSSOProviderConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.ssoProvider.QLSSOProviderFilter;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.SSOService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class SsoProviderConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLSSOProviderFilter, QLNoOpSortCriteria, QLSSOProviderConnection> {
  @Inject private SSOService ssoService;

  @Override
  @AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
  public QLSSOProviderConnection fetchConnection(List<QLSSOProviderFilter> appFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    List<SSOSettings> ssoSettings =
        ssoService.getAccountAccessManagementSettings(getAccountId(), false).getSsoSettings();
    List<QLSSOProvider> nodes = new ArrayList<>();
    int total = 0;
    for (SSOSettings setting : ssoSettings) {
      if (setting.getType() != SSOType.LDAP && setting.getType() != SSOType.SAML) {
        continue;
      }
      QLSSOProviderBuilder builder = QLSSOProvider.builder();
      nodes.add(SSOProviderController.populateSSOProvider(setting, builder).build());
      total += 1;
    }
    QLSSOProviderConnectionBuilder connectionBuilder = QLSSOProviderConnection.builder();
    QLPageInfoBuilder pageInfoBuilder = QLPageInfo.builder();
    QLPageInfo pageInfo = pageInfoBuilder.total(total)
                              .limit(pageQueryParameters.getLimit())
                              .offset(pageQueryParameters.getOffset())
                              .hasMore(total > pageQueryParameters.getOffset())
                              .build();
    connectionBuilder.pageInfo(pageInfo).nodes(nodes);
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLSSOProviderFilter> filters, Query query) {
    // do nothing
  }

  @Override
  protected QLSSOProviderFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
