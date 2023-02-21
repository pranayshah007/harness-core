package io.harness.aws.sam;

import io.harness.serverless.Flag;
import io.harness.serverless.ServerlessUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class AwsSamClient {
  private String awsSamPath;

  private AwsSamClient(String awsSamPath) {
    this.awsSamPath = awsSamPath;
  }

  public static AwsSamClient client(String awsSamPath) {
    return new AwsSamClient(awsSamPath);
  }

  public String command() {
    StringBuilder command = new StringBuilder(256);
    if (StringUtils.isNotBlank(awsSamPath)) {
      command.append(ServerlessUtils.encloseWithQuotesIfNeeded(awsSamPath));
    } else {
      command.append("sam ");
    }
    return command.toString();
  }

  public DeployCommand deploy() {
    return new DeployCommand(this);
  }

  public static String option(Option type, String value) {
    return type.toString() + " " + value + " ";
  }

  public static String flag(Flag type) {
    return "--" + type.toString() + " ";
  }

  public static String home(String directory) {
    return "HOME=" + directory + " ";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AwsSamClient that = (AwsSamClient) o;
    return Objects.equals(awsSamPath, that.awsSamPath);
  }
}
