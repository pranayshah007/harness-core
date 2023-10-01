/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;

import software.wings.beans.AwsConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.TaskType;
import software.wings.beans.dto.SettingAttribute;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.aws.model.AwsRequest;
import software.wings.service.intfc.DelegateService;
import software.wings.settings.SettingValue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._910_DELEGATE_SERVICE_DRIVER)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class DelegateInvocationHandler implements InvocationHandler {
  private DelegateService delegateService;
  private SyncTaskContext syncTaskContext;
  private TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  public DelegateInvocationHandler(SyncTaskContext syncTaskContext, DelegateService delegateService,
      TaskSetupAbstractionHelper taskSetupAbstractionHelper) {
    this.delegateService = delegateService;
    this.syncTaskContext = syncTaskContext;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    TaskType taskType = method.getAnnotation(DelegateTaskType.class).value();
    Object[] delegateArguments = new Object[args.length + 2];
    delegateArguments[0] = proxy.getClass().getInterfaces()[0].getName();
    delegateArguments[1] = method.getName();
    System.arraycopy(args, 0, delegateArguments, 2, args.length);
    DelegateTaskBuilder builder = getDelegateTaskBuilderV2(taskType, delegateArguments);

    String awsConfigTag = getAwsConfigTags(args);
    if (isNotEmpty(awsConfigTag)) {
      builder.tags(singletonList(awsConfigTag));
    }
    RemoteMethodReturnValueData returnValueData = delegateService.executeTaskV2(builder.build());
    if (returnValueData.getException() != null) {
      throw returnValueData.getException();
    } else {
      return returnValueData.getReturnValue();
    }
  }

  private DelegateTaskBuilder getDelegateTaskBuilderV2(TaskType taskType, Object[] delegateArguments) {
    return DelegateTask.builder()
        .taskDataV2(TaskDataV2.builder()
                        .async(false)
                        .taskType(taskType.name())
                        .parameters(delegateArguments)
                        .timeout(syncTaskContext.getTimeout())
                        .build())
        .accountId(syncTaskContext.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, syncTaskContext.getAppId())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, syncTaskContext.getEnvId())
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
            syncTaskContext.getEnvType() == null ? null : syncTaskContext.getEnvType().name())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, syncTaskContext.getInfrastructureMappingId())
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, syncTaskContext.getServiceId())
        .setupAbstraction(OWNER,
            taskSetupAbstractionHelper.getOwner(syncTaskContext.getAccountId(), syncTaskContext.getOrgIdentifier(),
                syncTaskContext.getProjectIdentifier()))
        .setupAbstraction(NG, syncTaskContext.isNgTask() ? "true" : "false")
        .tags(syncTaskContext.getTags());
  }

  private String getAwsConfigTags(Object[] args) {
    if (isNotEmpty(args)) {
      for (Object arg : args) {
        if (arg instanceof AwsConfig) {
          return ((AwsConfig) arg).getTag();
        } else if (arg instanceof SettingAttribute) {
          SettingValue settingValue = ((SettingAttribute) arg).getValue();
          if (settingValue instanceof AwsConfig) {
            return ((AwsConfig) settingValue).getTag();
          }
        } else if (arg instanceof ContainerServiceParams) {
          ContainerServiceParams containerServiceParams = (ContainerServiceParams) arg;
          SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
          SettingValue settingValue = settingAttribute.getValue();
          if (settingValue instanceof AwsConfig) {
            return ((AwsConfig) settingValue).getTag();
          }
        } else if (arg instanceof AwsRequest) {
          AwsConfig awsConfig = ((AwsRequest) arg).getAwsConfig();
          return awsConfig.getTag();
        }
      }
    }
    return null;
  }
}
