/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QLCEViewFilterWrapper {
  QLCEViewFilter idFilter;
  QLCEViewTimeFilter timeFilter;
  QLCEViewMetadataFilter viewMetadataFilter;
  QLCEViewRule ruleFilter;
  @JsonIgnore QLCEInExpressionFilter inExpressionFilter;
}
