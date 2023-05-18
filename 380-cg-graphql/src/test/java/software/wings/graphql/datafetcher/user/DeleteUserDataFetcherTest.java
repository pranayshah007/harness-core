/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.user;

import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.user.QLDeleteUserInput;
import software.wings.graphql.schema.type.user.QLDeleteUserPayload;
import software.wings.service.intfc.UserService;

import graphql.schema.DataFetchingEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class DeleteUserDataFetcherTest extends CategoryTest {
  @Mock UserService userService;

  @InjectMocks @Spy DeleteUserDataFetcher deleteUserDataFetcher = new DeleteUserDataFetcher(userService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_deleteUser() {
    doNothing().when(userService).delete(anyString(), eq("accountId"));
    final QLDeleteUserInput qlDeleteUserInput =
        QLDeleteUserInput.builder().id("userId").clientMutationId("request1").build();

    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountId")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    QLDeleteUserPayload qlDeleteUserPayload = deleteUserDataFetcher.mutateAndFetch(qlDeleteUserInput, mutationContext);
    verify(userService, times(1)).delete(eq("accountId"), eq("userId"));
    assertThat(qlDeleteUserPayload.getClientMutationId()).isEqualTo("request1");
  }
}
