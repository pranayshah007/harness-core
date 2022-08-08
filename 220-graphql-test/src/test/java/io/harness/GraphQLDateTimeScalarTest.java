/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.graphql.scalar.GraphQLDateTimeScalar.INVALID_INPUT_INSTANCE_TYPE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.graphql.scalar.GraphQLDateTimeScalar;

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingSerializeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

public class GraphQLDateTimeScalarTest extends CategoryTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testParseLiteralWithInvalidInput() {
    Integer invalidInput = 1;
    thrown.expect(CoercingParseLiteralException.class);
    thrown.expectMessage(INVALID_INPUT_INSTANCE_TYPE + GraphQLDateTimeScalar.typeName(invalidInput));
    GraphQLDateTimeScalar.type.getCoercing().parseLiteral(invalidInput);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testParseLiteral() {
    long currentTimeInMillis = System.currentTimeMillis();
    // OffsetDateTime expectedDateTime = GraphQLDateTimeScalar.parseOffsetDateTime(dateAsString, null);
    StringValue dateTime = new StringValue(Long.toString(currentTimeInMillis));
    Long actualOffsetDateTime = (Long) GraphQLDateTimeScalar.type.getCoercing().parseLiteral(dateTime);
    assertThat(actualOffsetDateTime).isEqualTo(currentTimeInMillis);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSerializeWithInvalidInput() {
    Integer invalidInput = 1;
    thrown.expect(CoercingSerializeException.class);
    thrown.expectMessage(INVALID_INPUT_INSTANCE_TYPE + GraphQLDateTimeScalar.typeName(invalidInput));
    GraphQLDateTimeScalar.type.getCoercing().serialize(invalidInput);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSerialize() {
    long currentTimeInMillis = System.currentTimeMillis();
    String expectedFormattedDateTime = Long.toString(currentTimeInMillis);
    Long actualFormattedDateTime = (Long) GraphQLDateTimeScalar.type.getCoercing().serialize(currentTimeInMillis);
    assertThat(currentTimeInMillis).isEqualTo(actualFormattedDateTime);
  }
}
