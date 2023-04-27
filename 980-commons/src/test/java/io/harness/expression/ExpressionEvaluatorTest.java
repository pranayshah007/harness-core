/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.FunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExpressionEvaluatorTest extends CategoryTest {
  @Value
  @Builder
  public static class Person {
    private Address address;
    private int age;
  }

  @Value
  @Builder
  public static class Address {
    private String city;
  }

  Person sam = Person.builder().age(20).address(Address.builder().city("San Francisco").build()).build();
  Person bob = Person.builder().age(40).address(Address.builder().city("New York").build()).build();

  Map<String, Object> persons = new HashMap<String, Object>() {
    {
      put("sam", sam);
      put("bob", bob);
    }
  };

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNormalizeNestedExpression() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    assertThat(expressionEvaluator.normalizeExpression("${empty(\"${target_VAR_281.var1}\")} == false", persons, "bob"))
        .isEqualTo("empty(\"target_VAR_281.var1\") == false");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNormalizeExpression() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    assertThat(expressionEvaluator.normalizeExpression("address.city.length()", persons, "bob"))
        .isEqualTo("address.city.length()");
    assertThat(expressionEvaluator.normalizeExpression("${sam.address.city.length()}", persons, "bob"))
        .isEqualTo("sam.address.city.length()");
    assertThat(expressionEvaluator.normalizeExpression("${address.city.length()}", persons, "bob"))
        .isEqualTo("bob.address.city.length()");

    assertThat(expressionEvaluator.normalizeExpression("${foo}", persons, "bar")).isEqualTo("foo");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstituteWithNull() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    assertThat(expressionEvaluator.substitute(null, persons)).isNull();
    assertThat(expressionEvaluator.substitute(null, persons, (String) null)).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotCrashWithNull() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    Map<String, Object> context = new HashMap<>();
    context.put("name", null);
    assertThat(expressionEvaluator.substitute("${name}", context)).isEqualTo("null");
    assertThat(expressionEvaluator.evaluate("name", context)).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateRecursively() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("name", "bob").put("bob", bob).build();
    assertThat(expressionEvaluator.substitute("${${name}.address.city}", context)).isEqualTo("New York");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldEvaluateVar() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context = ImmutableMap.<String, Object>builder()
                                      .put("vare", ImmutableMap.<String, Object>builder().put("foo", "bar").build())
                                      .build();
    String retValue = expressionEvaluator.substitute("--- ${vare.foo} ---", context);
    assertThat(retValue).isEqualTo("--- bar ---");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateWithNull() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    assertThat(expressionEvaluator.evaluate(null, persons)).isNull();
    assertThat(expressionEvaluator.evaluate(null, persons, null)).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateWithNameValue() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    String expr = "sam.age < 25 && sam.address.city=='San Francisco'";
    Object retValue = expressionEvaluator.evaluate(expr, "sam", sam);
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(Boolean.class);
    assertThat(retValue).isEqualTo(true);

    expr = "sam.getAge() == 20 && sam.getAddress().city.length()==13";
    retValue = expressionEvaluator.evaluate(expr, "sam", sam);
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(Boolean.class);
    assertThat(retValue).isEqualTo(true);

    expr = "sam.address.city";
    retValue = expressionEvaluator.evaluate(expr, "sam", sam);
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(String.class);
    assertThat(retValue).isEqualTo("San Francisco");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateWithMap() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    String expr = "sam.age < bob.age && sam.address.city.length()>bob.address.city.length()";
    Object retValue = expressionEvaluator.evaluate(expr, persons);
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(Boolean.class);
    assertThat(retValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateWithDefaultPrefix() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    String expr = "sam.age < bob.age && sam.address.city.length() > address.city.length()";
    Object retValue = expressionEvaluator.evaluate(expr, persons, "bob");
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(Boolean.class);
    assertThat(retValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldEvaluateWithSubexpression() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    String expr = "sam.age < bob.age && sam.address.city.length() > ${address.city.length()}";
    Object retValue = expressionEvaluator.evaluate(expr, persons, "bob");
    assertThat(retValue).isNotNull();
    assertThat(retValue).isInstanceOf(Boolean.class);
    assertThat(retValue).isEqualTo(true);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstituteWithDefaultPrefix() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    String expr = "${sam.address.city}, ${address.city}";
    Object retValue = expressionEvaluator.substitute(expr, persons, "bob");
    assertThat(retValue).isNotNull();
    assertThat(retValue).isEqualTo("San Francisco, New York");

    assertThat(expressionEvaluator.substitute("${regex.extract('..', address.city)}", persons, null, "bob"))
        .isEqualTo("Ne");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNormalizeExpressionDoNotExpandRegex() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    assertThat(expressionEvaluator.normalizeExpression("${regex.match('', '')}", persons, "bob"))
        .isEqualTo("regex.match('', '')");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSubstituteReExtract() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    assertThat(expressionEvaluator.substitute("${regex.extract('Y..k', ${bob.address.city})}", persons))
        .isEqualTo("York");

    assertThat(expressionEvaluator.substitute("${regex.extract('..', ${bob.address.city})}", persons)).isEqualTo("Ne");

    assertThat(expressionEvaluator.substitute("${regex.extract('has matching', 'text has matching pattern')}", persons))
        .isEqualTo("has matching");

    assertThat(expressionEvaluator.substitute("${regex.extract('has matching', 'no matching pattern')}", persons))
        .isEqualTo("");

    assertThat(
        expressionEvaluator.substitute(
            "${regex.extract('.*?(?=/)', 'atlassian-aid_docker.aid-driving.eu/cloud/services/device-backend:m-201905311225-5569-715e235')}",
            persons))
        .isEqualTo("atlassian-aid_docker.aid-driving.eu");
    assertThat(
        expressionEvaluator.substitute(
            "${regex.extract('(?<=/).*?(?=:)', 'atlassian-aid_docker.aid-driving.eu/cloud/services/device-backend:m-201905311225-5569-715e235')}",
            persons))
        .isEqualTo("cloud/services/device-backend");
    assertThat(
        expressionEvaluator.substitute(
            "${regex.extract('(?<=:).*', 'atlassian-aid_docker.aid-driving.eu/cloud/services/device-backend:m-201905311225-5569-715e235')}",
            persons))
        .isEqualTo("m-201905311225-5569-715e235");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotHangForCircle() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder().put("foo", "${bar}").put("bar", "${foo}").build();
    assertThatThrownBy(() -> expressionEvaluator.substitute("${foo}", context)).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSubstituteDoNotExpandRe() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    assertThat(expressionEvaluator.substitute("${regex.extract('match', 'has matching pattern')}", persons, "bob"))
        .isEqualTo("match");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstituteReReplace() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    assertThat(expressionEvaluator.substitute("${regex.replace('foo', ${bob.address.city}, 'foo bar baz')}", persons))
        .isEqualTo("New York bar baz");

    assertThat(expressionEvaluator.substitute("${regex.replace('foo', 'bar', 'foo bar baz')}", persons))
        .isEqualTo("bar bar baz");

    assertThat(
        expressionEvaluator.substitute("${regex.replace('.*(York)', 'New $1, New $1', ${bob.address.city})}", persons))
        .isEqualTo("New York, New York");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSubstituteReMatch() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("regex", new RegexFunctor());

    assertThat(expressionEvaluator.evaluate("regex.match('has matching', 'text has matching pattern')", persons))
        .isEqualTo(true);

    assertThat(expressionEvaluator.evaluate("regex.match('.*has matching.*', 'text has matching pattern')", persons))
        .isEqualTo(true);

    assertThat(expressionEvaluator.evaluate("regex.match('^has matching$', 'text has matching pattern')", persons))
        .isEqualTo(false);

    assertThat(expressionEvaluator.evaluate("regex.match('has matching', 'no matching pattern')", persons))
        .isEqualTo(false);

    assertThat(expressionEvaluator.evaluate("regex.match('York', ${bob.address.city})", persons)).isEqualTo(true);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldAccessJson() throws IOException {
    URL url = getClass().getResource("/store.json");
    String json = Resources.toString(url, Charsets.UTF_8);

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("json", new JsonFunctor());

    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("body", json).build();

    assertThat(expressionEvaluator.substitute("${json.object(body).store.bicycle.color}", context)).isEqualTo("red");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSelectJsonPath() throws IOException {
    URL url = getClass().getResource("/store.json");
    String json = Resources.toString(url, Charsets.UTF_8);

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("json", new JsonFunctor());

    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("body", json).build();

    assertThat(expressionEvaluator.substitute("${json.select(\"$..book[2]\", body).isbn}", context))
        .isEqualTo("0-553-21311-3");
    assertThat(expressionEvaluator.substitute("${json.select(\"$..book[2].isbn\", body)}", context))
        .isEqualTo("0-553-21311-3");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldListJsonPath() throws IOException {
    URL url = getClass().getResource("/store.json");
    String json = Resources.toString(url, Charsets.UTF_8);

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("json", new JsonFunctor());

    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("body", json).build();

    assertThat(expressionEvaluator.substitute("${json.list(\"store.book\", body).get(2).isbn}", context))
        .isEqualTo("0-553-21311-3");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldFormatAsJson() throws IOException {
    Map<String, Object> context =
        ImmutableMap.<String, Object>builder().put("list", asList("foo", "bar", "baz")).build();

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("json", new JsonFunctor());

    assertThat(expressionEvaluator.substitute("${json.format(list)}", context)).isEqualTo("[\"foo\",\"bar\",\"baz\"]");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSelectXPath() throws IOException {
    URL url = getClass().getResource("/store.xml");
    String json = Resources.toString(url, Charsets.UTF_8);

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("xml", new XmlFunctor());

    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("body", json).build();

    assertThat(expressionEvaluator.substitute("${xml.select(\"/bookstore/book[1]/title\", body)}", context))
        .isEqualTo("Everyday Italian");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotCollideVars() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context = ImmutableMap.<String, Object>builder()
                                      .put("BA1BA", "${AC1AC}")
                                      .put("AC1AC", "${AB1AB}")
                                      .put("AB1AB", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
                                      .build();
    assertThat(expressionEvaluator.substitute("${BA1BA}", context))
        .isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldBeOkWithSameVarsFromDifferentIterations() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder().put("A", "${B}").put("B", "${C}").put("C", "done").build();
    assertThat(expressionEvaluator.substitute("${A}, ${B}", context)).isEqualTo("done, done");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDetectExponentialGrowth() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder()
            .put("B", "${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A} ${A}")
            .put("A", "${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B} ${B}")
            .build();
    assertThatThrownBy(() -> expressionEvaluator.substitute("${A}", context)).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRenderLateBoundValue() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context = new HashMap<>();

    context.put("workflow", new LateBindingValue() {
      @Override
      public Object bind() {
        return ImmutableMap.of("foo", "bar");
      }
    });

    assertThat(expressionEvaluator.substitute("${workflow.foo}", context)).isEqualTo("bar");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldEscapeSpecialCharacters() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    Map<String, Object> context =
        ImmutableMap.<String, Object>builder()
            .put("WINDOWS_RUNTIME_PATH", "%USERPROFILE%\\${app.name}\\${service.name}\\${env.name}\\runtime")
            .build();
    assertThat(expressionEvaluator.substitute("${WINDOWS_RUNTIME_PATH}", context))
        .isEqualTo("%USERPROFILE%\\${app.name}\\${service.name}\\${env.name}\\runtime");
  }

  @Test(expected = FunctorException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testThrowExceptionFunctor() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("thrower", new ExceptionThrowFunctor());
    expressionEvaluator.substitute("${thrower.throwException()}", new HashMap<>());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testVariableResolverTracker() {
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    VariableResolverTracker resolverTracker = new VariableResolverTracker();

    Map<String, Object> context = ImmutableMap.<String, Object>builder()
                                      .put("BA1BA", "${AC1AC}${AC1AC}")
                                      .put("AC1AC", "${AB1AB}${AB1AB}")
                                      .put("AB1AB", "final")
                                      .build();
    assertThat(expressionEvaluator.substitute("${BA1BA}", context, resolverTracker)).isEqualTo("finalfinalfinalfinal");

    assertThat(resolverTracker.getUsage())
        .isEqualTo(ImmutableMap.<String, Map<Object, Integer>>builder()
                       .put("AB1AB", ImmutableMap.<Object, Integer>builder().put("final", Integer.valueOf(4)).build())
                       .put("AC1AC",
                           ImmutableMap.<Object, Integer>builder().put("${AB1AB}${AB1AB}", Integer.valueOf(2)).build())
                       .put("BA1BA",
                           ImmutableMap.<Object, Integer>builder().put("${AC1AC}${AC1AC}", Integer.valueOf(1)).build())
                       .build());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldThrowInValidVariableName() {
    ExpressionEvaluator.isValidVariableName("${invalidVar}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldSelectJsonFromListWithOneElement() {
    String json = "[{\"title\":\"testValue\"}]";

    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    expressionEvaluator.addFunctor("json", new JsonFunctor());

    Map<String, Object> context = ImmutableMap.<String, Object>builder().put("body", json).build();

    assertThat(expressionEvaluator.substitute("${json.select(\".[0].title\", body)}", context)).isEqualTo("testValue");
  }
}
