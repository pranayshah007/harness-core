/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.rancher;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static software.wings.delegatetasks.rancher.RancherResolveClustersTask.COMMAND_UNIT_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ClusterSelectionCriteriaEntry;
import software.wings.beans.RancherConfig;
import software.wings.delegatetasks.rancher.RancherClusterDataResponse.ClusterData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class RancherResolveClustersTaskTest extends WingsBaseTest {
  @Mock private RancherTaskHelper helper;
  @Mock private ILogStreamingTaskClient streamingTaskClient;

  @InjectMocks
  RancherResolveClustersTask task = new RancherResolveClustersTask(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().async(false).build()).build(),
      streamingTaskClient, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() {
    LogCallback logCallback = spy(LogCallback.class);
    doReturn(logCallback).when(streamingTaskClient).obtainLogCallback(COMMAND_UNIT_NAME);
    doNothing().when(logCallback).saveExecutionLog(anyString(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRun() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = new ArrayList<>();
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("env").labelValues("PROD").build());
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("org").labelValues("CDP").build());
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doReturn(getSampleRancherResponse()).when(helper).resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getClusters().contains("cluster1")).isTrue();
    assertThat(response.getClusters().contains("cluster2")).isFalse();
    assertThat(response.getClusters().contains("cluster3")).isFalse();
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRunWithMultipleLabelValues() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = new ArrayList<>();
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("env").labelValues("PROD, QA").build());
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("org").labelValues("CDP").build());
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doReturn(getSampleRancherResponse()).when(helper).resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getClusters().contains("cluster1")).isTrue();
    assertThat(response.getClusters().contains("cluster2")).isTrue();
    assertThat(response.getClusters().contains("cluster3")).isFalse();
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRunWithNoMatchingCluster() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = new ArrayList<>();
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("env").labelValues("PROD").build());
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("org").labelValues("NonExistent").build());
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doReturn(getSampleRancherResponse()).when(helper).resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRunWithNoSelectionCriteria() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = null;
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doReturn(getSampleRancherResponse()).when(helper).resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getClusters().contains("cluster1")).isTrue();
    assertThat(response.getClusters().contains("cluster2")).isTrue();
    assertThat(response.getClusters().contains("cluster3")).isTrue();
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRunWithRancherException() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = new ArrayList<>();
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("env").labelValues("PROD").build());
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("org").labelValues("CDP").build());
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doThrow(new IOException("Unable to make rancher call")).when(helper).resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("Unable to make rancher call");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testRunWithEmptyClusterListFromRancher() throws IOException {
    RancherConfig rancherConfig = mock(RancherConfig.class);
    List<ClusterSelectionCriteriaEntry> selectionCriteria = new ArrayList<>();
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("env").labelValues("PROD").build());
    selectionCriteria.add(ClusterSelectionCriteriaEntry.builder().labelName("org").labelValues("CDP").build());
    RancherResolveClustersTaskParameters parameters = RancherResolveClustersTaskParameters.builder()
                                                          .clusterSelectionCriteria(selectionCriteria)
                                                          .rancherConfig(rancherConfig)
                                                          .build();

    doReturn(RancherClusterDataResponse.builder().resourceType("clusters").build())
        .when(helper)
        .resolveRancherClusters(any(), any());
    doReturn("dummyHostUrl").when(rancherConfig).getRancherUrl();

    RancherResolveClustersResponse response = task.run(parameters);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  private RancherClusterDataResponse getSampleRancherResponse() {
    List<ClusterData> clusterData = new ArrayList<>();
    clusterData.add(getCluster1());
    clusterData.add(getCluster2());
    clusterData.add(getCluster3());
    return RancherClusterDataResponse.builder().resourceType("cluster").data(clusterData).build();
  }

  private ClusterData getCluster1() {
    HashMap<String, String> labels = new HashMap<>();
    labels.put("foo", "bar");
    labels.put("env", "PROD");
    labels.put("org", "CDP");

    return ClusterData.builder().id("cluster1").name("cluster1").labels(labels).build();
  }

  private ClusterData getCluster2() {
    HashMap<String, String> labels = new HashMap<>();
    labels.put("newLabel", "newLabelValue");
    labels.put("env", "QA");
    labels.put("org", "CDP");

    return ClusterData.builder().id("cluster2").name("cluster2").labels(labels).build();
  }

  private ClusterData getCluster3() {
    HashMap<String, String> labels = new HashMap<>();
    labels.put("foo", "barNot");
    labels.put("env", "prodNot");
    labels.put("org", "CCM");

    return ClusterData.builder().id("cluster3").name("cluster3").labels(labels).build();
  }
}
