/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.common;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.rule.Owner;

import software.wings.api.ServiceElement;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.sm.ExecutionContextImpl;

import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * The Class ServiceExpressionProcessorTest.
 *
 * @author Rishi
 */
public class ServiceExpressionProcessorTest extends CategoryTest {
  /**
   * The Mockito rule.
   */
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private String appId = generateUuid();

  @Mock private ExecutionContextImpl context;

  @Mock private ServiceResourceService serviceResourceService;

  /**
   * Should return matching services.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnMatchingServices() {
    List<ServiceElement> services = Lists.newArrayList(ServiceElement.builder().name("A1234").build(),
        ServiceElement.builder().name("B1234").build(), ServiceElement.builder().name("C1234").build());

    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(aWorkflowStandardParams().build());

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    processor.setServiceResourceService(serviceResourceService);

    List<ServiceElement> matchingServices = processor.matchingServices(services, "A1234", "B1234");
    assertThat(matchingServices)
        .isNotNull()
        .hasSize(2)
        .doesNotContainNull()
        .extracting("name")
        .containsExactly("A1234", "B1234");

    matchingServices = processor.matchingServices(services, "B*4", "C1234");
    assertThat(matchingServices)
        .isNotNull()
        .hasSize(2)
        .doesNotContainNull()
        .extracting("name")
        .containsExactly("B1234", "C1234");
  }

  /**
   * Should return list all.
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldReturnListAll() {
    List<Service> services = Lists.newArrayList(Service.builder().name("A1234").build(),
        Service.builder().name("B1234").build(), Service.builder().name("C1234").build());

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(aWorkflowStandardParams().build());

    ServiceResourceService serviceResourceService = mock(ServiceResourceService.class);

    PageResponse<Service> res = new PageResponse<>();
    res.setResponse(services);

    when(serviceResourceService.list(any(PageRequest.class), eq(false), eq(true), eq(false), eq(null))).thenReturn(res);

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    processor.setServiceResourceService(serviceResourceService);

    List<ServiceElement> matchingServices = processor.list();
    assertThat(matchingServices)
        .isNotNull()
        .hasSize(3)
        .doesNotContainNull()
        .extracting("name")
        .contains("A1234", "B1234", "C1234");
  }

  /**
   * Should return list all from context.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnListAllFromContext() {
    Service serviceC = Service.builder().name("C1234").uuid(SERVICE_ID).build();

    ServiceElement serviceCElement = ServiceElement.builder().name(serviceC.getName()).uuid(serviceC.getUuid()).build();

    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(aWorkflowStandardParams().build());
    when(context.getContextElement(ContextElementType.SERVICE)).thenReturn(serviceCElement);

    when(serviceResourceService.getWithDetails(anyString(), anyString())).thenReturn(serviceC);

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    processor.setServiceResourceService(serviceResourceService);

    List<ServiceElement> matchingServices = processor.list();
    assertThat(matchingServices).isNotNull().hasSize(1).doesNotContainNull().extracting("name").contains("C1234");
  }

  /**
   * Should return list some by name.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnListSomeByName() {
    List<Service> services = Lists.newArrayList(Service.builder().name("A1234").build(),
        Service.builder().name("B1234").build(), Service.builder().name("C1234").build());

    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(aWorkflowStandardParams().build());

    PageResponse<Service> res = new PageResponse<>();
    res.setResponse(services);

    when(serviceResourceService.list(any(PageRequest.class), eq(false), eq(true), eq(false), eq(null))).thenReturn(res);

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    processor.setServiceResourceService(serviceResourceService);

    List<ServiceElement> matchingServices = processor.withNames("B1234", "C12*").list();
    assertThat(matchingServices)
        .isNotNull()
        .hasSize(2)
        .doesNotContainNull()
        .extracting("name")
        .containsExactly("B1234", "C1234");
  }

  /**
   * Should return list some by name.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnSelectedListSomeByName() {
    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.STANDARD))
        .thenReturn(
            aWorkflowStandardParams()
                .withServices(Lists.newArrayList(ServiceElement.builder().name("A1234").build(),
                    ServiceElement.builder().name("B1234").build(), ServiceElement.builder().name("C1234").build()))
                .build());

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    List<ServiceElement> matchingServices = processor.withNames("B1234", "C12*").list();
    assertThat(matchingServices)
        .isNotNull()
        .hasSize(2)
        .doesNotContainNull()
        .extracting("name")
        .contains("B1234", "C1234");
  }

  /**
   * Should return not from context.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReturnNotFromContext() {
    Service serviceC = Service.builder().name("C1234").build();

    List<Service> services =
        Lists.newArrayList(Service.builder().name("A1234").build(), Service.builder().name("B1234").build(), serviceC);

    ServiceElement serviceCElement = ServiceElement.builder().name(serviceC.getName()).build();

    when(context.getApp()).thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(context.getContextElement(ContextElementType.SERVICE)).thenReturn(serviceCElement);
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(aWorkflowStandardParams().build());

    PageResponse<Service> res = new PageResponse<>();
    res.setResponse(services);
    when(serviceResourceService.list(any(PageRequest.class), eq(false), eq(true), eq(false), eq(null))).thenReturn(res);

    ServiceExpressionProcessor processor = new ServiceExpressionProcessor(context);
    processor.setServiceResourceService(serviceResourceService);

    List<ServiceElement> matchingServices = processor.withNames("A*").list();
    assertThat(matchingServices).isNotNull().hasSize(1).doesNotContainNull().extracting("name").contains("A1234");
  }
}
