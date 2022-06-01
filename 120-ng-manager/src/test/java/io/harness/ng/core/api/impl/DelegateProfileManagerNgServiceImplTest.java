/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.beans.Cd1SetupFields.ENV_ID_FIELD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.filter.FilterType.DELEGATEPROFILE;

import static software.wings.beans.Variable.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.beans.ScopingRuleDetailsNg;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesDTO;
import io.harness.delegateprofile.DelegateProfileGrpc;
import io.harness.delegateprofile.DelegateProfilePageResponseGrpc;
import io.harness.delegateprofile.ProfileId;
import io.harness.delegateprofile.ProfileScopingRule;
import io.harness.delegateprofile.ScopingValues;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.grpc.DelegateProfileServiceGrpcClient;
import io.harness.ng.core.events.DelegateConfigurationCreateEvent;
import io.harness.ng.core.events.DelegateConfigurationDeleteEvent;
import io.harness.ng.core.events.DelegateConfigurationUpdateEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.owner.OrgIdentifier;
import io.harness.owner.ProjectIdentifier;
import io.harness.paging.PageRequestGrpc;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.Environment;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.DEL)
public class DelegateProfileManagerNgServiceImplTest {
  private static final String TEST_ACCOUNT_ID = generateUuid();
  private static final String TEST_DELEGATE_PROFILE_ID = generateUuid();
  public static final String TEST_FILTER_ID = "filterId";

  @Mock private DelegateProfileServiceGrpcClient delegateProfileServiceGrpcClient;
  @Mock private HPersistence hPersistence;
  @Mock private OutboxService outboxService;
  @Mock private FilterService filterService;
  private DelegateProfileManagerNgServiceImpl underTest;

  @Before
  public void setup() throws IllegalAccessException {
    underTest = new DelegateProfileManagerNgServiceImpl(
        delegateProfileServiceGrpcClient, hPersistence, outboxService, filterService);
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldList() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfiles(
             any(AccountId.class), any(PageRequestGrpc.class), eq(true), any(OrgIdentifier.class), eq(null)))
        .thenReturn(null);
    when(delegateProfileServiceGrpcClient.listProfiles(
             any(AccountId.class), any(PageRequestGrpc.class), eq(true), eq(null), any(ProjectIdentifier.class)))
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.list(TEST_ACCOUNT_ID, pageRequest, "orgId", null);
    assertThat(delegateProfileDetailsPageResponse).isNull();

