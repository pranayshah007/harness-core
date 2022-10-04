/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.beans.SearchFilter.Operator.AND;
import static io.harness.beans.SearchFilter.Operator.ELEMENT_MATCH;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.EXISTS;
import static io.harness.beans.SearchFilter.Operator.NOT_EXISTS;
import static io.harness.beans.SearchFilter.Operator.OR;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.joining;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.mongo.SampleEntity.SampleEntityKeys;
import io.harness.persistence.HQuery;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@UtilityClass
@Slf4j
public class PageController {
  /**
   * Query page request.
   *
   * @param <T>    the generic type
   * @param q      the q
   * @param mapper the mapper
   * @param cls    the cls
   * @param req    the req
   * @return the page response
   */
  @SuppressWarnings("deprecation")
  public static <T> PageResponse<T> queryPageRequest(
      Datastore datastore, Query<T> q, Mapper mapper, Class<T> cls, PageRequest<T> req) {
    q = applyPageRequest(datastore, q, req, cls, mapper);

    PageResponse<T> response = new PageResponse<>(req);

    // when pageRequest has SKIPCOUNT option, we won't do count query to db.
    boolean hasSkipCount = req.getOptions() != null && req.getOptions().contains(PageRequest.Option.SKIPCOUNT);
    if (req.getOptions() == null || req.getOptions().contains(PageRequest.Option.LIST) || hasSkipCount) {
      q.offset(req.getStart());

      int limit = PageRequest.UNLIMITED.equals(req.getLimit()) ? PageRequest.DEFAULT_UNLIMITED : req.getPageSize();
      q.limit(limit);

      List<T> list = q.asList();
      response.setResponse(list);

      if (req.getOptions() == null || (req.getOptions().contains(PageRequest.Option.COUNT) && !hasSkipCount)) {
        // if the size list is less than the limit we know the the total count. There is no need
        // to query for it.
        response.setTotal((list.size() < limit) ? (long) req.getStart() + list.size() : q.count());
      }
    }
    if (req.getOptions() == null || (req.getOptions().contains(PageRequest.Option.COUNT) && !hasSkipCount)) {
      response.setTotal(q.count());
    }

    return response;
  }

  /**
   * Apply page request.
   *
   * @param <T>    the generic type
   * @param query  the query
   * @param req    the req
   * @param cls    the cls
   * @param mapper the mapper
   * @return the query
   */
  @SuppressWarnings("deprecation")
  public static <T> Query<T> applyPageRequest(
      Datastore datastore, Query<T> query, PageRequest<T> req, Class<T> cls, Mapper mapper) {
    if (req == null) {
      return query;
    }

    MappedClass mappedClass = mapper.addMappedClass(cls);

    Preconditions.checkNotNull(query, "Query cannot be null");
    if (req.getUriInfo() != null) {
      req.populateFilters(req.getUriInfo().getQueryParameters(), mappedClass, mapper);
    }

    query = applySearchFilters(datastore, query, req.getFilters(), cls, mapper);

    if (req.getOrders() != null) {
      // Add default sorting if none present
      if (req.getOrders().isEmpty()) {
        SortOrder sortOrder = new SortOrder();
        sortOrder.setFieldName(SampleEntityKeys.createdAt);
        sortOrder.setOrderType(OrderType.DESC);
        req.addOrder(sortOrder);
      }

      query.order(req.getOrders()
                      .stream()
                      .map(so -> (DESC == so.getOrderType()) ? "-" + so.getFieldName() : so.getFieldName())
                      .collect(joining(", ")));
    }

    List<String> fieldsIncluded = req.getFieldsIncluded();
    List<String> fieldsExcluded = req.getFieldsExcluded();

    if (isNotEmpty(fieldsIncluded)) {
      query.retrievedFields(true, fieldsIncluded.toArray(new String[0]));
    } else if (isNotEmpty(fieldsExcluded)) {
      query.retrievedFields(false, fieldsExcluded.toArray(new String[0]));
    }

    return query;
  }

