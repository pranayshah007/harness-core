/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.KubernetesApiClientRuntimeException;
import io.harness.exception.runtime.utils.KubernetesCertificateType;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Set;

@OwnedBy(CDP)
public class KubernetesApiClientRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.of(KubernetesApiClientRuntimeException.class);
  }

  @Override
  public WingsException handleException(Exception exception) {
    KubernetesApiClientRuntimeException kubernetesApiClientRuntimeException =
        (KubernetesApiClientRuntimeException) exception;
    KubernetesCertificateType kubernetesCertificateType =
        kubernetesApiClientRuntimeException.getKubernetesCertificateType();
    if (kubernetesApiClientRuntimeException.getCause() != null) {
      Throwable cause = kubernetesApiClientRuntimeException.getCause();
      if (cause instanceof CertificateException) {
        return NestedExceptionUtils.hintWithExplanationException(
            format(KubernetesExceptionHints.API_CLIENT_CA_CERT_INVALID, kubernetesCertificateType.getName()),
            format(KubernetesExceptionExplanation.API_CLIENT_CA_CERT_INVALID, kubernetesCertificateType.getName())
                + ": " + cause.getMessage(),
            new KubernetesTaskException(cause.getMessage()));
      }

      if (cause instanceof IOException) {
        return NestedExceptionUtils.hintWithExplanationException(
            format(KubernetesExceptionHints.API_CLIENT_CA_CERT_INCOMPLETE, kubernetesCertificateType.getName()),
            format(KubernetesExceptionExplanation.API_CLIENT_CA_CERT_INCOMPLETE, kubernetesCertificateType.getName())
                + ": " + cause.getMessage(),
            new KubernetesTaskException(cause.getMessage()));
      }
    }

    return NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.API_CLIENT_CREATE_FAILED,
        KubernetesExceptionExplanation.API_CLIENT_CREATE_FAILED,
        new KubernetesTaskException(kubernetesApiClientRuntimeException.getMessage()));
  }
}
