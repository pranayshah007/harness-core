/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureAppServiceConnectionString implements NestedAnnotationResolver {
  private String name;
  @Expression(ALLOW_SECRETS) private String value;
  private AzureAppServiceConnectionStringType type;

  @JsonProperty(value = "slotSetting") private boolean sticky;
}
