/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.eventsframework.consumer.Message;
import io.harness.resourcegroup.beans.ValidatorType;
import io.harness.resourcegroup.v2.model.AttributeFilter;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(PL)
public interface Resource {
  String getType();

  /*
  Redundant method, as scope level information is available from getSelectorKind
  TODO: Remove this method
   */
  Set<ScopeLevel> getValidScopeLevels();

  Optional<String> getEventFrameworkEntityType();

  ResourceInfo getResourceInfoFromEvent(Message message);

  List<Boolean> validate(List<String> resourceIds, Scope scope);

  Map<ScopeLevel, EnumSet<ValidatorType>> getSelectorKind();

  boolean isValidAttributeFilter(AttributeFilter attributeFilter);
}
