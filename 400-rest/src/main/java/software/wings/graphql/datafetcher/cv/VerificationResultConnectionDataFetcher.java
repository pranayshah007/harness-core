/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cv;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.graphql.utils.nameservice.NameService.application;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cv.WorkflowVerificationResult;
import io.harness.cv.WorkflowVerificationResult.WorkflowVerificationResultKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HIterator;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLPageInfo.QLPageInfoBuilder;
import software.wings.graphql.schema.type.QLVerificationResult;
import software.wings.graphql.schema.type.QLVerificationResult.QLVerificationResultBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.cv.QLVerificationResultConnection;
import software.wings.graphql.schema.type.aggregation.cv.QLVerificationResultConnection.QLVerificationResultConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.cv.QLVerificationResultFilter;
import software.wings.security.AppPermissionSummaryForUI;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class VerificationResultConnectionDataFetcher extends AbstractConnectionV2DataFetcher<QLVerificationResultFilter,
    QLNoOpSortCriteria, QLVerificationResultConnection> {
  @Inject private AppService appService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;

  private LoadingCache<EntityKey, Application> appCache = CacheBuilder.newBuilder()
                                                              .maximumSize(1000)
                                                              .expireAfterWrite(1, TimeUnit.HOURS)
                                                              .build(new CacheLoader<EntityKey, Application>() {
                                                                @Override
                                                                public Application load(EntityKey entityKey) {
                                                                  return appService.get(entityKey.getAppId());
                                                                }
                                                              });

  private LoadingCache<EntityKey, Service> serviceCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(1, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, Service>() {
            @Override
            public Service load(EntityKey entityKey) {
              return serviceResourceService.get(entityKey.getServiceId());
            }
          });

  private LoadingCache<EntityKey, Environment> environmentCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(1, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, Environment>() {
            @Override
            public Environment load(EntityKey entityKey) {
              return environmentService.get(entityKey.getAppId(), entityKey.getEnvId());
            }
          });
  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  public QLVerificationResultConnection fetchConnection(List<QLVerificationResultFilter> resultFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<WorkflowVerificationResult> query =
        populateFilters(wingsPersistence, resultFilters, WorkflowVerificationResult.class, true);
    query.order(Sort.descending(WorkflowVerificationResultKeys.lastUpdatedAt));

    QLVerificationResultConnectionBuilder connectionBuilder = QLVerificationResultConnection.builder();
    QLPageInfoBuilder pageInfoBuilder =
        QLPageInfo.builder().limit(pageQueryParameters.getLimit()).offset(pageQueryParameters.getOffset());

    try (HIterator<WorkflowVerificationResult> iterator = new HIterator<>(query.fetch(new FindOptions()))) {
      int count = 0;
      int offset = 0;
      while (count < pageQueryParameters.getLimit() && iterator.hasNext()) {
        WorkflowVerificationResult verificationResult = iterator.next();
        if (offset < pageQueryParameters.getOffset()) {
          offset++;
          continue;
        }
        if (shouldAddResult(resultFilters, verificationResult)) {
          QLVerificationResultBuilder builder = QLVerificationResult.builder();
          EntityKey entityKey = EntityKey.builder()
                                    .appId(verificationResult.getAppId())
                                    .serviceId(verificationResult.getServiceId())
                                    .envId(verificationResult.getEnvId())
                                    .build();

          try {
            Application application = appCache.get(entityKey);
            Service service = serviceCache.get(entityKey);
            Environment environment = environmentCache.get(entityKey);
            VerificationResultController.populateQLApplication(
                verificationResult, builder, application.getName(), service.getName(), environment.getName());
            connectionBuilder.node(builder.build());
            count++;
          } catch (ExecutionException e) {
            log.error("error while getting entity", e);
          }
        }
      }

      if (pageQueryParameters.isHasMoreRequested()) {
        pageInfoBuilder.hasMore(iterator.hasNext());
      }

      if (pageQueryParameters.isTotalRequested()) {
        pageInfoBuilder.total((int) query.count());
      }
    }
    connectionBuilder.pageInfo(pageInfoBuilder.build());
    return connectionBuilder.build();
  }

  private boolean shouldAddResult(
      List<QLVerificationResultFilter> resultFilters, WorkflowVerificationResult verificationResult) {
    User user = UserThreadLocal.get();
    UserPermissionInfo userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();
    if (!userPermissionInfo.getAppPermissionMap().containsKey(verificationResult.getAppId())) {
      return false;
    }
    AppPermissionSummaryForUI appPermissionSummary =
        userPermissionInfo.getAppPermissionMap().get(verificationResult.getAppId());
    if (!appPermissionSummary.getServicePermissions().containsKey(verificationResult.getServiceId())) {
      return false;
    }

    if (!appPermissionSummary.getEnvPermissions().containsKey(verificationResult.getEnvId())) {
      return false;
    }

    if (!appPermissionSummary.getWorkflowPermissions().containsKey(verificationResult.getWorkflowId())) {
      return false;
    }

    if (isEmpty(resultFilters)) {
      return true;
    }

    EntityKey entityKey = EntityKey.builder()
                              .appId(verificationResult.getAppId())
                              .serviceId(verificationResult.getServiceId())
                              .envId(verificationResult.getEnvId())
                              .build();
    try {
      Application application = appCache.get(entityKey);
      Service service = serviceCache.get(entityKey);
      Environment environment = environmentCache.get(entityKey);
      for (QLVerificationResultFilter resultFilter : resultFilters) {
        boolean rollbackFilter =
            resultFilter.getRollback() == null || resultFilter.getRollback().equals(verificationResult.isRollback());
        boolean analyzedFilter = false;

        boolean appFilter = resultFilter.getApplication() == null
            || shouldInclude(resultFilter.getApplication().getOperator(), resultFilter.getApplication().getValues(),
                application.getName().toLowerCase());

        boolean serviceFilter = resultFilter.getService() == null
            || shouldInclude(resultFilter.getService().getOperator(), resultFilter.getService().getValues(),
                service.getName().toLowerCase());

        boolean envFilter = resultFilter.getEnvironment() == null
            || shouldInclude(resultFilter.getEnvironment().getOperator(), resultFilter.getEnvironment().getValues(),
                environment.getName().toLowerCase());

        boolean statusFilter = false;

        return rollbackFilter && analyzedFilter && appFilter && serviceFilter && envFilter && statusFilter;
      }
    } catch (ExecutionException e) {
      log.error("error fetching entity", e);
      return false;
    }

    return false;
  }

  private boolean shouldInclude(QLIdOperator operator, String[] values, String matchingValue) {
    switch (operator) {
      case IN:
      case EQUALS:
        for (String value : values) {
          if (matchingValue.equals(value.toLowerCase())) {
            return true;
          }
        }
        break;
      case LIKE:
        for (String value : values) {
          if (matchingValue.contains(value.toLowerCase())) {
            return true;
          }
        }
        break;
      case NOT_IN:
        for (String value : values) {
          if (matchingValue.equals(value.toLowerCase())) {
            return false;
          }
        }
        return true;
      default:
        throw new IllegalStateException("Invalid filter type " + operator);
    }
    return false;
  }

  @Override
  protected void populateFilters(List<QLVerificationResultFilter> filters, Query query) {
    //    applicationQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLVerificationResultFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return null; // QLApplicationFilter.builder().application(idFilter).build();
    }
    throw new InvalidRequestException("Unsupported field " + key + " while generating filter");
  }

  @Value
  @Builder
  private static class EntityKey {
    private String appId;
    private String serviceId;
    private String envId;
  }
}
