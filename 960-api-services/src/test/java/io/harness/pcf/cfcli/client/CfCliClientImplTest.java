/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfcli.client;

import static io.harness.pcf.model.PcfConstants.APP_TOKEN;
import static io.harness.pcf.model.PcfConstants.AUTOSCALING_APPS_PLUGIN_NAME;
import static io.harness.pcf.model.PcfConstants.CF_HOME;
import static io.harness.pcf.model.PcfConstants.CF_PLUGIN_HOME;
import static io.harness.pcf.model.PcfConstants.HARNESS__ACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.pcf.model.PcfRouteType.PCF_ROUTE_TYPE_HTTP;
import static io.harness.pcf.model.PcfRouteType.PCF_ROUTE_TYPE_TCP;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.pcf.PcfUtils;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.cfcli.CfCliCommandType;
import io.harness.pcf.cfcli.resolver.CfCliCommandResolver;
import io.harness.pcf.cfsdk.CfSdkClientImpl;
import io.harness.pcf.cfsdk.CloudFoundryClientProvider;
import io.harness.pcf.cfsdk.CloudFoundryOperationsProvider;
import io.harness.pcf.cfsdk.ConnectionContextProvider;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CfRunPluginScriptRequestData;
import io.harness.pcf.model.PcfRouteInfo;
import io.harness.rule.Owner;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.cloudfoundry.operations.domains.Domain;
import org.cloudfoundry.operations.domains.Status;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfCliClientImplTest extends CategoryTest {
  public static final String BIN_BASH = "/bin/bash";
  public static final String CF_COMMAND_FOR_APP_LOG_TAILING = "cf logs <APP_NAME>";
  public static final String CF_COMMAND_FOR_CHECKING_AUTOSCALAR = "cf plugins | grep autoscaling-apps";
  public static final String APP_NAME = "APP_NAME";
  public static final String PATH = "path";

  // cfSdkClient
  @Spy private ConnectionContextProvider connectionContextProvider;
  @Spy private CloudFoundryClientProvider cloudFoundryClientProvider;
  @InjectMocks @Spy private CloudFoundryOperationsProvider cloudFoundryOperationsProvider;
  @InjectMocks @Spy private CfSdkClientImpl cfSdkClient;

  // cfCliClient
  @Mock private LogCallback logCallback;
  @InjectMocks @Spy private CfCliClientImpl cfCliClient;

  @Before
  public void setUp() {
    clearProperties();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPerformConfigureAutoscalar() throws Exception {
    CfCliClientImpl cfCliClient = spy(CfCliClientImpl.class);
    doNothing().when(logCallback).saveExecutionLog(any());
    doAnswer((Answer<Boolean>) invocation -> { return true; }).when(cfCliClient).doLogin(any(), any(), any());

    String AUTOSCALAR_MANIFEST = "autoscalar config";

    File file = new File("./autoscalar" + System.currentTimeMillis() + ".yml");
    file.createNewFile();
    FileIo.writeFile(file.getAbsolutePath(), AUTOSCALAR_MANIFEST.getBytes());

    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    ProcessResult processResult = mock(ProcessResult.class);
    doReturn(processResult).when(processExecutor).execute();
    doReturn(0).doReturn(0).doReturn(1).doReturn(1).when(processResult).getExitValue();

    doReturn(processExecutor).when(cfCliClient).createProcessExecutorForCfTask(anyLong(), anyString(), anyMap(), any());
    cfCliClient.performConfigureAutoscaler(
        CfAppAutoscalarRequestData.builder()
            .autoscalarFilePath(file.getAbsolutePath())
            .timeoutInMins(1)
            .cfRequestConfig(CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).build())
            .build(),
        logCallback);

    try {
      cfCliClient.performConfigureAutoscaler(
          CfAppAutoscalarRequestData.builder().autoscalarFilePath(file.getAbsolutePath()).timeoutInMins(1).build(),
          logCallback);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }

    doThrow(IOException.class).when(processExecutor).execute();
    try {
      cfCliClient.performConfigureAutoscaler(
          CfAppAutoscalarRequestData.builder().autoscalarFilePath(file.getAbsolutePath()).timeoutInMins(1).build(),
          logCallback);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }

    FileIo.deleteFileIfExists(file.getAbsolutePath());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCheckIfAppHasAutoscalarAttached() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(any());
    doAnswer((Answer<Boolean>) invocation -> { return true; }).when(cfCliClient).doLogin(any(), any(), any());

    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    ProcessResult processResult = mock(ProcessResult.class);

    doReturn(processResult).when(processExecutor).execute();
    doReturn("asd").doReturn(null).doReturn(EMPTY).when(processResult).outputUTF8();
    doReturn(processExecutor).when(cfCliClient).createProcessExecutorForCfTask(anyLong(), anyString(), anyMap(), any());

    CfAppAutoscalarRequestData autoscalarRequestData =
        CfAppAutoscalarRequestData.builder()
            .applicationName(APP_NAME)
            .applicationGuid(APP_NAME)
            .timeoutInMins(1)
            .cfRequestConfig(CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).build())
            .build();
    assertThat(cfCliClient.checkIfAppHasAutoscalerAttached(autoscalarRequestData, logCallback)).isTrue();

    assertThat(cfCliClient.checkIfAppHasAutoscalerAttached(autoscalarRequestData, logCallback)).isFalse();
    assertThat(cfCliClient.checkIfAppHasAutoscalerAttached(autoscalarRequestData, logCallback)).isFalse();
    doThrow(IOException.class).when(processExecutor).execute();
    try {
      cfCliClient.checkIfAppHasAutoscalerAttached(CfAppAutoscalarRequestData.builder().build(), logCallback);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testChangeAutoscalarState() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(any());
    doAnswer((Answer<Boolean>) invocation -> { return true; }).when(cfCliClient).doLogin(any(), any(), any());

    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    ProcessResult processResult = mock(ProcessResult.class);
    doReturn(processResult).when(processExecutor).execute();
    doReturn(0).doReturn(0).doReturn(1).doReturn(1).when(processResult).getExitValue();

    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                                .applicationName(APP_NAME)
                                                                .timeoutInMins(1)
                                                                .cfRequestConfig(CfRequestConfig.builder().build())
                                                                .build();
    doReturn(processExecutor).when(cfCliClient).createProcessExecutorForCfTask(anyLong(), anyString(), anyMap(), any());
    doReturn("cf").when(cfCliClient).generateChangeAutoscalerStateCommand(any(), anyBoolean());
    cfCliClient.changeAutoscalerState(cfAppAutoscalarRequestData, logCallback, true);

    try {
      cfCliClient.changeAutoscalerState(cfAppAutoscalarRequestData, logCallback, false);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }

    doThrow(IOException.class).when(processExecutor).execute();
    try {
      cfCliClient.changeAutoscalerState(cfAppAutoscalarRequestData, logCallback, true);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetAppAutoscalarEnvMapForCustomPlugin() throws Exception {
    Map<String, String> appAutoscalarEnvMapForCustomPlugin = cfCliClient.getAppAutoscalerEnvMapForCustomPlugin(
        CfAppAutoscalarRequestData.builder()
            .cfRequestConfig(CfRequestConfig.builder().endpointUrl("test").build())
            .configPathVar(PATH)
            .build());

    assertThat(appAutoscalarEnvMapForCustomPlugin.size()).isEqualTo(2);
    assertThat(appAutoscalarEnvMapForCustomPlugin.get(CF_HOME)).isEqualTo(PATH);
    assertThat(appAutoscalarEnvMapForCustomPlugin.containsKey(CF_PLUGIN_HOME)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateProccessExecutorForPcfTask() throws Exception {
    Map<String, String> appAutoscalarEnvMapForCustomPlugin = cfCliClient.getAppAutoscalerEnvMapForCustomPlugin(
        CfAppAutoscalarRequestData.builder()
            .cfRequestConfig(CfRequestConfig.builder().endpointUrl("test").build())
            .configPathVar(PATH)
            .build());
    doNothing().when(logCallback).saveExecutionLog(anyString());

    ProcessExecutor processExecutor = cfCliClient.createProcessExecutorForCfTask(
        1, CF_COMMAND_FOR_CHECKING_AUTOSCALAR, appAutoscalarEnvMapForCustomPlugin, logCallback);

    assertThat(processExecutor.getCommand()).containsExactly("/bin/bash", "-c", "cf plugins | grep autoscaling-apps");
    assertThat(processExecutor.getEnvironment()).isEqualTo(appAutoscalarEnvMapForCustomPlugin);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateExecutorForAutoscalarPluginCheck() {
    Map<String, String> appAutoscalarEnvMapForCustomPlugin = cfCliClient.getAppAutoscalerEnvMapForCustomPlugin(
        CfAppAutoscalarRequestData.builder()
            .cfRequestConfig(CfRequestConfig.builder().endpointUrl("test").build())
            .configPathVar(PATH)
            .build());
    doNothing().when(logCallback).saveExecutionLog(anyString());
    String command =
        CfCliCommandResolver.getCheckingPluginsCliCommand("cf", CfCliVersion.V6, AUTOSCALING_APPS_PLUGIN_NAME);

    ProcessExecutor processExecutor =
        PcfUtils.createExecutorForAutoscalarPluginCheck(command, appAutoscalarEnvMapForCustomPlugin);

    assertThat(processExecutor.getCommand()).containsExactly("/bin/bash", "-c", CF_COMMAND_FOR_CHECKING_AUTOSCALAR);
    assertThat(processExecutor.getEnvironment()).isEqualTo(appAutoscalarEnvMapForCustomPlugin);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateChangeAutoscalarStateCommand() throws Exception {
    CfAppAutoscalarRequestData autoscalarRequestData =
        CfAppAutoscalarRequestData.builder()
            .applicationName(APP_NAME)
            .cfRequestConfig(CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).build())
            .build();
    String command = cfCliClient.generateChangeAutoscalerStateCommand(autoscalarRequestData, true);
    assertThat(command).isEqualTo("cf enable-autoscaling " + APP_NAME);
    command = cfCliClient.generateChangeAutoscalerStateCommand(autoscalarRequestData, false);
    assertThat(command).isEqualTo("cf disable-autoscaling " + APP_NAME);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testLogInForAppAutoscalarCliCommand() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(anyString());

    CfAppAutoscalarRequestData autoscalarRequestData =
        CfAppAutoscalarRequestData.builder().cfRequestConfig(CfRequestConfig.builder().loggedin(true).build()).build();
    cfCliClient.logInForAppAutoscalarCliCommand(autoscalarRequestData, logCallback);
    verify(cfCliClient, never()).doLogin(any(), any(), any());

    doReturn(true).when(cfCliClient).doLogin(any(), any(), any());
    autoscalarRequestData.getCfRequestConfig().setLoggedin(false);
    cfCliClient.logInForAppAutoscalarCliCommand(autoscalarRequestData, logCallback);
    verify(cfCliClient, times(1)).doLogin(any(), any(), any());
  }

  @Test(expected = Test.None.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_runPcfPluginScript()
      throws PivotalClientApiException, InterruptedException, TimeoutException, IOException {
    final CfRunPluginScriptRequestData requestData =
        CfRunPluginScriptRequestData.builder()
            .cfRequestConfig(CfRequestConfig.builder().timeOutIntervalInMins(5).build())
            .workingDirectory("/tmp/abc")
            .build();

    doNothing().when(logCallback).saveExecutionLog(anyString());
    doReturn(true).when(cfCliClient).doLogin(any(CfRequestConfig.class), any(LogCallback.class), anyString());
    doReturn(new ProcessResult(0, null))
        .when(cfCliClient)
        .getProcessResult(nullable(String.class), any(), anyInt(), any());
    cfCliClient.runPcfPluginScript(requestData, logCallback);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void test_getProcessExecutorForLogTailing() {
    doNothing().when(logCallback).saveExecutionLog(anyString());
    ProcessExecutor processExecutorForLogTailing = cfCliClient.getProcessExecutorForLogTailing(
        CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).applicationName(APP_NAME).build(),
        logCallback);

    assertThat(processExecutorForLogTailing.getCommand())
        .containsExactly(BIN_BASH, "-c", CF_COMMAND_FOR_APP_LOG_TAILING.replace(APP_TOKEN, APP_NAME));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void test_tailLogsForPcf() throws Exception {
    reset(cfCliClient);
    doNothing().when(logCallback).saveExecutionLog(anyString());

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().applicationName(APP_NAME).loggedin(true).build();
    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    StartedProcess startedProcess = mock(StartedProcess.class);
    doReturn(processExecutor).when(cfCliClient).getProcessExecutorForLogTailing(any(), any());
    doReturn(startedProcess).when(processExecutor).start();

    StartedProcess startedProcessRet = cfCliClient.tailLogsForPcf(cfRequestConfig, logCallback);
    assertThat(startedProcess).isEqualTo(startedProcessRet);
    verify(cfCliClient, never()).doLogin(any(), any(), any());

    cfRequestConfig.setLoggedin(false);
    doReturn(true).when(cfCliClient).doLogin(any(), any(), any());
    startedProcessRet = cfCliClient.tailLogsForPcf(cfRequestConfig, logCallback);
    assertThat(startedProcess).isEqualTo(startedProcessRet);
    verify(cfCliClient, times(1)).doLogin(any(), any(), any());

    reset(cfCliClient);
    doReturn(false).when(cfCliClient).doLogin(any(), any(), any());
    try {
      cfCliClient.tailLogsForPcf(cfRequestConfig, logCallback);
    } catch (PivotalClientApiException e) {
      assertThat(e.getCause().getMessage()).contains("Failed to login");
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void test_doLogin() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(anyString());
    doReturn(0).when(cfCliClient).executeCommand(anyString(), any(), any(), any());
    Map<String, String> env = new HashMap<>();
    env.put("CF_HOME", "CF_HOME");
    doReturn(env).when(cfCliClient).getEnvironmentMapForCfExecutor(anyString(), anyString());
    CfRequestConfig config = CfRequestConfig.builder()
                                 .endpointUrl("api.pivotal.io")
                                 .userName("user")
                                 .cfCliPath("cf")
                                 .cfCliVersion(CfCliVersion.V6)
                                 .password("passwd")
                                 .orgName("org with space")
                                 .spaceName("space with name")
                                 .build();
    cfCliClient.doLogin(config, logCallback, "conf");
    verify(cfCliClient, times(3)).executeCommand(anyString(), anyMap(), any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void test_extractRouteInfoFromPath() throws Exception {
    Set<String> domains = new HashSet<>(asList("example.com", "z.example.com"));

    PcfRouteInfo info = cfCliClient.extractRouteInfoFromPath(domains, "example.com:5000");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_TCP);
    assertThat(info.getDomain()).isEqualTo("example.com");
    assertThat(info.getPort()).isEqualTo("5000");

    info = cfCliClient.extractRouteInfoFromPath(domains, "cdp-10515.z.example.com/path");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_HTTP);
    assertThat(info.getDomain()).isEqualTo("z.example.com");
    assertThat(info.getHostName()).isEqualTo("cdp-10515");
    assertThat(info.getPath()).isEqualTo("path");

    info = cfCliClient.extractRouteInfoFromPath(domains, "cdp-10515.z.example.com");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_HTTP);
    assertThat(info.getDomain()).isEqualTo("z.example.com");
    assertThat(info.getHostName()).isEqualTo("cdp-10515");
    assertThat(info.getPath()).isNullOrEmpty();

    info = cfCliClient.extractRouteInfoFromPath(domains, "z.example.com");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_HTTP);
    assertThat(info.getDomain()).isEqualTo("z.example.com");
    assertThat(info.getHostName()).isNullOrEmpty();
    assertThat(info.getPath()).isNullOrEmpty();

    info = cfCliClient.extractRouteInfoFromPath(
        new HashSet<>(asList("example.com", "domain.example.com")), "my-domain.example.com");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_HTTP);
    assertThat(info.getDomain()).isEqualTo("example.com");
    assertThat(info.getHostName()).isEqualTo("my-domain");
    assertThat(info.getPath()).isNullOrEmpty();

    info = cfCliClient.extractRouteInfoFromPath(
        new HashSet<>(asList("example.com", "domain.example.com")), "my-domain.example.com/path");
    assertThat(info.getType()).isEqualTo(PCF_ROUTE_TYPE_HTTP);
    assertThat(info.getDomain()).isEqualTo("example.com");
    assertThat(info.getHostName()).isEqualTo("my-domain");
    assertThat(info.getPath()).isEqualTo("path");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void test_executeRoutesOperationForApplicationUsingCli() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(anyString());
    CfRequestConfig requestConfig = CfRequestConfig.builder()
                                        .useCFCLI(true)
                                        .cfCliPath("cf")
                                        .cfCliVersion(CfCliVersion.V6)
                                        .loggedin(false)
                                        .cfHomeDirPath("/cf/home")
                                        .applicationName("App_BG_00")
                                        .build();
    doReturn(true).when(cfCliClient).doLogin(any(), any(), anyString());
    List<Domain> domains = singletonList(Domain.builder().name("example.com").id("id").status(Status.OWNED).build());
    doReturn(domains).when(cfSdkClient).getAllDomainsForSpace(any());
    Map<String, String> envMap = new HashMap<>();
    envMap.put("CF_HOME", "/cf/home");
    PcfRouteInfo info = PcfRouteInfo.builder()
                            .type(PCF_ROUTE_TYPE_HTTP)
                            .domain("example.com")
                            .hostName("cdp-10515")
                            .path("path")
                            .build();
    doReturn(info).when(cfCliClient).extractRouteInfoFromPath(any(), anyString());
    doReturn(0).when(cfCliClient).executeCommand(anyString(), any(), any(), any());
    cfCliClient.executeRoutesOperationForApplicationUsingCli(
        CfCliCommandType.MAP_ROUTE, requestConfig, singletonList("cdp-10515.z.example.com/path"), logCallback);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(cfCliClient).executeCommand(captor.capture(), any(), any(), any());
    String value = captor.getValue();
    assertThat(value).isEqualTo("cf map-route App_BG_00 example.com --hostname cdp-10515 --path path");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSetEnvVariablesForApplication() throws Exception {
    reset(cfCliClient);
    doReturn(false).when(cfCliClient).doLogin(any(), any(), any());

    CfRequestConfig cfRequestConfig = getCfRequestConfigWithCfCliPath();
    doNothing().when(logCallback).saveExecutionLog(any());

    // login failed
    try {
      cfCliClient.setEnvVariablesForApplication(null, cfRequestConfig, logCallback);
      fail("should not reach here");
    } catch (Exception e) {
      assertThat(e.getMessage()).contains("Failed to login when performing: set-env");
    }

    // check command generated
    doReturn(true).when(cfCliClient).doLogin(any(), any(), any());
    doReturn(0).when(cfCliClient).executeCommand(any(), anyMap(), any(), any());
    cfCliClient.setEnvVariablesForApplication(
        Collections.singletonMap(HARNESS__STATUS__IDENTIFIER, HARNESS__ACTIVE__IDENTIFIER), cfRequestConfig,
        logCallback);
    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(cfCliClient).executeCommand(commandCaptor.capture(), anyMap(), any(), any());

    assertThat(commandCaptor.getValue()).isEqualTo("cf set-env app HARNESS__STATUS__IDENTIFIER ACTIVE");

    // Command execution failed, returned 1
    doReturn(1).when(cfCliClient).executeCommand(anyString(), anyMap(), any(), any());
    try {
      cfCliClient.setEnvVariablesForApplication(
          Collections.singletonMap(HARNESS__STATUS__IDENTIFIER, HARNESS__ACTIVE__IDENTIFIER), cfRequestConfig,
          logCallback);
      fail("should not reach here");
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
      assertThat(e.getMessage()).contains("Failed to set env var: <HARNESS__STATUS__IDENTIFIER:ACTIVE>");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUnsetEnvVariablesForApplication() throws Exception {
    reset(cfCliClient);
    doReturn(false).when(cfCliClient).doLogin(any(), any(), any());

    CfRequestConfig cfRequestConfig = getCfRequestConfigWithCfCliPath();
    doNothing().when(logCallback).saveExecutionLog(anyString());

    // login failed
    try {
      cfCliClient.unsetEnvVariablesForApplication(null, cfRequestConfig, logCallback);
      fail("should not reach here");
    } catch (Exception e) {
      assertThat(e.getMessage()).contains("Failed to login when performing: set-env");
    }

    // check command generated
    doReturn(true).when(cfCliClient).doLogin(any(), any(), any());
    doReturn(0).when(cfCliClient).executeCommand(any(), anyMap(), any(), any());
    cfCliClient.unsetEnvVariablesForApplication(
        Collections.singletonList(HARNESS__STATUS__IDENTIFIER), cfRequestConfig, logCallback);
    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(cfCliClient).executeCommand(commandCaptor.capture(), anyMap(), any(), any());

    assertThat(commandCaptor.getValue()).isEqualTo("cf unset-env app HARNESS__STATUS__IDENTIFIER");

    // Command execution failed, returned 1
    doReturn(1).when(cfCliClient).executeCommand(anyString(), anyMap(), any(), any());
    try {
      cfCliClient.unsetEnvVariablesForApplication(
          Collections.singletonList(HARNESS__STATUS__IDENTIFIER), cfRequestConfig, logCallback);
      fail("should not reach here");
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
      assertThat(e.getMessage()).contains("Failed to unset env var: HARNESS__STATUS__IDENTIFIER");
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testcheckIfAppHasAutoscalarWithExpectedState() throws Exception {
    doNothing().when(logCallback).saveExecutionLog(any());
    doAnswer((Answer<Boolean>) invocation -> { return true; }).when(cfCliClient).doLogin(any(), any(), any());

    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    ProcessResult processResult = mock(ProcessResult.class);

    doReturn(processResult).when(processExecutor).execute();
    doReturn(" true ")
        .doReturn(" string not containing trueValue")
        .doReturn(null)
        .doReturn(EMPTY)
        .doReturn(" string containing true for expectedEnable false ")
        .when(processResult)
        .outputUTF8();
    doReturn(processExecutor).when(cfCliClient).createProcessExecutorForCfTask(anyLong(), anyString(), anyMap(), any());

    CfAppAutoscalarRequestData autoscalarRequestDataWithExpectedEnabletrue =
        CfAppAutoscalarRequestData.builder()
            .applicationName(APP_NAME)
            .applicationGuid(APP_NAME)
            .expectedEnabled(true)
            .timeoutInMins(1)
            .cfRequestConfig(CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).build())
            .build();
    CfAppAutoscalarRequestData autoscalarRequestDataWithExpectedEnableFalse =
        CfAppAutoscalarRequestData.builder()
            .applicationName(APP_NAME)
            .applicationGuid(APP_NAME)
            .expectedEnabled(false)
            .timeoutInMins(1)
            .cfRequestConfig(CfRequestConfig.builder().cfCliPath("cf").cfCliVersion(CfCliVersion.V6).build())
            .build();

    assertThat(
        cfCliClient.checkIfAppHasAutoscalerWithExpectedState(autoscalarRequestDataWithExpectedEnabletrue, logCallback))
        .isTrue();

    assertThat(
        cfCliClient.checkIfAppHasAutoscalerWithExpectedState(autoscalarRequestDataWithExpectedEnabletrue, logCallback))
        .isFalse();
    assertThat(
        cfCliClient.checkIfAppHasAutoscalerWithExpectedState(autoscalarRequestDataWithExpectedEnabletrue, logCallback))
        .isFalse();

    assertThat(
        cfCliClient.checkIfAppHasAutoscalerWithExpectedState(autoscalarRequestDataWithExpectedEnableFalse, logCallback))
        .isFalse();

    try {
      cfCliClient.checkIfAppHasAutoscalerWithExpectedState(CfAppAutoscalarRequestData.builder().build(), logCallback);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testperformCfPushUsingCli() throws Exception {
    CfCreateApplicationRequestData requestData = mock(CfCreateApplicationRequestData.class);
    doNothing().when(logCallback).saveExecutionLog(anyString());

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                                          .applicationName("app")
                                          .userName("username")
                                          .password("password")
                                          .loggedin(true)
                                          .useCFCLI(true)
                                          .build();

    doReturn(cfRequestConfig).when(requestData).getCfRequestConfig();
    doReturn("path").when(requestData).getManifestFilePath();
    doReturn(true).when(cfCliClient).doLogin(any(), any(), anyString());

    try {
      cfCliClient.pushAppByCli(requestData, logCallback);
    } catch (Exception e) {
      ArgumentCaptor<CfCreateApplicationRequestData> requestDataArgumentCaptorCaptor =
          ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);

      verify(cfCliClient).getEnvironmentMapForCfPush(requestDataArgumentCaptorCaptor.capture());
      assertThat(requestDataArgumentCaptorCaptor.getValue()).isEqualTo(requestData);

      assertThat(e instanceof PivotalClientApiException).isTrue();
      assertThat(e.getMessage())
          .isEqualTo("Exception occurred while creating Application: app, Error: App creation process Failed");
    }
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetEnvironmentMapForPcfExecutorWithNoProxyPort() {
    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "80");
    Map<String, String> environmentProperties = cfCliClient.getEnvironmentMapForCfExecutor("app.host.io", "test");
    assertThat(environmentProperties.size()).isEqualTo(2);
    assertThat(environmentProperties.get("https_proxy")).isEqualTo("http://testProxyHost");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetEnvironmentMapForPcfExecutorWithProxyPort() {
    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "8080");
    Map<String, String> environmentProperties = cfCliClient.getEnvironmentMapForCfExecutor("app.host.io", "test");
    assertThat(environmentProperties.size()).isEqualTo(2);
    assertThat(environmentProperties.get("https_proxy")).isEqualTo("http://testProxyHost:8080");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetEnvironmentMapForPcfExecutorWithAuthDetail() {
    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "8080");
    System.setProperty("http.proxyUser", "username");
    System.setProperty("http.proxyPassword", "password");
    Map<String, String> environmentProperties = cfCliClient.getEnvironmentMapForCfExecutor("app.host.io", "test");
    assertThat(environmentProperties.size()).isEqualTo(2);
    assertThat(environmentProperties.get("https_proxy")).isEqualTo("http://username:password@testProxyHost:8080");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetEnvironmentMapForNoProxyHost() {
    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "8080");
    System.setProperty("http.proxyUser", "username");
    System.setProperty("http.proxyPassword", "password");
    System.setProperty("http.nonProxyHosts", "*.host.io");
    Map<String, String> environmentProperties = cfCliClient.getEnvironmentMapForCfExecutor("app.host.io", "test");
    assertThat(environmentProperties.size()).isEqualTo(1);
    assertThat(environmentProperties.get("https_proxy")).isNull();
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testAppSetupTimeoutUsedInAutoscaling() throws PivotalClientApiException {
    int timeout = 2903;
    CfRequestConfig pcfRequestConfig = getCfRequestConfigWithCfCliPath();
    pcfRequestConfig.setTimeOutIntervalInMins(timeout);
    pcfRequestConfig.setLoggedin(true);
    doNothing().when(logCallback).saveExecutionLog(anyString());

    cfCliClient.checkIfAppHasAutoscalerAttached(
        CfAppAutoscalarRequestData.builder().cfRequestConfig(pcfRequestConfig).build(), logCallback);
    cfCliClient.checkIfAppHasAutoscalerWithExpectedState(
        CfAppAutoscalarRequestData.builder().cfRequestConfig(pcfRequestConfig).build(), logCallback);

    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(cfCliClient, times(2)).createProcessExecutorForCfTask(captor.capture(), any(), any(), any());

    List<Long> capturedPeople = captor.getAllValues();
    assertThat(capturedPeople.get(0)).isEqualTo(timeout);
    assertThat(capturedPeople.get(1)).isEqualTo(timeout);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testAddCfCliToPATHSystemVariable() {
    String cfCliPath = "/Users/user.name/cf_cli/v7/cf";
    String pathSystemVariable = cfCliClient.getFullDirectoryPathNoEndSeparator(cfCliPath);
    assertThat(pathSystemVariable).isNotBlank();
    assertThat(pathSystemVariable).isEqualTo("/Users/user.name/cf_cli/v7");

    cfCliPath = "/usr/local/bin/v7/cf";
    pathSystemVariable = cfCliClient.getFullDirectoryPathNoEndSeparator(cfCliPath);
    assertThat(pathSystemVariable).isNotBlank();
    assertThat(pathSystemVariable).isEqualTo("/usr/local/bin/v7");

    cfCliPath = "/usr/bin/v6/cf";
    pathSystemVariable = cfCliClient.getFullDirectoryPathNoEndSeparator(cfCliPath);
    assertThat(pathSystemVariable).isNotBlank();
    assertThat(pathSystemVariable).isEqualTo("/usr/bin/v6");

    cfCliPath = "/custom/path/to/v6/cf";
    pathSystemVariable = cfCliClient.getFullDirectoryPathNoEndSeparator(cfCliPath);
    assertThat(pathSystemVariable).isNotBlank();
    assertThat(pathSystemVariable).isEqualTo("/custom/path/to/v6");
  }

  private CfRequestConfig getCfRequestConfigWithCfCliPath() {
    return CfRequestConfig.builder()
        .useCFCLI(true)
        .cfCliPath("cf")
        .cfCliVersion(CfCliVersion.V6)
        .loggedin(false)
        .applicationName("app")
        .build();
  }

  private void clearProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }
}
