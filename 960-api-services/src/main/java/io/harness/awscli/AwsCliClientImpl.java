package io.harness.awscli;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.awscli.AwsCliConstants.CONFIGURE_OPTION;
import static io.harness.awscli.AwsCliConstants.CONFIGURE_VALUE;
import static io.harness.awscli.AwsCliConstants.ROLE_ARN;
import static io.harness.awscli.AwsCliConstants.ROLE_SESSION_NAME;
import static io.harness.awscli.AwsCliConstants.SET_CONFIGURE_COMMAND;
import static io.harness.awscli.AwsCliConstants.STS_COMMAND;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.cli.EmptyLogOutputStream;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
@Singleton
public class AwsCliClientImpl implements AwsCliClient {
  @Inject CliHelper cliHelper;
  @Override
  public CliResponse setConfigure(String configureOption, String configureValue, Map<String, String> envVariables,
      String directory, LogCallback executionLogCallback, long timeoutInMillis, String loggingCommand)
      throws IOException, InterruptedException, TimeoutException {
    String awsConfigureCommand =
        SET_CONFIGURE_COMMAND.replace(CONFIGURE_OPTION, configureOption).replace(CONFIGURE_VALUE, configureValue);
    return cliHelper.executeCliCommand(awsConfigureCommand, timeoutInMillis, envVariables, directory,
        executionLogCallback, loggingCommand, new EmptyLogOutputStream());
  }

  @Override
  public CliResponse stsAssumeRole(String roleArn, String roleSessionName, String externalId,
      Map<String, String> envVariables, String directory, LogCallback executionLogCallback, long timeoutInMillis,
      String loggingCommand) throws IOException, InterruptedException, TimeoutException {
    String awsStsAssumeRoleCommand = STS_COMMAND.replace(ROLE_ARN, roleArn).replace(ROLE_SESSION_NAME, roleSessionName);
    if (EmptyPredicate.isNotEmpty(externalId)) {
      awsStsAssumeRoleCommand += " --external-id " + externalId;
    }
    return cliHelper.executeCliCommand(awsStsAssumeRoleCommand, timeoutInMillis, envVariables, directory,
        executionLogCallback, loggingCommand, new EmptyLogOutputStream());
  }
}
