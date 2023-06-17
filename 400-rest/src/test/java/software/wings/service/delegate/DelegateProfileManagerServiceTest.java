/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.beans.Cd1SetupFields.APP_ID_FIELD;
import static io.harness.beans.Cd1SetupFields.ENV_ID_FIELD;
import static io.harness.beans.Cd1SetupFields.SERVICE_ID_FIELD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.DelegateProfileDetails;
import io.harness.delegate.beans.ScopingRuleDetails;
import io.harness.delegateprofile.DelegateProfileGrpc;
import io.harness.delegateprofile.DelegateProfilePageResponseGrpc;
import io.harness.delegateprofile.ProfileId;
import io.harness.delegateprofile.ProfileScopingRule;
import io.harness.delegateprofile.ScopingValues;
import io.harness.exception.InvalidArgumentsException;
import io.harness.grpc.DelegateProfileServiceGrpcClient;
import io.harness.paging.PageRequestGrpc;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.service.impl.DelegateProfileManagerServiceImpl;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class DelegateProfileManagerServiceTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String DELEGATE_PROFILE_ID = generateUuid();
  private static final String TEST_DELEGATE_PROFILE_IDENTIFIER = generateUuid();

  private final Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();

  @Mock private DelegateProfileServiceGrpcClient delegateProfileServiceGrpcClient;
  @Mock private AppService appService;
  @InjectMocks @Inject private DelegateProfileManagerServiceImpl delegateProfileManagerService;
  @Inject private HPersistence persistence;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldList() {
    PageRequest<DelegateProfileDetails> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfiles(
             any(AccountId.class), any(PageRequestGrpc.class), eq(false), eq(null), eq(null)))
        .thenReturn(null)
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetails> delegateProfileDetailsPageResponse =
        delegateProfileManagerService.list(ACCOUNT_ID, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNull();

    delegateProfileDetailsPageResponse = delegateProfileManagerService.list(ACCOUNT_ID, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldGet() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    when(delegateProfileServiceGrpcClient.getProfile(any(AccountId.class), any(ProfileId.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetails updatedDelegateProfileDetails =
        delegateProfileManagerService.get(ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails =
        delegateProfileManagerService.get(ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetails profileDetail = DelegateProfileDetails.builder()
                                               .accountId(ACCOUNT_ID)
                                               .name("test")
                                               .description("description")
                                               .startupScript("startupScript")
                                               .build();
    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder()
                                               .description("test")
                                               .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                               .applicationId("appId")
                                               .build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .build();

    when(delegateProfileServiceGrpcClient.updateProfile(any(DelegateProfileGrpc.class)))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetails updatedDelegateProfileDetails = delegateProfileManagerService.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerService.update(profileDetail);
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails).isEqualToIgnoringGivenFields(profileDetail, "uuid");
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(updatedDelegateProfileDetails.getDescription()).isEqualTo("description");
    assertThat(updatedDelegateProfileDetails.getScopingRules().get(0).getDescription()).isEqualTo("test");
    assertThat(updatedDelegateProfileDetails.getScopingRules().get(0).getApplicationId()).isEqualTo("appId");
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingProfile() {
    DelegateProfileDetails profileDetail = DelegateProfileDetails.builder()
                                               .accountId(ACCOUNT_ID)
                                               .name("test")
                                               .description("description")
                                               .startupScript("startupScript")
                                               .build();
    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(() -> delegateProfileManagerService.update(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule requires application!");

    DelegateProfileDetails profileDetail2 = DelegateProfileDetails.builder()
                                                .accountId(ACCOUNT_ID)
                                                .name("test2")
                                                .description("description")
                                                .startupScript("startupScript")
                                                .build();
    ScopingRuleDetails scopingRuleDetail2 = ScopingRuleDetails.builder().applicationId(APP_ID).build();
    ScopingRuleDetails scopingRuleDetail3 = ScopingRuleDetails.builder().applicationId(APP_ID).build();

    when(appService.get(APP_ID, false)).thenReturn(app);

    profileDetail2.setScopingRules(Arrays.asList(scopingRuleDetail2, scopingRuleDetail3));
    assertThatThrownBy(() -> delegateProfileManagerService.update(profileDetail2))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(APP_NAME + " is already used for a scoping rule!");
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldAdd() {
    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(Cd1SetupFields.APP_ID_FIELD, ScopingValues.newBuilder().addValue("appId").build());
    scopingEntities.put(ENV_ID_FIELD, ScopingValues.newBuilder().addAllValue(Arrays.asList("env1", "env2")).build());

    DelegateProfileDetails profileDetail = DelegateProfileDetails.builder()
                                               .accountId(ACCOUNT_ID)
                                               .name("test")
                                               .description("description")
                                               .startupScript("startupScript")
                                               .identifier(TEST_DELEGATE_PROFILE_IDENTIFIER)
                                               .build();
    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder()
                                               .description("test")
                                               .environmentIds(new HashSet(Arrays.asList("env1", "env2")))
                                               .applicationId("appId")
                                               .build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));

    DelegateProfileGrpc delegateProfileGrpc =
        DelegateProfileGrpc.newBuilder()
            .setName("test")
            .setDescription("description")
            .setStartupScript("startupScript")
            .setAccountId(AccountId.newBuilder().setId(ACCOUNT_ID).build())
            .addScopingRules(
                ProfileScopingRule.newBuilder().setDescription("test").putAllScopingEntities(scopingEntities).build())
            .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
            .setIdentifier(TEST_DELEGATE_PROFILE_IDENTIFIER)
            .build();

    ArgumentCaptor<DelegateProfileGrpc> argumentCaptor = ArgumentCaptor.forClass(DelegateProfileGrpc.class);
    when(delegateProfileServiceGrpcClient.addProfile(argumentCaptor.capture())).thenReturn(delegateProfileGrpc);

    DelegateProfileDetails result = delegateProfileManagerService.add(profileDetail);
    assertThat(result).isNotNull().isEqualToIgnoringGivenFields(profileDetail, "uuid");

    DelegateProfileGrpc capturedGrpc = argumentCaptor.getValue();
    assertThat(capturedGrpc.getNg()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenAddingProfile() {
    DelegateProfileDetails profileDetail = DelegateProfileDetails.builder()
                                               .accountId(ACCOUNT_ID)
                                               .name("test")
                                               .description("description")
                                               .startupScript("startupScript")
                                               .build();
    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(() -> delegateProfileManagerService.add(profileDetail))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule requires application!");
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldUpdateScopingRules() {
    DelegateProfileGrpc delegateProfileGrpc = DelegateProfileGrpc.newBuilder()
                                                  .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                                  .setProfileId(ProfileId.newBuilder().setId(generateUuid()).build())
                                                  .build();

    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder()
                                               .description("test")
                                               .applicationId("appId")
                                               .environmentIds(new HashSet<>(Collections.singletonList("PROD")))
                                               .build();

    when(delegateProfileServiceGrpcClient.updateProfileScopingRules(
             any(AccountId.class), any(ProfileId.class), anyList()))
        .thenReturn(null)
        .thenReturn(delegateProfileGrpc);

    DelegateProfileDetails updatedDelegateProfileDetails = delegateProfileManagerService.updateScopingRules(
        ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerService.updateScopingRules(
        ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), Collections.singletonList(scopingRuleDetail));
    assertThat(updatedDelegateProfileDetails).isNotNull();
    assertThat(updatedDelegateProfileDetails.getUuid()).isEqualTo(delegateProfileGrpc.getProfileId().getId());
    assertThat(updatedDelegateProfileDetails.getAccountId()).isEqualTo(delegateProfileGrpc.getAccountId().getId());
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldValidateScopesWhenUpdatingScopingRule() {
    DelegateProfileDetails profileDetail = DelegateProfileDetails.builder()
                                               .accountId(ACCOUNT_ID)
                                               .name("test")
                                               .description("description")
                                               .startupScript("startupScript")
                                               .build();
    ScopingRuleDetails scopingRuleDetail = ScopingRuleDetails.builder().description("test").build();
    profileDetail.setScopingRules(Collections.singletonList(scopingRuleDetail));
    assertThatThrownBy(()
                           -> delegateProfileManagerService.updateScopingRules(
                               ACCOUNT_ID, generateUuid(), Collections.singletonList(scopingRuleDetail)))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("The Scoping rule requires application!");
  }

  @Test
  @Owner(developers = OwnerRule.SANJA)
  @Category(UnitTests.class)
  public void shouldDelete() {
    delegateProfileManagerService.delete(ACCOUNT_ID, DELEGATE_PROFILE_ID);

    AccountId accountId = AccountId.newBuilder().setId(ACCOUNT_ID).build();
    ProfileId profileId = ProfileId.newBuilder().setId(DELEGATE_PROFILE_ID).build();
    verify(delegateProfileServiceGrpcClient, times(1)).deleteProfile(eq(accountId), eq(profileId));
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
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

    DelegateProfileDetails updatedDelegateProfileDetails = delegateProfileManagerService.updateSelectors(
        ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);
    assertThat(updatedDelegateProfileDetails).isNull();

    updatedDelegateProfileDetails = delegateProfileManagerService.updateSelectors(
        ACCOUNT_ID, delegateProfileGrpc.getProfileId().getId(), selectors);

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

    ScopingValues scopingValuesAppId = ScopingValues.newBuilder().addValue("Harness App").build();

    Application application = Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).build();
    persistence.save(application);

    Map<String, ScopingValues> scopingEntities = new HashMap<>();
    scopingEntities.put(APP_ID_FIELD, scopingValuesAppId);
    scopingEntities.put(SERVICE_ID_FIELD, scopingValuesEnvTypeId);

    String description = delegateProfileManagerService.generateScopingRuleDescription(scopingEntities);

    assertThat(description).isNotNull().isEqualTo("Application: Harness App; Service: service1, service2; ");
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveScopingRuleApplicationEntityName() {
    List<String> scopingEntitiesIds = new ArrayList<>();

    Application application = Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).build();
    persistence.save(application);

    Application retrievedApplication = persistence.get(Application.class, application.getUuid());

    scopingEntitiesIds.add(retrievedApplication.getName());

    List<String> retrieveScopingRuleEntitiesNames =
        delegateProfileManagerService.retrieveScopingRuleEntitiesNames(APP_ID_FIELD, scopingEntitiesIds);

    assertThat(retrieveScopingRuleEntitiesNames).isNotNull().containsExactly(APP_NAME);
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveScopingRuleServiceEntityNames() {
    List<String> scopingEntitiesIds = new ArrayList<>();

    Service service1 = Service.builder().uuid("SERVICE_ID1").name("To-Do List K8s").build();
    persistence.save(service1);

    Service service2 = Service.builder().uuid("SERVICE_ID2").name("To-Do List Docker").build();
    persistence.save(service2);

    Service retrievedService1 = persistence.get(Service.class, service1.getUuid());
    Service retrievedService2 = persistence.get(Service.class, service2.getUuid());

    scopingEntitiesIds.add(retrievedService1.getName());
    scopingEntitiesIds.add(retrievedService2.getName());

    List<String> retrieveScopingRuleEntitiesNames =
        delegateProfileManagerService.retrieveScopingRuleEntitiesNames(SERVICE_ID_FIELD, scopingEntitiesIds);

    assertThat(retrieveScopingRuleEntitiesNames).isNotNull().containsExactly("To-Do List K8s", "To-Do List Docker");
  }

  @Test
  @Owner(developers = OwnerRule.VUK)
  @Category(UnitTests.class)
  public void shouldRetrieveScopingRuleEnvEntityName() {
    List<String> scopingEntitiesIds = new ArrayList<>();

    Environment environment = Environment.Builder.anEnvironment().uuid(ENV_ID).name("qa").build();
    persistence.save(environment);

    Environment retrievedEnvironment = persistence.get(Environment.class, environment.getUuid());

    scopingEntitiesIds.add(retrievedEnvironment.getName());

    List<String> retrieveScopingRuleEntitiesNames =
        delegateProfileManagerService.retrieveScopingRuleEntitiesNames(ENV_ID_FIELD, scopingEntitiesIds);

    assertThat(retrieveScopingRuleEntitiesNames).isNotNull().containsExactly("qa");
  }
}
