/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.rule.OwnerRule.ANUPAM;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NICOLAS;
import static io.harness.rule.OwnerRule.VLAD;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.DelegateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.AutoUpgrade;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.delegate.beans.DelegateInsightsBarDetails;
import io.harness.delegate.beans.DelegateInsightsDetails;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateSize;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.filter.DelegateInstanceConnectivityStatus;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.impl.DelegateSetupServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.version.VersionInfo;
import io.harness.version.VersionInfoManager;

import software.wings.beans.SelectorType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
public class DelegateSetupServiceTest extends DelegateServiceTestBase {
  private static final String VERSION = "1.0.0";
  private static final String IMMUTABLE_DELEGATE_VERSION = "22.08.77000";
  private static final String MANAGER_VERSION = "1.0.77000";
  private static final String MANAGER_BUILD_NUMBER = "77000";
  private static final String GROUPED_HOSTNAME_SUFFIX = "-{n}";

  private static final String TEST_ACCOUNT_ID = "accountId";
  private static final String TEST_DELEGATE_GROUP_ID_1 = "delegateGroupId1";
  private static final String TEST_DELEGATE_GROUP_ID_2 = "delegateGroupId2";
  private static final String TEST_DELEGATE_GROUP_ID_3 = "delegateGroupId3";
  private static final String TEST_DELEGATE_GROUP_ID_4 = "delegateGroupId4";

  @Mock private DelegateCache delegateCache;
  @Mock private VersionInfoManager versionInfoManager;

