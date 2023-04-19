/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.user;

import static io.harness.rule.OwnerRule.VARDAN_BANSAL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.AuthRuleGraphQL;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.user.QLUpdateUserInput;
import software.wings.graphql.schema.type.user.QLUpdateUserPayload;
import software.wings.service.intfc.UserService;

import graphql.schema.DataFetchingEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class UpdateUserDataFetcherTest extends CategoryTest {
  @Mock UserService userService;
  @Mock AuthRuleGraphQL authRuleInstrumentation;
  @Mock DataFetcherUtils utils;
  @InjectMocks @Spy UpdateUserDataFetcher updateUserDataFetcher = new UpdateUserDataFetcher(userService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_updateUserWithoutExternalUserId() {
    doReturn(User.Builder.anUser().uuid("userId").name("old name").email("test@harness.io").build())
        .when(userService)
        .get("userId");
    doReturn(User.Builder.anUser().build()).when(userService).update(any(User.class));
    final QLUpdateUserInput updateUserInput = QLUpdateUserInput.builder()
                                                  .clientMutationId("clientMutationId1")
                                                  .id("userId")
                                                  .name(RequestField.ofNullable("newUserName"))
                                                  .build();
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountId")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    final QLUpdateUserPayload qlUpdateUserPayload =
        updateUserDataFetcher.mutateAndFetch(updateUserInput, mutationContext);
    assertThat(qlUpdateUserPayload.getClientMutationId()).isEqualTo("clientMutationId1");

    verify(userService, times(1)).get("userId");
    final ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
    verify(userService, times(1)).update(userArgumentCaptor.capture());

    final User userArgument = userArgumentCaptor.getValue();
    assertThat(userArgument.getName()).isEqualTo("newUserName");
    assertThat(userArgument.getUuid()).isEqualTo("userId");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_updateUserWithExternalUserId_withoutExistingExternalUserId() {
    doReturn(User.Builder.anUser().uuid("userId").name("old name").email("test@harness.io").build())
        .when(userService)
        .get("userId");
    doReturn(User.Builder.anUser().build()).when(userService).update(any(User.class));
    final QLUpdateUserInput updateUserInput = QLUpdateUserInput.builder()
                                                  .clientMutationId("clientMutationId1")
                                                  .id("userId")
                                                  .name(RequestField.ofNullable("newUserName"))
                                                  .externalUserId(RequestField.ofNullable("newExternalUserId"))
                                                  .build();
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountId")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    final QLUpdateUserPayload qlUpdateUserPayload =
        updateUserDataFetcher.mutateAndFetch(updateUserInput, mutationContext);
    assertThat(qlUpdateUserPayload.getClientMutationId()).isEqualTo("clientMutationId1");

    verify(userService, times(1)).get("userId");
    final ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
    verify(userService, times(1)).update(userArgumentCaptor.capture());

    final User userArgument = userArgumentCaptor.getValue();
    assertThat(userArgument.getName()).isEqualTo("newUserName");
    assertThat(userArgument.getUuid()).isEqualTo("userId");
    assertThat(userArgument.getExternalUserId()).isEqualTo("newExternalUserId");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_updateUserWithExternalUserId_withExistingExternalUserId() {
    doReturn(User.Builder.anUser()
                 .uuid("userId")
                 .name("old name")
                 .email("test@harness.io")
                 .externalUserId("oldExternalUserId")
                 .build())
        .when(userService)
        .get("userId");
    doReturn(User.Builder.anUser().build()).when(userService).update(any(User.class));
    final QLUpdateUserInput updateUserInput = QLUpdateUserInput.builder()
                                                  .clientMutationId("clientMutationId1")
                                                  .id("userId")
                                                  .name(RequestField.ofNullable("newUserName"))
                                                  .externalUserId(RequestField.ofNullable("newExternalUserId"))
                                                  .build();
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountId")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    final QLUpdateUserPayload qlUpdateUserPayload =
        updateUserDataFetcher.mutateAndFetch(updateUserInput, mutationContext);
    assertThat(qlUpdateUserPayload.getClientMutationId()).isEqualTo("clientMutationId1");

    verify(userService, times(1)).get("userId");
    final ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
    verify(userService, times(1)).update(userArgumentCaptor.capture());

    final User userArgument = userArgumentCaptor.getValue();
    assertThat(userArgument.getName()).isEqualTo("newUserName");
    assertThat(userArgument.getUuid()).isEqualTo("userId");
    assertThat(userArgument.getExternalUserId()).isEqualTo("newExternalUserId");
  }
}
