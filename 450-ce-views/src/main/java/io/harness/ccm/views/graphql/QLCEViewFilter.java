/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import io.leangen.graphql.annotations.GraphQLNonNull;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QLCEViewFilter {
  @GraphQLNonNull QLCEViewFieldInput field;
  @ApiModelProperty(allowableValues = "NOT_IN,IN,EQUALS,NOT_NULL,NULL,LIKE")
  @GraphQLNonNull
  QLCEViewFilterOperator operator;
  @GraphQLNonNull String[] values;
}
