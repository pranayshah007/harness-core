/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.datafetcher.service;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.pcf.model.CfCliVersion;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.graphql.schema.mutation.service.input.QLUpdateServiceMetadataInput;
import software.wings.graphql.schema.mutation.service.payload.QLUpdateServiceMetadataPayload;
import software.wings.graphql.schema.type.QLService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class UpdateServiceMetadataDataFetcherTest extends CategoryTest {
  @Mock ServiceResourceService serviceResourceService;
  @Mock AppService appService;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Spy UpdateServiceMetadataDataFetcher updateServiceMetadataDataFetcher;

  private final String ACCOUNT = "account_id";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(getApplication()).when(appService).get(any());
    doReturn(null).when(serviceResourceService).update(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_mutateAndFetch() {
    QLUpdateServiceMetadataInput qlUpdateServiceMetadataInput = QLUpdateServiceMetadataInput.builder()
                                                                    .applicationId(Arrays.asList("App1", "App2"))
                                                                    .cfCliVersion(CfCliVersion.V7)
                                                                    .clientMutationId("mutation_Id")
                                                                    .excludeServices(RequestField.absent())
                                                                    .build();

    List<Service> serviceListForApp1 = getServiceList(Arrays.asList("s1", "s2", "s3"));
    List<Service> serviceListForApp2 = getServiceList(Arrays.asList("s4", "s5", "s6"));

    doReturn(true).when(featureFlagService).isEnabled(any(), any());

    doReturn(serviceListForApp1).when(serviceResourceService).findServicesByApp("App1");
    doReturn(serviceListForApp2).when(serviceResourceService).findServicesByApp("App2");

    QLUpdateServiceMetadataPayload qlUpdateServiceMetadataPayload =
        updateServiceMetadataDataFetcher.mutateAndFetch(qlUpdateServiceMetadataInput, null);

    assertThat(qlUpdateServiceMetadataPayload.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    List<QLService> qlServices = getServiceList(Arrays.asList("s1", "s2", "s3", "s4", "s5", "s6"))
                                     .stream()
                                     .map(ServiceController::buildQLService)
                                     .collect(Collectors.toList());
    assertThat(qlUpdateServiceMetadataPayload.getUpdatedService()).isEqualTo(qlServices);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_mutateAndFetchWithExcludeService() {
    QLUpdateServiceMetadataInput qlUpdateServiceMetadataInput =
        QLUpdateServiceMetadataInput.builder()
            .applicationId(Arrays.asList("App1", "App2"))
            .cfCliVersion(CfCliVersion.V7)
            .clientMutationId("mutation_Id")
            .excludeServices(RequestField.ofNullable(Arrays.asList("s2", "s4")))
            .build();

    List<Service> serviceListForApp1 = getServiceList(Arrays.asList("s1", "s2", "s3"));
    List<Service> serviceListForApp2 = getServiceList(Arrays.asList("s4", "s5", "s6"));

    doReturn(true).when(featureFlagService).isEnabled(any(), any());

    doReturn(serviceListForApp1).when(serviceResourceService).findServicesByApp("App1");
    doReturn(serviceListForApp2).when(serviceResourceService).findServicesByApp("App2");

    QLUpdateServiceMetadataPayload qlUpdateServiceMetadataPayload =
        updateServiceMetadataDataFetcher.mutateAndFetch(qlUpdateServiceMetadataInput, null);

    assertThat(qlUpdateServiceMetadataPayload.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    List<QLService> qlServices = getServiceList(Arrays.asList("s1", "s3", "s5", "s6"))
                                     .stream()
                                     .map(ServiceController::buildQLService)
                                     .collect(Collectors.toList());
    assertThat(qlUpdateServiceMetadataPayload.getUpdatedService()).isEqualTo(qlServices);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_mutateAndFetchFFNotEnabled() {
    QLUpdateServiceMetadataInput qlUpdateServiceMetadataInput =
        QLUpdateServiceMetadataInput.builder()
            .applicationId(Arrays.asList("App1", "App2"))
            .cfCliVersion(CfCliVersion.V7)
            .clientMutationId("mutation_Id")
            .excludeServices(RequestField.ofNullable(Arrays.asList("s2", "s4")))
            .build();

    List<Service> serviceListForApp1 = getServiceList(Arrays.asList("s1", "s2", "s3"));
    List<Service> serviceListForApp2 = getServiceList(Arrays.asList("s4", "s5", "s6"));

    doReturn(false).when(featureFlagService).isEnabled(any(), any());

    doReturn(serviceListForApp1).when(serviceResourceService).findServicesByApp("App1");
    doReturn(serviceListForApp2).when(serviceResourceService).findServicesByApp("App2");

    assertThatThrownBy(() -> updateServiceMetadataDataFetcher.mutateAndFetch(qlUpdateServiceMetadataInput, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void test_mutateAndFetchWithNoApplicationId() {
    QLUpdateServiceMetadataInput qlUpdateServiceMetadataInput =
        QLUpdateServiceMetadataInput.builder()
            .applicationId(new ArrayList<>())
            .cfCliVersion(CfCliVersion.V7)
            .clientMutationId("mutation_Id")
            .excludeServices(RequestField.ofNullable(Arrays.asList("s2", "s4")))
            .build();

    List<Service> serviceListForApp1 = getServiceList(Arrays.asList("s1", "s2", "s3"));
    List<Service> serviceListForApp2 = getServiceList(Arrays.asList("s4", "s5", "s6"));

    doReturn(true).when(featureFlagService).isEnabled(any(), any());

    doReturn(serviceListForApp1).when(serviceResourceService).findServicesByApp("App1");
    doReturn(serviceListForApp2).when(serviceResourceService).findServicesByApp("App2");

    assertThatThrownBy(() -> updateServiceMetadataDataFetcher.mutateAndFetch(qlUpdateServiceMetadataInput, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  private List<Service> getServiceList(List<String> serviceIdList) {
    List<Service> services = new ArrayList<>();
    for (String serviceId : serviceIdList) {
      services.add(Service.builder()
                       .name(serviceId)
                       .uuid(serviceId)
                       .deploymentType(DeploymentType.PCF)
                       .accountId(ACCOUNT)
                       .build());
    }
    return services;
  }

  private Application getApplication() {
    return Application.Builder.anApplication().appId("App1").accountId(ACCOUNT).build();
  }
}
