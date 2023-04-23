/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.tag;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.HarnessTagLink;
import software.wings.graphql.datafetcher.AuthRuleGraphQL;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.mutation.tag.QLDetachTagPayload;
import software.wings.graphql.schema.type.aggregation.QLEntityType;
import software.wings.service.intfc.HarnessTagService;

import com.google.common.collect.ImmutableMap;
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

public class DetachTagDataFetcherTest extends CategoryTest {
  @Mock AuthRuleGraphQL authRuleInstrumentation;
  @Mock DataFetcherUtils utils;
  @Mock TagHelper tagHelper;
  @Mock HarnessTagService tagService;
  @InjectMocks @Spy DetachTagDataFetcher detachTagDataFetcher = new DetachTagDataFetcher(tagService);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void test_mutateAndFetch() throws Exception {
    final DataFetchingEnvironment dataFetchingEnvironment = Mockito.mock(DataFetchingEnvironment.class);
    doReturn(ImmutableMap.of("clientMutationId", "req1", "entityId", "appId", "entityType", "APPLICATION", "name",
                 "tagName", "value", "tagValue"))
        .when(dataFetchingEnvironment)
        .getArguments();
    doReturn("accountid").when(utils).getAccountId(dataFetchingEnvironment);
    doReturn("appId").when(tagHelper).validateAndFetchAppId("appId", QLEntityType.APPLICATION);

    {
      final QLDetachTagPayload qlDetachTagPayload = detachTagDataFetcher.get(dataFetchingEnvironment);
      ArgumentCaptor<HarnessTagLink> tagLinkArgumentCaptor = ArgumentCaptor.forClass(HarnessTagLink.class);

      verify(tagService, times(1)).detachTag(tagLinkArgumentCaptor.capture());
      verify(tagService, times(1)).authorizeTagAttachDetach(eq("appId"), tagLinkArgumentCaptor.capture());
      final HarnessTagLink tagLink = tagLinkArgumentCaptor.getValue();
      assertThat(tagLink.getAppId()).isEqualTo("appId");
    }
  }
}
