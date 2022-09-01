/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.taskhandler;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.management.ManagementGroupInfo;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListManagementGroupResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import software.wings.delegatetasks.azure.arm.AbstractAzureARMTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
public class AzureARMListManagementGroupTaskHandler extends AbstractAzureARMTaskHandler {
  @Inject private AzureManagementClient azureManagementClient;

  @Override
  protected AzureARMTaskResponse executeTaskInternal(AzureARMTaskParameters azureARMTaskParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient) {
    List<ManagementGroupInfo> managementGroupInfos = azureManagementClient.listManagementGroups(azureConfig);
    return AzureARMListManagementGroupResponse.builder().mngGroups(getManagementGroups(managementGroupInfos)).build();
  }

  private List<ManagementGroupData> getManagementGroups(List<ManagementGroupInfo> managementGroupInfos) {
    return managementGroupInfos.stream().map(this::toManagementGroupData).collect(Collectors.toList());
  }

  private ManagementGroupData toManagementGroupData(ManagementGroupInfo group) {
    String id = fixGroupId(group.getId());
    String displayName = group.getProperties().getDisplayName();
    return ManagementGroupData.builder().id(id).name(group.getName()).displayName(displayName).build();
  }

  @NotNull
  private String fixGroupId(String groupId) {
    return groupId.replace(AzureConstants.MANAGEMENT_GROUP_PROVIDERS_PREFIX, EMPTY);
  }
}
