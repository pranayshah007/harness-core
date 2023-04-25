/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.gitops;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.gitops.models.Repository;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class RepoResourceImplTest extends CategoryTest {
  @Mock private GitopsResourceClient gitopsResourceClient;
  @Inject @InjectMocks RepositoryResourceImpl repositoryResource;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getType() {
    assertThat(repositoryResource.getType()).isEqualTo("GITOPS_REPOSITORY");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getEventFrameworkEntityType() {
    assertThat(repositoryResource.getEventFrameworkEntityType().get())
        .isEqualTo(EventsFrameworkMetadataConstants.GITOPS_REPOSITORY_ENTITY);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validateEmptyResourceList() {
    assertThat(repositoryResource.validate(
                   new ArrayList<>(), Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)))
        .isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    List<String> resourceIds = new ArrayList<>();
    List<Repository> repositories = new ArrayList<>();
    for (int i = 0; i < 205; i++) {
      resourceIds.add(String.valueOf(i));
      repositories.add(Repository.builder().identifier(String.valueOf(i)).build());
    }

    Call call = Mockito.mock(Call.class);

    when(call.execute())
        .thenReturn(Response.success(PageResponse.<Repository>builder().content(repositories.subList(0, 30)).build()));
    doReturn(call).when(gitopsResourceClient).listRepositories(any());

    final List<Boolean> validate =
        repositoryResource.validate(resourceIds, Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER));
    assertThat(validate).hasSize(205);
    for (int i = 0; i < 205; i++) {
      if (i < 30 && validate.get(i) == Boolean.FALSE) {
        fail("repositories between 1-30 should be valid");
      }
      if (i >= 30 && validate.get(i) == Boolean.TRUE) {
        fail("repositories > 30 should be valid");
      }
    }
  }
}
