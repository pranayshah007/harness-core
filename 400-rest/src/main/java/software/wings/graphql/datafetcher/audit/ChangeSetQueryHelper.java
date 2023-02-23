/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.GraphQLException;
import io.harness.exception.WingsException;

import software.wings.audit.AuditHeader;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeRange;
import software.wings.graphql.schema.type.aggregation.audit.QLChangeSetFilter;
import software.wings.graphql.schema.type.aggregation.audit.QLRelativeTimeRange;
import software.wings.graphql.schema.type.aggregation.audit.QLTimeUnit;
import software.wings.utils.TimeUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author vardan
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ChangeSetQueryHelper {
  @Inject protected DataFetcherUtils utils;

  public void setQuery(List<QLChangeSetFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<AuditHeader>> field;

      if (filter.getTime() != null) {
        field = query.field("createdAt");
        QLTimeRange specificTimeRangeFilter = filter.getTime().getSpecific();
        QLRelativeTimeRange relativeTimeRangeFilter = filter.getTime().getRelative();
        // query can container either specificTimeRange filter or relativeTimeRangeFilter
        if (specificTimeRangeFilter != null && relativeTimeRangeFilter == null) {
          Long from = specificTimeRangeFilter.getFrom();
          Long to = specificTimeRangeFilter.getTo();
          if (to == null) {
            to = new Date().getTime();
          }
          if (from > to) {
            throw new GraphQLException("Time range is invalid", WingsException.USER_SRE);
          }
          utils.setTimeFilter(field, QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(from).build());
          utils.setTimeFilter(field, QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(to).build());

        } else if (specificTimeRangeFilter == null && relativeTimeRangeFilter != null) {
          QLTimeUnit timeUnit = relativeTimeRangeFilter.getTimeUnit();
          Long noOfUnits = relativeTimeRangeFilter.getNoOfUnits();
          long timeDuration = new Date().getTime() - TimeUtils.getMillisFromTime(timeUnit, noOfUnits);
          QLTimeFilter timeFilter = QLTimeFilter.builder().operator(QLTimeOperator.AFTER).value(timeDuration).build();
          utils.setTimeFilter(field, timeFilter);
        } else {
          throw new GraphQLException(
              "Either specific or relative timeRange filter is applicable at a time", WingsException.USER_SRE);
        }
      }
      // TODO will enable this is Sprint 67
      //      if (isNotEmpty(filter.getResources())) {
      //        query.field("entityAuditRecords.entityType").in(filter.getResources());
      //      }
    });
  }
}
