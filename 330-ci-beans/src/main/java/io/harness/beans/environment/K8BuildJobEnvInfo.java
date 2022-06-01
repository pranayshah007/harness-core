/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment;

import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

/**
 * Stores K8 specific data to setup Pod for running CI job
 */

@Data
@Value
@Builder
@TypeAlias("k8BuildJobEnvInfo")
public class K8BuildJobEnvInfo implements BuildJobEnvInfo {
  @NotEmpty private PodsSetupInfo podsSetupInfo;
  @NotEmpty private String workDir;
  private Map<String, List<ConnectorConversionInfo>> stepConnectorRefs;

  @Override
  public Type getType() {
    return Type.K8;
  }

  @Data
  @Builder
  public static final class PodsSetupInfo {
    private List<PodSetupInfo> podSetupInfoList = new ArrayList<>();
  }

  @Data
  @Builder
  public static final class ConnectorConversionInfo {
    private String connectorRef;
    private Map<EnvVariableEnum, String> envToSecretsMap;
  }
}
