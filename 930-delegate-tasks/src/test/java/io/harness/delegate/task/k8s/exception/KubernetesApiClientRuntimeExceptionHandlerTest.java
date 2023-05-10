/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.KubernetesApiClientRuntimeException;
import io.harness.exception.runtime.utils.KubernetesCertificateType;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.rule.Owner;

import java.io.IOException;
import java.security.cert.CertificateException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class KubernetesApiClientRuntimeExceptionHandlerTest extends CategoryTest {
  KubernetesApiClientRuntimeExceptionHandler handler = new KubernetesApiClientRuntimeExceptionHandler();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCertificateException() {
    WingsException result = handler.handleException(new KubernetesApiClientRuntimeException(
        "failed", new CertificateException("failed cert exception"), KubernetesCertificateType.CLIENT_CERTIFICATE));
    assertThat(ExceptionUtils.cause(HintException.class, result))
        .hasMessageContaining(format(KubernetesExceptionHints.API_CLIENT_CA_CERT_INVALID,
            KubernetesCertificateType.CLIENT_CERTIFICATE.getName()));
    assertThat(ExceptionUtils.cause(ExplanationException.class, result))
        .hasMessageContaining(format(KubernetesExceptionExplanation.API_CLIENT_CA_CERT_INVALID,
            KubernetesCertificateType.CLIENT_CERTIFICATE.getName()));
    assertThat(ExceptionUtils.cause(KubernetesTaskException.class, result))
        .hasMessageContaining("failed cert exception");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIOException() {
    WingsException result = handler.handleException(new KubernetesApiClientRuntimeException(
        "failed", new IOException("failed io exception"), KubernetesCertificateType.CA_CERTIFICATE));
    assertThat(ExceptionUtils.cause(HintException.class, result))
        .hasMessageContaining(format(KubernetesExceptionHints.API_CLIENT_CA_CERT_INCOMPLETE,
            KubernetesCertificateType.CA_CERTIFICATE.getName()));
    assertThat(ExceptionUtils.cause(ExplanationException.class, result))
        .hasMessageContaining(format(KubernetesExceptionExplanation.API_CLIENT_CA_CERT_INCOMPLETE,
            KubernetesCertificateType.CA_CERTIFICATE.getName()));
    assertThat(ExceptionUtils.cause(KubernetesTaskException.class, result)).hasMessageContaining("failed io exception");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testOther() {
    WingsException result = handler.handleException(
        new KubernetesApiClientRuntimeException("failed runtime exception", new RuntimeException("failed")));
    assertThat(ExceptionUtils.cause(HintException.class, result))
        .hasMessageContaining(KubernetesExceptionHints.API_CLIENT_CREATE_FAILED);
    assertThat(ExceptionUtils.cause(ExplanationException.class, result))
        .hasMessageContaining(KubernetesExceptionExplanation.API_CLIENT_CREATE_FAILED);
    assertThat(ExceptionUtils.cause(KubernetesTaskException.class, result))
        .hasMessageContaining("failed runtime exception");
  }
}