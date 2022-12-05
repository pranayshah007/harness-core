/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.rule.OwnerRule.PRATEEK;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class JWTTokenFlowAuthFilterUtilsTest {
  private final String TEST_ACCOUNT_ID = "testAccountID";

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_validJWT() {
    final String validJWTType =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdXRoVG9rZW4iOiI2MmU3NTUxMzJjNThkZDAxM2ZlNDEzOWIiLCJpc3MiOiJIYXJuZXNzIEluYyIsImV4cCI6MTY1OTQxNDE2MywiZW52IjoiZ2F0ZXdheSIsImlhdCI6MTY1OTMyNzcwM30.ud35uShhaOGMXgsdDAYbMl8bZX40muRdwqBByxQUqhA";
    assertTrue(JWTTokenFlowAuthFilterUtils.isJWTTokenType(validJWTType.split("\\."), TEST_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testIsJwtTokenType_invalidJWT() {
    final String inValidJWTType =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6InB1YmxpYzozOTk5MzIxMy0zZWQ3LTQ1ODItODZjNS03NDVlODFkOGZkYTkifQ.eyJhY3IiOiIwIiwiYXRfaGFzaCI6ImVjRGVtSnZKRFBaUXJkTDg0b1RSTVEiLCJhdWQiOlsiaHJuLXN0LWJhY2t1cCJdLCJhdXRoX3RpbWUiOjE2Njk5Mzc2MTAsImV4cCI6MTY2OTk0MTIxOSwiaWF0IjoxNjY5OTM3NjE5LCJpc3MiOiJodHRwczovL2xvY2FsaG9zdDo1NDQ0LyIsImp0aSI6IjcwMzRmNzU1LTMwYjYtNDEyYy1iZjk0LTNjYjEzOWRjNTEwZCIsIm5vbmNlIjoiaWR5c2tqd3ViYXZoZ3ppaGttbmpobXR1IiwicmF0IjoxNjY5OTM3NTk4LCJzaWQiOiJjMTQxNWI2Ni1mZGVjLTQ5MjMtOGIzZi04YjQ0NmVjYjI1YmEiLCJzdWIiOiJmb29AYmFyLmNvbSJ9.tV76sxqVM4xgy8auofarB2zFItZrWgW6R025j_V8jKtgXB11t_m8FMiESGxqbPCJV6hY_2fowSURC0MPSpz284K2a7eAmZCHR6f_jLjALITdQrExmkcE1wSMQmbGy9wqYBiHVGCjCeUFUxyR6kEFRti_jR6SO8NMmutoMhqi5f9183QBSnuiFNWpJvTdDiGEQ-gJ0B50WSREBEVrnEMCQKl5-GczIAJI-kWQTvHzbIFL-VuCWFJtozJ1P3_eYKH_xqFTFaagXd89NpanXPDB7GRypn5EUBla-D8uAzuRJXDH__IpsekEMTWRgXpTnJkOJf69m5h7VVxmnS_0gkSe3PE7b5E9clsGZN3MPhawUfBY08O089XG3R_qcr98eriQSTJhUxon0hm6FJr2rNEbmVN90OWpSijaPwCqtgy-kKR21kDI30RAmkj9AepZZPFVmHWZM_XIrJeN9zJV3YUM9-mZFPhHVFhvUL-pJJ-FSuxX-U-5EjdE1Rt5hWGeqJ2HbUtFRqf-nEgHeKhbUOdsKCKiVCV-DD4hjHnOr2wqMgaddch6QnMnBqC8Z1P6mpkBQ0OwtXR0IaKLCzH4th9u3Rbx5js0dCbznhDF1zNj9yH_23ekmD8DqrKgJTA1A9JmzDLJp5l1GZoRJ8Ad_S8SbMXv_Tc2XpxFYLD3ZsT6tWI";
    assertThatThrownBy(() -> JWTTokenFlowAuthFilterUtils.isJWTTokenType(inValidJWTType.split("\\."), TEST_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }
}
