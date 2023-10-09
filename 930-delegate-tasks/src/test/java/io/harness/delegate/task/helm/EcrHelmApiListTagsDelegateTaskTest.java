/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.EcrImageDetailConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.helm.EcrHelmApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskResponse;
import io.harness.rule.Owner;

import com.amazonaws.services.ecr.model.ImageDetail;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class EcrHelmApiListTagsDelegateTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks
  private EcrHelmApiListTagsDelegateTask ecrHelmApiListTagsDelegateTask = new EcrHelmApiListTagsDelegateTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);
  @Mock private OciHelmEcrConfigApiHelper ociHelmEcrConfigApiHelper;
  private static final String chartName = "chartName";

  @Before
  public void setup() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testRunEcrHelmApiListTagsDelegateTask() {
    List<String> chartVersion = Arrays.asList("0.1", "0.2", "0.3");
    EcrHelmApiListTagsTaskParams taskParameters = EcrHelmApiListTagsTaskParams.builder().chartName(chartName).build();
    EcrImageDetailConfig ecrImageDetailConfig = EcrImageDetailConfig.builder()
                                                    .imageDetails(Collections.singletonList(new ImageDetail()))
                                                    .nextToken("lastTag")
                                                    .build();
    doReturn(ecrImageDetailConfig).when(ociHelmEcrConfigApiHelper).getEcrImageDetailConfig(eq(taskParameters), eq(20));
    doReturn(chartVersion).when(ociHelmEcrConfigApiHelper).getChartVersionsFromImageDetails(eq(ecrImageDetailConfig));
    OciHelmDockerApiListTagsTaskResponse ociHelmDockerApiListTagsTaskResponse =
        (OciHelmDockerApiListTagsTaskResponse) ecrHelmApiListTagsDelegateTask.run(taskParameters);
    assertThat(ociHelmDockerApiListTagsTaskResponse.getLastTag()).isEqualTo("lastTag");
    assertThat(ociHelmDockerApiListTagsTaskResponse.getChartVersions()).isEqualTo(chartVersion);
    assertThat(ociHelmDockerApiListTagsTaskResponse.getChartName()).isEqualTo(chartName);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testRunEcrHelmApiListTagsDelegateTaskWithPageSize() {
    int pageSize = 100;
    List<String> chartVersion = Arrays.asList("0.1", "0.2", "0.3");
    EcrHelmApiListTagsTaskParams taskParameters =
        EcrHelmApiListTagsTaskParams.builder().chartName(chartName).pageSize(pageSize).build();
    EcrImageDetailConfig ecrImageDetailConfig = EcrImageDetailConfig.builder()
                                                    .imageDetails(Collections.singletonList(new ImageDetail()))
                                                    .nextToken("lastTag")
                                                    .build();
    doReturn(ecrImageDetailConfig)
        .when(ociHelmEcrConfigApiHelper)
        .getEcrImageDetailConfig(eq(taskParameters), eq(pageSize));
    doReturn(chartVersion).when(ociHelmEcrConfigApiHelper).getChartVersionsFromImageDetails(eq(ecrImageDetailConfig));
    OciHelmDockerApiListTagsTaskResponse ociHelmDockerApiListTagsTaskResponse =
        (OciHelmDockerApiListTagsTaskResponse) ecrHelmApiListTagsDelegateTask.run(taskParameters);
    assertThat(ociHelmDockerApiListTagsTaskResponse.getLastTag()).isEqualTo("lastTag");
    assertThat(ociHelmDockerApiListTagsTaskResponse.getChartVersions()).isEqualTo(chartVersion);
    assertThat(ociHelmDockerApiListTagsTaskResponse.getChartName()).isEqualTo(chartName);
  }
}
