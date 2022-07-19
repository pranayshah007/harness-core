/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public abstract class ContainerParams implements NestedAnnotationResolver {
  private String name;
  @Expression(ALLOW_SECRETS) private ImageDetailsWithConnector imageDetailsWithConnector;
  private List<String> commands;
  @Expression(ALLOW_SECRETS) private List<String> args;
  @Expression(ALLOW_SECRETS) private String workingDir;
  private List<Integer> ports;
  @Expression(ALLOW_SECRETS) private Map<String, String> envVars;
  @Expression(ALLOW_SECRETS) private Map<String, String> envVarsWithSecretRef;
  private Map<String, SecretVarParams> secretEnvVars;
  private Map<String, SecretVolumeParams> secretVolumes;
  @Expression(ALLOW_SECRETS) private String imageSecret;
  @Expression(ALLOW_SECRETS) private Map<String, String> volumeToMountPath;
  private ContainerResourceParams containerResourceParams;
  private ContainerSecrets containerSecrets;
  @Deprecated private Integer runAsUser;
  @Deprecated private boolean privileged;
  private String imagePullPolicy;
  private ContainerSecurityContext securityContext;
  private ConnectorDetails gitConnector;

  public abstract ContainerParams.Type getType();

  public enum Type {
    K8, // Generic K8 container configuration
    K8_GIT_CLONE, // K8 container configuration to clone a git repository
  }
}
