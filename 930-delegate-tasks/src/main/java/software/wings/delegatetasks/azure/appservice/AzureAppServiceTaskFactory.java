/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice;

import static io.harness.azure.model.AzureConstants.COMMAND_TYPE_BLANK_VALIDATION_MSG;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAppServiceTaskFactory {
  @Inject private Map<String, AbstractAzureAppServiceTaskHandler> azureAppServiceTaskTypeToTaskHandlerMap;

  public AbstractAzureAppServiceTaskHandler getAzureAppServiceTask(String commandType) {
    if (isBlank(commandType)) {
      throw new InvalidArgumentsException(COMMAND_TYPE_BLANK_VALIDATION_MSG);
    }
    return azureAppServiceTaskTypeToTaskHandlerMap.get(commandType);
  }
}
