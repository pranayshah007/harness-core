/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.encoding.EncodingUtils;
import io.harness.expression.ExpressionEvaluatorContext;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class Base64ManagerFunctorTest extends CategoryTest {
  Set<String> expressionFunctors = Stream.of("functor1", "functor2").collect(Collectors.toSet());
  String contentToBeFormatted = "before %s between %s after";
  String evaluatorHash1 = "evaluatorHash1";
  String evaluatorValue1 = "evaluatorValue1";
  String evaluatorHash2 = "evaluatorHash2";
  String evaluatorValue2 = "evaluatorValue2";

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void canEncode() {
    ExpressionEvaluatorContext.set(Map.of(evaluatorHash1, evaluatorValue1, evaluatorHash2, evaluatorValue2));

    String content = format(contentToBeFormatted, evaluatorHash1, evaluatorHash2);

    Base64ManagerFunctor base64ManagerFunctor =
        Base64ManagerFunctor.builder().expressionFunctors(expressionFunctors).build();

    String result = (String) base64ManagerFunctor.encode(content);
    String decodedRet = EncodingUtils.decodeBase64ToString(result);

    assertThat(decodedRet).isEqualTo(format(contentToBeFormatted, evaluatorValue1, evaluatorValue2));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void canEncodeFromFuture() throws ExecutionException, InterruptedException {
    Future future1 = mock(Future.class);
    Future future2 = mock(Future.class);
    when(future1.get()).thenReturn(evaluatorValue1);
    when(future2.get()).thenReturn(evaluatorValue2);

    ExpressionEvaluatorContext.set(Map.of(evaluatorHash1, future1, evaluatorHash2, future2));

    String content = format(contentToBeFormatted, evaluatorHash1, evaluatorHash2);

    Base64ManagerFunctor base64ManagerFunctor =
        Base64ManagerFunctor.builder().expressionFunctors(expressionFunctors).build();

    String result = (String) base64ManagerFunctor.encode(content);
    String decodedRet = EncodingUtils.decodeBase64ToString(result);

    assertThat(decodedRet).isEqualTo(format(contentToBeFormatted, evaluatorValue1, evaluatorValue2));
  }
}
