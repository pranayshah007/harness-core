/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_PRUNING_ENABLED_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(CDP)
public class K8sRelease implements IK8sRelease {
  @Getter private V1Secret releaseSecret;

  @Override
  public Integer getReleaseNumber() {
    String releaseNumberLabelValue =
        K8sReleaseSecretHelper.getReleaseLabelValue(releaseSecret, RELEASE_NUMBER_LABEL_KEY);
    if (isNotEmpty(releaseNumberLabelValue)) {
      return Integer.parseInt(releaseNumberLabelValue);
    }
    return -1;
  }

  @Override
  public Status getReleaseStatus() {
    String releaseStatusLabelValue =
        K8sReleaseSecretHelper.getReleaseLabelValue(releaseSecret, RELEASE_STATUS_LABEL_KEY);
    return Status.valueOf(releaseStatusLabelValue);
  }

  @Override
  public List<KubernetesResource> getResourcesWithSpecs() {
    if (releaseSecret == null) {
      return emptyList();
    }

    try {
      Map<String, byte[]> secretData = releaseSecret.getData();
      if (secretData != null && secretData.containsKey(RELEASE_KEY)) {
        byte[] compressedYaml = secretData.get(RELEASE_KEY);
        String manifestsYaml = deCompressString(compressedYaml);
        return ManifestHelper.processYaml(manifestsYaml);
      }
    } catch (IOException e) {
      IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = ExceptionUtils.getMessage(ex);
      String secretName = releaseSecret.getMetadata() != null ? releaseSecret.getMetadata().getName() : "";

      log.error("Failed to extract resources from release secret {}.", secretName, ex);
      throw new InvalidRequestException(
          String.format("Failed to extract resources from release secret %s. Error: %s", secretName, errorMessage));
    }
    return emptyList();
  }

  @Override
  public List<KubernetesResourceId> getResourceIds() {
    List<KubernetesResource> resources = getResourcesWithSpecs();
    return resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());
  }

  @Override
  public IK8sRelease setReleaseData(List<KubernetesResource> resources, boolean isPruningEnabled) {
    try {
      releaseSecret.putDataItem(RELEASE_KEY, getCompressedYaml(resources));
      K8sReleaseSecretHelper.putLabelsItem(
          releaseSecret, RELEASE_PRUNING_ENABLED_KEY, String.valueOf(isPruningEnabled));
    } catch (IOException e) {
      IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = ExceptionUtils.getMessage(ex);
      String secretName = releaseSecret.getMetadata() != null ? releaseSecret.getMetadata().getName() : "";

      log.error("Failed to set resources in release secret {}.", secretName, ex);
      throw new InvalidRequestException(
          String.format("Failed to set resources in release secret %s. Error: %s", secretName, errorMessage));
    }
    return this;
  }

  @Override
  public IK8sRelease updateReleaseStatus(Status status) {
    V1ObjectMeta releaseMeta = releaseSecret.getMetadata();
    if (releaseMeta != null && releaseMeta.getLabels() != null) {
      Map<String, String> labels = releaseMeta.getLabels();
      labels.put(RELEASE_STATUS_LABEL_KEY, status.name());
      releaseMeta.setLabels(labels);
      releaseSecret.setMetadata(releaseMeta);
    }
    return this;
  }

  @Override
  public String getReleaseColor() {
    return K8sReleaseSecretHelper.getReleaseColor(releaseSecret);
  }

  @Override
  public String getBgEnvironment() {
    return K8sReleaseSecretHelper.getReleaseBGEnvironment(releaseSecret);
  }

  @Override
  public String getManifestHash() {
    return K8sReleaseSecretHelper.getReleaseManifestHash(releaseSecret);
  }

  @Override
  public void setBgEnvironment(@NotNull String bgEnv) {
    K8sReleaseSecretHelper.putLabelsItem(releaseSecret, RELEASE_SECRET_RELEASE_BG_ENVIRONMENT_KEY, bgEnv);
  }

  @Override
  public void setManifestHash(@NotNull String manifestHash) {
    K8sReleaseSecretHelper.putLabelsItem(releaseSecret, RELEASE_SECRET_RELEASE_MANIFEST_HASH_KEY, manifestHash);
  }

  private byte[] getCompressedYaml(List<KubernetesResource> resources) throws IOException {
    String manifestsYaml = ManifestHelper.toYaml(resources);
    return compressString(manifestsYaml, Deflater.BEST_COMPRESSION);
  }
}
