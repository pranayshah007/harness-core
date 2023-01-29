/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;

import software.wings.app.MainConfiguration;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.directive.DataFetcherDirective.DataFetcherDirectiveAttributes;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLEnum;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLPageInfo.QLPageInfoBuilder;
import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLEnumOperator;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberOperator;
import software.wings.graphql.schema.type.aggregation.QLStringFilter;
import software.wings.graphql.schema.type.aggregation.QLStringOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.resources.graphql.TriggeredByType;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class DataFetcherUtils {
  public static final String GENERIC_EXCEPTION_MSG = "An error has occurred. Please contact the Harness support team.";
  public static final String NEGATIVE_LIMIT_ARG_MSG = "Limit argument accepts only non negative values";
  public static final String NEGATIVE_OFFSET_ARG_MSG = "Offset argument accepts only non negative values";
  public static final String EXCEPTION_MSG_DELIMITER = ";; ";

  @Inject private MainConfiguration configuration;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject private CEMetadataRecordDao metadataRecordDao;

  private final LoadingCache<String, Boolean> isClusterDataPresentCache =
      Caffeine.newBuilder().expireAfterAccess(15, TimeUnit.SECONDS).build(this::isAnyClusterDataPresent);

  public String fetchSampleAccountIdIfNoClusterData(@NotNull String accountId) {
    if (featureFlagService.isEnabledReloadCache(FeatureName.CE_SAMPLE_DATA_GENERATION, accountId)) {
      log.info("feature flag CE_SAMPLE_DATA_GENERATION enabled: true");
      if (Boolean.FALSE.equals(isClusterDataPresentCache.get(accountId))) {
        return configuration.getCeSetUpConfig().getSampleAccountId();
      }
    }
    log.info("feature flag CE_SAMPLE_DATA_GENERATION enabled: false");
    return accountId;
  }

  public boolean isAnyClusterDataPresent(String accountId) {
    CEMetadataRecord ceMetadataRecord = metadataRecordDao.getByAccountId(accountId);
    boolean isClusterDataPresent = false;
    if (ceMetadataRecord != null) {
      if (ceMetadataRecord.getClusterDataConfigured() != null) {
        isClusterDataPresent = ceMetadataRecord.getClusterDataConfigured();
      }
    }
    return isClusterDataPresent;
  }

  public boolean isSampleClusterDataPresent() {
    String sampleAccountId = configuration.getCeSetUpConfig().getSampleAccountId();
    return isNotEmpty(sampleAccountId) && isAnyClusterDataPresent(sampleAccountId);
  }

  @NotNull
  public Query populateAccountFilter(WingsPersistence wingsPersistence, String accountId, Class entityClass) {
    Query query = wingsPersistence.createQuery(entityClass);
    query.filter(SettingAttributeKeys.accountId, accountId);
    return query;
  }

  public Object getFieldValue(Object obj, String fieldName) {
    try {
      return PropertyUtils.getProperty(obj, fieldName);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      throw new InvalidRequestException(String.format("Failed to obtain the value for field %s", fieldName), exception);
    }
  }

  public Map<String, String> getContextFieldArgsMap(
      Map<String, DataFetcherDirectiveAttributes> parentToContextFieldArgsMap, String parentTypeName) {
    DataFetcherDirectiveAttributes dataFetcherDirectiveAttributes = parentToContextFieldArgsMap.get(parentTypeName);
    Map<String, String> contextFieldArgsMap = null;
    if (dataFetcherDirectiveAttributes != null) {
      contextFieldArgsMap = dataFetcherDirectiveAttributes.getContextFieldArgsMap();
    }
    return contextFieldArgsMap;
  }

  public String getAccountId(DataFetchingEnvironment environment) {
    GraphQLContext context = null;

    if (environment.getContext() instanceof GraphQLContext) {
      context = environment.getContext();
    } else if (environment.getContext() instanceof GraphQLContext.Builder) {
      GraphQLContext.Builder builder = environment.getContext();
      context = builder.build();
    } else {
      throw new WingsException("Unknown context");
    }

    String accountId = context.get("accountId");

    if (isEmpty(accountId)) {
      throw new WingsException("accountId is null in the environment");
    }

    return accountId;
  }

  public interface Controller<T> {
    void populate(T entity);
  }

  public void setStringFilter(FieldEnd<? extends Query<?>> field, String value) {
    field.equal(value);
  }

  public void setStringFilter(FieldEnd<? extends Query<?>> field, QLStringFilter stringFilter) {
    if (stringFilter == null) {
      throw new WingsException("Filter is null");
    }

    QLStringOperator operator = stringFilter.getOperator();
    if (operator == null) {
      throw new WingsException("String Operator cannot be null");
    }

    String[] stringFilterValues = stringFilter.getValues();

    if (isEmpty(stringFilterValues)) {
      throw new WingsException("Values cannot be empty");
    }

    switch (operator) {
      case IN:
        field.in(Arrays.asList(stringFilterValues));
        break;
      case EQUALS:
        if (stringFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator EQUALS");
        }
        field.equal(stringFilterValues[0]);
        break;
      default:
        throw new WingsException("Unknown String operator " + operator);
    }
  }

  public void setIdFilter(FieldEnd<? extends Query<?>> field, QLIdFilter idFilter) {
    if (idFilter == null) {
      return;
    }

    QLIdOperator operator = idFilter.getOperator();
    if (operator == null) {
      throw new WingsException("Id Operator cannot be null");
    }

    String[] idFilterValues = idFilter.getValues();

    if (isEmpty(idFilterValues) && operator != QLIdOperator.NOT_NULL) {
      throw new WingsException("Values cannot be empty");
    }

    switch (operator) {
      case IN:
        field.in(Arrays.asList(idFilterValues));
        break;
      case EQUALS:
        if (idFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator EQUALS");
        }
        field.equal(idFilterValues[0]);
        break;
      case NOT_NULL:
        field.notEqual(null);
        break;
      case NOT_IN:
        field.notIn(Arrays.asList(idFilterValues));
        break;
      case LIKE:
        if (idFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator LIKE");
        }
        field.contains(idFilterValues[0]);
        break;
      default:
        throw new WingsException("Unknown Id operator " + operator);
    }
  }

  public CriteriaContainer getIdFilterCriteria(Query<?> query, String field, QLIdFilter idFilter) {
    if (idFilter == null) {
      return null;
    }

    QLIdOperator operator = idFilter.getOperator();
    if (operator == null) {
      throw new WingsException("Id Operator cannot be null");
    }

    String[] idFilterValues = idFilter.getValues();

    if (isEmpty(idFilterValues) && operator != QLIdOperator.NOT_NULL) {
      throw new WingsException("Values cannot be empty");
    }

    switch (operator) {
      case IN:
        return query.criteria(field).in(Arrays.asList(idFilterValues));
      case EQUALS:
        if (idFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator EQUALS");
        }
        return query.criteria(field).equal(idFilterValues[0]);
      case NOT_NULL:
        return query.criteria(field).notEqual(null);
      case NOT_IN:
        return query.criteria(field).notIn(Arrays.asList(idFilterValues));
      case LIKE:
        if (idFilterValues.length > 1) {
          throw new WingsException("Only one value needs to be inputted for operator LIKE");
        }
        return query.criteria(field).contains(idFilterValues[0]);
      default:
        throw new WingsException("Unknown Id operator " + operator);
    }
  }

  public void setEnumFilter(FieldEnd<? extends Query<?>> field, Filter filter) {
    if (filter == null) {
      return;
    }

    QLEnumOperator operator = (QLEnumOperator) filter.getOperator();
    if (operator == null) {
      throw new WingsException("Enum Operator cannot be null");
    }

    if (isEmpty(filter.getValues())) {
      throw new WingsException("Enum values cannot be empty");
    }

    if (!(filter.getValues() instanceof QLEnum[])) {
      throw new WingsException("Incompatible enum filter value");
    }

    QLEnum[] values = (QLEnum[]) filter.getValues();

    List<String> enumValueList = Arrays.stream(values).map(QLEnum::getStringValue).collect(Collectors.toList());
    switch (operator) {
      case IN:
        field.in(enumValueList);
        break;
      case EQUALS:
        if (enumValueList.size() > 1) {
          throw new WingsException("Only one value needs to be inputted for operator EQUALS");
        }
        field.equal(enumValueList.get(0));
        break;
      default:
        throw new WingsException("Unknown Enum operator " + operator);
    }
  }

  public void setNumberFilter(FieldEnd<? extends Query<?>> field, QLNumberFilter numberFilter) {
    if (numberFilter == null) {
      throw new WingsException("Filter is null");
    }

    QLNumberOperator operator = numberFilter.getOperator();
    if (operator == null) {
      throw new WingsException("Number operator is null");
    }

    Number[] numberFilterValues = numberFilter.getValues();

    if (isEmpty(numberFilterValues)) {
      throw new WingsException("Values cannot be empty");
    }

    switch (operator) {
      case EQUALS:
      case NOT_EQUALS:
      case LESS_THAN:
      case LESS_THAN_OR_EQUALS:
      case GREATER_THAN:
      case GREATER_THAN_OR_EQUALS:
        if (numberFilterValues.length > 1) {
          throw new WingsException("Only one value is expected for operator " + operator);
        }
        break;
      default:
        break;
    }

    switch (operator) {
      case EQUALS:
        field.equal(numberFilterValues[0]);
        break;
      case IN:
        field.in(Arrays.asList(numberFilterValues));
        break;
      case NOT_EQUALS:
        field.notEqual(numberFilterValues[0]);
        break;
      case LESS_THAN:
        field.lessThan(numberFilterValues[0]);
        break;
      case LESS_THAN_OR_EQUALS:
        field.lessThanOrEq(numberFilterValues[0]);
        break;
      case GREATER_THAN:
        field.greaterThan(numberFilterValues[0]);
        break;
      case GREATER_THAN_OR_EQUALS:
        field.greaterThanOrEq(numberFilterValues[0]);
        break;
      default:
        throw new WingsException("Unknown Number operator " + operator);
    }
  }

  public void setTimeFilter(FieldEnd<? extends Query<?>> field, QLTimeFilter timeFilter) {
    if (timeFilter == null) {
      throw new WingsException("Filter is null");
    }

    QLTimeOperator operator = timeFilter.getOperator();
    if (operator == null) {
      throw new WingsException("Time operator is null");
    }

    Number[] values = timeFilter.getValues();

    if (isEmpty(values)) {
      throw new WingsException("Values cannot be empty");
    }

    switch (operator) {
      case EQUALS:
      case BEFORE:
      case AFTER:
        if (values.length > 1) {
          throw new WingsException("Only one value is expected for operator " + operator);
        }
        break;
      default:
        break;
    }

    switch (operator) {
      case EQUALS:
        field.equal(values[0]);
        break;
      case BEFORE:
        field.lessThanOrEq(values[0]);
        break;
      case AFTER:
        field.greaterThanOrEq(values[0]);
        break;
      default:
        throw new WingsException("Unknown Time operator " + operator);
    }
  }

  public <M> QLPageInfo populate(
      QLPageQueryParameters page, Query<M> query, BasicDBObject hint, Controller<M> controller) {
    QLPageInfoBuilder builder = QLPageInfo.builder().limit(page.getLimit()).offset(page.getOffset());

    // A full count of all items that match particular filter could be expensive. This is why using has more feature is
    // recommended over obtaining total. To determine if we have more, we fetch 1 more than the requested.
    final FindOptions options = new FindOptions().limit(page.getLimit() + 1).skip(page.getOffset());
    if (hint != null) {
      options.hint(hint);
    }

    try (HIterator<M> iterator = new HIterator<>(query.fetch(options))) {
      int count = 0;
      for (; count < page.getLimit() && iterator.hasNext(); count++) {
        controller.populate(iterator.next());
      }

      if (page.isHasMoreRequested()) {
        builder.hasMore(iterator.hasNext());
      }

      if (page.isTotalRequested()) {
        // If we need total we still have a way to avoid the second query to mongo. If the data we already fetch is all
        // we have, we can calculate the total instead.
        // But not so fast if we did not fetch even a single record, we might of have offset bigger than the amount of
        // data we have.
        // And of course if we did not skip at all we still can owner this result.
        if (iterator.hasNext() || (count == 0 && options.getSkip() > 0)) {
          builder.total((int) query.count());
        } else {
          builder.total(options.getSkip() + count);
        }
      }
    }
    return builder.build();
  }

  public <M> QLPageInfo populate(QLPageQueryParameters page, Query<M> query, Controller<M> controller) {
    return populate(page, query, null, controller);
  }

  public Calendar getDefaultCalendar() {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
  }

  public void ensureNotBlankField(String field, String fieldName) {
    if (StringUtils.isBlank(field)) {
      throw new InvalidRequestException(format("Field: [%s] should not be blank", fieldName));
    }
  }

  public void ensureNotNullField(Object field, String fieldName) {
    if (field == null) {
      throw new InvalidRequestException(format("Field: [%s] is required", fieldName));
    }
  }

  public Principal getTriggeredBy(DataFetchingEnvironment environment) {
    GraphQLContext context = null;

    if (environment.getContext() instanceof GraphQLContext) {
      context = environment.getContext();
    } else if (environment.getContext() instanceof GraphQLContext.Builder) {
      GraphQLContext.Builder builder = environment.getContext();
      context = builder.build();
    } else {
      throw new WingsException("Unknown context");
    }

    String triggeredById = context.get("triggeredById");

    TriggeredByType triggeredByType = context.get("triggeredByType");

    return Principal.builder().triggeredByType(triggeredByType).triggeredById(triggeredById).build();
  }
}
