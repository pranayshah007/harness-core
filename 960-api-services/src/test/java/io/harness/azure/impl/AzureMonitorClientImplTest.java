/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.rule.OwnerRule.IVAN;

import static com.azure.resourcemanager.monitor.models.ActivityLogs.ActivityLogsQueryDefinitionStages.WithActivityLogsQueryExecute;
import static com.azure.resourcemanager.monitor.models.ActivityLogs.ActivityLogsQueryDefinitionStages.WithActivityLogsSelectFilter;
import static com.azure.resourcemanager.monitor.models.ActivityLogs.ActivityLogsQueryDefinitionStages.WithEventDataEndFilter;
import static com.azure.resourcemanager.monitor.models.ActivityLogs.ActivityLogsQueryDefinitionStages.WithEventDataFieldFilter;
import static com.azure.resourcemanager.monitor.models.ActivityLogs.ActivityLogsQueryDefinitionStages.WithEventDataStartTimeFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureClient;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.network.Http;
import io.harness.rule.Owner;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.monitor.models.ActivityLogs;
import com.azure.resourcemanager.monitor.models.EventData;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Subscriptions;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import reactor.core.publisher.Mono;

@PrepareForTest({AzureResourceManager.class, AzureClient.class, Http.class, TimeLimiter.class})
public class AzureMonitorClientImplTest extends CategoryTest {
  @Mock private AzureResourceManager.Configurable configurable;
  @Mock private AzureResourceManager.Authenticated authenticated;
  @Mock private AzureResourceManager azure;

  @InjectMocks AzureMonitorClientImpl azureMonitorClient;

  @Before
  public void before() throws Exception {
    MockitoAnnotations.openMocks(this);

    ClientSecretCredential clientSecretCredential = Mockito.mock(ClientSecretCredential.class);
    PowerMockito.whenNew(ClientSecretCredential.class).withAnyArguments().thenReturn(clientSecretCredential);

    AccessToken accessToken = Mockito.mock(AccessToken.class);
    PowerMockito.whenNew(AccessToken.class).withAnyArguments().thenReturn(accessToken);

    Mockito.when(clientSecretCredential.getToken(any())).thenReturn(Mono.just(accessToken));

    MockedStatic<AzureResourceManager> azureResourceManagerMockedStatic = mockStatic(AzureResourceManager.class);

    azureResourceManagerMockedStatic.when(() -> AzureResourceManager.configure()).thenReturn(configurable);

    Mockito.when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
    azureResourceManagerMockedStatic
        .when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    Mockito.when(configurable.withHttpClient(any())).thenReturn(configurable);
    when(configurable.withRetryPolicy(any())).thenReturn(configurable);
    Mockito.when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    Mockito.when(configurable.authenticate(any(ClientSecretCredential.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    Mockito.when(authenticated.subscriptions()).thenReturn(getSubscriptions());
    Mockito.when(authenticated.withSubscription(any())).thenReturn(azure);
    Mockito.when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListEventDataWithAllPropertiesByResourceId() {
    DateTime endDate = DateTime.now();
    DateTime startDate = endDate.minusMinutes(1);
    String subscriptionId = "subscriptionId";
    String resourceId = "resourceId";
    AzureConfig azureConfig = AzureConfig.builder()
                                  .clientId("clientId")
                                  .tenantId("tenantId")
                                  .key("key".toCharArray())
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();
    ActivityLogs activityLogs = mock(ActivityLogs.class);
    WithEventDataStartTimeFilter query = mock(WithEventDataStartTimeFilter.class);
    WithEventDataEndFilter queryWithStartDate = mock(WithEventDataEndFilter.class);
    WithEventDataFieldFilter queryWithStartAndEndDate = mock(WithEventDataFieldFilter.class);
    WithActivityLogsSelectFilter queryWithStartAndEndDateAndProperties = mock(WithActivityLogsSelectFilter.class);
    WithActivityLogsQueryExecute queryExecute = mock(WithActivityLogsQueryExecute.class);
    EventData eventData = mock(EventData.class);
    List<EventData> responseList = new ArrayList<>();
    responseList.add(eventData);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    when(azure.activityLogs()).thenReturn(activityLogs);
    when(activityLogs.defineQuery()).thenReturn(query);
    when(query.startingFrom(any())).thenReturn(queryWithStartDate);
    when(queryWithStartDate.endsBefore(any())).thenReturn(queryWithStartAndEndDate);
    when(queryWithStartAndEndDate.withAllPropertiesInResponse()).thenReturn(queryWithStartAndEndDateAndProperties);
    when(queryWithStartAndEndDateAndProperties.filterByResource(resourceId)).thenReturn(queryExecute);
    when(queryExecute.execute()).thenReturn(getPagedIterable(simpleResponse));

    List<EventData> response = azureMonitorClient.listEventDataWithAllPropertiesByResourceId(
        azureConfig, subscriptionId, startDate, endDate, resourceId);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0)).isInstanceOf(EventData.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListEventDataWithAllPropertiesByResourceIdWithException() {
    DateTime endDate = DateTime.now();
    DateTime startDate = endDate.minusMinutes(1);
    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

    assertThatThrownBy(()
                           -> azureMonitorClient.listEventDataWithAllPropertiesByResourceId(
                               azureConfig, null, startDate, endDate, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Subscriptions getSubscriptions() {
    Subscription subscription = PowerMockito.mock(Subscription.class);
    List<Subscription> responseList = new ArrayList<>();
    responseList.add(subscription);
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);

    return new Subscriptions() {
      @Override
      public Subscription getById(String s) {
        return null;
      }

      @Override
      public Mono<Subscription> getByIdAsync(String s) {
        return null;
      }

      @Override
      public PagedIterable<Subscription> list() {
        return getPagedIterable(simpleResponse);
      }

      @Override
      public PagedFlux<Subscription> listAsync() {
        return null;
      }
    };
  }

  @NotNull
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }
}
