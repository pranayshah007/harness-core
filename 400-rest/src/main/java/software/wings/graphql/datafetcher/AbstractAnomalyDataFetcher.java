/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.graphql.schema.type.aggregation.anomaly.QLAnomalyDataList;
import software.wings.service.impl.FilterLogContext;
import software.wings.service.impl.GroupByLogContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public abstract class AbstractAnomalyDataFetcher<F, G> implements DataFetcher {
  @Inject protected DataFetcherUtils utils;
  private static final String FILTERS = "filters";
  private static final String GROUP_BY = "groupBy";
  private static final String EXCEPTION_MSG_DELIMITER = ";; ";
  protected static final String EXCEPTION_MSG = "An error has occurred. Please contact the Harness support team.";

  public static final int MAX_RETRY = 3;

  protected abstract QLAnomalyDataList fetch(String accountId, List<F> filters, List<G> groupBy);

  @Override
  public QLAnomalyDataList get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {
    QLAnomalyDataList result;
    long startTime = System.currentTimeMillis();
    try {
      Type[] typeArguments = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();
      Class<F> filterClass = (Class<F>) typeArguments[0];
      Class<G> groupByClass = (Class<G>) typeArguments[1];

      final List<F> filters = fetchObject(dataFetchingEnvironment, FILTERS, filterClass);

      final List<G> groupBy = fetchObject(dataFetchingEnvironment, GROUP_BY, groupByClass);

      String accountId = utils.getAccountId(dataFetchingEnvironment);
      final String accountIdDataToFetch = utils.fetchSampleAccountIdIfNoClusterData(accountId);

      try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
           AutoLogContext ignore3 = new FilterLogContext(filterClass.getSimpleName(), OVERRIDE_ERROR);
           AutoLogContext ignore4 = new GroupByLogContext(groupByClass.getSimpleName(), OVERRIDE_ERROR)) {
        QLAnomalyDataList qlData = fetch(accountIdDataToFetch, filters, groupBy);
        result = qlData;
      }
    } catch (WingsException exception) {
      throw new InvalidRequestException(getCombinedErrorMessages(exception), exception, exception.getReportTargets());
    } catch (Exception exception) {
      throw new InvalidRequestException(EXCEPTION_MSG, exception, WingsException.USER_SRE);
    } finally {
      log.info("Time taken for the stats call (abstractStatsDataFetcherWithAggregationList) {}",
          System.currentTimeMillis() - startTime);
    }

    return result;
  }

  private <O> List<O> fetchObject(DataFetchingEnvironment dataFetchingEnvironment, String fieldName, Class<O> klass) {
    Object object = dataFetchingEnvironment.getArguments().get(fieldName);
    if (object == null) {
      return (List<O>) Lists.newArrayList();
    }
    Collection returnCollection = Lists.newArrayList();
    Collection collection = (Collection) object;
    collection.forEach(item -> returnCollection.add(convertToObject(item, klass)));
    return (List<O>) returnCollection;
  }

  private <O> O convertToObject(Object fromValue, Class<O> klass) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(fromValue, klass);
  }

  protected String getAccountId(DataFetchingEnvironment environment) {
    GraphQLContext context = environment.getContext();
    String accountId = context.get("accountId");

    if (isEmpty(accountId)) {
      throw new InvalidRequestException("accountId is null in the environment");
    }

    return accountId;
  }

  private String getCombinedErrorMessages(WingsException ex) {
    List<ResponseMessage> responseMessages =
        ExceptionLogger.getResponseMessageList(ex, WingsException.ReportTarget.GRAPHQL_API);
    return responseMessages.stream()
        .map(ResponseMessage::getMessage)
        .collect(Collectors.joining(EXCEPTION_MSG_DELIMITER));
  }
}
