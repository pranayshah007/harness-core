/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.inputset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetErrorResponse")
@Schema(name = "InputSetErrorWrapper",
    description = "This is the wrapper of list of errors for a field while saving an Input Set")
public class InputSetErrorResponseDTOPMS {
  @Schema(
      name = "InputSetError", description = "This contains the list of errors for a field while saving an Input Set")
  @Builder.Default
  List<InputSetErrorDTOPMS> errors = new ArrayList<>();
}
