/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serverless.*;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.Spy;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessAwsCommandTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessTaskPluginHelper serverlessTaskPluginHelper;
  @Mock private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @InjectMocks private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  private ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema =
      ServerlessAwsLambdaManifestSchema.builder().plugins(Arrays.asList("asfd", "asfdasdf")).build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams = ServerlessDelegateTaskParams.builder().build();
  @Mock private LogCallback logCallback;
  @Mock private ServerlessClient serverlessClient;
  @Mock private ServerlessUtils serverlessUtils;
  @Mock private ConfigCredentialCommand command;
  @Mock private ProcessExecutor processExecutor;

  private static String CLOUDFORMATION_UPDATE_FILE = "cloudformation-template-update-stack.json";
  private static String cloudDir = "cloudDir";

//  @Test
//  @Owner(developers = ALLU_VAMSI)
//  @Category(UnitTests.class)
//  public void configCredentialTest() throws IOException, InterruptedException, TimeoutException {
//    doReturn(command).when(serverlessClient).configCredential();
//    doNothing().when(command).provider("provider");
//    doNothing().when(command).key("accessKey");
//    doNothing().when(command).provider("secretKey");
//    doNothing().when(command).overwrite(true);
//    ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder()
//                                                         .provider("provider").accessKey("accessKey").secretKey("secretKey")
//                                                         .build();
//    ServerlessCliResponse serverlessCliResponse =
//            ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output("output").build();
//    //Files.createFile(Paths.get(tempDirPath, "test.yaml"));
//    ServerlessDelegateTaskParams serverlessDelegateTaskParams = ServerlessDelegateTaskParams.builder()
//            .workingDirectory("./repository/serverless/").serverlessClientPath("/path").build();
//    //ProcessResult processResult = new ProcessResult(3,new ProcessOutput(new byte[]{0x05}));
//     //AbstractExecutable command = Mockito.mock(AbstractExecutable.class, Mockito.CALLS_REAL_METHODS);
//   // doReturn(processResult).when(serverlessUtils).executeScript("/dir/","command",any(),any(),30);
////    System.out.println("hifwerdvc");
////    System.out.println(abstractExecutable);
//    //ConfigCredentialCommand command = spy(new ConfigCredentialCommand(serverlessClient));
//    //doReturn(processResult).when(processExecutor).execute();
//
//    doReturn(serverlessCliResponse).when(command).execute(eq("./repository/serverless/"),any(OutputStream.class),any(OutputStream.class),anyBoolean(),anyInt());
//    serverlessAwsCommandTaskHelper.configCredential(serverlessClient,
//             serverlessAwsLambdaConfig,  serverlessDelegateTaskParams,
//            logCallback, true, 30);
//  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPreviousVersionTimeStamp() {
    String output = "Warning: Invalid configuration encountered\n"
        + "  at 'provider.tracing': must be object\n"
        + "\n"
        + "Learn more about configuration validation here: http://slss.io/configuration-validation\n"
        +

        "2022-03-11 08:48:51 UTC\n"
        + "Timestamp: 1646988531400\n"
        + "Files:\n"
        + "  - aws-node-http-api-project-1.zip\n"
        + "  - compiled-cloudformation-template.json\n"
        + "  - custom-resources.zip\n"
        + "  - serverless-state.json\n"
        + "2022-03-11 08:58:16 UTC\n"
        + "Timestamp: 1646989096845\n"
        + "Files:\n"
        + "  - aws-node-http-api-project-1.zip\n"
        + "  - compiled-cloudformation-template.json\n"
        + "  - custom-resources.zip\n"
        + "  - serverless-state.json";

    String serverlessManifest = "service: ABC";
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        ServerlessAwsLambdaInfraConfig.builder().region("us-east-2").stage("dev").build();
    ServerlessDeployRequest serverlessDeployRequest = ServerlessDeployRequest.builder()
                                                          .manifestContent(serverlessManifest)
                                                          .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
                                                          .build();

    List<String> timeStamps = serverlessAwsCommandTaskHelper.getDeployListTimeStamps(output);
    assertThat(timeStamps).contains("1646988531400", "1646989096845");
    doReturn("abc1646988531400xyz").when(awsCFHelperServiceDelegate).getStackBody(any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    assertThat(serverlessAwsCommandTaskHelper.getPreviousVersionTimeStamp(timeStamps, null, serverlessDeployRequest))
        .isEqualTo(Optional.of("1646988531400"));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void installPluginsSuccessTest() throws Exception {
    ServerlessCliResponse serverlessCliResponse =
        ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        ServerlessAwsLambdaManifestConfig.builder().configOverridePath("c").build();
    doReturn(serverlessCliResponse)
        .when(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfd", logCallback, 10, "c");
    doReturn(serverlessCliResponse)
        .when(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfdasdf", logCallback, 10, "c");
    serverlessAwsCommandTaskHelper.installPlugins(serverlessAwsLambdaManifestSchema, serverlessDelegateTaskParams,
        logCallback, serverlessClient, 10, serverlessAwsLambdaManifestConfig);
    verify(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfd", logCallback, 10, "c");
    verify(serverlessTaskPluginHelper)
        .installServerlessPlugin(serverlessDelegateTaskParams, serverlessClient, "asfdasdf", logCallback, 10, "c");
  }

    @Test
    @Owner(developers = ALLU_VAMSI)
    @Category(UnitTests.class)
    public void fetchFunctionOutputFromCloudFormationTemplateEmptyTest() throws Exception {

      FileIo.createDirectoryIfDoesNotExist("cloudDir");
      File cloudFile = new File("cloudDir/"+CLOUDFORMATION_UPDATE_FILE);
      cloudFile.createNewFile();
      System.out.println("hi vamsi");
      System.out.println(Paths.get(cloudDir, CLOUDFORMATION_UPDATE_FILE).toString());
      serverlessAwsCommandTaskHelper.fetchFunctionOutputFromCloudFormationTemplate("cloudDir");
    }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchFunctionOutputFromCloudFormationTemplateTest() throws Exception {
    FileIo.createDirectoryIfDoesNotExist("cloudDir");
//    File cloudFile = new File("cloudDir/"+CLOUDFORMATION_UPDATE_FILE);
//    cloudFile.createNewFile();
    JSONObject jsonObj = new JSONObject();
    try {
      jsonObj.put("name", "Google");
      jsonObj.put("employees", 140000);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    FileWriter cloudFile = new FileWriter("cloudDir/"+CLOUDFORMATION_UPDATE_FILE);
    cloudFile.write(jsonObj.toString());
    System.out.println("hi vamsi");
    System.out.println(Paths.get(cloudDir, CLOUDFORMATION_UPDATE_FILE).toString());
    serverlessAwsCommandTaskHelper.fetchFunctionOutputFromCloudFormationTemplate("cloudDir");
  }

}
