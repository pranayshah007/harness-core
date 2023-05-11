/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.RancherClusterElement;
import software.wings.common.RancherK8sClusterProcessor.RancherClusterElementList;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class RancherK8sClusterProcessorTest extends WingsBaseTest {
  @Mock private SweepingOutputService sweepingOutputService;

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testList() {
    RancherClusterElementList elementList = new RancherClusterElementList(getSampleRancherClusterElements());
    doReturn(elementList).when(sweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn("workflow-execution-id").when(context).getWorkflowExecutionId();

    RancherK8sClusterProcessor processor = new RancherK8sClusterProcessor(sweepingOutputService, context);
    List<RancherClusterElement> clusterElements = processor.list();

    assertThat(clusterElements.size()).isEqualTo(getSampleRancherClusterElements().size());
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testListNullSweepingOutput() {
    doReturn(null).when(sweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn("workflow-execution-id").when(context).getWorkflowExecutionId();

    RancherK8sClusterProcessor processor = new RancherK8sClusterProcessor(sweepingOutputService, context);
    List<RancherClusterElement> clusterElements = processor.list();

    assertThat(clusterElements).isNull();
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetClusters() {
    RancherClusterElementList elementList = new RancherClusterElementList(getSampleRancherClusterElements());
    doReturn(elementList).when(sweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn("workflow-execution-id").when(context).getWorkflowExecutionId();

    RancherK8sClusterProcessor processor = new RancherK8sClusterProcessor(sweepingOutputService, context);
    String clusters = processor.getClusters();
    assertThat(clusters).isEqualTo("Cluster1,Cluster2,Cluster3");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetClustersWithNullElementsList() {
    doReturn(null).when(sweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn("workflow-execution-id").when(context).getWorkflowExecutionId();

    RancherK8sClusterProcessor processor = new RancherK8sClusterProcessor(sweepingOutputService, context);
    String clusters = processor.getClusters();
    assertThat(clusters).isEqualTo("");
  }

  private List<RancherClusterElement> getSampleRancherClusterElements() {
    List<RancherClusterElement> elements = new ArrayList<>();

    elements.add(new RancherClusterElement(UUIDGenerator.generateUuid(), "Cluster1"));
    elements.add(new RancherClusterElement(UUIDGenerator.generateUuid(), "Cluster2"));
    elements.add(new RancherClusterElement(UUIDGenerator.generateUuid(), "Cluster3"));

    return elements;
  }
}