  private static <T> Query<T> applySearchFilters(
      Datastore datastore, Query<T> query, List<SearchFilter> filters, Class<T> cls, Mapper mapper) {
    if (isEmpty(filters)) {
      return query;
    }
    for (SearchFilter filter : filters) {
      if (filter == null || filter.getOp() == null) {
        continue;
      }

      switch (filter.getOp()) {
        case OR:
        case AND:
          List<Criteria> criteria = new ArrayList<>();
          for (Object opFilter : filter.getFieldValues()) {
            if (!(opFilter instanceof SearchFilter)) {
              log.error("OR/AND operator can only be used with SearchFilter values");
              throw new WingsException(ErrorCode.DEFAULT_ERROR_CODE);
            }
            SearchFilter opSearchFilter = (SearchFilter) opFilter;
            if (opSearchFilter.getOp() == OR || opSearchFilter.getOp() == AND
                || opSearchFilter.getOp() == ELEMENT_MATCH) {
              Query<T> tQuery = applySearchFilters(datastore, datastore.createQuery(cls).disableValidation(),
                  Arrays.asList(opSearchFilter), cls, mapper);
              criteria.addAll(((HQuery) tQuery).getChildren());
            } else {
              criteria.add(applyOperator(query.criteria(opSearchFilter.getFieldName()), opSearchFilter));
            }
          }

          if (filter.getOp() == OR) {
            query.or(criteria.toArray(new Criteria[0]));
          } else {
            query.and(criteria.toArray(new Criteria[0]));
          }
          break;
        case ELEMENT_MATCH:
          assertOne(filter.getFieldValues());

          final PageRequest request = (PageRequest) filter.getFieldValues()[0];
          Query elementMatchQuery = datastore.createQuery(cls).disableValidation();
          elementMatchQuery = applyPageRequest(datastore, elementMatchQuery, request, cls, mapper);

          query.field(filter.getFieldName()).elemMatch(elementMatchQuery);
          break;
        case TEXT_SEARCH:
          assertOne(filter.getFieldValues());
          query.search(String.valueOf(filter.getFieldValues()[0]));
          break;
        default:
          FieldEnd<? extends Query<T>> fieldEnd = query.field(filter.getFieldName());
          query = applyOperator(fieldEnd, filter);
          break;
      }
    }
    return query;
  }

  private static <T> T applyOperator(FieldEnd<T> fieldEnd, SearchFilter filter) {
    if (!(filter.getOp() == EXISTS || filter.getOp() == NOT_EXISTS) && isEmpty(filter.getFieldValues())) {
      throw new InvalidRequestException("Unspecified fieldValue for search");
    }
    Operator op = filter.getOp();
    if (op == null) {
      op = EQ;
    }
    switch (op) {
      case LT:
        assertOne(filter.getFieldValues());
        return fieldEnd.lessThan(filter.getFieldValues()[0]);

      case LT_EQ:
        assertOne(filter.getFieldValues());
        return fieldEnd.lessThanOrEq(filter.getFieldValues()[0]);

      case GT:
        assertOne(filter.getFieldValues());
        return fieldEnd.greaterThan(filter.getFieldValues()[0]);

      case GE:
        assertOne(filter.getFieldValues());
        return fieldEnd.greaterThanOrEq(filter.getFieldValues()[0]);

      case EQ:
        assertOne(filter.getFieldValues());
        return fieldEnd.equal(filter.getFieldValues()[0]);

      case NOT_EQ:
        assertOne(filter.getFieldValues());
        return fieldEnd.notEqual(filter.getFieldValues()[0]);

      case CONTAINS:
        assertOne(filter.getFieldValues());
        return fieldEnd.containsIgnoreCase(quote(String.valueOf(filter.getFieldValues()[0])));

      case STARTS_WITH:
        assertOne(filter.getFieldValues());
        return fieldEnd.startsWithIgnoreCase(quote(String.valueOf(filter.getFieldValues()[0])));

      case HAS:
        return fieldEnd.hasAnyOf(asList(filter.getFieldValues()));

      case IN:
        return fieldEnd.hasAnyOf(asList(filter.getFieldValues()));

      case NOT_IN:
        return fieldEnd.hasNoneOf(asList(filter.getFieldValues()));

      case EXISTS:
        assertNone(filter.getFieldValues());
        return fieldEnd.exists();

      case NOT_EXISTS:
        return fieldEnd.doesNotExist();

      case HAS_ALL:
        return fieldEnd.hasAllOf(Arrays.asList(filter.getFieldValues()));

      default:
        unhandled(op);
    }
    return null;
  }

  private static void assertNone(Object[] values) {
    if (isNotEmpty(values)) {
      log.error(
          "Unexpected number of arguments {} in expression when none are expected", values.length, new Exception(""));
    }
  }

  private static void assertOne(Object[] values) {
    int length = values == null ? 0 : values.length;
    if (length < 1) {
      throw new InvalidRequestException("Not enough arguments provided");
    }
    if (length > 1) {
      log.error("Unexpected number of arguments {} in expression when 1 is expected", length, new Exception(""));
    }
  }
}
