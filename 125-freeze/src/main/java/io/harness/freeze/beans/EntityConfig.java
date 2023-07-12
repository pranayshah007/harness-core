/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@RecasterAlias("io.harness.freeze.beans.EntityConfig")
public class EntityConfig {
  @NotNull @JsonProperty("type") FreezeEntityType freezeEntityType;

  @JsonProperty("entityRefs") List<String> entityReference;

  @NotNull FilterType filterType;
}
