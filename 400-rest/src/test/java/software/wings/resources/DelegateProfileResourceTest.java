/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_PROFILE_ID;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfileDetails;
import io.harness.delegate.beans.ScopingRuleDetails;
import io.harness.delegate.beans.ScopingRules;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.DelegateProfileManagerService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.utils.ResourceTestRule;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

public class DelegateProfileResourceTest extends CategoryTest {
  private static DelegateProfileService delegateProfileService = mock(DelegateProfileService.class);
  private static DelegateProfileManagerService delegateProfileManagerService =
      mock(DelegateProfileManagerService.class);
  private static HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

  @Parameterized.Parameter public String apiUrl;

  @Parameterized.Parameters
  public static String[] data() {
    return new String[] {null, "https://testUrl"};
  }

  @ClassRule
  public static final ResourceTestRule RESOURCES =

      ResourceTestRule.builder()
          .instance(new DelegateProfileResource(delegateProfileService, delegateProfileManagerService))
          .instance(new AbstractBinder() {
            @Override
            protected void configure() {
              bind(httpServletRequest).to(HttpServletRequest.class);
            }
          })
          .type(WingsExceptionMapper.class)
          .build();

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldListDelegateProfiles() {
    PageResponse<DelegateProfile> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(DelegateProfile.builder().build()));
    pageResponse.setTotal(1l);
    when(delegateProfileService.list(any(PageRequest.class))).thenReturn(pageResponse);
    RestResponse<PageResponse<DelegateProfile>> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles?accountId=" + ACCOUNT_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<DelegateProfile>>>() {});
    PageRequest<DelegateProfile> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    verify(delegateProfileService, atLeastOnce()).list(pageRequest);
    assertThat(restResponse.getResource().getResponse().size()).isEqualTo(1);
    assertThat(restResponse.getResource().getResponse().get(0)).isNotNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldDeleteDelegateProfile() {
    RESOURCES.client().target("/delegate-profiles/" + ID_KEY + "?accountId=" + ACCOUNT_ID).request().delete();

    verify(delegateProfileService, atLeastOnce()).delete(ACCOUNT_ID, ID_KEY);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldAddDelegateProfile() {
    DelegateProfile delegateProfile = DelegateProfile.builder().uuid(ID_KEY).accountId(ACCOUNT_ID).build();

    when(delegateProfileService.add(any(DelegateProfile.class))).thenReturn(delegateProfile);
    RestResponse<DelegateProfile> restResponse = RESOURCES.client()
                                                     .target("/delegate-profiles/?accountId=" + ACCOUNT_ID)
                                                     .request()
                                                     .post(entity(delegateProfile, MediaType.APPLICATION_JSON),
                                                         new GenericType<RestResponse<DelegateProfile>>() {});

    assertThat(restResponse.getResource()).isNotNull();
    verify(delegateProfileService, atLeastOnce()).add(delegateProfile);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateProfile() {
    DelegateProfile delegateProfile = DelegateProfile.builder().uuid(ID_KEY).accountId(ACCOUNT_ID).build();

    when(delegateProfileService.update(any(DelegateProfile.class))).thenReturn(delegateProfile);
    RestResponse<DelegateProfile> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/" + ID_KEY + "?accountId=" + ACCOUNT_ID)
            .request()
            .put(entity(delegateProfile, MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateProfile>>() {});

    ArgumentCaptor<DelegateProfile> captor = ArgumentCaptor.forClass(DelegateProfile.class);
    verify(delegateProfileService, atLeastOnce()).update(captor.capture());
    assertThat(restResponse.getResource()).isNotNull();
    DelegateProfile captorValue = captor.getValue();
    assertThat(captorValue.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(captorValue.getUuid()).isEqualTo(ID_KEY);
    DelegateProfile resource = restResponse.getResource();
    assertThat(resource.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(resource.getUuid()).isEqualTo(ID_KEY);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldListDelegateProfilesV2() {
    PageRequest<DelegateProfileDetails> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");

    PageResponse<DelegateProfileDetails> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(DelegateProfileDetails.builder().build()));
    pageResponse.setTotal(1l);

    when(delegateProfileManagerService.list(ACCOUNT_ID, pageRequest)).thenReturn(pageResponse);
    RestResponse<PageResponse<DelegateProfileDetails>> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2?accountId=" + ACCOUNT_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<DelegateProfileDetails>>>() {});

    verify(delegateProfileManagerService, times(1)).list(ACCOUNT_ID, pageRequest);
    assertThat(restResponse.getResource().size()).isEqualTo(1);
    assertThat(restResponse.getResource().get(0)).isNotNull();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldGetDelegateProfileV2() {
    DelegateProfileDetails delegateProfile = DelegateProfileDetails.builder().build();
    when(delegateProfileManagerService.get(ACCOUNT_ID, DELEGATE_PROFILE_ID)).thenReturn(delegateProfile);
    RestResponse<DelegateProfileDetails> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2/" + DELEGATE_PROFILE_ID + "?accountId=" + ACCOUNT_ID)
            .request()
            .get(new GenericType<RestResponse<DelegateProfileDetails>>() {});
    PageRequest<DelegateProfileDetails> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    verify(delegateProfileManagerService, times(1)).get(ACCOUNT_ID, DELEGATE_PROFILE_ID);
    assertThat(restResponse.getResource()).isEqualTo(delegateProfile);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateProfileV2() {
    String profileId = generateUuid();
    DelegateProfileDetails toBeUpdated =
        DelegateProfileDetails.builder().accountId(ACCOUNT_ID).uuid(profileId).name("test").build();
    when(delegateProfileManagerService.update(toBeUpdated)).thenReturn(toBeUpdated);
    RestResponse<DelegateProfileDetails> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2/" + profileId + "?accountId=" + ACCOUNT_ID)
            .request()
            .put(entity(toBeUpdated, MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateProfileDetails>>() {});
    verify(delegateProfileManagerService, times(1)).update(toBeUpdated);
    assertThat(restResponse.getResource()).isEqualTo(toBeUpdated);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateProfileScopingRulesV2() {
    String profileId = generateUuid();
    Set<String> environments = new HashSet<>(asList("PROD"));
    Set<String> services = new HashSet<>(asList("svc1"));
    ScopingRuleDetails rule1 =
        ScopingRuleDetails.builder().applicationId("sample1").environmentIds(environments).build();
    ScopingRuleDetails rule2 = ScopingRuleDetails.builder().applicationId("sample1").serviceIds(services).build();
    List<ScopingRuleDetails> rules = asList(rule1, rule2);
    DelegateProfileDetails result =
        DelegateProfileDetails.builder().accountId(ACCOUNT_ID).uuid(profileId).scopingRules(rules).name("test").build();
    when(delegateProfileManagerService.updateScopingRules(ACCOUNT_ID, profileId, rules)).thenReturn(result);
    RestResponse<DelegateProfileDetails> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2/" + profileId + "/scoping-rules?accountId=" + ACCOUNT_ID)
            .request()
            .put(entity(ScopingRules.builder().scopingRuleDetails(rules).build(), MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateProfileDetails>>() {});
    verify(delegateProfileManagerService, times(1)).updateScopingRules(ACCOUNT_ID, profileId, rules);
    assertThat(restResponse.getResource()).isEqualTo(result);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldAddDelegateProfileV2() {
    String profileId = generateUuid();
    DelegateProfileDetails toBeAdded = DelegateProfileDetails.builder().accountId(ACCOUNT_ID).name("test").build();
    DelegateProfileDetails result =
        DelegateProfileDetails.builder().accountId(ACCOUNT_ID).uuid(profileId).name("test").build();
    when(delegateProfileManagerService.add(toBeAdded)).thenReturn(result);
    RestResponse<DelegateProfileDetails> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2/?accountId=" + ACCOUNT_ID)
            .request()
            .post(entity(toBeAdded, MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateProfileDetails>>() {});
    verify(delegateProfileManagerService, times(1)).add(toBeAdded);
    assertThat(restResponse.getResource()).isEqualTo(result);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldDeleteV2() {
    String profileId = generateUuid();
    RESOURCES.client().target("/delegate-profiles/v2/" + profileId + "?accountId=" + ACCOUNT_ID).request().delete();
    verify(delegateProfileManagerService, times(1)).delete(ACCOUNT_ID, profileId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldUpdateDelegateProfileSelectorsV2() {
    List<String> profileSelectors = Arrays.asList("xxx");

    DelegateProfileDetails delegateProfileDetails =
        DelegateProfileDetails.builder().uuid(ID_KEY).accountId(ACCOUNT_ID).selectors(profileSelectors).build();

    when(delegateProfileManagerService.updateSelectors(ACCOUNT_ID, ID_KEY, profileSelectors))
        .thenReturn(delegateProfileDetails);

    RestResponse<DelegateProfileDetails> restResponse =
        RESOURCES.client()
            .target("/delegate-profiles/v2/" + ID_KEY + "/selectors?accountId=" + ACCOUNT_ID)
            .request()
            .put(entity(profileSelectors, MediaType.APPLICATION_JSON),
                new GenericType<RestResponse<DelegateProfileDetails>>() {});
    verify(delegateProfileManagerService, atLeastOnce()).updateSelectors(ACCOUNT_ID, ID_KEY, profileSelectors);

    DelegateProfileDetails resource = restResponse.getResource();
    assertThat(resource.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(resource.getUuid()).isEqualTo(ID_KEY);
    assertThat(resource.getSelectors()).isEqualTo(profileSelectors);
  }
}
