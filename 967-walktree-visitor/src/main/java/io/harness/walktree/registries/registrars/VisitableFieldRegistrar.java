/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.registries.registrars;

import io.harness.registries.Registrar;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldType;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;

import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public interface VisitableFieldRegistrar extends Registrar<VisitorFieldType, VisitableFieldProcessor<?>> {
  void register(Set<Pair<VisitorFieldType, VisitableFieldProcessor<?>>> fieldClasses);
  void registerFieldTypes(Set<Pair<Class<? extends VisitorFieldWrapper>, VisitorFieldType>> fieldTypeClasses);
}
