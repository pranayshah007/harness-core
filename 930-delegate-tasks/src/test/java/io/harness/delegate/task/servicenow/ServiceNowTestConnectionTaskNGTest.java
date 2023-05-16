/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectionTaskParams;
import io.harness.delegate.beans.connector.servicenow.connection.ServiceNowTestConnectionTaskNGResponse;
import io.harness.delegate.task.servicenow.connection.ServiceNowTestConnectionTaskNG;
import io.harness.exception.HintException;
import io.harness.rule.Owner;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceNowTestConnectionTaskNGTest extends CategoryTest {
  @Mock private ServiceNowTaskNgHelper serviceNowTaskNgHelper;

  @InjectMocks
  private final ServiceNowTestConnectionTaskNG serviceNowTestConnectionTaskNG = new ServiceNowTestConnectionTaskNG(
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRunObjectParamsShouldThrowMotImplementedException() {
    assertThatThrownBy(() -> serviceNowTestConnectionTaskNG.run(new Object[1]))
        .hasMessage("This method is deprecated")
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRun() {
    ServiceNowTaskNGResponse taskResponse = ServiceNowTaskNGResponse.builder().build();
    when(serviceNowTaskNgHelper.getServiceNowResponse(any(), any())).thenReturn(taskResponse);
    DelegateResponseData response =
        serviceNowTestConnectionTaskNG.run(ServiceNowConnectionTaskParams.builder().build());

    assertThat(((ServiceNowTestConnectionTaskNGResponse) response).getCanConnect()).isEqualTo(true);

    verify(serviceNowTaskNgHelper).getServiceNowResponse(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRunWhenCantConnect() {
    when(serviceNowTaskNgHelper.getServiceNowResponse(any(), any())).thenThrow(new RuntimeException("exception"));
    DelegateResponseData response =
        serviceNowTestConnectionTaskNG.run(ServiceNowConnectionTaskParams.builder().build());

    assertThat(((ServiceNowTestConnectionTaskNGResponse) response).getCanConnect()).isEqualTo(false);

    verify(serviceNowTaskNgHelper).getServiceNowResponse(any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRunWhenCantConnectWithHintException() {
    when(serviceNowTaskNgHelper.getServiceNowResponse(any(), any())).thenThrow(new HintException("exception"));
    assertThatThrownBy(() -> serviceNowTestConnectionTaskNG.run(ServiceNowConnectionTaskParams.builder().build()))
        .isInstanceOf(HintException.class);
    verify(serviceNowTaskNgHelper).getServiceNowResponse(any(), any());
  }
}