    delegateProfileDetailsPageResponse = underTest.list(TEST_ACCOUNT_ID, pageRequest, null, "projectId");
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldReturnList() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfilesV2(eq(""), eq(null), any()))
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", "", "", null, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldGetExistingFilter() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfilesV2(eq(""), eq(null), any()))
        .thenReturn(delegateProfilePageResponseGrpc);
    when(filterService.get(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, DELEGATEPROFILE))
        .thenReturn(new FilterDTO());

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, "", null, pageRequest);

    verify(filterService).get(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, DELEGATEPROFILE);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldThrowException() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    assertThatThrownBy(()
                           -> underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", "filterId", "",
                               DelegateProfileFilterPropertiesDTO.builder().build(), pageRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Can not apply both filter properties and saved filter together");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldGet() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    when(delegateProfileServiceGrpcClient.getProfile(any(AccountId.class), any(ProfileId.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails =
        underTest.get(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = underTest.get(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .uuid(generateUuid())
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .identifier("_ident")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                                 .build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setIdentifier("_ident")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .build();

    when(delegateProfileServiceGrpcClient.updateProfile(any(DelegateProfileGrpc.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);
    when(delegateProfileServiceGrpcClient.getProfile(any(AccountId.class), any(ProfileId.class)))
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails = underTest.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNull();

    ArgumentCaptor<DelegateConfigurationUpdateEvent> configCaptor =
        ArgumentCaptor.forClass(DelegateConfigurationUpdateEvent.class);
    when(outboxService.save(configCaptor.capture())).thenReturn(null);

    updatedDelegateProfileDetails = underTest.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isEqualToIgnoringGivenFields(profileDetail, "uuid");
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(updatedDelegateProfileDetails.getDescription()).isEqualTo("description");
    assertThat(updatedDelegateProfileDetails.getScopingRules().get(0).getDescription()).isEqualTo("test");

    DelegateConfigurationUpdateEvent delegateConfigurationUpdateEvent = configCaptor.getValue();
    assertThat(delegateConfigurationUpdateEvent.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateConfigurationUpdateEvent.getOrgIdentifier()).isNull();
    assertThat(delegateConfigurationUpdateEvent.getProjectIdentifier()).isNull();
    assertThat(delegateConfigurationUpdateEvent.getOldProfile()).isNotNull();
    assertThat(delegateConfigurationUpdateEvent.getNewProfile()).isNotNull();
    assertThat(delegateConfigurationUpdateEvent.getResource().getIdentifier()).isEqualTo("_ident");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingProfile() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    assertThatThrownBy(() -> underTest.update(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldAdd() {
    long createdAt = System.currentTimeMillis();
    long updatedAt = System.currentTimeMillis();

    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .identifier("_ident")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .orgIdentifier("orgId")
                                                 .projectIdentifier("projectId")
                                                 .createdAt(createdAt)
                                                 .lastUpdatedAt(updatedAt)
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                                 .build();

    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setIdentifier("_ident")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .setCreatedAt(createdAt)
            .setLastUpdatedAt(updatedAt)
            .setProjectIdentifier(ProjectIdentifier.newBuilder().setId("projectId").build())
            .setOrgIdentifier(OrgIdentifier.newBuilder().setId("orgId").build())
            .build();

    ArgumentCaptor<DelegateProfileGrpc> captor = ArgumentCaptor.forClass(DelegateProfileGrpc.class);
    when(delegateProfileServiceGrpcClient.addProfile(captor.capture())).thenReturn(delegateProfileGrpc);
    ArgumentCaptor<DelegateConfigurationCreateEvent> configCaptor =
        ArgumentCaptor.forClass(DelegateConfigurationCreateEvent.class);
    when(outboxService.save(configCaptor.capture())).thenReturn(null);

    DelegateProfileDetailsNg result = underTest.add(profileDetail);
    assertThat(result).isNotNull().isEqualToIgnoringGivenFields(profileDetail, DelegateProfileKeys.uuid);

    DelegateProfileGrpc capturedProfile = captor.getValue();
    assertThat(capturedProfile.getNg()).isTrue();
    assertThat(capturedProfile.getOrgIdentifier().getId()).isEqualTo("orgId");
    assertThat(capturedProfile.getProjectIdentifier().getId()).isEqualTo("projectId");

    DelegateConfigurationCreateEvent delegateConfigurationCreateEvent = configCaptor.getValue();
    assertThat(delegateConfigurationCreateEvent.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateConfigurationCreateEvent.getOrgIdentifier())
        .isEqualTo(delegateProfileGrpc.getOrgIdentifier().getId());
    assertThat(delegateConfigurationCreateEvent.getProjectIdentifier())
        .isEqualTo(delegateProfileGrpc.getProjectIdentifier().getId());
    assertThat(delegateConfigurationCreateEvent.getResource().getIdentifier()).isEqualTo("_ident");

    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getOrgIdentifier())
        .isEqualTo(delegateProfileGrpc.getOrgIdentifier().getId());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getProjectIdentifier())
        .isEqualTo(delegateProfileGrpc.getProjectIdentifier().getId());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getName())
        .isEqualTo(delegateProfileGrpc.getName());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getDescription())
        .isEqualTo(delegateProfileGrpc.getDescription());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().isPrimary())
        .isEqualTo(delegateProfileGrpc.getPrimary());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().isApprovalRequired())
        .isEqualTo(delegateProfileGrpc.getApprovalRequired());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getStartupScript())
        .isEqualTo(delegateProfileGrpc.getStartupScript());
    assertThat(delegateConfigurationCreateEvent.getDelegateProfile().getScopingRules())
        .isEqualTo(Lists.newArrayList(ScopingRuleDetailsNg.builder()
                                          .description("test")
                                          .environmentIds(Sets.newHashSet("env1", "env2"))
                                          .build()));
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenAddingProfile() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(() -> underTest.add(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdateScopingRules() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder()
                                                 .description("test")
                                                 .environmentIds(new HashSet<>(Collections.singletonList("PROD")))
                                                 .build();

    when(delegateProfileServiceGrpcClient.updateProfileScopingRules(
             any(AccountId.class), any(ProfileId.class), anyList()))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails = underTest.updateScopingRules(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = underTest.updateScopingRules(
        TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingScopingRule() {
    DelegateProfileDetailsNg profileDetail = DelegateProfileDetailsNg.builder()
                                                 .accountId(TEST_ACCOUNT_ID)
                                                 .name("test")
                                                 .description("description")
                                                 .startupScript("startupScript")
                                                 .build();
    ScopingRuleDetailsNg scopingRuleDetail = ScopingRuleDetailsNg.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(()
                           -> underTest.updateScopingRules(
                               TEST_ACCOUNT_ID, generateUuid(), Collections.singletonList(scopingRuleDetail)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule is empty.");
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldDelete() {
    when(delegateProfileServiceGrpcClient.getProfile(any(AccountId.class), any(ProfileId.class)))
        .thenReturn(DelegateProfileGrpc.newBuilder().build());
    underTest.delete(TEST_ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    AccountId accountId = AccountId.newBuilder().setId(TEST_ACCOUNT_ID).build();
    ProfileId profileId = ProfileId.newBuilder().setId(TEST_DELEGATE_PROFILE_ID).build();
    verify(delegateProfileServiceGrpcClient, times(1)).deleteProfile(eq(accountId), eq(profileId));
    verify(outboxService, times(1)).save(any(DelegateConfigurationDeleteEvent.class));
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldUpdateSelectors() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    List<String> selectors = Collections.singletonList("selectors");

    when(delegateProfileServiceGrpcClient.updateProfileSelectors(any(AccountId.class), any(ProfileId.class), anyList()))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetailsNg updatedDelegateProfileDetails =
        underTest.updateSelectors(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails =
        underTest.updateSelectors(TEST_ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);

    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldGenerateScopingRuleDescription() {
    List<String> serviceNames = Arrays.asList("service1, service2");

    ScopingValues scopingValuesEnvTypeId = ScopingValues.newBuilder().addAllValue(serviceNames).build();

    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(ENV_ID, scopingValuesEnvTypeId);

    String description = underTest.generateScopingRuleDescription(scopingEntities);

    assertThat(description).isNotNull().isEqualTo("Environment: service1, service2; ");
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveScopingRuleEnvEntityName() {
    List<String> scopingEntitiesIds = new ArrayList<>();

    Environment environment = Environment.Builder.anEnvironment().uuid(ENV_ID).name("qa").build();
    when(hPersistence.get(Environment.class, ENV_ID)).thenReturn(environment);

    Environment retrievedEnvironment = hPersistence.get(Environment.class, environment.getUuid());

    scopingEntitiesIds.add(retrievedEnvironment.getName());

    List<String> retrieveScopingRuleEntitiesNames =
        underTest.retrieveScopingRuleEntitiesNames(ENV_ID_FIELD, scopingEntitiesIds);

    assertThat(retrieveScopingRuleEntitiesNames).isNotNull().containsExactly("qa");
  }
}
