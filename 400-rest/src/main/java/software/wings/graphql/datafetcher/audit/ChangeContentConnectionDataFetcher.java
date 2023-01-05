/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.GraphQLException;
import io.harness.exception.WingsException;

import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.Principal;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.audit.QLChangeContentFilter;
import software.wings.graphql.schema.type.audit.QLChangeContentConnection;
import software.wings.graphql.schema.type.audit.QLChangeContentConnection.QLChangeContentConnectionBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ChangeContentConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLChangeContentFilter, QLNoOpSortCriteria, QLChangeContentConnection> {
  @Inject private ChangeContentHelper changeContentHelper;
  @Inject private ChangeContentController changeContentController;
  @Override
  @AuthRule(permissionType = PermissionType.AUDIT_VIEWER)
  public QLChangeContentConnection fetchConnection(List<QLChangeContentFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    if (isNotEmpty(filters)) {
      if (filters.size() == 1 && null != filters.get(0)) {
        QLChangeContentFilter filter = filters.get(0);
        String changeSetId = filter.getChangeSetId();
        String resourceId = filter.getResourceId();
        QLChangeContentConnectionBuilder connectionBuilder = QLChangeContentConnection.builder();
        // get yaml diff for a specific changeSetId and resourceId
        if (resourceId != null) {
          changeContentController.populateChangeContent(changeSetId, resourceId, connectionBuilder);
        } else { // get yaml diff for a specific changeSetId and all resourceIds under it
          changeContentController.populateChangeContent(changeSetId, connectionBuilder, pageQueryParameters);
        }
        final String accountId = getAccountId();
        final Principal triggeredBy = getTriggeredBy();
        changeContentHelper.reportAuditTrailExportToSegment(accountId, triggeredBy);
        return connectionBuilder.build();
      } else {
        throw new GraphQLException("Query supports only one filter at a time", WingsException.SRE);
      }
    } else {
      throw new GraphQLException("Change set id filter is mandatory", WingsException.SRE);
    }
  }

  @Override
  protected void populateFilters(List<QLChangeContentFilter> filters, Query query) {}

  @Override
  protected QLChangeContentFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    return null;
  }
}
