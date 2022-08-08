/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.registrars;

import io.harness.walktree.beans.DummyVisitorField;
import io.harness.walktree.beans.DummyVisitorFieldProcessor;
import io.harness.walktree.registries.registrars.VisitableFieldRegistrar;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldType;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class WalkTreeVisitorFieldRegistrar implements VisitableFieldRegistrar {
  @Inject private Injector injector;

  @Override
  public void register(Set<Pair<VisitorFieldType, VisitableFieldProcessor<?>>> fieldClasses) {
    fieldClasses.add(
        Pair.of(DummyVisitorField.VISITOR_FIELD_TYPE, injector.getInstance(DummyVisitorFieldProcessor.class)));
  }

  @Override
  public void registerFieldTypes(Set<Pair<Class<? extends VisitorFieldWrapper>, VisitorFieldType>> fieldTypeClasses) {
    fieldTypeClasses.add(Pair.of(DummyVisitorField.class, DummyVisitorField.VISITOR_FIELD_TYPE));
  }
}
