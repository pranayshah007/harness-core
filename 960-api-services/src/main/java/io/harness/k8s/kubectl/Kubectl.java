/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import io.harness.beans.version.Version;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

public class Kubectl {
  private final String kubectlPath;
  private final String configPath;
  @Getter private final ClientType clientType;
  @Getter @Setter private Version version;

  protected Kubectl(String kubectlPath, String configPath, ClientType clientType) {
    this.clientType = clientType;
    this.kubectlPath = kubectlPath;
    this.configPath = configPath;
  }

  public static Kubectl client(String kubectlPath, String configPath) {
    return new Kubectl(kubectlPath, configPath, ClientType.KUBECTL);
  }

  public VersionCommand version() {
    return new VersionCommand(this);
  }

  public ApplyCommand apply() {
    return new ApplyCommand(this);
  }

  public DeleteCommand delete() {
    return new DeleteCommand(this);
  }

  public GetCommand get() {
    return new GetCommand(this);
  }

  public AuthCommand auth() {
    return new AuthCommand(this);
  }

  public DescribeCommand describe() {
    return new DescribeCommand(this);
  }

  public RolloutCommand rollout() {
    return new RolloutCommand(this);
  }

  public GetPodCommand getPod() {
    return new GetPodCommand(new GetCommand(this));
  }

  public GetJobCommand getJobCommand(String jobName, String namespace) {
    return new GetJobCommand(new GetCommand(this), jobName, namespace);
  }

  public ScaleCommand scale() {
    return new ScaleCommand(this);
  }

  public CreateCommand create(String manifestName) {
    return new CreateCommand(this, manifestName);
  }

  public String command() {
    StringBuilder command = new StringBuilder(128);
    if (StringUtils.isNotBlank(kubectlPath)) {
      command.append(encloseWithQuotesIfNeeded(kubectlPath)).append(' ');
    } else {
      command.append("kubectl ");
    }

    if (StringUtils.isNotBlank(configPath)) {
      command.append("--kubeconfig=" + encloseWithQuotesIfNeeded(configPath) + " ");
    }

    return command.toString();
  }

  public static String option(Option type, String value) {
    return "--" + type.toString() + "=" + value + " ";
  }

  public static String option(Option type, int value) {
    return "--" + type.toString() + "=" + value + " ";
  }

  public static String flag(Flag type) {
    return "--" + type.toString() + " ";
  }

  public static String flag(String flag) {
    return flag + " ";
  }

  public static String flag(Flag type, boolean value) {
    return "--" + type.toString() + "=" + value + " ";
  }

  public static String flag(Flag type, String value) {
    return "--" + type.toString() + "=" + value + " ";
  }

  public enum ClientType { KUBECTL, OC }
}
