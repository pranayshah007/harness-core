/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.exception.HintException;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceNowTaskNGTest extends CategoryTest {
  @Mock private ServiceNowTaskNgHelper serviceNowTaskNgHelper;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @InjectMocks
  private final ServiceNowTaskNG serviceNowTaskNG =
      new ServiceNowTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRunObjectParamsShouldThrowMotImplementedException() {
    assertThatThrownBy(() -> serviceNowTaskNG.run(new Object[1]))
        .hasMessage("not implemented")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRun() {
    ServiceNowTaskNGResponse taskResponse = ServiceNowTaskNGResponse.builder().build();
    when(serviceNowTaskNgHelper.getServiceNowResponse(any(), any())).thenReturn(taskResponse);
    assertThatCode(() -> serviceNowTaskNG.run(ServiceNowTaskNGParameters.builder().build())).doesNotThrowAnyException();
    verify(serviceNowTaskNgHelper)
        .getServiceNowResponse(ServiceNowTaskNGParameters.builder().build(), logStreamingTaskClient);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRunFailure() {
    when(serviceNowTaskNgHelper.getServiceNowResponse(any(), any())).thenThrow(new HintException("Exception"));
    assertThatThrownBy(() -> serviceNowTaskNG.run(ServiceNowTaskNGParameters.builder().build()))
        .isInstanceOf(HintException.class);
    verify(serviceNowTaskNgHelper)
        .getServiceNowResponse(ServiceNowTaskNGParameters.builder().build(), logStreamingTaskClient);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetServiceNowTaskNGDelegateSelectors() {
    ServiceNowTaskNGParameters serviceNowTaskNGParameters =
        ServiceNowTaskNGParameters.builder()
            .delegateSelectors(Arrays.asList("selector1"))
            .serviceNowConnectorDTO(ServiceNowConnectorDTO.builder().build())
            .build();
    assertThat(serviceNowTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1");
    serviceNowTaskNGParameters.getServiceNowConnectorDTO().setDelegateSelectors(ImmutableSet.of("selector2"));
    assertThat(serviceNowTaskNGParameters.getDelegateSelectors()).containsExactlyInAnyOrder("selector1", "selector2");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testisSupportingErrorFramework() {
    assertThat(serviceNowTaskNG.isSupportingErrorFramework()).isTrue();
  }
}
