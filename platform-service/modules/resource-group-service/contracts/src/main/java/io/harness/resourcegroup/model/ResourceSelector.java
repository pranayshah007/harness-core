/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StaticResourceSelector.class, name = "StaticResourceSelector")
  , @JsonSubTypes.Type(value = DynamicResourceSelector.class, name = "DynamicResourceSelector"),
      @JsonSubTypes.Type(value = ResourceSelectorByScope.class, name = "ResourceSelectorByScope")
})
@JsonIgnoreProperties(ignoreUnknown = true)
/*This class cannot be moved since it is missing type alias*/
public interface ResourceSelector {}
