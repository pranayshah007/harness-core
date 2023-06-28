/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.K8sConstants.KUBE_CONFIG_EXEC_TEMPLATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.utils.system.SystemWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class K8sApiClientHelper {
  public String generateExecFormatKubeconfig(KubernetesConfig config) {
    String insecureSkipTlsVerify = isEmpty(config.getCaCert()) ? "insecure-skip-tls-verify: true" : "";
    String certificateAuthorityData =
        isNotEmpty(config.getCaCert()) ? ("certificate-authority-data: " + String.valueOf(config.getCaCert())) : "";
    String namespace = isNotEmpty(config.getNamespace()) ? ("namespace: " + config.getNamespace()) : "";
    String exec;
    try {
      ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                       .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                       .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                       .enable(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS));
      objectMapper.enable(SerializationFeature.WRAP_ROOT_VALUE);
      objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
      // Indenting the exec yaml string by 4 white spaces so that the complete string remains coherent
      exec = objectMapper.writeValueAsString(config.getExec()).replace("\n", "\n    ").stripTrailing();
    } catch (JsonProcessingException ex) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "For kubernetes version < 1.26 then use auth-provider to generate kubeconfig file.",
          "An error has occurred. Please contact the Harness support team.",
          new InvalidRequestException("Unable to convert kubeconfig with client-go-exec-plugin JSON to YAML.", ex));
    }
    String kubeconfig = KUBE_CONFIG_EXEC_TEMPLATE.replace("${MASTER_URL}", config.getMasterUrl())
                            .replace("${INSECURE_SKIP_TLS_VERIFY}", insecureSkipTlsVerify)
                            .replace("${CERTIFICATE_AUTHORITY_DATA}", certificateAuthorityData)
                            .replace("${NAMESPACE}", namespace)
                            .replace("${EXEC}", exec);

    if (config.getAzureConfig() != null) {
      kubeconfig = kubeconfig.replace("CLUSTER_NAME", config.getAzureConfig().getClusterName())
                       .replace("HARNESS_USER", config.getAzureConfig().getClusterUser())
                       .replace("CURRENT_CONTEXT", config.getAzureConfig().getCurrentContext());
    }
    return kubeconfig;
  }

  public Optional<Long> getTimeout(String environmentVariable) {
    try {
      String timeout = SystemWrapper.getenv(environmentVariable);
      if (isEmpty(timeout)) {
        return Optional.empty();
      }
      return Optional.of(Long.parseLong(timeout));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
