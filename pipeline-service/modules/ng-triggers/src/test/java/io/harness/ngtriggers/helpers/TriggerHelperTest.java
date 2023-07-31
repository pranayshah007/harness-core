/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.ARTIFACT_BUILD_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_METADATA_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_TYPE;
import static io.harness.ngtriggers.Constants.BRANCH;
import static io.harness.ngtriggers.Constants.COMMIT_SHA;
import static io.harness.ngtriggers.Constants.CUSTOM_TYPE;
import static io.harness.ngtriggers.Constants.EVENT;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.MANIFEST_EXPR;
import static io.harness.ngtriggers.Constants.MANIFEST_TYPE;
import static io.harness.ngtriggers.Constants.MANIFEST_VERSION_EXPR;
import static io.harness.ngtriggers.Constants.PR;
import static io.harness.ngtriggers.Constants.PR_NUMBER;
import static io.harness.ngtriggers.Constants.PR_TITLE;
import static io.harness.ngtriggers.Constants.PUSH;
import static io.harness.ngtriggers.Constants.REPO_URL;
import static io.harness.ngtriggers.Constants.SCHEDULED_TYPE;
import static io.harness.ngtriggers.Constants.SOURCE_BRANCH;
import static io.harness.ngtriggers.Constants.SOURCE_TYPE;
import static io.harness.ngtriggers.Constants.TARGET_BRANCH;
import static io.harness.ngtriggers.Constants.TYPE;
import static io.harness.ngtriggers.Constants.WEBHOOK_TYPE;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.pms.contracts.triggers.SourceType.GITHUB_REPO;
import static io.harness.pms.contracts.triggers.Type.SCHEDULED;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class TriggerHelperTest extends CategoryTest {
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceWebhookPR() {
    User sender = User.newBuilder().setLogin("user").build();
    Repository repo = Repository.newBuilder().setLink("repo_link").build();
    PullRequest pr = PullRequest.newBuilder()
                         .setSource("branch")
                         .setTarget("target_branch")
                         .setNumber(123l)
                         .setTitle("title")
                         .setSha("sha")
                         .build();
    ParsedPayload prParsedPayload =
        ParsedPayload.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(pr).setRepo(repo).setSender(sender).build())
            .build();
    TriggerPayload prPayload =
        TriggerPayload.newBuilder().setParsedPayload(prParsedPayload).setSourceType(GITHUB_REPO).build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(prPayload);
    assertThat(jsonObject.get(BRANCH)).isEqualTo("branch");
    assertThat(jsonObject.get(TARGET_BRANCH)).isEqualTo("target_branch");
    assertThat(jsonObject.get(SOURCE_BRANCH)).isEqualTo("branch");
    assertThat(jsonObject.get(EVENT)).isEqualTo(PR);
    assertThat(jsonObject.get(PR_NUMBER)).isEqualTo("123");
    assertThat(jsonObject.get(PR_TITLE)).isEqualTo("title");
    assertThat(jsonObject.get(COMMIT_SHA)).isEqualTo("sha");
    assertThat(jsonObject.get(TYPE)).isEqualTo(WEBHOOK_TYPE);
    assertThat(jsonObject.get(REPO_URL)).isEqualTo("repo_link");
    assertThat(jsonObject.get(GIT_USER)).isEqualTo("user");
    assertThat(jsonObject.get(SOURCE_TYPE)).isEqualTo(GITHUB.getValue());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceWebhookPush() {
    User sender = User.newBuilder().setLogin("user").build();
    Repository repo = Repository.newBuilder().setLink("repo_link").build();
    PushHook push =
        PushHook.newBuilder().setRepo(repo).setRef("refs/heads/branch").setSender(sender).setAfter("sha").build();
    ParsedPayload pushParsedPayload = ParsedPayload.newBuilder().setPush(push).build();
    TriggerPayload pushPayload =
        TriggerPayload.newBuilder().setParsedPayload(pushParsedPayload).setSourceType(GITHUB_REPO).build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(pushPayload);
    assertThat(jsonObject.get(BRANCH)).isEqualTo("branch");
    assertThat(jsonObject.get(TARGET_BRANCH)).isEqualTo("branch");
    assertThat(jsonObject.get(EVENT)).isEqualTo(PUSH);
    assertThat(jsonObject.get(COMMIT_SHA)).isEqualTo("sha");
    assertThat(jsonObject.get(TYPE)).isEqualTo(WEBHOOK_TYPE);
    assertThat(jsonObject.get(REPO_URL)).isEqualTo("repo_link");
    assertThat(jsonObject.get(GIT_USER)).isEqualTo("user");
    assertThat(jsonObject.get(SOURCE_TYPE)).isEqualTo(GITHUB.getValue());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceWebhookCustom() {
    TriggerPayload customPayload = TriggerPayload.newBuilder().build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(customPayload);
    assertThat(jsonObject.get(TYPE)).isEqualTo(CUSTOM_TYPE);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceArtifact() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", "my_url");
    TriggerPayload artifactPayload =
        TriggerPayload.newBuilder()
            .setArtifactData(ArtifactData.newBuilder().setBuild("1.0").putAllMetadata(metadata).build())
            .build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(artifactPayload);
    assertThat(jsonObject.get(TYPE)).isEqualTo(ARTIFACT_TYPE);
    assertThat(((Map<String, Object>) jsonObject.get(ARTIFACT_EXPR)).get(ARTIFACT_BUILD_EXPR)).isEqualTo("1.0");
    assertThat(((Map<String, Object>) jsonObject.get(ARTIFACT_EXPR)).get(ARTIFACT_METADATA_EXPR)).isEqualTo(metadata);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceManifest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", "my_url");
    TriggerPayload manifestPayload =
        TriggerPayload.newBuilder().setManifestData(ManifestData.newBuilder().setVersion("1.0").build()).build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(manifestPayload);
    assertThat(jsonObject.get(TYPE)).isEqualTo(MANIFEST_TYPE);
    assertThat(((Map<String, Object>) jsonObject.get(MANIFEST_EXPR)).get(MANIFEST_VERSION_EXPR)).isEqualTo("1.0");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testBuildJsonObjectFromAmbianceScheduled() {
    TriggerPayload scheduledPayload = TriggerPayload.newBuilder().setType(SCHEDULED).build();
    Map<String, Object> jsonObject = TriggerHelper.buildJsonObjectFromAmbiance(scheduledPayload);
    assertThat(jsonObject.get(TYPE)).isEqualTo(SCHEDULED_TYPE);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIsBranchExpr() {
    assertThat(TriggerHelper.isBranchExpr("<+trigger.branch>")).isEqualTo(true);
  }
}
