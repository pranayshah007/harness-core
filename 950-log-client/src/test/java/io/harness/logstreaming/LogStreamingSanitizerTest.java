/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.rule.OwnerRule.GAURAV;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.windows.CmdUtils.WIN_RM_MARKER;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LogStreamingSanitizerTest extends CategoryTest {
  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotSanitizeWhenNoSecretsAvailable() {
    String message = "test message without secrets";
    LogLine logLine = LogLine.builder().message(message).build();

    LogStreamingSanitizer logStreamingSanitizer = LogStreamingSanitizer.builder().secrets(null).build();

    logStreamingSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isEqualTo(message);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldSanitizeWhenSecretsAreAvailable() {
    String message = "test message with secret1 and secret2";
    String sanitizedMessage = "test message with " + SECRET_MASK + " and " + SECRET_MASK;

    LogLine logLine = LogLine.builder().message(message).build();

    Set<String> secrets = new HashSet<>();
    secrets.add("secret1");
    secrets.add("secret2");

    LogStreamingSanitizer logStreamingSanitizer = LogStreamingSanitizer.builder().secrets(secrets).build();

    logStreamingSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isEqualTo(sanitizedMessage);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void shouldMaskJwt() {
    String validDummyJWT =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
    String message = String.format(
        "JWT: a1c.x2z.123 \n JWT2: %s JWT3: abc.abc.abc... \n JWT4: xyz.xyz JWT5: abc.abc.abc.abc \n JWT6: abc.abc.abc. xyz \n JWT7: %s;%s ...",
        validDummyJWT, validDummyJWT, validDummyJWT);
    String sanitizedMessage = String.format(
        "JWT: a1c.x2z.123 \n JWT2: %s JWT3: abc.abc.abc... \n JWT4: xyz.xyz JWT5: abc.abc.abc.abc \n JWT6: abc.abc.abc. xyz \n JWT7: %s;%s ...",
        SECRET_MASK, SECRET_MASK, SECRET_MASK, SECRET_MASK);

    LogLine logLine = LogLine.builder().message(message).build();
    LogStreamingSanitizer logStreamingSanitizer = LogStreamingSanitizer.builder().secrets(null).build();

    logStreamingSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isEqualTo(sanitizedMessage);
  }

  @Test
  @Owner(developers = GAURAV)
  @Category(UnitTests.class)
  public void shouldReplaceMultiLineSecret() {
    Set<String> secrets = ImmutableSet.<String>builder().add("this\nis multiline\nsecret").build();
    LogStreamingSanitizer logSanitizer = LogStreamingSanitizer.builder().secrets(secrets).build();

    LogLine logLine = LogLine.builder()
                          .message("sanitize this log: this\n"
                              + "      is multiline\n"
                              + "       secret")
                          .build();
    logSanitizer.sanitizeLogMessage(logLine);

    Assertions.assertThat(logLine.getMessage())
        .isEqualTo("sanitize ************** log: **************\n"
            + "      **************\n"
            + "       **************");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGenericSanitize_Null_Empty_Message() {
    LogStreamingSanitizer logSanitizer = LogStreamingSanitizer.builder().build();

    // empty message
    LogLine logLine = LogLine.builder().message("").build();
    logSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isEqualTo("");

    // null message
    logLine.setMessage(null);
    logSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isNull();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void shouldSanitizeWinRmWhenSecretsAreAvailable() {
    String specialCharSecret = "ab>cd<ef&gh|";
    String specialCharSecretEscaped = "ab^>cd^<ef^&gh^|";
    String message = "test message with secret1 and " + specialCharSecretEscaped;
    String sanitizedMessage = "test message with " + SECRET_MASK + " and " + SECRET_MASK;

    LogLine logLine = LogLine.builder().message(message).build();

    Set<String> secrets = new HashSet<>();
    secrets.add("secret1");
    secrets.add(specialCharSecret);

    LogStreamingSanitizer logStreamingSanitizer = LogStreamingSanitizer.builder().secrets(secrets).build();

    logStreamingSanitizer.sanitizeLogMessage(logLine);
    Assertions.assertThat(logLine.getMessage()).isNotEqualTo(sanitizedMessage);

    logStreamingSanitizer.sanitizeLogMessage(logLine, Set.of(WIN_RM_MARKER));
    Assertions.assertThat(logLine.getMessage()).isEqualTo(sanitizedMessage);
  }
}
