/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.expressions.functors.PayloadFunctor;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PayloadFunctorTest extends CategoryTest {
  private String bigPayload = "{\n"
      + " \"Type\" : \"Notification\",\n"
      + " \"Timestamp\" : \"2021-03-23T20:42:23.163Z\",\n"
      + " \"SignatureVersion\" : \"1\",\n"
      + " \"UnsubscribeURL\" : \"https://sns.eu-central-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger:bf6ca40a-eb7c-43a8-b452-ec5869813da4\"\n"
      + "}";
  PayloadFunctor payloadFunctor = new PayloadFunctor(bigPayload);
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    Object object = payloadFunctor.bind();
    assertThat(((HashMap) object).get("Timestamp")).isEqualTo("2021-03-23T20:42:23.163Z");
    assertThat(((HashMap) object).get("Type")).isEqualTo("Notification");

    // Exception case
    payloadFunctor = new PayloadFunctor(",");
    assertThat(payloadFunctor.bind()).isEqualTo(",");
  }
}
