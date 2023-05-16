/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KARAN_SARASWAT;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.offset;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.ChangeCategory;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.DeepLink;
import io.harness.cvng.beans.change.InternalChangeEvent;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.change.ChangeTimeline;
import io.harness.cvng.core.beans.change.ChangeTimeline.TimeRangeDetail;
import io.harness.cvng.core.beans.monitoredService.DurationDTO;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.impl.ChangeEventServiceImpl.TimelineObject;
import io.harness.cvng.core.utils.FeatureFlagNames;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class ChangeEventServiceImplTest extends CvNextGenTestBase {
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ActivityService activityService;
  @Inject ChangeEventServiceImpl changeEventService;
  @Inject ChangeSourceService changeSourceService;
  @Inject HPersistence hPersistence;

  BuilderFactory builderFactory;
  FeatureFlagService featureFlagService;

  List<String> changeSourceIdentifiers = Arrays.asList("changeSourceID");

  @Before
  public void before() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceService.createDefault(builderFactory.getProjectParams(),
        builderFactory.getContext().getServiceIdentifier(), builderFactory.getContext().getEnvIdentifier());
    MockitoAnnotations.initMocks(this);
    featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(
             eq(builderFactory.getContext().getAccountId()), eq(FeatureFlagNames.SRM_INTERNAL_CHANGE_SOURCE_CE)))
        .thenReturn(true);
    FieldUtils.writeField(changeEventService, "featureFlagService", featureFlagService, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testRegister_insert() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(Arrays.asList(builderFactory.getHarnessCDChangeSourceDTOBuilder().build())));
    ChangeEventDTO changeEventDTO = builderFactory.harnessCDChangeEventDTOBuilder().build();

    changeEventService.register(changeEventDTO);

    Activity activityFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(activityFromDb).isNotNull();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_insertInternalChangeEvent() {
    ChangeEventDTO changeEventDTO = builderFactory.getInternalChangeEventDTO_FFBuilder().build();

    changeEventService.register(changeEventDTO);

    Activity activityFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(activityFromDb).isNotNull();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_insertCustomChangeEvent_withoutEventId() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(
            Arrays.asList(builderFactory.getCustomChangeSourceDTOBuilder(ChangeSourceType.CUSTOM_DEPLOY).build())));
    ChangeEventDTO changeEventDTO = builderFactory.getCustomChangeEventBuilder(ChangeSourceType.CUSTOM_DEPLOY).build();

    changeEventService.register(changeEventDTO);
    changeEventService.register(changeEventDTO);

    Activity activityFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(activityFromDb).isNotNull();
    long count = hPersistence.createQuery(Activity.class).count();
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_insertCustomChangeEvent_withDuplicateEventId() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(
            Arrays.asList(builderFactory.getCustomChangeSourceDTOBuilder(ChangeSourceType.CUSTOM_DEPLOY).build())));
    ChangeEventDTO changeEventDTO =
        builderFactory.getCustomChangeEventBuilder(ChangeSourceType.CUSTOM_DEPLOY).id("identifier").build();

    changeEventService.register(changeEventDTO);
    changeEventService.register(changeEventDTO);

    long count = hPersistence.createQuery(Activity.class).count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_insertCustomChangeEvent_withUniqueEventId() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(
            Arrays.asList(builderFactory.getCustomChangeSourceDTOBuilder(ChangeSourceType.CUSTOM_DEPLOY).build())));
    ChangeEventDTO changeEventDTO =
        builderFactory.getCustomChangeEventBuilder(ChangeSourceType.CUSTOM_DEPLOY).id("identifier1").build();

    changeEventService.register(changeEventDTO);
    ChangeEventDTO changeEventDTO2 =
        builderFactory.getCustomChangeEventBuilder(ChangeSourceType.CUSTOM_DEPLOY).id("identifier2").build();
    changeEventService.register(changeEventDTO2);

    long count = hPersistence.createQuery(Activity.class).count();
    assertThat(count).isEqualTo(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRegister_insertWithNoMonitoredService() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(Arrays.asList(builderFactory.getHarnessCDChangeSourceDTOBuilder().build())));
    ChangeEventDTO changeEventDTO =
        builderFactory.harnessCDChangeEventDTOBuilder().monitoredServiceIdentifier(null).build();

    boolean saved = changeEventService.register(changeEventDTO);
    assertThat(saved).isTrue();
    Activity activityFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(activityFromDb).isNotNull();
    assertThat(activityFromDb.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_insertWithNoMonitoredServiceInternalChangeEvent() {
    ChangeEventDTO changeEventDTO =
        builderFactory.getInternalChangeEventDTO_FFBuilder().monitoredServiceIdentifier(null).build();

    boolean saved = changeEventService.register(changeEventDTO);
    assertThat(saved).isTrue();
    Activity activityFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(activityFromDb).isNotNull();
    assertThat(activityFromDb.getMonitoredServiceIdentifier())
        .isEqualTo(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testRegister_update() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(Arrays.asList(builderFactory.getHarnessCDChangeSourceDTOBuilder().build())));

    ChangeEventDTO changeEventDTO = builderFactory.harnessCDChangeEventDTOBuilder().build();
    changeEventService.register(changeEventDTO);
    Long eventTime = 123L;
    ChangeEventDTO changeEventDTO2 = builderFactory.harnessCDChangeEventDTOBuilder().eventTime(eventTime).build();
    changeEventService.register(changeEventDTO2);

    Assertions.assertThat(hPersistence.createQuery(Activity.class).count()).isEqualTo(1);
    Activity changeEventFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(changeEventFromDb.getEventTime().toEpochMilli()).isEqualTo(eventTime);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_multipleInternalChangeEvent() {
    ChangeEventDTO changeEventDTO = builderFactory.getInternalChangeEventDTO_FFBuilder().eventTime(100L).build();
    changeEventService.register(changeEventDTO);
    Long eventTime = 123L;
    ChangeEventDTO changeEventDTO2 = builderFactory.getInternalChangeEventDTO_FFBuilder().eventTime(eventTime).build();
    changeEventService.register(changeEventDTO2);

    Assertions.assertThat(hPersistence.createQuery(Activity.class).count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testRegister_updateInternalChangeEvent() {
    ChangeEventDTO changeEventDTO = builderFactory.getInternalChangeEventDTO_FFBuilder().build();
    changeEventService.register(changeEventDTO);
    ChangeEventDTO changeEventDTO2 =
        builderFactory.getInternalChangeEventDTO_FFBuilder()
            .metadata(
                InternalChangeEventMetaData.builder()
                    .activityType(ActivityType.FEATURE_FLAG)
                    .updatedBy("user2")
                    .eventStartTime(1000l)
                    .internalChangeEvent(
                        InternalChangeEvent.builder()
                            .changeEventDetailsLink(DeepLink.builder()
                                                        .action(DeepLink.Action.FETCH_DIFF_DATA)
                                                        .url("changeEventDetails")
                                                        .build())
                            .internalLinkToEntity(
                                DeepLink.builder().action(DeepLink.Action.REDIRECT_URL).url("internalUrl").build())
                            .eventDescriptions(Arrays.asList("eventDesc1", "eventDesc2"))
                            .build())
                    .build())
            .build();
    changeEventService.register(changeEventDTO2);

    Assertions.assertThat(hPersistence.createQuery(Activity.class).count()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testRegister_noChangeSource() {
    changeSourceService.create(builderFactory.getContext().getMonitoredServiceParams(),
        new HashSet<>(Arrays.asList(builderFactory.getHarnessCDChangeSourceDTOBuilder().build())));
    ChangeEventDTO changeEventDTO = builderFactory.harnessCDChangeEventDTOBuilder().build();

    changeEventService.register(changeEventDTO);

    Activity changeEventFromDb = hPersistence.createQuery(Activity.class).get();
    Assertions.assertThat(changeEventFromDb).isNotNull();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetPaginated() {
    Activity harnessCDActivity_1 =
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build();
    Activity harnessCDActivity_2 =
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(200)).build();
    Activity harnessCDActivity_3 =
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build();
    hPersistence.save(Arrays.asList(harnessCDActivity_1, harnessCDActivity_2, harnessCDActivity_3));
    PageResponse<ChangeEventDTO> firstPage = changeEventService.getChangeEvents(
        builderFactory.getContext().getProjectParams(), null, null, null, null, null, Instant.ofEpochSecond(100),
        Instant.ofEpochSecond(400), PageRequest.builder().pageIndex(0).pageSize(2).build());
    PageResponse<ChangeEventDTO> secondPage = changeEventService.getChangeEvents(
        builderFactory.getContext().getProjectParams(), null, null, null, null, null, Instant.ofEpochSecond(100),
        Instant.ofEpochSecond(400), PageRequest.builder().pageIndex(1).pageSize(2).build());

    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(200000);
    assertThat(secondPage.getContent().get(0).getEventTime()).isEqualTo(100000);
    assertThat(secondPage.getPageIndex()).isEqualTo(1);
    assertThat(secondPage.getPageItemCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetPaginated_withServiceFiltering() {
    Activity harnessCDActivity_1 =
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build();
    Activity harnessCDActivity_2 = builderFactory.getDeploymentActivityBuilder()
                                       .monitoredServiceIdentifier("service2_env2")
                                       .eventTime(Instant.ofEpochSecond(200))
                                       .build();
    Activity harnessCDActivity_3 =
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build();
    hPersistence.save(Arrays.asList(harnessCDActivity_1, harnessCDActivity_2, harnessCDActivity_3));
    PageResponse<ChangeEventDTO> firstPage = changeEventService.getChangeEvents(
        builderFactory.getContext().getProjectParams(),
        Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null, null, null, null,
        Instant.ofEpochSecond(100), Instant.ofEpochSecond(400), PageRequest.builder().pageIndex(0).pageSize(2).build());

    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(2);
    assertThat(firstPage.getTotalPages()).isEqualTo(1);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(100000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetPaginated_withTypeFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    PageResponse<ChangeEventDTO> firstPage =
        changeEventService.getChangeEvents(builderFactory.getContext().getProjectParams(),
            Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null, null,
            Arrays.asList(ChangeCategory.DEPLOYMENT, ChangeCategory.ALERTS),
            Arrays.asList(ChangeSourceType.HARNESS_CD, ChangeSourceType.KUBERNETES), Instant.ofEpochSecond(100),
            Instant.ofEpochSecond(400), PageRequest.builder().pageIndex(0).pageSize(2).build());

    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(2);
    assertThat(firstPage.getTotalPages()).isEqualTo(1);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(100000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testCreateTextSearchQuery() {
    // testing query as our test MongoServer doesn't support text search:
    // https://github.com/bwaldvogel/mongo-java-server
    Query<Activity> activityQuery = changeEventService.createTextSearchQuery(Instant.parse("2023-01-31T00:00:00.00Z"),
        Instant.parse("2023-01-31T10:00:00.00Z"), "searchText",
        Arrays.asList(ChangeCategory.DEPLOYMENT, ChangeCategory.ALERTS),
        Arrays.asList(ChangeSourceType.HARNESS_CD, ChangeSourceType.KUBERNETES));

    assertThat(activityQuery.toString())
        .isEqualTo(
            "{ {\"$and\": [{\"$text\": {\"$search\": \"searchText\"}}, {\"eventTime\": {\"$lt\": {\"$date\": \"2023-01-31T10:00:00Z\"}}}, {\"eventTime\": {\"$gte\": {\"$date\": \"2023-01-31T00:00:00Z\"}}}]}  }");

    activityQuery = changeEventService.createTextSearchQuery(
        Instant.parse("2023-01-31T00:00:00.00Z"), Instant.parse("2023-01-31T10:00:00.00Z"), "searchText", null, null);
    assertThat(activityQuery.toString())
        .isEqualTo(
            "{ {\"$and\": [{\"$text\": {\"$search\": \"searchText\"}}, {\"eventTime\": {\"$lt\": {\"$date\": \"2023-01-31T10:00:00Z\"}}}, {\"eventTime\": {\"$gte\": {\"$date\": \"2023-01-31T00:00:00Z\"}}}]}  }");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(400)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(400)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(), (List<String>) null, null,
            null, null, Instant.ofEpochSecond(300), Instant.ofEpochSecond(500));

    // to verify that the keys remain in the same order
    assertThat(changeSummaryDTO.getCategoryCountMap().keySet())
        .isEqualTo(new LinkedHashSet<>(List.of(ChangeCategory.values())));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(3);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getPercentageChange())
        .isCloseTo(200.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCount()).isEqualTo(2);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getPercentageChange())
        .isCloseTo(100.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getTotal().getCount()).isEqualTo(7);
    assertThat(changeSummaryDTO.getTotal().getCountInPrecedingWindow()).isEqualTo(4);
    assertThat(changeSummaryDTO.getTotal().getPercentageChange()).isCloseTo(75.0, offset(0.1));
  }

  @Test
  @Owner(developers = KARAN_SARASWAT)
  @Category(UnitTests.class)
  public void testGetChangeSummary_withCEFeatureFlagOff() {
    hPersistence.save(
        Arrays.asList(builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(300)).build()));

    when(featureFlagService.isFeatureFlagEnabled(
             eq(builderFactory.getContext().getAccountId()), eq(FeatureFlagNames.SRM_INTERNAL_CHANGE_SOURCE_CE)))
        .thenReturn(false);
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(), (List<String>) null, null,
            null, null, Instant.ofEpochSecond(100), Instant.ofEpochSecond(500));

    assertThat(changeSummaryDTO.getTotal().getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getTotal().getCountInPrecedingWindow()).isEqualTo(0);
    assertThat(changeSummaryDTO.getTotal().getPercentageChange()).isCloseTo(0.0, offset(0.1));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary_withServiceFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(50))
            .build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(50))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(250))
            .build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(250))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(),
            Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null, null, null,
            Instant.ofEpochSecond(300), Instant.ofEpochSecond(500));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(2);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getPercentageChange())
        .isCloseTo(100.0, offset(0.1));
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getPercentageChange())
        .isCloseTo(0.0, offset(0.1));

    assertThat(changeSummaryDTO.getTotal().getCount()).isEqualTo(5);
    assertThat(changeSummaryDTO.getTotal().getCountInPrecedingWindow()).isEqualTo(4);
    assertThat(changeSummaryDTO.getTotal().getPercentageChange()).isCloseTo(25.00, offset(0.1));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary_withTypeFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service2_env2")
            .eventTime(Instant.ofEpochSecond(350))
            .build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(350))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(),
            Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null,
            Arrays.asList(ChangeCategory.DEPLOYMENT, ChangeCategory.ALERTS),
            Arrays.asList(ChangeSourceType.HARNESS_CD, ChangeSourceType.KUBERNETES), Instant.ofEpochSecond(300),
            Instant.ofEpochSecond(500));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetChangeSummary_withTypeFilteringInternalChangeSource() {
    List<Activity> activities =
        Arrays.asList(builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getInternalChangeActivity_FFBuilder()
                .monitoredServiceIdentifier("service_env2")
                .eventTime(Instant.ofEpochSecond(50))
                .build(),
            builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getInternalChangeActivity_CEBuilder()
                .monitoredServiceIdentifier("service_env2")
                .eventTime(Instant.ofEpochSecond(50))
                .build(),
            builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
            builderFactory.getInternalChangeActivity_FFBuilder()
                .monitoredServiceIdentifier("service_env2")
                .eventTime(Instant.ofEpochSecond(350))
                .build(),
            builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
            builderFactory.getInternalChangeActivity_CEBuilder()
                .monitoredServiceIdentifier("service_env2")
                .eventTime(Instant.ofEpochSecond(350))
                .build(),
            builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .eventTime(Instant.ofEpochSecond(300))
                .build());
    activities.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(),
            Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null,
            Arrays.asList(ChangeCategory.DEPLOYMENT, ChangeCategory.FEATURE_FLAG, ChangeCategory.CHAOS_EXPERIMENT),
            Arrays.asList(ChangeSourceType.HARNESS_FF, ChangeSourceType.KUBERNETES, ChangeSourceType.HARNESS_CE),
            Instant.ofEpochSecond(300), Instant.ofEpochSecond(500));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary_withEnvironmentFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(50))
            .build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(50))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_FFBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(250))
            .build(),
        builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getInternalChangeActivity_CEBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(250))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getProjectParams(), (List<String>) null,
            Arrays.asList(builderFactory.getContext().getEnvIdentifier()), null, null, Instant.ofEpochSecond(300),
            Instant.ofEpochSecond(500));

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount()).isEqualTo(2);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCount()).isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.ALERTS).getCountInPrecedingWindow())
        .isEqualTo(0);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.INFRASTRUCTURE).getCountInPrecedingWindow())
        .isEqualTo(1);

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.FEATURE_FLAG).getPercentageChange())
        .isEqualTo(0);

    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCount()).isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
    assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.CHAOS_EXPERIMENT).getPercentageChange())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimeline() {
    List<Activity> activityList =
        Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(200)).build(),
            builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(250)).build(),
            builderFactory.getInternalChangeActivity_FFBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getInternalChangeActivity_CEBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .eventTime(Instant.ofEpochSecond(300))
                .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline = changeEventService.getTimeline(builderFactory.getContext().getProjectParams(), null,
        null, null, false, null, null, null, Instant.ofEpochSecond(100), Instant.ofEpochSecond(500), 2);

    // to verify that the keys remain in the same order
    assertThat(changeTimeline.getCategoryTimeline().keySet())
        .isEqualTo(new LinkedHashSet<>(List.of(ChangeCategory.values())));

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> featureFlagChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.FEATURE_FLAG);
    assertThat(featureFlagChanges.size()).isEqualTo(2);
    assertThat(featureFlagChanges.get(0).getCount()).isEqualTo(2);
    assertThat(featureFlagChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(featureFlagChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(featureFlagChanges.get(1).getCount()).isEqualTo(1);
    assertThat(featureFlagChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(featureFlagChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> chaosExperimentChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.CHAOS_EXPERIMENT);
    assertThat(chaosExperimentChanges.size()).isEqualTo(1);
    assertThat(chaosExperimentChanges.get(0).getCount()).isEqualTo(1);
    assertThat(chaosExperimentChanges.get(0).getStartTime()).isEqualTo(300000);
    assertThat(chaosExperimentChanges.get(0).getEndTime()).isEqualTo(600000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceChangeTimeline() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service1_env1")
            .eventTime(Instant.ofEpochSecond(500))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(14398)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(14399500))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline =
        changeEventService.getMonitoredServiceChangeTimeline(builderFactory.getContext().getMonitoredServiceParams(),
            null, null, DurationDTO.FOUR_HOURS, Instant.ofEpochSecond(14398));

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(14100000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(14400000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(300000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimeline_withTypeFilters() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getPagerDutyActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline = changeEventService.getTimeline(builderFactory.getContext().getProjectParams(), null,
        null, null, false, null, Arrays.asList(ChangeCategory.DEPLOYMENT, ChangeCategory.ALERTS),
        Arrays.asList(ChangeSourceType.HARNESS_CD, ChangeSourceType.KUBERNETES), Instant.ofEpochSecond(100),
        Instant.ofEpochSecond(500), 2);

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges).isEmpty();
    List<TimeRangeDetail> alertChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.ALERTS);
    assertThat(alertChanges).isEmpty();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimelineObject_forAggregationValidation() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(200)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(350)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(600))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    Iterator<TimelineObject> changeTimelineObject =
        changeEventService.getTimelineObject(builderFactory.getContext().getProjectParams(), null, null, null, null,
            null, Instant.ofEpochSecond(0), Instant.ofEpochSecond(600), 2, false);
    List<TimelineObject> timelineObjectList = new ArrayList<>();

    changeTimelineObject.forEachRemaining(timelineObjectList::add);

    assertThat(timelineObjectList.size()).isEqualTo(3);
    assertThat(timelineObjectList.stream()
                   .filter(timelineObject
                       -> timelineObject.id.index.equals(0) && timelineObject.id.type.equals(ActivityType.DEPLOYMENT))
                   .findAny()
                   .get()
                   .count)
        .isEqualTo(2);
    assertThat(timelineObjectList.stream()
                   .filter(timelineObject
                       -> timelineObject.id.index.equals(1) && timelineObject.id.type.equals(ActivityType.DEPLOYMENT))
                   .findAny()
                   .get()
                   .count)
        .isEqualTo(1);
    assertThat(timelineObjectList.stream()
                   .filter(timelineObject
                       -> timelineObject.id.index.equals(1) && timelineObject.id.type.equals(ActivityType.KUBERNETES))
                   .findAny()
                   .get()
                   .count)
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimeline_withServiceFiltering() {
    List<Activity> activityList =
        Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
            builderFactory.getDeploymentActivityBuilder()
                .monitoredServiceIdentifier("monitoredservice2")
                .eventTime(Instant.ofEpochSecond(200))
                .build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .eventTime(Instant.ofEpochSecond(300))
                .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline = changeEventService.getTimeline(builderFactory.getContext().getProjectParams(),
        Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null, null, false, null, null, null,
        Instant.ofEpochSecond(100), Instant.ofEpochSecond(500), 2);

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(600000);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTimeline_withMonitoredServiceFiltering() {
    List<Activity> activityList =
        Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
            builderFactory.getDeploymentActivityBuilder()
                .monitoredServiceIdentifier("monitoredservice2")
                .eventTime(Instant.ofEpochSecond(200))
                .build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .eventTime(Instant.ofEpochSecond(300))
                .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline = changeEventService.getTimeline(builderFactory.getContext().getProjectParams(), null,
        null, Arrays.asList(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier()),
        false, null, null, null, Instant.ofEpochSecond(100), Instant.ofEpochSecond(500), 2);

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(600000);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetTimeline_withScopedMonitoredServiceFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("monitoredServiceV2")
            .eventTime(Instant.ofEpochSecond(300))
            .build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline =
        changeEventService.getTimeline(builderFactory.getContext().getProjectParams(), null, null,
            Arrays.asList(
                ScopedInformation.getScopedInformation(builderFactory.getContext().getAccountId(),
                    builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
                    builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier()),
                ScopedInformation.getScopedInformation(builderFactory.getContext().getAccountId(),
                    builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
                    "monitoredServiceV2")),
            true, null, null, null, Instant.ofEpochSecond(0), Instant.ofEpochSecond(600), 2);

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);
    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(1).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(1).getEndTime()).isEqualTo(600000);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTimeline_withMonitoredServiceAndServiceFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("monitoredservice2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    assertThatThrownBy(
        ()
            -> changeEventService.getTimeline(builderFactory.getContext().getProjectParams(),
                Arrays.asList(builderFactory.getContext().getServiceIdentifier()), null,
                Arrays.asList(builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier()),
                false, null, null, null, Instant.ofEpochSecond(100), Instant.ofEpochSecond(500), 2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("serviceIdentifier, envIdentifier filter can not be used with monitoredServiceIdentifier filter");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetTimeline_withEnvironmentFiltering() {
    List<Activity> activityList =
        Arrays.asList(builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
            builderFactory.getDeploymentActivityBuilder()
                .monitoredServiceIdentifier("service2_env2")
                .eventTime(Instant.ofEpochSecond(200))
                .build(),
            builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(300)).build(),
            builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
                .eventTime(Instant.ofEpochSecond(300))
                .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeTimeline changeTimeline = changeEventService.getTimeline(builderFactory.getContext().getProjectParams(), null,
        Arrays.asList(builderFactory.getContext().getEnvIdentifier()), null, false, null, null, null,
        Instant.ofEpochSecond(100), Instant.ofEpochSecond(500), 2);

    List<TimeRangeDetail> deploymentChanges = changeTimeline.getCategoryTimeline().get(ChangeCategory.DEPLOYMENT);
    assertThat(deploymentChanges.size()).isEqualTo(2);
    assertThat(deploymentChanges.get(0).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(0).getStartTime()).isEqualTo(0);
    assertThat(deploymentChanges.get(0).getEndTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getCount()).isEqualTo(1);
    assertThat(deploymentChanges.get(1).getStartTime()).isEqualTo(300000);
    assertThat(deploymentChanges.get(1).getEndTime()).isEqualTo(600000);

    List<TimeRangeDetail> infrastructureChanges =
        changeTimeline.getCategoryTimeline().get(ChangeCategory.INFRASTRUCTURE);
    assertThat(infrastructureChanges.size()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getCount()).isEqualTo(1);
    assertThat(infrastructureChanges.get(0).getStartTime()).isEqualTo(300000);
    assertThat(infrastructureChanges.get(0).getEndTime()).isEqualTo(600000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetPaginated_withEnvironmentFiltering() {
    List<Activity> activityList = Arrays.asList(
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder().eventTime(Instant.ofEpochSecond(50)).build(),
        builderFactory.getDeploymentActivityBuilder().eventTime(Instant.ofEpochSecond(100)).build(),
        builderFactory.getDeploymentActivityBuilder()
            .monitoredServiceIdentifier("service_env2")
            .eventTime(Instant.ofEpochSecond(200))
            .build(),
        builderFactory.getKubernetesClusterActivityForAppServiceBuilder()
            .eventTime(Instant.ofEpochSecond(300))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    PageResponse<ChangeEventDTO> firstPage =
        changeEventService.getChangeEvents(builderFactory.getContext().getProjectParams(), null,
            Arrays.asList(builderFactory.getContext().getEnvIdentifier()), null, null, null, Instant.ofEpochSecond(100),
            Instant.ofEpochSecond(400), PageRequest.builder().pageIndex(0).pageSize(2).build());

    assertThat(firstPage.getPageIndex()).isEqualTo(0);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getTotalItems()).isEqualTo(2);
    assertThat(firstPage.getTotalPages()).isEqualTo(1);
    assertThat(firstPage.getPageItemCount()).isEqualTo(2);
    assertThat(firstPage.getContent().size()).isEqualTo(2);
    assertThat(firstPage.getContent().get(0).getEventTime()).isEqualTo(300000);
    assertThat(firstPage.getContent().get(1).getEventTime()).isEqualTo(100000);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetChangeSummary_WithServiceParams() {
    List<Activity> activityList = Arrays.asList(builderFactory.getDeploymentActivityBuilder().build(),
        builderFactory.getDeploymentActivityBuilder()
            .eventTime(builderFactory.getClock().instant().minus(Duration.ofMinutes(15)))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO =
        changeEventService.getChangeSummary(builderFactory.getContext().getMonitoredServiceParams(),
            changeSourceIdentifiers, builderFactory.getClock().instant().minus(Duration.ofMinutes(10)),
            builderFactory.getClock().instant().plus(Duration.ofMinutes(10)));
    Assertions.assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount())
        .isEqualTo(1);
    Assertions
        .assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetChangeSummary_WithMonitoredService() {
    List<Activity> activityList = Arrays.asList(builderFactory.getDeploymentActivityBuilder().build(),
        builderFactory.getDeploymentActivityBuilder()
            .eventTime(builderFactory.getClock().instant().minus(Duration.ofMinutes(15)))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO = changeEventService.getChangeSummary(builderFactory.getProjectParams(), null,
        Collections.singletonList(
            builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier()),
        false, null, null, builderFactory.getClock().instant().minus(Duration.ofMinutes(10)),
        builderFactory.getClock().instant().plus(Duration.ofMinutes(10)));
    Assertions.assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount())
        .isEqualTo(1);
    Assertions
        .assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetChangeSummary_WithScopedMonitoredService() {
    List<Activity> activityList = Arrays.asList(builderFactory.getDeploymentActivityBuilder().build(),
        builderFactory.getDeploymentActivityBuilder()
            .eventTime(builderFactory.getClock().instant().minus(Duration.ofMinutes(15)))
            .build());
    activityList.forEach(activity -> activityService.upsert(activity));
    ChangeSummaryDTO changeSummaryDTO = changeEventService.getChangeSummary(builderFactory.getProjectParams(), null,
        Collections.singletonList(ScopedInformation.getScopedInformation(builderFactory.getContext().getAccountId(),
            builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
            builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())),
        true, null, null, builderFactory.getClock().instant().minus(Duration.ofMinutes(10)),
        builderFactory.getClock().instant().plus(Duration.ofMinutes(10)));
    Assertions.assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCount())
        .isEqualTo(1);
    Assertions
        .assertThat(changeSummaryDTO.getCategoryCountMap().get(ChangeCategory.DEPLOYMENT).getCountInPrecedingWindow())
        .isEqualTo(1);
  }
}
