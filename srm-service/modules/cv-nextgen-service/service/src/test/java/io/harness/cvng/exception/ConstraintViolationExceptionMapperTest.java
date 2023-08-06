/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.exception;

import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.exception.mapper.ConstraintViolationExceptionMapper;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.List;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConstraintViolationExceptionMapperTest extends CategoryTest {
  private ConstraintViolationExceptionMapper constraintViolationExceptionMapper;

  @Before
  public void setup() {
    constraintViolationExceptionMapper = new ConstraintViolationExceptionMapper();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testToResponse() {
    ConstraintViolationException constraintViolationException = new ConstraintViolationException(new HashSet<>());
    Response response = constraintViolationExceptionMapper.toResponse(constraintViolationException);
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(List.class);
  }
}
