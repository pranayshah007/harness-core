/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SortOrder.Builder.aSortOrder;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SearchFilter.SearchFilterBuilder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.data.parser.Parser;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.mongodb.BasicDBObject;
import dev.morphia.Key;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.MappedField;
import dev.morphia.mapping.Mapper;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

/**
 * PageRequest bean class.
 *
 * @param <T> the generic type
 * @author Rishi
 */
public class PageRequest<T> {
  public static final String UNLIMITED = "UNLIMITED";
  public static final int DEFAULT_UNLIMITED = 1000;
  public static final String LIMIT_2K_PAGE_SIZE = "2000";
  public static final int DEFAULT_PAGE_SIZE = 50;

  private static Pattern searchField = Pattern.compile("search\\[[0-9]+]\\[field]");

  @JsonIgnore Class<T> persistentClass;
  @DefaultValue("0") @QueryParam("offset") private String offset;
  private int start;
  @QueryParam("limit") private String limit;
  private List<SearchFilter> filters = new ArrayList<>();
  private List<SortOrder> orders = new ArrayList<>();

  @QueryParam("fieldsIncluded") private List<String> fieldsIncluded = new ArrayList<>();

  @QueryParam("fieldsExcluded") private List<String> fieldsExcluded = new ArrayList<>();

  @JsonIgnore @Context private UriInfo uriInfo;

  @JsonIgnore @Context private ContainerRequestContext requestContext; // TODO: remove UriInfo

  @JsonIgnore private boolean isOr;

  @JsonIgnore private List<Option> options;
  @JsonIgnore private BasicDBObject indexHint;
  /**
   * Instantiates a new page request.
   */
  public PageRequest() {}

  /**
   * Copy Constructor for PageRequest.
   *
   * @param req PageRequest to copy.
   */
  public PageRequest(PageRequest<T> req) {
    this.offset = req.offset;
    this.start = req.start;
    this.limit = req.limit;
    this.filters = req.filters;
    this.orders = req.orders;
    this.isOr = req.isOr;
    this.fieldsIncluded = req.fieldsIncluded;
    this.fieldsExcluded = req.fieldsExcluded;
  }

  /**
   * Gets uri info.
   *
   * @return the uri info
   */
  public UriInfo getUriInfo() {
    return uriInfo;
  }

  /**
   * Sets uri info.
   *
   * @param uriInfo the uri info
   */
  public void setUriInfo(UriInfo uriInfo) {
    this.uriInfo = uriInfo;
  }

  /**
   * Gets offset.
   *
   * @return the offset
   */
  public String getOffset() {
    return offset;
  }

  /**
   * Sets offset.
   *
   * @param offset the offset
   */
  public void setOffset(String offset) {
    this.offset = offset;
  }

  /**
   * Gets limit.
   *
   * @return the limit
   */
  public String getLimit() {
    return limit;
  }

  /**
   * Sets limit.
   *
   * @param limit the limit
   */
  public void setLimit(String limit) {
    this.limit = limit;
  }

  /**
   * Gets page size.
   *
   * @return the page size
   */
  public int getPageSize() {
    return Parser.asInt(limit, DEFAULT_UNLIMITED);
  }

  public void setPageSize(int pageSize) {
    // nothing to do
  }

  /**
   * Gets start.
   *
   * @return the start
   */
  public int getStart() {
    return Parser.asInt(offset);
  }

  /**
   * Is or boolean.
   *
   * @return the boolean
   */
  public boolean isOr() {
    return isOr;
  }

  /**
   * Sets or.
   *
   * @param isOr the is or
   */
  public void setOr(boolean isOr) {
    this.isOr = isOr;
  }

  /**
   * Gets fields included.
   *
   * @return the fields included
   */
  public List<String> getFieldsIncluded() {
    return new ArrayList<>(fieldsIncluded);
  }

  /**
   * Sets fields included.
   *
   * @param fieldsIncluded the fields included
   */
  public void setFieldsIncluded(List<String> fieldsIncluded) {
    this.fieldsIncluded = fieldsIncluded;
  }

  /**
   * Adds the fields included.
   *
   * @param fieldsIncluded the fields included
   */
  public PageRequest<T> addFieldsIncluded(String fieldsIncluded) {
    this.fieldsIncluded.add(fieldsIncluded);
    return this;
  }

  /**
   * Gets fields excluded.
   *
   * @return the fields excluded
   */
  public List<String> getFieldsExcluded() {
    return new ArrayList<>(fieldsExcluded);
  }

