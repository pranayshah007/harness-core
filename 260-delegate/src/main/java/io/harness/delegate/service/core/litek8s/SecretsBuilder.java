/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.Secret;
import io.harness.delegate.service.core.k8s.K8SSecret;
import io.harness.delegate.service.core.util.ApiExceptionLogger;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class SecretsBuilder {
  private static final String DOCKER_CONFIG_KEY = ".dockercfg";

  private final K8SRunnerConfig config;
  private final RunnerDecryptionService decryptionService;
  private final CoreV1Api coreApi;

  /**
   * TODO: CI & CD image pull secrets seem to work vastly different.
   * For CD it seems most of the processing is in PMS and delegate just does decryption.
   * For CI most of the processing is on client side.
   * CD Style seems simpler for the client and might support more use cases. Investigate more and  do that.
   * Below is skeleton that wont work right now (data needs to be .configjson, not just secret). Bonus if we could do
   * .dockerconfigjson
   *
   * @see
   *     io.harness.pms.expressions.utils.ImagePullSecretUtils#getImagePullSecret(io.harness.cdng.artifact.outcome.ArtifactOutcome,
   *     io.harness.pms.contracts.ambiance.Ambiance)
   */
  public V1Secret createImagePullSecrets(final String taskGroupId, final Secret infraSecret, final long index) {
    final var secretName = K8SResourceHelper.getImagePullSecretName(taskGroupId, index);
    final var decryptedSecrets = decryptionService.decrypt(infraSecret);
    if (decryptedSecrets.values().size() != 1) {
      log.warn("Multiple [{}] image pull secret configs available for {}/{}, picking only one",
          decryptedSecrets.values().size(), taskGroupId, index);
    }
    final var optionalSecret = decryptedSecrets.values().stream().findFirst();
    if (optionalSecret.isEmpty()) {
      throw new IllegalStateException(
          "Trying to create registry secret, but no secret data present for " + taskGroupId + index);
    }

    try {
      return K8SSecret.imagePullSecret(secretName, config.getNamespace(), taskGroupId)
          .putDataItem(DOCKER_CONFIG_KEY, String.valueOf(optionalSecret.get()).getBytes(Charsets.UTF_8))
          .create(coreApi);
    } catch (ApiException e) {
      log.error(ApiExceptionLogger.format(e));
      throw new RuntimeException("K8S Api invocation failed creating image pull secret", e);
    }
  }

  public V1Secret createSecret(final String taskGroupId, final String taskId, final Secret infraSecret) {
    final var secretName = K8SResourceHelper.getSecretName(taskId);
    final var decryptedSecrets = decryptionService.decrypt(infraSecret);
    try {
      return K8SSecret.secret(secretName, config.getNamespace(), taskGroupId)
          .putAllCharDataItems(decryptedSecrets)
          .create(coreApi);
    } catch (ApiException e) {
      log.error(ApiExceptionLogger.format(e));
      throw new RuntimeException("K8S Api invocation failed creating secret", e);
    }
  }
}
