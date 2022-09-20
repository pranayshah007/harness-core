/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import lombok.extern.log4j.Log4j;
import org.apache.commons.lang3.EnumUtils;

/**
 * SortOrder bean class.
 *
 * @author Rishi
 */
@Log4j
public class SortOrder {
  public static final String FIELD_NAME_ORDER_TYPE_SEPARATOR = ",";

  private String fieldName;
  private OrderType orderType;

  public SortOrder(String fieldNameOrderType) {
    try {
      if (isNotEmpty(fieldNameOrderType)) {
        String[] sortInfo = fieldNameOrderType.split(FIELD_NAME_ORDER_TYPE_SEPARATOR);
        if (isEmpty(sortInfo[0].trim())) {
          throw new IllegalArgumentException("fieldName cannot be blank");
        }
        this.fieldName = sortInfo[0];
        this.orderType = sortInfo.length > 1 ? EnumUtils.getEnumIgnoreCase(OrderType.class, sortInfo[1], OrderType.DESC)
                                             : OrderType.DESC;
      }
    } catch (Exception exception) {
      log.warn(String.format("Unable to set SortOrder from: %s", fieldNameOrderType), exception);
    }
  }

  public SortOrder() {}

  /**
   * Gets field name.
   *
   * @return the field name
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Sets field name.
   *
   * @param fieldName the field name
   */
  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  /**
   * Gets order type.
   *
   * @return the order type
   */
  public OrderType getOrderType() {
    return orderType;
  }

  /**
   * Sets order type.
   *
   * @param orderType the order type
   */
  public void setOrderType(OrderType orderType) {
    this.orderType = orderType;
  }

  /**
   * The Enum OrderType.
   */
  public enum OrderType {
    /**
     * Asc order type.
     */
    ASC,
    /**
     * Desc order type.
     */
    DESC
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String fieldName;
    private OrderType orderType;

    private Builder() {}

    /**
     * A sort order builder.
     *
     * @return the builder
     */
    public static Builder aSortOrder() {
      return new Builder();
    }

    /**
     * With field builder.
     *
     * @param fieldName the field name
     * @param orderType the order type
     * @return the builder
     */
    public Builder withField(String fieldName, OrderType orderType) {
      this.fieldName = fieldName;
      this.orderType = orderType;
      return this;
    }

    /**
     * Build sort order.
     *
     * @return the sort order
     */
    public SortOrder build() {
      SortOrder sortOrder = new SortOrder();
      sortOrder.setFieldName(fieldName);
      sortOrder.setOrderType(orderType);
      return sortOrder;
    }
  }
}
