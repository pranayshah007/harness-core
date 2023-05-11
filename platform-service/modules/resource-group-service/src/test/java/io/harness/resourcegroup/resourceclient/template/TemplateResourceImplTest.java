/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.template;

import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_IDENTIFIER;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE;
import static io.harness.resourcegroup.beans.ValidatorType.BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateListType;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

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

public class TemplateResourceImplTest {
  @Inject @InjectMocks TemplateResourceImpl templateResource;
  @Mock TemplateResourceClient templateServiceClient;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    List<String> templateIds = Arrays.asList("TEMPLATE1", "TEMPLATE2");
    Call call = Mockito.mock(Call.class);
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder().templateIdentifiers(templateIds).build();
    when(templateServiceClient.listTemplates(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
             TemplateListType.STABLE_TEMPLATE_TYPE, 0, 2, templateFilterPropertiesDTO))
        .thenReturn(call);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(PageResponse.getEmptyPageResponse(null))));
    assertThat(templateResource.validate(templateIds,
                   Scope.builder()
                       .accountIdentifier(ACCOUNT_IDENTIFIER)
                       .orgIdentifier(ORG_IDENTIFIER)
                       .projectIdentifier(PROJECT_IDENTIFIER)
                       .build()))
        .containsExactly(Boolean.FALSE, Boolean.FALSE);
    verify(templateServiceClient)
        .listTemplates(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, TemplateListType.STABLE_TEMPLATE_TYPE, 0,
            2, templateFilterPropertiesDTO);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testOtherConfigs() {
    assertThat(templateResource.getEventFrameworkEntityType().orElse(""))
        .isEqualTo(EventsFrameworkMetadataConstants.TEMPLATE_ENTITY);
    assertThat(templateResource.getType()).isEqualTo("TEMPLATE");
    assertThat(templateResource.getSelectorKind().get(ScopeLevel.ACCOUNT))
        .containsExactlyInAnyOrder(BY_RESOURCE_IDENTIFIER, BY_RESOURCE_TYPE, BY_RESOURCE_TYPE_INCLUDING_CHILD_SCOPES);
    assertThat(templateResource.getValidScopeLevels())
        .containsExactlyInAnyOrder(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT);
  }
}
