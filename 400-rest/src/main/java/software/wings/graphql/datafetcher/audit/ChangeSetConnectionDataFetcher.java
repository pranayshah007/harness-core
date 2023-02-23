/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.Principal;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.audit.QLChangeSetFilter;
import software.wings.graphql.schema.type.audit.QLChangeSet;
import software.wings.graphql.schema.type.audit.QLChangeSetConnection;
import software.wings.graphql.schema.type.audit.QLChangeSetConnection.QLChangeSetConnectionBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ChangeSetConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLChangeSetFilter, QLNoOpSortCriteria, QLChangeSetConnection> {
  @Inject ChangeSetQueryHelper changeSetQueryHelper;
  @Inject private ChangeContentHelper changeContentHelper;
  @Inject private ChangeSetController changeSetController;

  @Override
  @AuthRule(permissionType = PermissionType.AUDIT_VIEWER)
  public QLChangeSetConnection fetchConnection(List<QLChangeSetFilter> serviceFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<AuditHeader> query = populateFilters(wingsPersistence, serviceFilters, AuditHeader.class, true);
    query.order(Sort.descending(AuditHeaderKeys.createdAt));

    QLChangeSetConnectionBuilder connectionBuilder = QLChangeSetConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, audit -> {
      final QLChangeSet changeSet = changeSetController.populateChangeSet(audit);
      connectionBuilder.node(changeSet);
    }));
    final String accountId = getAccountId();
    final Principal triggeredBy = getTriggeredBy();
    changeContentHelper.reportAuditTrailExportToSegment(accountId, triggeredBy);
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLChangeSetFilter> filters, Query query) {
    changeSetQueryHelper.setQuery(filters, query);
  }

  @Override
  protected QLChangeSetFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
