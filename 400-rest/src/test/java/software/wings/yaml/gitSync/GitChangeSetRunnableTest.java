/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GitChangeSetRunnableTest extends WingsBaseTest {
  @Mock GitChangeSetRunnableHelper gitChangeSetRunnableHelper;
  @Mock YamlChangeSetService yamlChangeSetService;
  @Inject @InjectMocks private GitChangeSetRunnable gitChangeSetRunnable;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testShouldPerformStuckJobCheck() throws IllegalAccessException {
    ((AtomicLong) FieldUtils.readStaticField(GitChangeSetRunnable.class, "lastTimestampForStuckJobCheck", true)).set(0);

    assertThat(gitChangeSetRunnable.shouldPerformStuckJobCheck()).isTrue();

    ((AtomicLong) FieldUtils.readStaticField(GitChangeSetRunnable.class, "lastTimestampForStuckJobCheck", true))
        .set(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(120));
    assertThat(gitChangeSetRunnable.shouldPerformStuckJobCheck()).isTrue();

    ((AtomicLong) FieldUtils.readStaticField(GitChangeSetRunnable.class, "lastTimestampForStuckJobCheck", true))
        .set(System.currentTimeMillis());
    assertThat(gitChangeSetRunnable.shouldPerformStuckJobCheck()).isFalse();

    ((AtomicLong) FieldUtils.readStaticField(GitChangeSetRunnable.class, "lastTimestampForStuckJobCheck", true)).set(0);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRetryAnyStuckYamlChangeSet() {
    YamlChangeSet yamlChangeSet = YamlChangeSet.builder().accountId(WingsTestConstants.ACCOUNT_ID).build();
    yamlChangeSet.setUuid("12345");

    doReturn(Arrays.asList(yamlChangeSet)).when(gitChangeSetRunnableHelper).getStuckYamlChangeSets(any(), anyList());
    doReturn(true)
        .when(yamlChangeSetService)
        .updateStatusAndIncrementRetryCountForYamlChangeSets(anyString(), any(), anyList(), anyList());

    gitChangeSetRunnable.retryAnyStuckYamlChangeSet(Arrays.asList("12345"));
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(yamlChangeSetService)
        .updateStatusAndIncrementRetryCountForYamlChangeSets(anyString(), any(), anyList(), captor.capture());
    List stuckChangeSetIds = captor.getValue();
    assertThat(stuckChangeSetIds).isNotNull();
    assertThat(stuckChangeSetIds).hasSize(1);
    assertThat(stuckChangeSetIds.get(0)).isEqualTo("12345");
  }
}
