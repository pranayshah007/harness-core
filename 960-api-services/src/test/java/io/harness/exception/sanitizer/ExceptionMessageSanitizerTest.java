/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception.sanitizer;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secret.SecretSanitizerThreadLocal;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ExceptionMessageSanitizerTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizer() {
    IOException ex1 = new IOException("secret Key there is an password");
    Exception ex = new Exception("secret Key password", ex1);
    Set<String> secrets = new HashSet<>();
    secrets.add("password");
    secrets.add("secret Key");
    ExceptionMessageSanitizer.sanitizeException(ex, secrets);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizerThreadLocalAdd() {
    IOException ex1 = new IOException("secret Key there is an password");
    Exception ex = new Exception("secret Key password", ex1);
    SecretSanitizerThreadLocal.add("secret Key");
    SecretSanitizerThreadLocal.add("password");
    ExceptionMessageSanitizer.sanitizeException(ex);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testProcessResponseMessageSanitize() {
    String errorMessage = "secret Key: password is invalid";
    SecretSanitizerThreadLocal.add("secret Key");
    SecretSanitizerThreadLocal.add("password");
    String updatedMessage = ExceptionMessageSanitizer.sanitizeMessage(errorMessage);

    assertThat(updatedMessage).isEqualTo("**************: ************** is invalid");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testWingsException() {
    Map<String, Object> params = new HashMap<>();

    params.put("test", "secret Key password");
    params.put("test1", "secret Key");
    params.put("test2", 2345);
    params.put("test3", "exception.msg.org secret Key");

    WingsException wingsException = new WingsException(params, ErrorCode.KUBERNETES_API_TASK_EXCEPTION);
    Exception ex = new Exception("secret Key password", wingsException);
    SecretSanitizerThreadLocal.add("password");
    SecretSanitizerThreadLocal.add("secret Key");
    ExceptionMessageSanitizer.sanitizeException(ex);

    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(WingsException.class);
    WingsException responseException = (WingsException) ex.getCause();
    assertThat(responseException.getParams().get("test")).isEqualTo("************** **************");
    assertThat(responseException.getParams().get("test1")).isEqualTo("**************");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizerThreadLocalAddAll() {
    IOException ex1 = new IOException("secret Key there is an password");
    Exception ex = new Exception("secret Key password", ex1);
    SecretSanitizerThreadLocal.add("secret Key");
    SecretSanitizerThreadLocal.add("password");
    Set<String> secrets = new HashSet<>();
    secrets.add("secret Key");
    secrets.add("password");
    SecretSanitizerThreadLocal.addAll(secrets);
    ExceptionMessageSanitizer.sanitizeException(ex);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }
}
