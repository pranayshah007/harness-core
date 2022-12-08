/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.ecs.EcsRollingDeployCommandTaskHandler;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfSwapRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.ROLLBACK_OPERATOR;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static software.wings.beans.LogHelper.color;

public class CfSwapRouteCommandTaskHandlerNGTest extends CategoryTest {
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
    @Mock TasTaskHelperBase tasTaskHelperBase;
    @Mock CfCommandTaskHelperNG cfCommandTaskHelperNG;
    @Mock TasNgConfigMapper tasNgConfigMapper;
    @Mock CfDeploymentManager cfDeploymentManager;

    @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
    @Mock LogCallback deployLogCallback;
    @Spy @InjectMocks private CfSwapRollbackCommandTaskHandlerNG cfSwapRollbackCommandTaskHandlerNG;

    @Test
    @Owner(developers = PIYUSH_BHUWALKA)
    @Category(UnitTests.class)
    public void executeTaskInternalTestWhenRollbackOccurredBeforeSwapRoute() throws Exception {

        String commandName = "commandName";
        String organization = "org";
        String space = "space";
        TasInfraConfig tasInfraConfig = TasInfraConfig.builder().organization(organization).space(space).build();
        String prefix = "prefix";
        CfAppSetupTimeDetails newApplicationDetails = CfAppSetupTimeDetails.builder().build();
        String id = "id";
        CfAppSetupTimeDetails appDetailToBeDownsized = CfAppSetupTimeDetails.builder().applicationGuid(id).build();
        CfServiceData cfServiceData = CfServiceData.builder().desiredCount(1).previousCount(2).build();
        CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG = CfSwapRollbackCommandRequestNG.builder()
                .newApplicationDetails(newApplicationDetails).timeoutIntervalInMin(10).instanceData(Arrays.asList(cfServiceData)).appDetailsToBeDownsized(Arrays.asList(appDetailToBeDownsized)).cfCliVersion(CfCliVersion.V7)
                .tasInfraConfig(tasInfraConfig).commandName(commandName).cfAppNamePrefix(prefix).swapRouteOccured(false).build();
        CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
        doReturn(deployLogCallback)
                .when(tasTaskHelperBase)
                .getLogCallback(iLogStreamingTaskClient, commandName, true, commandUnitsProgress);

        String path = "./test" + System.currentTimeMillis();
        File workingDirectory = new File(path);
        doReturn(workingDirectory).when(cfCommandTaskHelperNG).generateWorkingDirectoryForDeployment();
        char[] userName = {'a'};
        String endpointUrl = "url";
        char[] password = {'p'};

        CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(userName).endpointUrl(endpointUrl).password(password).build();
        doReturn(cfConfig).when(tasNgConfigMapper).mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

        String cfCliPath = "cli";
        doReturn(cfCliPath).when(cfCommandTaskHelperNG).getCfCliPathOnDelegate(
                true, cfRollbackCommandRequestNG.getCfCliVersion());

        CfRequestConfig cfRequestConfig = CfRequestConfig.builder()
                .userName(String.valueOf(cfConfig.getUserName()))
                .endpointUrl(cfConfig.getEndpointUrl())
                .password(String.valueOf(cfConfig.getPassword()))
                .orgName(tasInfraConfig.getOrganization())
                .spaceName(tasInfraConfig.getSpace())
                .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
                .cfHomeDirPath(workingDirectory.getAbsolutePath())
                .cfCliPath(cfCliPath)
                .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
                .build();

        CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData =
                CfRouteUpdateRequestConfigData.builder()
                        .isRollback(true)
                        .existingApplicationDetails(cfRollbackCommandRequestNG.getExistingApplicationDetails())
                        .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
                        .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApps())
                        .existingApplicationNames(cfRollbackCommandRequestNG.getAppDetailsToBeDownsized()
                                .stream()
                                .map(CfAppSetupTimeDetails::getApplicationName)
                                .collect(toList()))
                        .tempRoutes(cfRollbackCommandRequestNG.getTempRouteMaps())
                        .skipRollback(false)
                        .isStandardBlueGreen(true)
                        .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails())
                        .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
                        .versioningChanged(false)
                        .nonVersioning(true)
                        .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
                        .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
                        .isMapRoutesOperation(false)
                        .build();

        CfInBuiltVariablesUpdateValues updateValues = CfInBuiltVariablesUpdateValues.builder().build();
        doReturn(updateValues).when(cfCommandTaskHelperNG).performAppRenaming(
                ROLLBACK_OPERATOR, pcfRouteUpdateConfigData, cfRequestConfig, deployLogCallback);

        String appName = "name";
        List<String> newApps = Arrays.asList(appName);
        doReturn(newApps).when(cfCommandTaskHelperNG).getAppNameBasedOnGuid(
                cfRequestConfig, pcfRouteUpdateConfigData.getCfAppNamePrefix(), pcfRouteUpdateConfigData.getNewApplicationDetails().getApplicationGuid());

        List<CfServiceData> upsizeList = new ArrayList<>();
        doReturn(upsizeList).when(cfCommandTaskHelperNG).getUpsizeListForRollback(cfRollbackCommandRequestNG);

        String appName1 = "app";
        List<String> apps = Arrays.asList(appName1);
        doReturn(apps).when(cfCommandTaskHelperNG).getAppNameBasedOnGuidForBlueGreenDeployment(cfRequestConfig, prefix, id);

        Mockito.mockStatic(FileIo.class);
        CfCommandResponseNG cfRollbackCommandResponseNG = cfSwapRollbackCommandTaskHandlerNG.executeTaskInternal(cfRollbackCommandRequestNG, iLogStreamingTaskClient,
                commandUnitsProgress);
        assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    }
}
