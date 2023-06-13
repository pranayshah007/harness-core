/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.dto;

import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.entityusageactivity.EntityUsageDetail;

import io.swagger.annotations.ApiModel;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@Value
@Builder
@ApiModel("EntityUsageActivityDetail")
public class EntityUsageActivityDetailDTO implements ActivityDetail {
  @NotBlank EntityDetail referredByEntity;
  @NotEmpty String activityStatusMessage;
  EntityUsageDetail usageDetail;
  List<ErrorDetail> errors;
  String errorSummary;
  Map<String, String> activityMetadata;
}
