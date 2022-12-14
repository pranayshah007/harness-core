/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.usage.params;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.usage.params.filter.FilterParams;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultPageableUsageRequestParams implements PageableUsageRequestParams {
  Pageable pageRequest;
  FilterParams filterParams;
}