  @InjectMocks @Inject private DelegateSetupServiceImpl delegateSetupService;
  @Inject private HPersistence persistence;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldListAccountDelegateGroups() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails =
        DelegateSizeDetails.builder().size(DelegateSize.LARGE).cpu(2.5d).label("size").ram(2048).replicas(2).build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    persistence.save(delegateGroup1);
    DelegateGroup delegateGroup2 =
        DelegateGroup.builder()
            .name("grp2")
            .accountId(accountId)
            .ng(true)
            .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).replicas(1).build())
            .build();
    persistence.save(delegateGroup2);

    when(delegateCache.getDelegateGroup(accountId, delegateGroup1.getUuid())).thenReturn(delegateGroup1);
    when(delegateCache.getDelegateGroup(accountId, delegateGroup2.getUuid())).thenReturn(delegateGroup2);

    Delegate delegate1 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-0")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-1")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis() - 60000)
                             .build();

    // this delegate should cause an empty group to be returned
    Delegate delegate3 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateName("grp2")
                             .hostName("kube-2")
                             .sizeDetails(DelegateSizeDetails.builder().replicas(1).build())
                             .delegateGroupId(delegateGroup2.getUuid())
                             .lastHeartBeat(System.currentTimeMillis() - 600000)
                             .build();

    Delegate deletedDelegate =
        createDelegateBuilder().accountId(accountId).status(DelegateInstanceStatus.DELETED).build();

    Delegate orgDelegate = createDelegateBuilder()
                               .accountId(accountId)
                               .owner(DelegateEntityOwner.builder().identifier(generateUuid()).build())
                               .build();

    persistence.save(Arrays.asList(orgDelegate, deletedDelegate, delegate1, delegate2, delegate3));

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetails(accountId, null, null);

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(2);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp1", "grp2");

    for (DelegateGroupDetails group : delegateGroupListing.getDelegateGroupDetails()) {
      if (group.getGroupName().equals("grp1")) {
        assertThat(group.getDelegateInstanceDetails()).hasSize(2);
        assertThat(group.getGroupId()).isEqualTo(delegateGroup1.getUuid());
        assertThat(group.getDelegateType()).isEqualTo(KUBERNETES);
        assertThat(group.getDelegateDescription()).isEqualTo("description");
        assertThat(group.getConnectivityStatus()).isEqualTo("connected");
        assertThat(group.getDelegateConfigurationId()).isEqualTo(delegateProfileId);
        assertThat(group.getGroupImplicitSelectors()).isNotNull();
        assertThat(group.getGroupImplicitSelectors().containsKey("grp1")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("kube-0")).isFalse();
        assertThat(group.getGroupImplicitSelectors().containsKey("kube-1")).isFalse();
        assertThat(group.getGroupImplicitSelectors().containsKey("profile")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("s1")).isTrue();
        assertThat(group.getGroupImplicitSelectors().containsKey("s2")).isTrue();
        assertThat(group.getGroupCustomSelectors()).isNotNull();
        assertThat(group.getGroupCustomSelectors().contains("custom-grp-tag")).isTrue();
        assertThat(group.getLastHeartBeat()).isEqualTo(delegate1.getLastHeartBeat());
        assertThat(group.getDelegateInstanceDetails())
            .extracting(DelegateGroupListing.DelegateInner::getUuid)
            .containsOnly(delegate1.getUuid(), delegate2.getUuid());
      } else if (group.getGroupName().equals("grp2")) {
        assertThat(group.getConnectivityStatus()).isEqualTo("disconnected");
        assertThat(group.getDelegateInstanceDetails())
            .extracting(DelegateGroupListing.DelegateInner::getUuid)
            .containsOnly(delegate3.getUuid());
        assertThat(group.getDelegateGroupExpirationTime()).isEqualTo(0);
        assertThat(group.getDelegateInstanceDetails().get(0).isActivelyConnected()).isEqualTo(false);
      }
    }
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteByAccountForDelegateGroups() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails =
        DelegateSizeDetails.builder().size(DelegateSize.LARGE).cpu(2.5d).label("size").ram(2048).replicas(2).build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    when(delegateCache.getDelegateGroup(accountId, delegateGroup1.getUuid())).thenReturn(delegateGroup1);
    persistence.save(delegateGroup1);
    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetails(accountId, null, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails().size()).isEqualTo(1);
    delegateSetupService.deleteByAccountId(accountId);
    delegateGroupListing = delegateSetupService.listDelegateGroupDetails(accountId, null, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void listDelegateShouldReturnDelegateGroups() {
    prepareInitialData();

    List<DelegateListResponse> delegateListResponses =
        delegateSetupService.listDelegates(TEST_ACCOUNT_ID, null, null, DelegateFilterPropertiesDTO.builder().build());

    assertThat(delegateListResponses).hasSize(3);
    assertThat(delegateListResponses).extracting(DelegateListResponse::getName).containsOnly("grp1", "grp2", "grp4");
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void listDelegateShouldReturnDelegateGroupsFilteredByTags() {
    prepareInitialData();

    List<DelegateListResponse> delegateListResponses = delegateSetupService.listDelegates(TEST_ACCOUNT_ID, null, null,
        DelegateFilterPropertiesDTO.builder().delegateTags(new HashSet<>(List.of("taggroup1"))).build());

    assertThat(delegateListResponses).hasSize(1);
    assertThat(delegateListResponses.get(0).getName()).isEqualTo("grp1");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroups() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(3);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp1", "grp2", "grp4");
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupVersion()).isEqualTo("22.09.76614");
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getAutoUpgrade()).isEqualTo(AutoUpgrade.DETECTING);
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(1).getAutoUpgrade()).isEqualTo(AutoUpgrade.OFF);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldThrowException() {
    delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null, null, "filterId", "",
        DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredByProperties_GroupIdentifier() {
    prepareInitialData();

    DelegateFilterPropertiesDTO filterProperties =
        DelegateFilterPropertiesDTO.builder().delegateGroupIdentifier("ier1").build();
    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(
        TEST_ACCOUNT_ID, null, null, "", "", filterProperties, PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp1");
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredByDelegateInstanceStatus() {
    prepareInitialData();

    DelegateFilterPropertiesDTO filterPropertiesConnected =
        DelegateFilterPropertiesDTO.builder().status(DelegateInstanceConnectivityStatus.CONNECTED).build();

    DelegateFilterPropertiesDTO filterPropertiesDisconnected =
        DelegateFilterPropertiesDTO.builder().status(DelegateInstanceConnectivityStatus.DISCONNECTED).build();

    DelegateGroupListing delegateGroupListing1 = delegateSetupService.listDelegateGroupDetailsV2(
        TEST_ACCOUNT_ID, null, null, "", "", filterPropertiesConnected, PageRequest.builder().build());

    DelegateGroupListing delegateGroupListing2 = delegateSetupService.listDelegateGroupDetailsV2(
        TEST_ACCOUNT_ID, null, null, "", "", filterPropertiesDisconnected, PageRequest.builder().build());

    assertThat(delegateGroupListing1.getDelegateGroupDetails()).hasSize(2);
    assertThat(delegateGroupListing2.getDelegateGroupDetails()).hasSize(0);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredBySearchTerm_Name() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "grp1", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp1");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsBySortOrderName() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "", DelegateFilterPropertiesDTO.builder().build(),
        PageRequest.builder()
            .sortOrders(Collections.singletonList(
                SortOrder.Builder.aSortOrder().withField("name", SortOrder.OrderType.DESC).build()))
            .build());
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(3);
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId()).isEqualTo("delegateGroupId4");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsBySortOrderVersion() {
    prepareInitialData();
    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "", DelegateFilterPropertiesDTO.builder().build(),
        PageRequest.builder()
            .sortOrders(Collections.singletonList(
                SortOrder.Builder.aSortOrder().withField("version", SortOrder.OrderType.DESC).build()))
            .build());
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(3);
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId()).isEqualTo("delegateGroupId1");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredBySearchTerm_Tags1() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "taggroup1", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredBySearchTerm_Tags3() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "taggroup3", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(0);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredBySearchTerm_Tags4() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "taggroup4", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void listV2ShouldReturnDelegateGroupsFilteredBySearchTerm_CommonTag() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing = delegateSetupService.listDelegateGroupDetailsV2(TEST_ACCOUNT_ID, null,
        null, "", "commonTag", DelegateFilterPropertiesDTO.builder().build(), PageRequest.builder().build());

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
    assertThat(delegateGroupListing.getDelegateGroupDetails())
        .extracting(DelegateGroupDetails::getGroupName)
        .containsOnly("grp2");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldListDelegateGroupsUpTheHierarchy() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    DelegateGroup acctGroup = DelegateGroup.builder()
                                  .accountId(accountId)
                                  .name("acctGroup")
                                  .ng(true)
                                  .tags(ImmutableSet.of("custom-grp-tag"))
                                  .build();
    DelegateGroup orgGroup = DelegateGroup.builder()
                                 .accountId(accountId)
                                 .name("orgGroup")
                                 .ng(true)
                                 .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                                 .build();
    DelegateGroup projectGroup = DelegateGroup.builder()
                                     .accountId(accountId)
                                     .ng(true)
                                     .name("projectGroup")
                                     .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                     .build();
    persistence.save(Arrays.asList(acctGroup, orgGroup, projectGroup));

    Delegate cgAcctDelegate = createDelegateBuilder()
                                  .accountId(accountId)
                                  .ng(false)
                                  .delegateType(KUBERNETES)
                                  .delegateName(generateUuid())
                                  .hostName(generateUuid())
                                  .build();

    Delegate acctDelegate = createDelegateBuilder()
                                .accountId(accountId)
                                .ng(true)
                                .delegateType(KUBERNETES)
                                .delegateName(generateUuid())
                                .hostName(generateUuid())
                                .delegateGroupId(acctGroup.getUuid())
                                .build();

    Delegate orgDelegate = createDelegateBuilder()
                               .accountId(accountId)
                               .ng(true)
                               .delegateType(KUBERNETES)
                               .delegateName(generateUuid())
                               .hostName(generateUuid())
                               .delegateGroupId(orgGroup.getUuid())
                               .owner(DelegateEntityOwner.builder().identifier(orgId).build())
                               .build();

    Delegate projectDelegate = createDelegateBuilder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateType(KUBERNETES)
                                   .delegateName(generateUuid())
                                   .hostName(generateUuid())
                                   .delegateGroupId(projectGroup.getUuid())
                                   .owner(DelegateEntityOwner.builder().identifier(orgId + "/" + projectId).build())
                                   .build();

    persistence.save(Arrays.asList(cgAcctDelegate, acctDelegate, orgDelegate, projectDelegate));

    DelegateGroupListing delegateGroupListing =
        delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, null, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
    assertThat(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId()).isEqualTo(acctGroup.getUuid());

    delegateGroupListing = delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, null);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(2);
    assertThat(Arrays.asList(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(1).getGroupId()))
        .containsExactlyInAnyOrder(acctGroup.getUuid(), orgGroup.getUuid());

    delegateGroupListing = delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, projectId);
    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(3);
    assertThat(Arrays.asList(delegateGroupListing.getDelegateGroupDetails().get(0).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(1).getGroupId(),
                   delegateGroupListing.getDelegateGroupDetails().get(2).getGroupId()))
        .containsExactlyInAnyOrder(acctGroup.getUuid(), orgGroup.getUuid(), projectGroup.getUuid());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldGetDelegateGroupDetails() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails =
        DelegateSizeDetails.builder().size(DelegateSize.LARGE).cpu(2.5d).label("size").ram(2048).replicas(2).build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    persistence.save(delegateGroup1);

    when(delegateCache.getDelegateGroup(accountId, delegateGroup1.getUuid())).thenReturn(delegateGroup1);

    // Insights
    DelegateInsightsDetails delegateInsightsDetails =
        DelegateInsightsDetails.builder()
            .insights(ImmutableList.of(
                DelegateInsightsBarDetails.builder().build(), DelegateInsightsBarDetails.builder().build()))
            .build();
    Delegate delegate1 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-0")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-1")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis() - 600000)
                             .build();

    persistence.save(Arrays.asList(delegate1, delegate2));

    DelegateGroupDetails delegateGroupDetails =
        delegateSetupService.getDelegateGroupDetails(accountId, delegateGroup1.getUuid());

    assertThat(delegateGroupDetails).isNotNull();

    assertThat(delegateGroupDetails.getGroupName()).isEqualTo("grp1");
    assertThat(delegateGroupDetails.getDelegateInstanceDetails()).hasSize(2);
    assertThat(delegateGroupDetails.getGroupId()).isEqualTo(delegateGroup1.getUuid());
    assertThat(delegateGroupDetails.getDelegateType()).isEqualTo(KUBERNETES);
    assertThat(delegateGroupDetails.getDelegateDescription()).isEqualTo("description");
    assertThat(delegateGroupDetails.getConnectivityStatus()).isEqualTo("partially connected");
    assertThat(delegateGroupDetails.getDelegateConfigurationId()).isEqualTo(delegateProfileId);
    assertThat(delegateGroupDetails.getGroupImplicitSelectors()).isNotNull();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("grp1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-0")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-1")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("profile")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s2")).isTrue();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("custom-grp-tag")).isTrue();
    assertThat(delegateGroupDetails.getLastHeartBeat()).isEqualTo(delegate1.getLastHeartBeat());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getUuid)
        .containsOnly(delegate1.getUuid(), delegate2.getUuid());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getHostName)
        .containsOnly("kube-0", "kube-1");
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getLastHeartbeat)
        .containsOnly(delegate1.getLastHeartBeat(), delegate2.getLastHeartBeat());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::isActivelyConnected)
        .containsOnly(true, false);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldGetDelegateGroupDetailsByIdentifier() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId))
        .thenReturn(DelegateProfile.builder().name("profile").selectors(ImmutableList.of("s1", "s2")).build());

    DelegateSizeDetails grp1SizeDetails =
        DelegateSizeDetails.builder().size(DelegateSize.LARGE).cpu(2.5d).label("size").ram(2048).replicas(2).build();

    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier("orgId/projectId").build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .identifier("identifier1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .owner(owner)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(grp1SizeDetails)
                                       .delegateConfigurationId(delegateProfileId)
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    persistence.save(delegateGroup1);

    // Insights
    DelegateInsightsDetails delegateInsightsDetails =
        DelegateInsightsDetails.builder()
            .insights(ImmutableList.of(
                DelegateInsightsBarDetails.builder().build(), DelegateInsightsBarDetails.builder().build()))
            .build();
    Delegate delegate1 = createDelegateBuilder()
                             .accountId(accountId)
                             .owner(owner)
                             .ng(true)
                             .lastHeartBeat(System.currentTimeMillis())
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .description("description")
                             .hostName("kube-0")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(accountId)
                             .ng(true)
                             .owner(owner)
                             .delegateType(KUBERNETES)
                             .delegateName("grp1")
                             .lastHeartBeat(System.currentTimeMillis())
                             .description("description")
                             .hostName("kube-1")
                             .sizeDetails(grp1SizeDetails)
                             .delegateGroupId(delegateGroup1.getUuid())
                             .delegateProfileId(delegateProfileId)
                             .lastHeartBeat(System.currentTimeMillis() - 60000)
                             .build();

    persistence.save(Arrays.asList(delegate1, delegate2));

    DelegateGroupDetails delegateGroupDetails =
        delegateSetupService.getDelegateGroupDetailsV2(accountId, "orgId", "projectId", delegateGroup1.getIdentifier());

    assertThat(delegateGroupDetails).isNotNull();

    assertThat(delegateGroupDetails.getDelegateGroupIdentifier()).isEqualTo("identifier1");
    assertThat(delegateGroupDetails.getGroupName()).isEqualTo("grp1");
    assertThat(delegateGroupDetails.getDelegateInstanceDetails()).hasSize(2);
    assertThat(delegateGroupDetails.getGroupId()).isEqualTo(delegateGroup1.getUuid());
    assertThat(delegateGroupDetails.getDelegateType()).isEqualTo(KUBERNETES);
    assertThat(delegateGroupDetails.getDelegateDescription()).isEqualTo("description");
    assertThat(delegateGroupDetails.getConnectivityStatus()).isEqualTo("connected");
    assertThat(delegateGroupDetails.getDelegateConfigurationId()).isEqualTo(delegateProfileId);
    assertThat(delegateGroupDetails.getGroupImplicitSelectors()).isNotNull();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("grp1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-0")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("kube-1")).isFalse();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("profile")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s1")).isTrue();
    assertThat(delegateGroupDetails.getGroupImplicitSelectors().containsKey("s2")).isTrue();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("custom-grp-tag")).isTrue();
    assertThat(delegateGroupDetails.getLastHeartBeat()).isEqualTo(delegate1.getLastHeartBeat());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getUuid)
        .containsOnly(delegate1.getUuid(), delegate2.getUuid());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getHostName)
        .containsOnly("kube-0", "kube-1");
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::getLastHeartbeat)
        .containsOnly(delegate1.getLastHeartBeat(), delegate2.getLastHeartBeat());
    assertThat(delegateGroupDetails.getDelegateInstanceDetails())
        .extracting(DelegateGroupListing.DelegateInner::isActivelyConnected)
        .containsOnly(true, true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testUpdateDelegateGroupShouldModifyTags() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .accountId(accountId)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    persistence.save(delegateGroup1);

    // Test with populated tags list
    DelegateGroupDetails delegateGroupDetails =
        delegateSetupService.updateDelegateGroup(accountId, delegateGroup1.getUuid(),
            DelegateGroupDetails.builder().groupCustomSelectors(ImmutableSet.of("tag1", "tag2")).build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("custom-grp-tag")).isFalse();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("tag1")).isTrue();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("tag2")).isTrue();

    // Test with empty tags list
    delegateGroupDetails = delegateSetupService.updateDelegateGroup(accountId, delegateGroup1.getUuid(),
        DelegateGroupDetails.builder().groupCustomSelectors(Collections.emptySet()).build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors()).isNull();

    // Test with null tags list
    delegateGroupDetails = delegateSetupService.updateDelegateGroup(
        accountId, delegateGroup1.getUuid(), DelegateGroupDetails.builder().build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors()).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testUpdateDelegateGroupDetailsByIdentifierShouldModifyTags() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String identifier = generateUuid();
    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier(orgId + "/" + projectId).build();

    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .name("grp1")
                                       .identifier(identifier)
                                       .accountId(accountId)
                                       .owner(owner)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .tags(ImmutableSet.of("custom-grp-tag"))
                                       .build();
    persistence.save(delegateGroup1);

    // Test with empty tags list
    DelegateGroupDetails delegateGroupDetails = delegateSetupService.updateDelegateGroup(accountId,
        delegateGroup1.getUuid(), DelegateGroupDetails.builder().groupCustomSelectors(Collections.emptySet()).build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors()).isNull();

    // Test with populated tags list
    delegateGroupDetails = delegateSetupService.updateDelegateGroup(accountId, orgId, projectId, identifier,
        DelegateGroupDetails.builder().groupCustomSelectors(ImmutableSet.of("tag1", "tag2")).build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("custom-grp-tag")).isFalse();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("tag1")).isTrue();
    assertThat(delegateGroupDetails.getGroupCustomSelectors().contains("tag2")).isTrue();

    // Test with null tags list
    delegateGroupDetails = delegateSetupService.updateDelegateGroup(
        accountId, delegateGroup1.getUuid(), DelegateGroupDetails.builder().build());

    assertThat(delegateGroupDetails).isNotNull();
    assertThat(delegateGroupDetails.getGroupCustomSelectors()).isNull();
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(KUBERNETES)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameNullValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate(null);

    assertThat(hostNameForGroupedDelegate).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameEmptyValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate("");

    assertThat(hostNameForGroupedDelegate).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldRetrieveGroupedHostnameValidValue() {
    String hostNameForGroupedDelegate = delegateSetupService.getHostNameForGroupedDelegate("test-hostname-1");

    assertThat(hostNameForGroupedDelegate).isEqualTo("test-hostname" + GROUPED_HOSTNAME_SUFFIX);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegatesImplicitSelectors() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(delegateProfileId)
                                          .accountId(accountId)
                                          .name(generateUuid())
                                          .selectors(ImmutableList.of("jkl", "fgh"))
                                          .build();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId)).thenReturn(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("host")
                            .delegateName("test")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateProfileId(delegateProfile.getUuid())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate, false).keySet();
    assertThat(tags.size()).isEqualTo(5);
    assertThat(tags).containsExactlyInAnyOrder(delegateProfile.getName().toLowerCase(), "test", "jkl", "fgh", "host");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithDelegateProfileSelectorsOnly() {
    String accountId = generateUuid();
    String delegateProfileId = generateUuid();

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(delegateProfileId)
                                          .accountId(accountId)
                                          .selectors(ImmutableList.of("jkl", "fgh"))
                                          .build();

    when(delegateCache.getDelegateProfile(accountId, delegateProfileId)).thenReturn(delegateProfile);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .delegateProfileId(delegateProfile.getUuid())
                            .ip("127.0.0.1")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);

    Set<String> selectors = delegateSetupService.retrieveDelegateImplicitSelectors(delegate, false).keySet();
    assertThat(selectors.size()).isEqualTo(2);
    assertThat(selectors).containsExactly("fgh", "jkl");
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithHostName() {
    String accountId = generateUuid();

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .ip("127.0.0.1")
                            .hostName("a.b.c")
                            .version(VERSION)
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate, false).keySet();
    assertThat(tags.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void shouldRetrieveDelegateImplicitSelectorsWithGroupSelectors() {
    String accountId = generateUuid();

    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .uuid(generateUuid())
                                      .accountId(accountId)
                                      .name("group")
                                      .tags(ImmutableSet.of("custom-tag"))
                                      .build();

    persistence.save(delegateGroup);

    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .version(VERSION)
                            .hostName("host")
                            .status(DelegateInstanceStatus.ENABLED)
                            .lastHeartBeat(System.currentTimeMillis())
                            .delegateGroupId(delegateGroup.getUuid())
                            .build();
    persistence.save(delegate);

    Set<String> tags = delegateSetupService.retrieveDelegateImplicitSelectors(delegate, false).keySet();
    assertThat(tags.size()).isEqualTo(3);
    assertThat(tags).containsExactlyInAnyOrder("group", "custom-tag", "host");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateGroupsShouldReturnEmptyList() {
    String accountId = generateUuid();
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null, null)).isEmpty();
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null, Collections.emptyList())).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateGroupsShouldReturnCorrectResult() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    DelegateGroup deletedGroup =
        DelegateGroup.builder().accountId(accountId).ng(true).status(DelegateGroupStatus.DELETED).build();
    DelegateGroup acctGroup = DelegateGroup.builder().accountId(accountId).ng(true).build();
    DelegateGroup orgGroup = DelegateGroup.builder()
                                 .accountId(accountId)
                                 .ng(true)
                                 .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                                 .build();
    DelegateGroup projectGroup = DelegateGroup.builder()
                                     .accountId(accountId)
                                     .ng(true)
                                     .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                     .build();
    persistence.save(Arrays.asList(deletedGroup, acctGroup, orgGroup, projectGroup));

    Delegate cgDelegate = Delegate.builder()
                              .accountId(accountId)
                              .delegateGroupId(generateUuid())
                              .status(DelegateInstanceStatus.ENABLED)
                              .build();
    Delegate deletedDelegate = Delegate.builder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateGroupId(deletedGroup.getUuid())
                                   .status(DelegateInstanceStatus.DELETED)
                                   .build();
    Delegate acctDelegate = Delegate.builder()
                                .accountId(accountId)
                                .ng(true)
                                .delegateGroupId(acctGroup.getUuid())
                                .status(DelegateInstanceStatus.ENABLED)
                                .build();
    Delegate orgDelegate = Delegate.builder()
                               .accountId(accountId)
                               .ng(true)
                               .delegateGroupId(orgGroup.getUuid())
                               .status(DelegateInstanceStatus.ENABLED)
                               .owner(DelegateEntityOwnerHelper.buildOwner(orgId, null))
                               .build();
    Delegate projectDelegate = Delegate.builder()
                                   .accountId(accountId)
                                   .ng(true)
                                   .delegateGroupId(projectGroup.getUuid())
                                   .status(DelegateInstanceStatus.ENABLED)
                                   .owner(DelegateEntityOwnerHelper.buildOwner(orgId, projectId))
                                   .build();

    persistence.saveBatch(Arrays.asList(cgDelegate, deletedDelegate, acctDelegate, orgDelegate, projectDelegate));

    // Test non-existing delegate
    assertThat(
        delegateSetupService.validateDelegateGroups(accountId, null, null, Collections.singletonList(generateUuid())))
        .containsExactly(false);

    // Test cg delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, null, null, Collections.singletonList(cgDelegate.getDelegateGroupId())))
        .containsExactly(false);

    // Test account delegate
    assertThat(delegateSetupService.validateDelegateGroups(accountId, null, null,
                   Arrays.asList(deletedDelegate.getDelegateGroupId(), acctDelegate.getDelegateGroupId())))
        .containsExactly(false, true);

    // Test org delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, orgId, null, Collections.singletonList(orgDelegate.getDelegateGroupId())))
        .containsExactly(true);

    // Test project delegate
    assertThat(delegateSetupService.validateDelegateGroups(
                   accountId, orgId, projectId, Collections.singletonList(projectDelegate.getDelegateGroupId())))
        .containsExactly(true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateConfigurationsShouldReturnEmptyList() {
    String accountId = generateUuid();
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null, null)).isEmpty();
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null, Collections.emptyList()))
        .isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testValidateDelegateConfigurationsShouldReturnCorrectResult() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    final DelegateProfile cgDelegateProfile = DelegateProfile.builder().accountId(accountId).name("cg").build();
    final DelegateProfile primaryAcctDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("primary").ng(true).primary(true).build();
    final DelegateProfile acctDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("acct").ng(true).build();

    final DelegateEntityOwner orgOwner = DelegateEntityOwnerHelper.buildOwner(orgId, null);
    final DelegateProfile primaryOrgDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("primary").ng(true).primary(true).owner(orgOwner).build();
    final DelegateProfile orgDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("org").ng(true).owner(orgOwner).build();

    final DelegateEntityOwner projectOwner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    final DelegateProfile primaryProjectDelegateProfile = DelegateProfile.builder()
                                                              .accountId(accountId)
                                                              .name("primary")
                                                              .ng(true)
                                                              .primary(true)
                                                              .owner(projectOwner)
                                                              .build();
    final DelegateProfile projectDelegateProfile =
        DelegateProfile.builder().accountId(accountId).name("project").ng(true).owner(projectOwner).build();

    persistence.saveBatch(Arrays.asList(cgDelegateProfile, primaryAcctDelegateProfile, acctDelegateProfile,
        primaryOrgDelegateProfile, orgDelegateProfile, primaryProjectDelegateProfile, projectDelegateProfile));

    // Test non-existing delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(
                   accountId, null, null, Collections.singletonList(generateUuid())))
        .containsExactly(false);

    // Test cg delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(
                   accountId, null, null, Collections.singletonList(cgDelegateProfile.getUuid())))
        .containsExactly(false);

    // Test account delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, null, null,
                   Arrays.asList(primaryAcctDelegateProfile.getUuid(), acctDelegateProfile.getUuid())))
        .containsExactly(true, true);

    // Test org delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, orgId, null,
                   Arrays.asList(primaryOrgDelegateProfile.getUuid(), orgDelegateProfile.getUuid())))
        .containsExactly(true, true);

    // Test project delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, orgId, projectId,
                   Arrays.asList(primaryProjectDelegateProfile.getUuid(), projectDelegateProfile.getUuid())))
        .containsExactly(true, true);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenRetrieveDelegateGroupImplicitSelectorsAndNullGroupThenEmptyTags() {
    final Map<String, SelectorType> actual = delegateSetupService.retrieveDelegateGroupImplicitSelectors(null);
    assertThat(actual).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenRetrieveDelegateGroupImplicitSelectorsThenExpectedTags() {
    final String accountId = "accId";
    final String configId = "configId";
    final DelegateGroup delegateGroup =
        DelegateGroup.builder().name("GroupName").accountId(accountId).delegateConfigurationId(configId).build();

    when(delegateCache.getDelegateProfile(accountId, configId))
        .thenReturn(DelegateProfile.builder().name("ProfileName").selectors(ImmutableList.of("SEL1", "asel2")).build());

    final Map<String, SelectorType> actual = delegateSetupService.retrieveDelegateGroupImplicitSelectors(delegateGroup);
    final Map<String, SelectorType> expectedSelectors = Maps.of("asel2", SelectorType.PROFILE_SELECTORS, "groupname",
        SelectorType.GROUP_NAME, "profilename", SelectorType.PROFILE_NAME, "sel1", SelectorType.PROFILE_SELECTORS);
    assertThat(actual).containsExactlyEntriesOf(expectedSelectors);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenRetrieveDelegateGroupImplicitSelectorsAndNoProfileThenExpectedTags() {
    final String accountId = "accId";
    final String configId = "configId";
    final DelegateGroup delegateGroup =
        DelegateGroup.builder().name("GroupName").accountId(accountId).delegateConfigurationId(configId).build();

    when(delegateCache.getDelegateProfile(any(), any())).thenReturn(null);

    final Map<String, SelectorType> actual = delegateSetupService.retrieveDelegateGroupImplicitSelectors(delegateGroup);
    final Map<String, SelectorType> expectedSelectors = Maps.of("groupname", SelectorType.GROUP_NAME);
    assertThat(actual).containsExactlyEntriesOf(expectedSelectors);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testValidateDelegateConfigurationsShouldWorkFineWithIdsAndIdentifiers() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();

    final DelegateEntityOwner projectOwner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    final DelegateProfile primaryProjectDelegateProfile = DelegateProfile.builder()
                                                              .accountId(accountId)
                                                              .name("primary")
                                                              .ng(true)
                                                              .primary(true)
                                                              .owner(projectOwner)
                                                              .build();
    final DelegateProfile projectDelegateProfileWithIdentifier = DelegateProfile.builder()
                                                                     .accountId(accountId)
                                                                     .name("project")
                                                                     .ng(true)
                                                                     .owner(projectOwner)
                                                                     .identifier("identifier")
                                                                     .build();

    persistence.saveBatch(Arrays.asList(primaryProjectDelegateProfile, projectDelegateProfileWithIdentifier));

    // Test project delegate profile
    assertThat(delegateSetupService.validateDelegateConfigurations(accountId, orgId, projectId,
                   Arrays.asList(
                       primaryProjectDelegateProfile.getUuid(), projectDelegateProfileWithIdentifier.getIdentifier())))
        .containsExactly(true, true);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldListDelegateGroupDetailsByTokenNameEmptyResult() {
    prepareInitialData();

    DelegateGroupListing delegateGroupListing =
        delegateSetupService.listDelegateGroupDetails(TEST_ACCOUNT_ID, null, null, "test");

    assertThat(delegateGroupListing.getDelegateGroupDetails()).isEmpty();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldListDelegateGroupDetailsByTokenName() {
    prepareInitialData();
    Delegate delegate = createDelegateBuilder()
                            .uuid("delegateId")
                            .accountId(TEST_ACCOUNT_ID)
                            .ng(true)
                            .tags(Lists.newArrayList("tag123", "tag456", "commonTag"))
                            .delegateType(KUBERNETES)
                            .delegateName("delegate")
                            .delegateGroupName("grp")
                            .description("description")
                            .hostName("kube-0")
                            .delegateGroupId(TEST_DELEGATE_GROUP_ID_1)
                            .delegateTokenName("test")
                            .build();
    persistence.save(delegate);
    persistence.save(
        DelegateToken.builder().accountId(TEST_ACCOUNT_ID).name("test").status(DelegateTokenStatus.ACTIVE).build());

    DelegateGroupListing delegateGroupListing =
        delegateSetupService.listDelegateGroupDetails(TEST_ACCOUNT_ID, null, null, "test");

    assertThat(delegateGroupListing.getDelegateGroupDetails()).hasSize(1);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldListDelegateGroupTags() {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .uuid(TEST_DELEGATE_GROUP_ID_1)
                                      .name("grp1")
                                      .identifier("identifier1")
                                      .accountId(TEST_ACCOUNT_ID)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LARGE).build())
                                      .tags(Sets.newHashSet("tag123", "tag456", "taggroup1"))
                                      .build();
    persistence.save(delegateGroup);

    Optional<DelegateGroupDTO> delegateGroupFromDB =
        delegateSetupService.listDelegateGroupTags(TEST_ACCOUNT_ID, null, null, "identifier1");

    assertThat(delegateGroupFromDB).isPresent();
    assertThat(delegateGroupFromDB.get().getIdentifier()).isEqualTo("identifier1");
    assertThat(delegateGroupFromDB.get().getTags()).containsExactlyInAnyOrder("tag123", "tag456", "taggroup1", "grp1");
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldAddDelegateGroupTags() {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .uuid(TEST_DELEGATE_GROUP_ID_1)
                                      .name("grp1")
                                      .identifier("identifier1")
                                      .accountId(TEST_ACCOUNT_ID)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LARGE).build())
                                      .tags(Sets.newHashSet("tag123", "tag456"))
                                      .build();
    persistence.save(delegateGroup);
    DelegateGroupTags delegateGroupTags = new DelegateGroupTags(Sets.newHashSet("  tag1  ", "tag2"));

    Optional<DelegateGroupDTO> updatedDelegateGroup =
        delegateSetupService.addDelegateGroupTags(TEST_ACCOUNT_ID, null, null, "identifier1", delegateGroupTags);

    assertThat(updatedDelegateGroup).isPresent();
    assertThat(updatedDelegateGroup.get().getIdentifier()).isEqualTo("identifier1");
    assertThat(updatedDelegateGroup.get().getTags()).containsExactlyInAnyOrder("tag123", "tag456", "tag1", "tag2");
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateGroupTags() {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .uuid(TEST_DELEGATE_GROUP_ID_1)
                                      .name("grp1")
                                      .identifier("identifier1")
                                      .accountId(TEST_ACCOUNT_ID)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LARGE).build())
                                      .tags(Sets.newHashSet("tag123", "tag456", "taggroup1"))
                                      .build();
    persistence.save(delegateGroup);
    DelegateGroupTags delegateGroupTags = new DelegateGroupTags(Sets.newHashSet("  tag123  ", "tag123", "  tag456"));
    Optional<DelegateGroupDTO> updatedDelegateGroup =
        delegateSetupService.updateDelegateGroupTags(TEST_ACCOUNT_ID, null, null, "identifier1", delegateGroupTags);

    assertThat(updatedDelegateGroup).isPresent();
    assertThat(updatedDelegateGroup.get().getIdentifier()).isEqualTo("identifier1");
    assertThat(updatedDelegateGroup.get().getTags()).containsExactlyInAnyOrder("tag123", "tag456");
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void shouldFetchDelegateExpirationTime() {
    when(versionInfoManager.getVersionInfo())
        .thenReturn(VersionInfo.builder().version(MANAGER_VERSION).buildNo(MANAGER_BUILD_NUMBER).build());
    long actualExpirationTime =
        delegateSetupService.getDelegateExpirationTime(IMMUTABLE_DELEGATE_VERSION, generateUuid());
    double diffInDays = (double) TimeUnit.MILLISECONDS.toDays(actualExpirationTime - System.currentTimeMillis());
    double diffInWeeks = Math.ceil(diffInDays / 7);
    assertThat(diffInWeeks).isEqualTo(24.0);
  }

  private void prepareInitialData() {
    List<DelegateGroup> delegateGroups = prepareDelegateGroups();
    List<Delegate> delegates = prepareDelegates();

    persistence.saveBatch(delegateGroups);
    persistence.saveBatch(delegates);
  }

  private List<Delegate> prepareDelegates() {
    // these three delegates should be returned for group 1
    Delegate delegate1 = createDelegateBuilder()
                             .uuid("delegateId1")
                             .accountId(TEST_ACCOUNT_ID)
                             .ng(true)
                             .tags(Lists.newArrayList("tag123", "tag456", "commonTag"))
                             .delegateType(KUBERNETES)
                             .delegateName("delegate1")
                             .delegateGroupName("grp1")
                             .description("description1")
                             .hostName("kube-0")
                             .version("22.09.76614")
                             .immutable(true)
                             .delegateGroupId(TEST_DELEGATE_GROUP_ID_1)
                             .delegateProfileId("delegateProfileId1")
                             .build();

    Delegate delegate2 = createDelegateBuilder()
                             .uuid("delegateId2")
                             .accountId(TEST_ACCOUNT_ID)
                             .ng(true)
                             .tags(Lists.newArrayList("tagdel2"))
                             .delegateType(KUBERNETES)
                             .delegateName("delegate2")
                             .delegateGroupName("grp1")
                             .description("description")
                             .hostName("kube-1")
                             .version("22.11.76800")
                             .immutable(true)
                             .delegateGroupId(TEST_DELEGATE_GROUP_ID_1)
                             .delegateProfileId("delegateProfileId1")
                             .lastHeartBeat(System.currentTimeMillis() - 60000)
                             .build();

    // this delegate should cause an empty group to be returned
    Delegate delegate3 = createDelegateBuilder()
                             .accountId(TEST_ACCOUNT_ID)
                             .ng(true)
                             .delegateName("delegate3")
                             .delegateGroupName("grp2")
                             .tags(Lists.newArrayList("tagdel3"))
                             .sizeDetails(DelegateSizeDetails.builder().replicas(1).build())
                             .hostName("kube-3")
                             .delegateGroupId(TEST_DELEGATE_GROUP_ID_2)
                             .build();

    Delegate deletedDelegate = createDelegateBuilder()
                                   .accountId(TEST_ACCOUNT_ID)
                                   .delegateGroupId(TEST_DELEGATE_GROUP_ID_3)
                                   .status(DelegateInstanceStatus.DELETED)
                                   .build();

    Delegate orgDelegate = createDelegateBuilder()
                               .accountId(TEST_ACCOUNT_ID)
                               .delegateGroupId(TEST_DELEGATE_GROUP_ID_3)
                               .owner(DelegateEntityOwner.builder().identifier(generateUuid()).build())
                               .build();

    return Lists.newArrayList(delegate1, delegate2, delegate3, deletedDelegate, orgDelegate);
  }

  private List<DelegateGroup> prepareDelegateGroups() {
    DelegateGroup delegateGroup1 = DelegateGroup.builder()
                                       .uuid(TEST_DELEGATE_GROUP_ID_1)
                                       .name("grp1")
                                       .identifier("identifier1")
                                       .accountId(TEST_ACCOUNT_ID)
                                       .ng(true)
                                       .delegateType(KUBERNETES)
                                       .description("description")
                                       .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LARGE).build())
                                       .delegateConfigurationId("profileID")
                                       .tags(Sets.newHashSet("tag123", "tag456", "taggroup1"))
                                       .build();

    DelegateGroup delegateGroup2 = DelegateGroup.builder()
                                       .uuid(TEST_DELEGATE_GROUP_ID_2)
                                       .name("grp2")
                                       .identifier("identifier2")
                                       .accountId(TEST_ACCOUNT_ID)
                                       .ng(true)
                                       .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                       .tags(Sets.newHashSet("tag45612", "commonTag"))
                                       .build();
    DelegateGroup delegateGroup3 = DelegateGroup.builder()
                                       .uuid(TEST_DELEGATE_GROUP_ID_3)
                                       .name("grp3")
                                       .identifier("identifier3")
                                       .accountId(TEST_ACCOUNT_ID)
                                       .ng(true)
                                       .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                       .status(DelegateGroupStatus.DELETED)
                                       .build();

    // group without delegates, e.g. created during yaml generation
    DelegateGroup delegateGroup4 = DelegateGroup.builder()
                                       .uuid(TEST_DELEGATE_GROUP_ID_4)
                                       .name("grp4")
                                       .identifier("identifier4")
                                       .accountId(TEST_ACCOUNT_ID)
                                       .ng(true)
                                       .sizeDetails(DelegateSizeDetails.builder().size(DelegateSize.LAPTOP).build())
                                       .status(DelegateGroupStatus.ENABLED)
                                       .tags(Sets.newHashSet("taggroup4"))
                                       .build();
    return Lists.newArrayList(delegateGroup1, delegateGroup2, delegateGroup3, delegateGroup4);
  }
}
