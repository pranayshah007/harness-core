/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.stencils;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Created by peeyushaggarwal on 6/27/16.
 *
 * @param <T> the type parameter
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface Stencil<T> {
  /**
   * The constant DEFAULT_DISPLAY_ORDER.
   */
  int DEFAULT_DISPLAY_ORDER = 3;

  /**
   * Gets type.
   *
   * @return the type
   */
  String getType();

  /**
   * Gets state class.
   *
   * @return the state class
   */
  @JsonIgnore Class<?> getTypeClass();

  /**
   * Gets type.
   *
   * @return the type
   */
  StencilCategory getStencilCategory();

  /**
   * Gets json schema.
   *
   * @return the json schema
   */
  JsonNode getJsonSchema();

  /**
   * Gets ui schema.
   *
   * @return the ui schema
   */
  Object getUiSchema();

  /**
   * Gets name.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets overriding stencil.
   *
   * @return the overriding stencil
   */
  @JsonIgnore OverridingStencil getOverridingStencil();

  /**
   * Gets type.
   *
   * @return the type
   */
  Integer getDisplayOrder();

  /**
   * New instance.
   *
   * @param id the id
   * @return the state
   */
  @JsonIgnore T newInstance(String id);

  boolean matches(Object context);
}
