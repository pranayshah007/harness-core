/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment.pod.container;

import io.harness.annotation.RecasterAlias;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

/**
 * Stores all details require to spawn container
 */

@Data
@Builder
@TypeAlias("containerDefinitionInfo")
@RecasterAlias("io.harness.beans.environment.pod.container.ContainerDefinitionInfo")
public class ContainerDefinitionInfo {
  @NotEmpty private String name;
  @NotEmpty private ContainerImageDetails containerImageDetails;
  @NotEmpty private CIContainerType containerType;
  @NotEmpty private ContainerResourceParams containerResourceParams;
  private boolean isHarnessManagedImage;
  private String stepIdentifier;
  private String stepName;
  private List<String> commands;
  private boolean isMainLiteEngine;
  private List<String> args;
  private List<Integer> ports;
  Map<String, String> envVars;
  Map<String, String> envVarsWithSecretRef;
  List<SecretNGVariable> secretVariables;
  private Boolean privileged;
  private Integer runAsUser;
  private String imagePullPolicy;
}
