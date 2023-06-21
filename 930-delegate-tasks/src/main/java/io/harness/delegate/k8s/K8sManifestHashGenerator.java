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

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sManifestHashGenerator {
  @Inject K8sTaskHelperBase k8sTaskHelperBase;
  private static final String MANIFEST_FOR_HASH = "manifest-hash.yaml";

  public String manifestHash(List<KubernetesResource> resources, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, long timeoutInMillis, Kubectl client) throws Exception {
    FileIo.writeUtf8StringToFile(
        k8sDelegateTaskParams.getWorkingDirectory() + "/" + MANIFEST_FOR_HASH, ManifestHelper.toYaml(resources));
    String output = "";
    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream(1024);
         LogOutputStream logErrorStream =
             K8sTaskHelperBase.getExecutionLogOutputStream(executionLogCallback, ERROR, errorCaptureStream)) {
      String kubectlCreateCommand = client.create(MANIFEST_FOR_HASH).command();
      ProcessResult processResult = k8sTaskHelperBase.executeShellCommand(
          k8sDelegateTaskParams.getWorkingDirectory(), kubectlCreateCommand, logErrorStream, timeoutInMillis);
      output = processResult.hasOutput() ? processResult.outputUTF8() : EMPTY;
      if (processResult.getExitValue() != 0) {
        throw new KubernetesCliTaskRuntimeException(output, KubernetesCliCommandType.GENERATE_HASH);
      }
    } finally {
      FileIo.deleteFileIfExists(k8sDelegateTaskParams.getWorkingDirectory() + "/" + MANIFEST_FOR_HASH);
    }
    return generatedHash(output);
  }

  public String generatedHash(String manifestOutput) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] messageDigest = md.digest(manifestOutput.getBytes());
    return encodeHexString(messageDigest);
  }
}
