/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;

import static org.apache.commons.codec.binary.Hex.encodeHexString;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.kubectl.CreateCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sManifestHashGenerator {
  @Inject K8sTaskHelperBase k8sTaskHelperBase;
  private static final String MANIFEST_FOR_HASH = "manifest-hash.yaml";

  public String manifestHash(List<KubernetesResource> resources, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, Kubectl client) throws Exception {
    String manifestHash;
    try {
      FileIo.writeUtf8StringToFile(
          k8sDelegateTaskParams.getWorkingDirectory() + "/" + MANIFEST_FOR_HASH, ManifestHelper.toYaml(resources));
      final CreateCommand kubectlCreateCommand = client.create(MANIFEST_FOR_HASH);
      ProcessResponse response = K8sTaskHelperBase.executeCommandSilentlyWithErrorCapture(
          kubectlCreateCommand, k8sDelegateTaskParams, executionLogCallback, ERROR);
      ProcessResult processResult = response.getProcessResult();
      String output = processResult.hasOutput() ? processResult.outputUTF8() : EMPTY;
      if (processResult.getExitValue() != 0) {
        k8sTaskHelperBase.logExecutableFailed(processResult, executionLogCallback);
        throw new KubernetesCliTaskRuntimeException(response, KubernetesCliCommandType.GENERATE_HASH);
      }
      manifestHash = generatedHash(output);
    } finally {
      FileIo.deleteFileIfExists(k8sDelegateTaskParams.getWorkingDirectory() + "/" + MANIFEST_FOR_HASH);
    }
    return manifestHash;
  }

  public String generatedHash(String manifestOutput) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] messageDigest = md.digest(manifestOutput.getBytes());
    return encodeHexString(messageDigest);
  }
}
