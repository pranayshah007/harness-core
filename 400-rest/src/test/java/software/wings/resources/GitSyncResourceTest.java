/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.exception.WingsExceptionMapper;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.utils.ResourceTestRule;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;

import com.google.common.collect.Lists;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * @author vardanb
 */
@Slf4j
public class GitSyncResourceTest extends WingsBaseTest {
  private static final GitSyncService GIT_SYNC_SERVICE = mock(GitSyncService.class);
  private static final GitSyncErrorService GIT_SYNC_ERROR_SERVICE = mock(GitSyncErrorService.class);
  private static final YamlChangeSetService YAML_CHANGE_SET_SERVICE = mock(YamlChangeSetService.class);

  @Captor private ArgumentCaptor<PageRequest<GitSyncError>> pageRequestArgumentCaptor;

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new GitSyncResource(GIT_SYNC_SERVICE, GIT_SYNC_ERROR_SERVICE, YAML_CHANGE_SET_SERVICE))
          .type(WingsExceptionMapper.class)
          .build();
  private static final GitToHarnessErrorDetails gitToHarnessErrorDetails =
      GitToHarnessErrorDetails.builder().gitCommitId("gitCommitId1").yamlContent("yamlContent").build();

  private static final GitSyncError GIT_SYNC_ERROR = GitSyncError.builder()
                                                         .accountId(ACCOUNT_ID)
                                                         .gitSyncDirection(GIT_TO_HARNESS.toString())
                                                         .yamlFilePath("yamlFilePath")
                                                         .build();

  /**
   * Should list git sync errors.
   */
  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void shouldListErrors() {
    PageResponse<GitSyncError> pageResponse = aPageResponse().withResponse(Lists.newArrayList(GIT_SYNC_ERROR)).build();
    when(GIT_SYNC_ERROR_SERVICE.fetchErrors(any(PageRequest.class))).thenReturn(pageResponse);

    RestResponse<PageResponse<GitSyncError>> restResponse =
        RESOURCES.client()
            .target(format("/git-sync/errors?accountId=%s", ACCOUNT_ID))
            .request()
            .get(new GenericType<RestResponse<PageResponse<GitSyncError>>>() {});

    log.info(JsonUtils.asJson(restResponse));
    verify(GIT_SYNC_ERROR_SERVICE).fetchErrors(pageRequestArgumentCaptor.capture());
    assertThat(pageRequestArgumentCaptor.getValue()).isNotNull();
    assertThat(restResponse).isNotNull().hasFieldOrPropertyWithValue("resource", pageResponse);
  }

  /**
   * Should discard git sync error
   */
  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void shouldDiscardErrors() {
    RestResponse restResponse = RESOURCES.client()
                                    .target(format("/git-sync/errors/_discard?accountId=%s", ACCOUNT_ID))
                                    .request()
                                    .post(entity(Arrays.asList("errorId"), MediaType.APPLICATION_JSON),
                                        new GenericType<RestResponse<List<GitSyncError>>>() {});

    assertThat(restResponse).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = GitSyncResource.class.getDeclaredMethod("discardGitSyncErrorV2", String.class, List.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_CONFIG_AS_CODE);
  }
}
