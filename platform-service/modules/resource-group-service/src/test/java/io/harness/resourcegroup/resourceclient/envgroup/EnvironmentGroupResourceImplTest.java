/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.envgroup;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class EnvironmentGroupResourceImplTest {
  @Inject @InjectMocks EnvironmentGroupResourceImpl environmentGroupResource;
  @Mock EnvironmentGroupResourceClient environmentGroupResourceClient;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    List<String> envGroupIds = Arrays.asList("envGroup1", "envGroup2");
    Call call = Mockito.mock(Call.class);

    when(environmentGroupResourceClient.listEnvironmentGroup(
             0, 2, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, envGroupIds))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(PageResponse.getEmptyPageResponse(null))));
    assertThat(environmentGroupResource.validate(envGroupIds,
                   Scope.builder()
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .orgIdentifier(ORG_IDENTIFIER)
                       .projectIdentifier(PROJECT_IDENTIFIER)
                       .build()))
        .containsExactly(Boolean.FALSE, Boolean.FALSE);
    verify(environmentGroupResourceClient)
        .listEnvironmentGroup(0, 2, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, envGroupIds);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testOtherConfigs() {
    assertThat(environmentGroupResource.getEventFrameworkEntityType().orElse(""))
        .isEqualTo(EventsFrameworkMetadataConstants.ENVIRONMENT_GROUP_ENTITY);
    assertThat(environmentGroupResource.getType()).isEqualTo("ENVIRONMENT_GROUP");
    assertThat(environmentGroupResource.getSelectorKind().get(ScopeLevel.ACCOUNT))
        .containsExactlyInAnyOrder(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES);
    assertThat(environmentGroupResource.getValidScopeLevels())
        .containsExactlyInAnyOrder(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT);
  }
}
