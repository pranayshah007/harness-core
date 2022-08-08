/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.walktree.registries.visitorfield;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.WalkTreeTestBase;
import io.harness.category.element.UnitTests;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VisitorFieldRegistryTest extends WalkTreeTestBase {
  @Inject Injector injector;
  @Inject VisitorFieldRegistry visitorFieldRegistry;

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestRegistry() {
    DummyVisitorTestField visitorField = new DummyVisitorTestField();
    visitorFieldRegistry.register(
        visitorField.getVisitorFieldType(), injector.getInstance(DummyVisitorTestFieldProcessor.class));
    VisitableFieldProcessor<?> processor = visitorFieldRegistry.obtain(visitorField.getVisitorFieldType());
    assertThat(processor).isNotNull();

    assertThatThrownBy(()
                           -> visitorFieldRegistry.register(visitorField.getVisitorFieldType(),
                               injector.getInstance(DummyVisitorTestFieldProcessor.class)))
        .isInstanceOf(DuplicateRegistryException.class);

    assertThatThrownBy(() -> visitorFieldRegistry.obtain(VisitorFieldType.builder().type("IGNORE").build()))
        .isInstanceOf(UnregisteredKeyAccessException.class);

    visitorFieldRegistry.registerFieldTypes(DummyVisitorTestField.class, visitorField.getVisitorFieldType());
    VisitorFieldType fieldType = visitorFieldRegistry.obtainFieldType(DummyVisitorTestField.class);
    assertThat(fieldType).isEqualTo(visitorField.getVisitorFieldType());

    assertThatThrownBy(
        () -> visitorFieldRegistry.registerFieldTypes(DummyVisitorTestField.class, visitorField.getVisitorFieldType()))
        .isInstanceOf(DuplicateRegistryException.class);

    assertThatThrownBy(() -> visitorFieldRegistry.obtainFieldType(VisitorFieldWrapper.class))
        .isInstanceOf(UnregisteredKeyAccessException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetType() {
    assertThat(visitorFieldRegistry.getType()).isEqualTo("VISITOR_FIELD");
  }

  private static class DummyVisitorTestField implements VisitorFieldWrapper {
    @Override
    public VisitorFieldType getVisitorFieldType() {
      return VisitorFieldType.builder().type("DUMMY").build();
    }
  }

  private static class DummyVisitorTestFieldProcessor implements VisitableFieldProcessor<DummyVisitorTestField> {
    @Override
    public String getExpressionFieldValue(DummyVisitorTestField actualField) {
      return null;
    }

    @Override
    public DummyVisitorTestField updateCurrentField(
        DummyVisitorTestField actualField, DummyVisitorTestField overrideField) {
      return null;
    }

    @Override
    public DummyVisitorTestField cloneField(DummyVisitorTestField actualField) {
      return null;
    }

    @Override
    public String getFieldWithStringValue(DummyVisitorTestField actualField) {
      return null;
    }

    @Override
    public DummyVisitorTestField createNewFieldWithStringValue(String stringValue) {
      return null;
    }

    @Override
    public boolean isNull(DummyVisitorTestField actualField) {
      return false;
    }
  }
}
