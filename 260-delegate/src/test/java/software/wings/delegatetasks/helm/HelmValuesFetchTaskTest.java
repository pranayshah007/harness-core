/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmValuesFetchTaskTest extends WingsBaseTest {
  @Mock private HelmTaskHelper helmTaskHelper;

  @InjectMocks
  HelmValuesFetchTask task = spy(new HelmValuesFetchTask(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().async(false).build()).build(),
      null, notifyResponseData -> {}, () -> true));

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunSuccessful() throws Exception {
    HelmValuesFetchTaskParameters parameters =
        HelmValuesFetchTaskParameters.builder()
            .accountId("accountId")
            .helmChartConfigTaskParams(HelmChartConfigParams.builder().chartName("chart").build())
            .build();

    String valuesYamlFileContent = "helmValue: value";
    Map<String, List<String>> mapK8sValuesLocationToContent = new HashMap<>();
    mapK8sValuesLocationToContent.put(K8sValuesLocation.Service.name(), singletonList(valuesYamlFileContent));

    doNothing().when(task).printHelmBinaryPathAndVersion(any(), any());
    doReturn(mapK8sValuesLocationToContent)
        .when(helmTaskHelper)
        .getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());

    HelmValuesFetchTaskResponse response = task.run(parameters);
    verify(helmTaskHelper, times(1)).getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getMapK8sValuesLocationToContent()).isEqualTo(mapK8sValuesLocationToContent);

    doReturn(null)
        .when(helmTaskHelper)
        .getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());

    HelmValuesFetchTaskResponse emptyResponse = task.run(parameters);
    verify(helmTaskHelper, times(2)).getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());
    assertThat(emptyResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunWithException() throws Exception {
    HelmValuesFetchTaskParameters parameters =
        HelmValuesFetchTaskParameters.builder()
            .accountId("accountId")
            .helmChartConfigTaskParams(HelmChartConfigParams.builder().chartName("chart").build())
            .build();

    doNothing().when(task).printHelmBinaryPathAndVersion(any(), any());
    doThrow(new RuntimeException("Unable to fetch Values.yaml"))
        .when(helmTaskHelper)
        .getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());

    HelmValuesFetchTaskResponse response = task.run(parameters);
    verify(helmTaskHelper, times(1)).getValuesYamlFromChart(any(HelmChartConfigParams.class), anyLong(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Execution failed with Exception: Unable to fetch Values.yaml");
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunWithObjectParams() {
    task.run(new Object[] {});
  }
}
