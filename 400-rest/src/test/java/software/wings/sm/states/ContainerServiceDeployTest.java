/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.SweepingOutputInstance.Scope.WORKFLOW;
import static io.harness.rule.OwnerRule.BOJANA;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.deployment.InstanceDetails;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ContainerServiceDeployTest extends WingsBaseTest {
  @Mock private SweepingOutputService mockedSweepingOutputService;
  @Mock private ExecutionContextImpl context;
  @InjectMocks
  private ContainerServiceDeploy containerServiceDeploy = mock(ContainerServiceDeploy.class, CALLS_REAL_METHODS);

  @Before
  public void setup() {
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(WORKFLOW);
    doReturn("some-string").when(context).appendStateExecutionId(anyString());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testsaveInstanceInfoToSweepingOutputDontSkipVerification() {
    on(containerServiceDeploy).set("sweepingOutputService", mockedSweepingOutputService);
    containerServiceDeploy.saveInstanceDetailsToSweepingOutput(context,
        asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(true).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(false);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testsaveInstanceInfoToSweepingOutputSkipVerification() {
    on(containerServiceDeploy).set("sweepingOutputService", mockedSweepingOutputService);
    containerServiceDeploy.saveInstanceDetailsToSweepingOutput(context,
        asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(false).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(true);
  }
}
