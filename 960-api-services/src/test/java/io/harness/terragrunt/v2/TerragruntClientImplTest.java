/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliCommandRequest;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.terragrunt.v2.request.AbstractTerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntCliArgs;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntPlanCliRequest;
import io.harness.terragrunt.v2.request.TerragruntRunType;
import io.harness.terragrunt.v2.request.TerragruntShowCliRequest;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
public class TerragruntClientImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CliHelper cliHelper;
  @Mock private LogCallback logCallback;
  @Mock private LogOutputStream logOutputStream;

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInit() {
    Path backendFilePath = Files.createFile(Paths.get("./backend-file"));
    try {
      FileIo.writeFile(backendFilePath, "backend config".getBytes(StandardCharsets.UTF_8));
      testInit("./backend-file",
          TerragruntCommandUtils.init("./backend-file", TerragruntRunType.RUN_MODULE, "-lock-timeout=10s"));
    } finally {
      FileIo.deleteFileIfExists(backendFilePath.toString());
    }
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitBackendFileDoesntExist() {
    testInit("./backend-file-2",
        TerragruntCommandUtils.init("./should-not-be-append", TerragruntRunType.RUN_MODULE, "-lock-timeout=10s"));
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRefreshRunModule() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand = TerragruntCommandUtils.refresh("", "");
    final TerragruntCliRequest request = TerragruntCliRequest.builder()
                                             .timeoutInMillis(60000L)
                                             .runType(TerragruntRunType.RUN_MODULE)
                                             .skipColorLogs(true)
                                             .build();

    setupCliResponse(CommandExecutionStatus.FAILURE, "error", expectedCommand, request);

    CliResponse response = tgClient.refresh(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getError()).isEqualTo("error");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRefreshRunAll() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand =
        TerragruntCommandUtils.runAllRefresh("-target=\"module.module-a\"  -target=\"module.module-b\"",
            " -var-file=\"var-files/file1.var\"  -var-file=\"var-files/file2.var\" ");
    final TerragruntCliRequest request = TerragruntCliRequest.builder()
                                             .timeoutInMillis(60000L)
                                             .runType(TerragruntRunType.RUN_ALL)
                                             .args(TerragruntCliArgs.builder()
                                                       .targets(asList("module.module-a", "module.module-b"))
                                                       .varFiles(asList("var-files/file1.var", "var-files/file2.var"))
                                                       .build())
                                             .build();

    setupCliResponse(CommandExecutionStatus.SUCCESS, "output", expectedCommand, request);

    CliResponse response = tgClient.refresh(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo("output");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPlanRunModule() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand = TerragruntCommandUtils.plan("-target=\"module.module-a\"",
        " -var-file=\"var-files/file1.var\"  -var-file=\"var-files/file2.var\" ", false, "-lock-timeout=10s");
    final TerragruntPlanCliRequest request =
        TerragruntPlanCliRequest.builder()
            .timeoutInMillis(60000L)
            .runType(TerragruntRunType.RUN_MODULE)
            .args(TerragruntCliArgs.builder()
                      .additionalCliArgs(new HashMap<>() {
                        { put("PLAN", "-lock-timeout=10s"); }
                      })
                      .targets(singletonList("module.module-a"))
                      .varFiles(asList("var-files/file1.var", "var-files/file2.var"))
                      .build())
            .build();

    setupCliResponse(CommandExecutionStatus.SUCCESS, "output", expectedCommand, request);

    CliResponse response = tgClient.plan(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo("output");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPlanRunAll() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand = TerragruntCommandUtils.runAllPlan("", "", false, "-lock-timeout=10s");
    final TerragruntPlanCliRequest request = TerragruntPlanCliRequest.builder()
                                                 .timeoutInMillis(60000L)
                                                 .args(TerragruntCliArgs.builder()
                                                           .additionalCliArgs(new HashMap<>() {
                                                             { put("PLAN", "-lock-timeout=10s"); }
                                                           })
                                                           .build())
                                                 .runType(TerragruntRunType.RUN_ALL)
                                                 .build();

    setupCliResponse(CommandExecutionStatus.FAILURE, "plan run-all error", expectedCommand, request);

    CliResponse response = tgClient.plan(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getError()).isEqualTo("plan run-all error");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testShowRunModule() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand = TerragruntCommandUtils.show(true, "tfplan");
    final TerragruntShowCliRequest request = TerragruntShowCliRequest.builder()
                                                 .timeoutInMillis(60000L)
                                                 .runType(TerragruntRunType.RUN_MODULE)
                                                 .outputStream(logOutputStream)
                                                 .planName("tfplan")
                                                 .json(true)
                                                 .build();

    setupCliResponse(CommandExecutionStatus.SUCCESS, "<json output>", expectedCommand, request);

    CliResponse response = tgClient.show(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo("<json output>");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testShowRunAllNonJson() {
    final TerragruntClient tgClient = createClient();
    final String expectedCommand = TerragruntCommandUtils.runAllShow(false, "tfplan");
    final TerragruntShowCliRequest request = TerragruntShowCliRequest.builder()
                                                 .timeoutInMillis(60000L)
                                                 .runType(TerragruntRunType.RUN_ALL)
                                                 .outputStream(logOutputStream)
                                                 .planName("tfplan")
                                                 .json(false)
                                                 .build();

    setupCliResponse(CommandExecutionStatus.SUCCESS, "<plan output>", expectedCommand, request);

    CliResponse response = tgClient.show(request, logCallback);
    verifyCommandExecuted(expectedCommand, request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo("<plan output>");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testShownJsonOldTfVersion() {
    final TerragruntClient tgClient = createClient(Version.parse("0.11.1"), Version.parse("0.39.1"));
    final TerragruntShowCliRequest request = TerragruntShowCliRequest.builder()
                                                 .timeoutInMillis(60000L)
                                                 .runType(TerragruntRunType.RUN_ALL)
                                                 .outputStream(logOutputStream)
                                                 .planName("tfplan")
                                                 .json(true)
                                                 .build();

    CliResponse response = tgClient.show(request, logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SKIPPED);
    assertThat(response.getOutput())
        .isEqualTo(
            "Command terragrunt show -json is not supported by terraform version '0.11.1'. Minimum required version is v0.12.x.");
  }

  private void testInit(String backendFile, String expectedCommand)
      throws IOException, InterruptedException, TimeoutException {
    final TerragruntClient tgClient = createClient();
    final TerragruntCliRequest terragruntCliRequest = TerragruntCliRequest.builder()
                                                          .envVars(ImmutableMap.of("TEST_ENV", "TEST_VALUE"))
                                                          .timeoutInMillis(60000L)
                                                          .runType(TerragruntRunType.RUN_MODULE)
                                                          .args(TerragruntCliArgs.builder()
                                                                    .backendConfigFile(backendFile)
                                                                    .additionalCliArgs(new HashMap<>() {
                                                                      { put("INIT", "-lock-timeout=10s"); }
                                                                    })
                                                                    .build())
                                                          .build();
    setupCliResponse(CommandExecutionStatus.SUCCESS, "init-output", expectedCommand, terragruntCliRequest);

    CliResponse response = tgClient.init(terragruntCliRequest, logCallback);
    verifyCommandExecuted(expectedCommand, terragruntCliRequest);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo("init-output");
  }

  private void setupCliResponse(CommandExecutionStatus status, String output, String command,
      AbstractTerragruntCliRequest request) throws IOException, InterruptedException, TimeoutException {
    doReturn(CliResponse.builder()
                 .commandExecutionStatus(status)
                 .output(status == CommandExecutionStatus.SUCCESS ? output : "")
                 .error(status == CommandExecutionStatus.FAILURE ? output : "")
                 .build())
        .when(cliHelper)
        .executeCliCommand(any());
  }

  @SneakyThrows
  private void verifyCommandExecuted(String command, AbstractTerragruntCliRequest request) {
    ArgumentCaptor<CliCommandRequest> captor = ArgumentCaptor.forClass(CliCommandRequest.class);

    verify(cliHelper).executeCliCommand(captor.capture());
    CliCommandRequest cliCommandRequest = captor.getValue();
    assertThat(cliCommandRequest.getCommand()).isEqualTo(command);
    assertThat(cliCommandRequest.getEnvVariables()).isEqualTo(request.getEnvVars());
    assertThat(cliCommandRequest.getLogCallback()).isEqualTo(logCallback);
    assertThat(cliCommandRequest.getLoggingCommand()).isEqualTo(command);
    assertThat((LogCallback) on(cliCommandRequest.getErrorLogOutputStream()).get("executionLogCallback"))
        .isEqualTo(logCallback);
    assertThat((boolean) on(cliCommandRequest.getErrorLogOutputStream()).get("skipColorLogs"))
        .isEqualTo(request.isSkipColorLogs());
  }

  private TerragruntClient createClient() {
    return createClient(Version.parse("0.12.1"), Version.parse("0.39.1"));
  }

  private TerragruntClient createClient(Version tfVersion, Version tgVersion) {
    return TerragruntClientImpl.builder()
        .terraformVersion(tfVersion)
        .terragruntVersion(tgVersion)
        .cliHelper(cliHelper)
        .build();
  }
}