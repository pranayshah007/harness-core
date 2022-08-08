/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WebhookParameters {
  private List<String> params;
  private List<String> expressions = new ArrayList<>();

  public static final String PULL_REQUEST_ID = "${pullrequest.id}";
  public static final String PULL_REQUEST_TITLE = "${pullrequest.title}";
  public static final String SOURCE_BRANCH_NAME = "${pullrequest.fromRef.branch.name}";
  public static final String TARGET_BRANCH_NAME = "${pullrequest.toRef.branch.name}";
  public static final String SOURCE_REPOSITORY_NAME = "${pullrequest.fromRef.repository.project.name}";
  public static final String SOURCE_REPOSITORY_OWNER = "${pullrequest.fromRef.repository.owner.username}";
  public static final String DESTINATION_REPOSITORY_NAME = "${pullrequest.toRef.repository.project.name}";
  public static final String DESTINATION_REPOSITORY_OWNER = "${pullrequest.toRef.repository.owner.username}";
  public static final String SOURCE_COMMIT_HASH = "${pullrequest.fromRef.commit.hash}";
  public static final String DESTINATION_COMMIT_HASH = "${pullrequest.toRef.commit.hash}";

  // Bit Bucket Push Request suggestions
  public static final String BIT_BUCKET_PUSH_BRANCH_REF = "${push.changes[0].'new'.name}";
  public static final String BIT_BUCKET_PUSH_BRANCH_FORMAT = "${push.changes[%d].'new'.name}";
  public static final String BIT_BUCKET_PULL_BRANCH_REF = "${pullrequest.source.branch.name}";
  public static final String BIT_BUCKET_ON_PREM_PULL_BRANCH_REF = "${pullRequest.fromRef.displayId}";
  public static final String BIT_BUCKET_COMMIT_ID = "${push.changes[0].'new'.target.hash}";
  public static final String BIT_BUCKET_REF_CHANGE_REQUEST_COMMIT_ID = "${changes[0].toHash}";
  public static final String BIT_BUCKET_REFS_CHANGED_REF = "${changes[0].refId.split('refs/heads/')[1]}";
  public static final String BIT_BUCKET_REPOSITORY_NAME = "${repository.name}";
  public static final String BIT_BUCKET_REPOSITORY_FULL_NAME = "${repository.full_name}";
  public static final String BIT_BUCKET_ON_PREM_PULL_REPOSITORY_NAME = "${pullRequest.toRef.repository.name}";
  // ?
  public static final String BIT_BUCKET_REPOSITORY_CLONE_HTTP =
      "${(${repository.links.clone.0.name} eq 'http') ? ${repository.links.clone.0.href} : ${repository.links.clone.1.href}}";
  public static final String BIT_BUCKET_REPOSITORY_CLONE_SSH =
      "${(${repository.links.clone.0.name} eq 'ssh') ? ${repository.links.clone.0.href} : ${repository.links.clone.1.href}}";
  public static final String BIT_BUCKET_ON_PREM_PULL_REPOSITORY_CLONE_HTTP =
      "${(${pullRequest.toRef.repository.links.clone.0.name} eq 'http') ? ${pullRequest.toRef.repository.links.clone.0.href} : ${pullRequest.toRef.repository.links.clone.1.href}}";
  public static final String BIT_BUCKET_ON_PREM_PULL_REPOSITORY_CLONE_SSH =
      "${(${pullRequest.toRef.repository.links.clone.0.name} eq 'ssh') ? ${pullRequest.toRef.repository.links.clone.0.href} : ${pullRequest.toRef.repository.links.clone.1.href}}";
  public static final String COMMON_EXPRESSION_PREFIX = "${";

  // Git Hub Pull request suggestions
  public static final String GH_PR_ID = "${pull_request.id}";
  public static final String GH_PR_NUMBER = "${pull_request.number}";
  public static final String GH_PR_STATE = "${pull_request.state}";
  public static final String GH_PR_URL = "${pull_request.url}";

  // Git Hub Release event suggestions
  public static final String GH_RELEASE_ID = "${release.id}";
  public static final String GH_RELEASE_NAME = "${release.name}";
  public static final String GH_RELEASE_TAG = "${release.tag_name}";
  public static final String GH_RELEASE_URL = "${release.url}";
  public static final String GH_RELEASE_COMMITISH = "${release.target_commitish}";
  public static final String GH_RELEASE_PRERELEASE = "${release.prerelease}";

  // Git Hub package event suggestions
  public static final String GH_PACKAGE_ID = "${package.id}";
  public static final String GH_PACKAGE_NAME = "${package.name}";
  public static final String GH_PACKAGE_TYPE = "${package.package_type}";
  public static final String GH_PACKAGE_HTML_URL = "${package.html_url}";

  // Git Hub Push event suggestions
  public static final String GH_PUSH_REF = "${ref}";
  public static final String GH_PUSH_REF_BRANCH = "${ref.split('refs/heads/')[1]}";
  public static final String GH_PULL_REF_BRANCH = "${pull_request.head.ref}";
  public static final String GH_PUSH_COMMIT_ID = "${commits[0].id}";
  public static final String GH_PUSH_HEAD_COMMIT_ID = "${head_commit.id}";
  public static final String GH_PUSH_REPOSITORY_NAME = "${repository.name}";
  public static final String GH_PUSH_REPOSITORY_FULL_NAME = "${repository.full_name}";
  public static final String GH_PUSH_REPOSITORY_ID = "${repository.id}";
  public static final String GH_PUSH_REPOSITORY_CLONE_HTTP = "${repository.clone_url}";
  public static final String GH_PUSH_REPOSITORY_CLONE_SSH = "${repository.ssh_url}";

  // Git Lab Push Event Suggestions
  public static final String GIT_LAB_PUSH_REF = "${ref}";
  public static final String GIT_LAB_PUSH_REF_BRANCH = "${ref.split('refs/heads/')[1]}";
  public static final String GIT_LAB_PULL_REF_BRANCH = "${object_attributes.source_branch}";
  public static final String GIT_LAB_PUSH_COMMIT_ID = "${checkout_sha}";
  public static final String GIT_LAB_PUSH_REPOSITORY_NAME = "${project.path_with_namespace.split('/')[1]}";
  public static final String GIT_LAB_PUSH_REPOSITORY_FULL_NAME = "${project.path_with_namespace}";
  public static final String GIT_LAB_PUSH_REPOSITORY_ID = "${repository.id}";
  public static final String GIT_LAB_PUSH_REPOSITORY_CLONE_HTTP = "${project.http_url}";
  public static final String GIT_LAB_PUSH_REPOSITORY_CLONE_SSH = "${project.ssh_url}";

  // Azure Devops Push event suggestions
  /* For Code Push Event */
  public static final String AZURE_DEVOPS_CODE_PUSH_REF_BRANCH =
      "${resource.refUpdates[0].name.split('refs/heads/')[1]}";
  public static final String AZURE_DEVOPS_CODE_PUSH_COMMIT_ID = "${resource.commits[0].commitId}";
  public static final String AZURE_DEVOPS_CODE_PUSH_REPOSITORY_FULL_NAME = "${resource.repository.name}";
  /* For Pull request merged event */
  public static final String AZURE_DEVOPS_PULL_REQUEST_MERGED_REF_BRANCH =
      "${resource.targetRefName.split('refs/heads/')[1]}";
  public static final String AZURE_DEVOPS_PULL_REQUEST_MERGED_COMMIT_ID = "${resource.lastMergeCommit.commitId}";
  public static final String AZURE_DEVOPS_PULL_REQUEST_MERGED_REPOSITORY_FULL_NAME = "${resource.repository.name}";

  public static List<String> bitBucketPullRequestExpressions() {
    List<String> prSuggestions = new ArrayList<>();
    prSuggestions.add(PULL_REQUEST_ID);
    prSuggestions.add(PULL_REQUEST_TITLE);
    prSuggestions.add(SOURCE_BRANCH_NAME);
    prSuggestions.add(TARGET_BRANCH_NAME);
    prSuggestions.add(SOURCE_REPOSITORY_NAME);
    prSuggestions.add(DESTINATION_REPOSITORY_NAME);
    prSuggestions.add(SOURCE_REPOSITORY_OWNER);
    prSuggestions.add(DESTINATION_REPOSITORY_OWNER);
    prSuggestions.add(SOURCE_COMMIT_HASH);
    prSuggestions.add(DESTINATION_COMMIT_HASH);
    return prSuggestions;
  }

  public static List<String> gitHubPullRequestExpressions() {
    List<String> prSuggestions = new ArrayList<>();
    prSuggestions.add(GH_PR_ID);
    prSuggestions.add(GH_PR_NUMBER);
    prSuggestions.add(GH_PR_STATE);
    prSuggestions.add(GH_PR_URL);
    return prSuggestions;
  }

  public static List<String> gitHubReleaseExpressions() {
    List<String> releaseSuggestions = new ArrayList<>();
    releaseSuggestions.add(GH_RELEASE_NAME);
    releaseSuggestions.add(GH_RELEASE_ID);
    releaseSuggestions.add(GH_RELEASE_TAG);
    releaseSuggestions.add(GH_RELEASE_PRERELEASE);
    releaseSuggestions.add(GH_RELEASE_COMMITISH);
    releaseSuggestions.add(GH_RELEASE_URL);
    return releaseSuggestions;
  }

  public static List<String> gitHubPackageExpressions() {
    List<String> packageSuggestions = new ArrayList<>();
    packageSuggestions.add(GH_PACKAGE_NAME);
    packageSuggestions.add(GH_PACKAGE_TYPE);
    packageSuggestions.add(GH_PACKAGE_ID);
    packageSuggestions.add(GH_PACKAGE_HTML_URL);
    return packageSuggestions;
  }

  public static List<String> gitHubPushEventExpressions() {
    List<String> pushSuggestions = new ArrayList<>();
    pushSuggestions.add(GH_PUSH_REF);
    pushSuggestions.add(GH_PUSH_REF_BRANCH);
    pushSuggestions.add(GH_PUSH_COMMIT_ID);
    pushSuggestions.add(GH_PUSH_HEAD_COMMIT_ID);
    pushSuggestions.add(GH_PUSH_REPOSITORY_NAME);
    pushSuggestions.add(GH_PUSH_REPOSITORY_ID);
    return pushSuggestions;
  }

  public static List<String> gitLabPushEventExpressions() {
    List<String> pushSuggestions = new ArrayList<>();
    pushSuggestions.add(GIT_LAB_PUSH_REF);
    pushSuggestions.add(GIT_LAB_PUSH_REF_BRANCH);
    pushSuggestions.add(GIT_LAB_PUSH_COMMIT_ID);
    pushSuggestions.add(GIT_LAB_PUSH_REPOSITORY_NAME);
    pushSuggestions.add(GIT_LAB_PUSH_REPOSITORY_ID);
    return pushSuggestions;
  }
  public static List<String> suggestExpressions(WebhookSource webhookSource, WebhookEventType eventType) {
    if (webhookSource == null || eventType == null) {
      return new ArrayList<>();
    }
    switch (webhookSource) {
      case BITBUCKET:
        if (eventType == WebhookEventType.PULL_REQUEST) {
          return bitBucketPullRequestExpressions();
        }
        return new ArrayList<>();
      case GITHUB:
        switch (eventType) {
          case PULL_REQUEST:
            return gitHubPullRequestExpressions();
          case PUSH:
            return gitHubPushEventExpressions();
          case RELEASE:
            return gitHubReleaseExpressions();
          case PACKAGE:
            return gitHubPackageExpressions();
          default:
            return new ArrayList<>();
        }
      case GITLAB:
        if (eventType == WebhookEventType.PUSH) {
          return gitLabPushEventExpressions();
        }
        return new ArrayList<>();
      default:
        return new ArrayList<>();
    }
  }
}
