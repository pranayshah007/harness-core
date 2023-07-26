/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.data.validator.EntityName;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Data;

@Data
@RecasterAlias("io.harness.freeze.beans.FreezeEntityRule")
public class FreezeEntityRule {
  @NotNull @JsonProperty("entities") List<EntityConfig> entityConfigList;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
}
