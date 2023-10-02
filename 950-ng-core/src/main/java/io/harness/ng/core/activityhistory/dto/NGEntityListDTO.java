/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.dto;

import io.harness.EntityType;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel("NGEntityList")
@AllArgsConstructor
@Schema(name = "NGEntityList", description = "This is the view of the NG Entity Types")
public class NGEntityListDTO {
  List<EntityType> entityTypeList;
}