  /**
   * Sets fields excluded.
   *
   * @param fieldsExcluded the fields excluded
   */
  public void setFieldsExcluded(List<String> fieldsExcluded) {
    this.fieldsExcluded = fieldsExcluded;
  }

  /**
   * Adds the fields excluded.
   *
   * @param fieldsExcluded the fields excluded
   */
  public void addFieldsExcluded(String fieldsExcluded) {
    this.fieldsExcluded.add(fieldsExcluded);
  }

  public List<Option> getOptions() {
    return options;
  }

  public BasicDBObject getIndexHint() {
    return indexHint;
  }

  public void setIndexHint(BasicDBObject indexHint) {
    this.indexHint = indexHint;
  }

  public void setOptions(List<Option> options) {
    this.options = options;
  }

  public void populateFilters(MultivaluedMap<String, String> map, MappedClass mappedClass, Mapper mapper) {
    int fieldCount = 0;
    int orderCount = 0;
    for (Entry<String, List<String>> entry : map.entrySet()) {
      String key = entry.getKey();
      if (searchField.matcher(key).matches()) {
        fieldCount++;
      } else if (key.startsWith("sort") && key.endsWith("[field]")) {
        orderCount++;
      } else if (key.equals("searchLogic")) {
        String searchLogic = Optional.of(map.getFirst("searchLogic")).orElse("").trim();
        if (searchLogic.equals("OR")) {
          isOr = true;
        }
      } else if (!(key.startsWith("search") || key.startsWith("sort") || mappedClass.getMappedField(key) == null)) {
        Operator op = Operator.IN;
        if (map.get(key).size() == 1) {
          op = Operator.EQ;
        }
        if (mappedClass.getMappedField(key).isReference()) {
          try {
            Class type;
            MappedField mappedField = mappedClass.getMappedField(key);
            Constructor constructor = null;
            if (mappedField.isMultipleValues()) {
              type = mappedField.getSubClass();
              constructor = type.getDeclaredConstructor();
            } else {
              constructor = mappedField.getCTor();
            }
            Object referenceObject = constructor.getDeclaringClass().newInstance();
            String collection = mapper.getCollectionName(referenceObject);
            addFilter(
                key, op, map.get(key).stream().map(s -> new Key(referenceObject.getClass(), collection, s)).toArray());
          } catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
            addFilter(key, op, map.get(key).toArray());
          }
        } else {
          if (asList(Boolean.TYPE).contains(mappedClass.getMappedField(key).getType())) {
            addFilter(key, op, map.get(key).stream().map(Boolean::parseBoolean).toArray());
          } else {
            addFilter(key, op, map.get(key).toArray());
          }
        }
      }
    }
    for (int index = 0; index < fieldCount; index++) {
      String key = "search[" + index + "]";
      final String name = map.getFirst(key + "[field]");
      if (name == null) {
        throw new InvalidRequestException(format("field name for index %d is missing", index));
      }

      final SearchFilterBuilder filterBuilder = SearchFilter.builder();
      filterBuilder.fieldName(name);
      Operator op = map.containsKey(key + "[op]") ? Operator.valueOf(map.getFirst(key + "[op]")) : Operator.EQ;
      filterBuilder.op(op);

      if (op == Operator.ELEMENT_MATCH) {
        final String prefix = key + "[value]";
        final MultivaluedMap<String, String> subMap = new MultivaluedHashMap<>();

        map.entrySet()
            .stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .forEach(entry -> subMap.put("search" + entry.getKey().substring(prefix.length()), entry.getValue()));

        final PageRequest pageRequest = aPageRequest().build();
        pageRequest.populateFilters(subMap, mappedClass, mapper);
        filterBuilder.fieldValues(new Object[] {pageRequest});
      } else if (map.containsKey(key + "[value]")) {
        MappedField mappedField = mappedClass == null ? null : mappedClass.getMappedField(name);
        if (mappedField != null
            && asList(Long.TYPE, Integer.TYPE, Short.TYPE, Byte.TYPE).contains(mappedField.getType())) {
          filterBuilder.fieldValues(new Object[] {Long.parseLong(map.getFirst(key + "[value]"))});
        } else if (mappedField != null && asList(Boolean.TYPE).contains(mappedField.getType())) {
          filterBuilder.fieldValues(new Object[] {Boolean.parseBoolean(map.getFirst(key + "[value]"))});
        } else {
          filterBuilder.fieldValues(map.get(key + "[value]").toArray());
        }
      }
      filters.add(filterBuilder.build());
    }
    for (int index = 0; index < orderCount; index++) {
      String key = "sort[" + index + "]";
      SortOrder order = new SortOrder();
      order.setFieldName(map.getFirst(key + "[field]"));
      order.setOrderType(OrderType.valueOf(map.getFirst(key + "[direction]").toUpperCase()));
      orders.add(order);
    }
  }

  /**
   * Gets filters.
   *
   * @return the filters
   */
  public List<SearchFilter> getFilters() {
    return new ArrayList<>(filters);
  }

  /**
   * Sets filters.
   *
   * @param filters the filters
   */
  public void setFilters(List<SearchFilter> filters) {
    this.filters = filters;
  }

  /**
   * Adds the filter.
   *
   * @param filter the filter
   */
  public void addFilter(SearchFilter filter) {
    this.filters.add(filter);
  }

  /**
   * Gets orders.
   *
   * @return the orders
   */
  public List<SortOrder> getOrders() {
    return new ArrayList<>(orders);
  }

  /**
   * Sets orders.
   *
   * @param orders the orders
   */
  public void setOrders(List<SortOrder> orders) {
    this.orders = orders;
  }

  /**
   * Adds the order.
   *
   * @param order the order
   */
  public void addOrder(SortOrder order) {
    this.orders.add(order);
  }

  /**
   * Add an order.
   *
   * @param fieldName the field name
   * @param orderType the order type
   */
  public PageRequest<T> addOrder(String fieldName, OrderType orderType) {
    this.orders.add(aSortOrder().withField(fieldName, orderType).build());
    return this;
  }

  /**
   * Creates and adds a new search filter to PageRequest.
   *
   * @param fieldName  name of field to apply filter on.
   * @param op         Operator for filter.
   */
  public PageRequest<T> addFilter(String fieldName, Operator op, Object... fieldValues) {
    this.filters.add(SearchFilter.builder().fieldName(fieldName).op(op).fieldValues(fieldValues).build());
    return this;
  }

  public PageRequest<T> copy() {
    PageRequest<T> clone = new PageRequest<>();
    clone.persistentClass = this.persistentClass;
    clone.offset = this.offset;
    clone.start = this.start;
    clone.limit = this.limit;
    clone.filters = this.filters;
    clone.orders = this.orders;
    clone.fieldsExcluded = this.fieldsExcluded;
    clone.fieldsIncluded = this.fieldsIncluded;
    clone.uriInfo = this.uriInfo;
    clone.requestContext = this.requestContext;
    clone.isOr = this.isOr;
    clone.options = this.options;
    clone.indexHint = this.indexHint;
    return clone;
  }

  public PageRequest<T> deepCopy() {
    List<SearchFilter> filtersCopy = new ArrayList<>();
    filtersCopy.addAll(this.filters);
    List<String> fieldsExcludedsCopy = new ArrayList<>();
    fieldsExcludedsCopy.addAll(this.fieldsExcluded);
    List<String> fieldsIncludedCopy = new ArrayList<>();
    fieldsIncludedCopy.addAll(this.fieldsIncluded);
    List<SortOrder> ordersCopy = new ArrayList<>();
    ordersCopy.addAll(this.orders);
    List<PageRequest.Option> optionsCopy = new ArrayList<>();
    ordersCopy.addAll(this.orders);

    PageRequest<T> clone = new PageRequest<>();
    clone.persistentClass = this.persistentClass;
    clone.offset = this.offset;
    clone.start = this.start;
    clone.limit = this.limit;
    clone.filters = filtersCopy;
    clone.orders = orders;
    clone.fieldsExcluded = fieldsExcludedsCopy;
    clone.fieldsIncluded = fieldsIncludedCopy;
    clone.uriInfo = this.uriInfo;
    clone.requestContext = this.requestContext;
    clone.isOr = this.isOr;
    clone.options = optionsCopy;
    clone.indexHint = this.indexHint;
    return clone;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  @lombok.Generated
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    PageRequest<?> that = (PageRequest<?>) obj;
    return start == that.start && isOr == that.isOr && Objects.equal(offset, that.offset)
        && Objects.equal(limit, that.limit) && Objects.equal(filters, that.filters)
        && Objects.equal(orders, that.orders) && Objects.equal(fieldsIncluded, that.fieldsIncluded)
        && Objects.equal(fieldsExcluded, that.fieldsExcluded);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  @lombok.Generated
  public int hashCode() {
    return Objects.hashCode(offset, start, limit, filters, orders, fieldsIncluded, fieldsExcluded, isOr);
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  @lombok.Generated
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("isOr", isOr)
        .add("uriInfo", uriInfo)
        .add("fieldsExcluded", fieldsExcluded)
        .add("fieldsIncluded", fieldsIncluded)
        .add("orders", orders)
        .add("filters", filters)
        .add("limit", limit)
        .add("start", start)
        .add("offset", offset)
        .add("indexHint", indexHint)
        .toString();
  }

  public enum Option { COUNT, LIST, SKIPCOUNT }
  /**
   * The enum Page request type.
   */
  public enum PageRequestType {
    /**
     * List without app id page request type.
     */
    LIST_WITHOUT_APP_ID,
    /**
     * List without env id page request type.
     */
    LIST_WITHOUT_ENV_ID,
    /**
     * Other page request type.
     */
    OTHER
  }

  /**
   * The Class PageRequestBuilder.
   */
  public static final class PageRequestBuilder {
    private String offset;
    private String limit;
    private List<SearchFilter> filters = new ArrayList<>();
    private List<SortOrder> orders = new ArrayList<>();
    private List<String> fieldsIncluded = new ArrayList<>();
    private List<String> fieldsExcluded = new ArrayList<>();
    private UriInfo uriInfo;
    private BasicDBObject indexHint;

    private PageRequestBuilder() {}

    /**
     * A page request.
     *
     * @return the builder
     */
    public static PageRequestBuilder aPageRequest() {
      return new PageRequestBuilder();
    }

    /**
     * With offset.
     *
     * @param offset the offset
     * @return the builder
     */
    public PageRequestBuilder withOffset(String offset) {
      this.offset = offset;
      return this;
    }

    /**
     * With limit.
     *
     * @param limit the limit
     * @return the builder
     */
    public PageRequestBuilder withLimit(String limit) {
      this.limit = limit;
      return this;
    }

    /**
     * Adds the filter.
     *
     * @param filter the filter
     * @return the builder
     */
    public PageRequestBuilder addFilter(SearchFilter filter) {
      this.filters.add(filter);
      return this;
    }

    /**
     * Add filter builder.
     *
     * @param fieldName   the field name
     * @param op          the op
     * @param fieldValues the field values
     * @return the builder
     */
    public PageRequestBuilder addFilter(String fieldName, Operator op, Object... fieldValues) {
      filters.add(SearchFilter.builder().fieldName(fieldName).op(op).fieldValues(fieldValues).build());
      return this;
    }

    /**
     * Adds the order.
     *
     * @param order the order
     * @return the builder
     */
    public PageRequestBuilder addOrder(SortOrder order) {
      this.orders.add(order);
      return this;
    }

    /**
     * Add order builder.
     *
     * @param fieldName the field name
     * @param orderType the order type
     * @return the builder
     */
    public PageRequestBuilder addOrder(String fieldName, OrderType orderType) {
      this.orders.add(aSortOrder().withField(fieldName, orderType).build());
      return this;
    }

    /**
     * With fields included.
     *
     * @param fieldsIncluded the fields included
     * @return the builder
     */
    public PageRequestBuilder addFieldsIncluded(String... fieldsIncluded) {
      this.fieldsIncluded.addAll(asList(fieldsIncluded));
      return this;
    }

    /**
     * With fields excluded.
     *
     * @param fieldsExcluded the fields excluded
     * @return the builder
     */
    public PageRequestBuilder addFieldsExcluded(String... fieldsExcluded) {
      this.fieldsExcluded.addAll(asList(fieldsExcluded));
      return this;
    }

    /**
     * With uri info.
     *
     * @param uriInfo the uri info
     * @return the builder
     */
    public PageRequestBuilder withUriInfo(UriInfo uriInfo) {
      this.uriInfo = uriInfo;
      return this;
    }

    public PageRequestBuilder withIndexHint(BasicDBObject hint) {
      this.indexHint = hint;
      return this;
    }

    /**
     * Builds the.
     *
     * @return the page request
     */
    public PageRequest build() {
      PageRequest pageRequest = new PageRequest();
      pageRequest.setOffset(offset);
      pageRequest.setLimit(limit);
      pageRequest.setFilters(filters);
      pageRequest.setOrders(orders);
      pageRequest.setFieldsIncluded(fieldsIncluded);
      pageRequest.setFieldsExcluded(fieldsExcluded);
      pageRequest.setUriInfo(uriInfo);
      pageRequest.setIndexHint(indexHint);
      return pageRequest;
    }
  }
}
