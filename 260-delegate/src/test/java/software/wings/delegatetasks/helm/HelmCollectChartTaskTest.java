/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.perpetualtask.manifest.HelmRepositoryService;
import io.harness.rule.Owner;

import software.wings.beans.dto.HelmChart;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams.HelmChartCollectionType;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.response.HelmCollectChartResponse;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HelmCollectChartTaskTest extends CategoryTest {
  @Mock HelmRepositoryService helmRepositoryService;
  @Mock ArtifactoryHelmRepositoryService artifactoryHelmRepositoryService;
  @InjectMocks
  @Inject
  HelmCollectChartTask helmCollectChartTask = new HelmCollectChartTask(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    on(helmCollectChartTask).set("helmCommandRepositoryService", helmRepositoryService);
    on(helmCollectChartTask).set("artifactoryHelmRepositoryService", artifactoryHelmRepositoryService);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCollectAllVersions() throws Exception {
    List<HelmChart> helmCharts = Arrays.asList(HelmChart.builder().accountId("accountId").uuid("uuid").build(),
        HelmChart.builder().accountId("accountId").uuid("uuid2").build());
    when(helmRepositoryService.collectManifests(any())).thenReturn(helmCharts);
    TaskParameters taskParameters =
        HelmChartCollectionParams.builder().collectionType(HelmChartCollectionType.ALL).build();
    HelmCollectChartResponse response = helmCollectChartTask.run(taskParameters);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getHelmCharts().stream().map(HelmChart::getUuid).collect(Collectors.toList()))
        .containsExactly("uuid", "uuid2");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCollectSpecificVersion() throws Exception {
    List<HelmChart> helmCharts =
        Arrays.asList(HelmChart.builder().accountId("accountId").uuid("uuid").version("v1").build());
    when(helmRepositoryService.collectManifests(any())).thenReturn(helmCharts);
    TaskParameters taskParameters =
        HelmChartCollectionParams.builder()
            .collectionType(HelmChartCollectionType.SPECIFIC_VERSION)
            .helmChartConfigParams(HelmChartConfigParams.builder().chartVersion("v1").build())
            .build();
    HelmCollectChartResponse response = helmCollectChartTask.run(taskParameters);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getHelmCharts().stream().map(HelmChart::getUuid).collect(Collectors.toList()))
        .containsExactly("uuid");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCollectSpecificVersionNotFound() throws Exception {
    when(helmRepositoryService.collectManifests(any())).thenReturn(Collections.emptyList());
    TaskParameters taskParameters =
        HelmChartCollectionParams.builder()
            .collectionType(HelmChartCollectionType.SPECIFIC_VERSION)
            .helmChartConfigParams(HelmChartConfigParams.builder().chartVersion("v1").build())
            .build();
    HelmCollectChartResponse response = helmCollectChartTask.run(taskParameters);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getHelmCharts()).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCollectFromArtifactoryOnArtifactoryRepoAndByPassHelmFetchEnabled() throws Exception {
    List<HelmChart> helmCharts = Arrays.asList(HelmChart.builder().accountId("accountId").uuid("uuid").build(),
        HelmChart.builder().accountId("accountId").uuid("uuid2").build());
    when(artifactoryHelmRepositoryService.collectManifests(any())).thenReturn(helmCharts);
    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder()
            .helmRepoConfig(HttpHelmRepoConfig.builder().chartRepoUrl("something/artifactory/something").build())
            .bypassHelmFetch(true)
            .build();
    HelmChartCollectionParams taskParameters = HelmChartCollectionParams.builder()
                                                   .collectionType(HelmChartCollectionType.ALL)
                                                   .helmChartConfigParams(helmChartConfigParams)
                                                   .build();
    HelmCollectChartResponse response = helmCollectChartTask.run(taskParameters);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getHelmCharts().stream().map(HelmChart::getUuid).collect(Collectors.toList()))
        .containsExactly("uuid", "uuid2");
    verify(artifactoryHelmRepositoryService).collectManifests(taskParameters);
  }
}
