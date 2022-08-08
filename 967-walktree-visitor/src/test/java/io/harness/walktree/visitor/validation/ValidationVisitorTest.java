/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.walktree.visitor.validation;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.walktree.beans.DummyVisitorField;
import io.harness.walktree.beans.DummyVisitorFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.visitor.utilities.VisitorDummyElementUtils;
import io.harness.walktree.visitor.validation.modes.ModeType;

import com.google.inject.Injector;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ValidationVisitorTest extends CategoryTest {
  @Mock Injector injector;
  @Mock VisitorFieldRegistry visitorFieldRegistry;

  @InjectMocks
  private ValidationVisitor validationVisitor = spy(new ValidationVisitor(injector, ModeType.PRE_INPUT_SET, true));

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    VisitableFieldProcessor visitableFieldProcessor = Mockito.mock(DummyVisitorFieldProcessor.class);
    when(visitorFieldRegistry.obtainFieldType(DummyVisitorField.class))
        .thenReturn(DummyVisitorField.VISITOR_FIELD_TYPE);
    when(visitorFieldRegistry.obtain(DummyVisitorField.VISITOR_FIELD_TYPE)).thenReturn(visitableFieldProcessor);
    when(visitableFieldProcessor.createNewFieldWithStringValue(any()))
        .thenReturn(DummyVisitorField.builder().value(".dummyTestField").build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddErrorChildrenToCurrentElement() {
    VisitorTestChild visitorTestChild = VisitorTestChild.builder().name("dummyTestPojo").build();
    VisitorTestParent visitorTestParent =
        VisitorTestParent.builder().name("parent").visitorTestChild(visitorTestChild).build();
    VisitorDummyElementUtils.addToDummyElementMap(
        validationVisitor.getElementToDummyElementMap(), visitorTestChild, VisitorTestChild.builder().build());
    when(validationVisitor.getHelperClass(visitorTestParent)).thenReturn(new VisitorTestParentVisitorHelper());
    validationVisitor.addErrorChildrenToCurrentElement(visitorTestParent);

    assertThat(validationVisitor.getElementToDummyElementMap().size()).isEqualTo(2);
    assertThat(validationVisitor.getElementToDummyElementMap().containsKey(visitorTestParent)).isEqualTo(true);
    assertThat(validationVisitor.getElementToDummyElementMap().get(visitorTestParent))
        .isEqualTo(VisitorTestParent.builder().visitorTestChild(VisitorTestChild.builder().build()).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateAnnotations() {
    VisitorTestChild visitorTestChild = VisitorTestChild.builder().name("dummyTestPojo").build();
    VisitorTestParent visitorTestParent = VisitorTestParent.builder().visitorTestChild(visitorTestChild).build();
    validationVisitor.getElementToDummyElementMap().put(visitorTestChild, VisitorTestChild.builder().build());
    when(validationVisitor.getHelperClass(visitorTestParent)).thenReturn(new VisitorTestParentVisitorHelper());
    VisitorTestParent dummy = VisitorTestParent.builder().build();
    validationVisitor.validateAnnotations(visitorTestParent, dummy);

    assertThat(validationVisitor.getElementToDummyElementMap().size()).isEqualTo(1);
    assertThat(validationVisitor.uuidToVisitorResponse.size()).isEqualTo(2);
    DummyVisitorField dummyTestField = dummy.getVisitorField();
    Assertions.assertThat(dummyTestField.getValue()).isEqualTo(".dummyTestField");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testVisitElement() {
    VisitorTestChild visitorTestChild = VisitorTestChild.builder().name("dummyTestPojo").build();
    VisitorTestParent visitorTestParent = VisitorTestParent.builder().visitorTestChild(visitorTestChild).build();
    validationVisitor.getElementToDummyElementMap().put(visitorTestChild, VisitorTestChild.builder().build());
    when(validationVisitor.getHelperClass(visitorTestParent)).thenReturn(new VisitorTestParentVisitorHelper());
    validationVisitor.visitElement(visitorTestParent);

    assertThat(validationVisitor.getElementToDummyElementMap().size()).isEqualTo(2);
    assertThat(validationVisitor.uuidToVisitorResponse.size()).isEqualTo(2);
    DummyVisitorField dummyTestField =
        ((VisitorTestParent) validationVisitor.getElementToDummyElementMap().get(visitorTestParent)).getVisitorField();
    Assertions.assertThat(dummyTestField.getValue()).isEqualTo(".dummyTestField");
  }
}
