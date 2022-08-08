/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.beans;

import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SORT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.PAGE_KEY;
import static io.harness.NGResourceFilterConstants.SIZE_KEY;
import static io.harness.NGResourceFilterConstants.SORT_KEY;

import io.harness.beans.SortOrder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.validation.constraints.Max;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
  @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(PAGE_KEY) @DefaultValue("0") int pageIndex;
  @Parameter(description = SIZE_PARAM_MESSAGE + "(max 100)"
          + "Default Value: 50")
  @QueryParam(SIZE_KEY)
  @DefaultValue("50")
  @Max(100)
  int pageSize;
  @Parameter(description = SORT_PARAM_MESSAGE) @QueryParam(SORT_KEY) List<SortOrder> sortOrders;
}
