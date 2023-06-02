/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.application.input.QLUpdateApplicationInput;
import software.wings.graphql.schema.mutation.application.payload.QLDeleteApplicationPayload;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import graphql.schema.DataFetchingEnvironment;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class DeleteApplicationDataFetcherTest extends CategoryTest {
  @Mock AppService appService;

  @InjectMocks
  @Spy
  DeleteApplicationDataFetcher deleteApplicationDataFetcher = new DeleteApplicationDataFetcher(appService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void mutateAndFetch() {
    doNothing().when(appService).delete(anyString());
    final QLUpdateApplicationInput applicationParameters =

        QLUpdateApplicationInput.builder().applicationId("appid").clientMutationId("req1").build();
    final MutationContext mutationContext = MutationContext.builder()
                                                .accountId("accountid")
                                                .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
                                                .build();

    final QLDeleteApplicationPayload qlDeleteApplicationPayload =
        deleteApplicationDataFetcher.mutateAndFetch(applicationParameters, mutationContext);
    verify(appService, times(1)).delete("appid");
    assertThat(qlDeleteApplicationPayload.getClientMutationId()).isEqualTo("req1");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = DeleteApplicationDataFetcher.class.getDeclaredMethod(
        "mutateAndFetch", QLUpdateApplicationInput.class, MutationContext.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_APPLICATIONS);
  }
}
