/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.expression;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.expression.TerraformPlanDelegateFunctor.TerraformPlan;
import io.harness.rule.Owner;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanDelegateFunctorTest extends CategoryTest {
  private static final int FUNCTOR_TOKEN = 123467890;
  private static final String ACCOUNT_ID = "accountId";

  @Mock private DelegateFileManagerBase delegateFileManager;

  private TerraformPlanDelegateFunctor delegateFunctor;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    delegateFunctor = TerraformPlanDelegateFunctor.builder()
                          .expressionFunctorToken(FUNCTOR_TOKEN)
                          .accountId(ACCOUNT_ID)
                          .delegateFileManager(delegateFileManager)
                          .build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainPlanJsonFilePath() throws IOException {
    final String fileId = "fileId";
    final String content = "fileContentPlanJson";

    TerraformPlan plan = null;
    try (PipedInputStream pipedInputStream = new PipedInputStream();
         PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
         GZIPOutputStream gzipOutputStream = new GZIPOutputStream(pipedOutputStream)) {
      doReturn(pipedInputStream)
          .when(delegateFileManager)
          .downloadByFileId(FileBucket.TERRAFORM_PLAN_JSON, fileId, ACCOUNT_ID);
      gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
      gzipOutputStream.finish();

      plan = delegateFunctor.obtainPlan(fileId, FUNCTOR_TOKEN);
      assertThat(Files.readAllLines(Paths.get(plan.jsonFilePath()))).isEqualTo(Collections.singletonList(content));
    } finally {
      if (plan != null) {
        FileUtils.deleteQuietly(new File(plan.jsonFilePath()));
      }
    }
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainCloudPlanJsonFilePath() throws IOException {
    final String fileId = "fileId";
    final String content = "fileContentPlanJson";

    TerraformPlan plan = null;
    try (PipedInputStream pipedInputStream = new PipedInputStream();
         PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
         GZIPOutputStream gzipOutputStream = new GZIPOutputStream(pipedOutputStream)) {
      doReturn(pipedInputStream)
          .when(delegateFileManager)
          .downloadByFileId(FileBucket.TERRAFORM_PLAN_JSON, fileId, ACCOUNT_ID);
      gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
      gzipOutputStream.finish();

      plan = delegateFunctor.obtainCloudPlan(fileId, FUNCTOR_TOKEN);
      assertThat(Files.readAllLines(Paths.get(plan.jsonFilePath()))).isEqualTo(Collections.singletonList(content));
    } finally {
      if (plan != null) {
        FileUtils.deleteQuietly(new File(plan.jsonFilePath()));
      }
    }
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainPolicyChecksJsonFilePath() throws IOException {
    final String fileId = "fileId";
    final String content = "fileContentPolicyChecksJson";

    TerraformPlan plan = null;
    try (PipedInputStream pipedInputStream = new PipedInputStream();
         PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
         GZIPOutputStream gzipOutputStream = new GZIPOutputStream(pipedOutputStream)) {
      doReturn(pipedInputStream)
          .when(delegateFileManager)
          .downloadByFileId(FileBucket.TERRAFORM_CLOUD_POLICY_CHECKS, fileId, ACCOUNT_ID);
      gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
      gzipOutputStream.finish();

      plan = delegateFunctor.obtainPolicyChecks(fileId, FUNCTOR_TOKEN);
      assertThat(Files.readAllLines(Paths.get(plan.policyChecksJsonFilePath())))
          .isEqualTo(Collections.singletonList(content));
    } finally {
      if (plan != null) {
        FileUtils.deleteQuietly(new File(plan.policyChecksJsonFilePath()));
      }
    }
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainHumanReadablePlanJsonFilePath() throws IOException {
    final String fileId = "fileId";
    final String content = "humanReadablePlanJson";

    TerraformPlan plan = null;
    try (PipedInputStream pipedInputStream = new PipedInputStream();
         PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
         GZIPOutputStream gzipOutputStream = new GZIPOutputStream(pipedOutputStream)) {
      doReturn(pipedInputStream)
          .when(delegateFileManager)
          .downloadByFileId(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN, fileId, ACCOUNT_ID);
      gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
      gzipOutputStream.finish();

      plan = delegateFunctor.obtainHumanReadablePlan(fileId, FUNCTOR_TOKEN);
      assertThat(Files.readAllLines(Paths.get(plan.humanReadableFilePath())))
          .isEqualTo(Collections.singletonList(content));
    } finally {
      if (plan != null) {
        FileUtils.deleteQuietly(new File(plan.humanReadableFilePath()));
      }
    }
  }
}
