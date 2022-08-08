/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;

import software.wings.WingsBaseTest;
import software.wings.beans.LogCollectionInfo;
import software.wings.beans.LogResponseMapping;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.sm.StateType;
import software.wings.verification.log.CustomLogCVServiceConfiguration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVConfigurationServiceImplTestBase extends WingsBaseTest {
  public static CustomLogCVServiceConfiguration createCustomLogsConfig(String accountId) throws Exception {
    CustomLogCVServiceConfiguration configuration =
        CustomLogCVServiceConfiguration.builder()
            .logCollectionInfo(LogCollectionInfo.builder()
                                   .collectionUrl("testUrl ${start_time} and ${end_time}")
                                   .method(Method.GET)
                                   .responseType(ResponseType.JSON)
                                   .responseMapping(LogResponseMapping.builder()
                                                        .hostJsonPath("hostname")
                                                        .logMessageJsonPath("message")
                                                        .timestampJsonPath("@timestamp")
                                                        .build())
                                   .build())
            .build();

    configuration.setAccountId(accountId);
    configuration.setStateType(StateType.STACK_DRIVER);
    configuration.setEnvId(generateUuid());
    configuration.setName("StackDriver");
    configuration.setConnectorId(generateUuid());
    configuration.setServiceId(generateUuid());
    configuration.setStateType(StateType.LOG_VERIFICATION);
    configuration.setQuery(generateUUID());
    configuration.setBaselineStartMinute(16);
    configuration.setBaselineEndMinute(30);
    return configuration;
  }
}
