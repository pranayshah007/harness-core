/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngtriggers.Constants.ARTIFACT_BUILD_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_METADATA_EXPR;
import static io.harness.ngtriggers.Constants.ARTIFACT_TYPE;
import static io.harness.ngtriggers.Constants.BASE_COMMIT_SHA;
import static io.harness.ngtriggers.Constants.BRANCH;
import static io.harness.ngtriggers.Constants.COMMIT_SHA;
import static io.harness.ngtriggers.Constants.CUSTOM_TYPE;
import static io.harness.ngtriggers.Constants.EVENT;
import static io.harness.ngtriggers.Constants.GIT_USER;
import static io.harness.ngtriggers.Constants.HEADER;
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
import static io.harness.ngtriggers.Constants.TAG;
import static io.harness.ngtriggers.Constants.TARGET_BRANCH;
import static io.harness.ngtriggers.Constants.TYPE;
import static io.harness.ngtriggers.Constants.WEBHOOK_TYPE;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;
import static io.harness.pms.contracts.triggers.SourceType.AWS_CODECOMMIT_REPO;
import static io.harness.pms.contracts.triggers.SourceType.BITBUCKET_REPO;
import static io.harness.pms.contracts.triggers.SourceType.CUSTOM_REPO;
import static io.harness.pms.contracts.triggers.SourceType.GITHUB_REPO;
import static io.harness.pms.contracts.triggers.SourceType.GITLAB_REPO;
import static io.harness.pms.contracts.triggers.Type.SCHEDULED;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.User;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerHelper {
  public Map<String, Object> buildJsonObjectFromAmbiance(TriggerPayload triggerPayload) {
    Map<String, Object> jsonObject = new HashMap<>();
    if (triggerPayload == null) {
      return jsonObject;
    }
    ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
    // branchesxv
    switch (parsedPayload.getPayloadCase()) {
      case PR:
        PullRequest pullRequest = parsedPayload.getPr().getPr();
        jsonObject.put(BRANCH, pullRequest.getSource());
        jsonObject.put(TARGET_BRANCH, pullRequest.getTarget());
        jsonObject.put(SOURCE_BRANCH, pullRequest.getSource());
        jsonObject.put(EVENT, PR);
        jsonObject.put(PR_NUMBER, Long.toString(pullRequest.getNumber()));
        jsonObject.put(PR_TITLE, pullRequest.getTitle());
        jsonObject.put(COMMIT_SHA, pullRequest.getSha());
        if (pullRequest.getBase() != null) {
          jsonObject.put(BASE_COMMIT_SHA, pullRequest.getBase().getSha());
        }
        jsonObject.put(TYPE, WEBHOOK_TYPE);
        jsonObject.put(REPO_URL, parsedPayload.getPr().getRepo().getLink());
        jsonObject.put(GIT_USER, getGitUser(parsedPayload));
        break;
      case PUSH:
        jsonObject.put(BRANCH, parsedPayload.getPush().getRef().replaceFirst("^refs/heads/", ""));
        jsonObject.put(TARGET_BRANCH, parsedPayload.getPush().getRef().replaceFirst("^refs/heads/", ""));
        jsonObject.put(COMMIT_SHA, parsedPayload.getPush().getAfter());
        jsonObject.put(EVENT, PUSH);
        jsonObject.put(TYPE, WEBHOOK_TYPE);
        jsonObject.put(REPO_URL, parsedPayload.getPush().getRepo().getLink());
        jsonObject.put(GIT_USER, parsedPayload.getPush().getSender().getLogin());
        if (parsedPayload.getPush().getRef().startsWith("refs/tags/")) {
          jsonObject.put(TAG, parsedPayload.getPush().getRef().replaceFirst("refs/tags/", ""));
        }
        break;
      default:
        if (SCHEDULED == triggerPayload.getType()) {
          jsonObject.put(TYPE, SCHEDULED_TYPE);
        } else {
          jsonObject.put(TYPE, CUSTOM_TYPE);
        }
        break;
    }

    setSourceType(jsonObject, triggerPayload);
    jsonObject.put(HEADER, triggerPayload.getHeadersMap());

    if (triggerPayload.hasArtifactData() || triggerPayload.hasManifestData()) {
      addBuildData(jsonObject, triggerPayload);
    }
    return jsonObject;
  }

  private void addBuildData(Map<String, Object> jsonObject, TriggerPayload triggerPayload) {
    HashMap<String, Object> map = new HashMap<>();
    if (triggerPayload.hasArtifactData()) {
      // <+trigger.artifact.build>
      map.put(ARTIFACT_BUILD_EXPR, triggerPayload.getArtifactData().getBuild());
      map.put(ARTIFACT_METADATA_EXPR, triggerPayload.getArtifactData().getMetadataMap());
      jsonObject.put(TYPE, ARTIFACT_TYPE);
      jsonObject.remove(SOURCE_TYPE);
      jsonObject.put(ARTIFACT_EXPR, map);
    } else if (triggerPayload.hasManifestData()) {
      // <+trigger.manifest.version>
      map.put(MANIFEST_VERSION_EXPR, triggerPayload.getManifestData().getVersion());
      jsonObject.put(MANIFEST_EXPR, map);
      jsonObject.put(TYPE, MANIFEST_TYPE);
      jsonObject.remove(SOURCE_TYPE);
    }
  }

  private String getGitUser(ParsedPayload parsedPayload) {
    User sender = parsedPayload.getPr().getSender();
    String gitUser = sender.getLogin();

    if (isBlank(gitUser)) {
      gitUser = sender.getName();
    }

    return gitUser;
  }

  private void setSourceType(Map<String, Object> jsonObject, TriggerPayload triggerPayload) {
    SourceType sourceRepo = triggerPayload.getSourceType();
    String sourceTypeVal = null;
    if (sourceRepo == GITHUB_REPO) {
      sourceTypeVal = GITHUB.getValue();
    } else if (sourceRepo == GITLAB_REPO) {
      sourceTypeVal = GITLAB.getValue();
    } else if (sourceRepo == BITBUCKET_REPO) {
      sourceTypeVal = BITBUCKET.getValue();
    } else if (sourceRepo == CUSTOM_REPO) {
      sourceTypeVal = CUSTOM.getValue();
    } else if (sourceRepo == AWS_CODECOMMIT_REPO) {
      sourceTypeVal = AWS_CODECOMMIT.getValue();
    }

    if (isNotBlank(sourceTypeVal)) {
      jsonObject.put(SOURCE_TYPE, sourceTypeVal);
    }
  }

  public String getTriggerRef(NGTriggerEntity ngTriggerEntity) {
    return new StringBuilder(128)
        .append(ngTriggerEntity.getAccountId())
        .append(':')
        .append(ngTriggerEntity.getOrgIdentifier())
        .append(':')
        .append(ngTriggerEntity.getProjectIdentifier())
        .append(':')
        .append(ngTriggerEntity.getTargetIdentifier())
        .append(':')
        .append(ngTriggerEntity.getIdentifier())
        .toString();
  }

  public List<String> getAllTriggerExpressions() {
    return Arrays.asList("trigger.targetBranch", "trigger.sourceBranch", "trigger.prNumber", "trigger.prTitle");
  }

  public static void stampWebhookRegistrationInfo(
      NGTriggerEntity ngTriggerEntity, WebhookAutoRegistrationStatus registrationStatus) {
    if (ngTriggerEntity.getTriggerStatus() == null) {
      ngTriggerEntity.setTriggerStatus(
          TriggerStatus.builder().webhookAutoRegistrationStatus(registrationStatus).build());
    } else {
      ngTriggerEntity.getTriggerStatus().setWebhookAutoRegistrationStatus(registrationStatus);
    }
  }

  public static void stampWebhookIdInfo(NGTriggerEntity ngTriggerEntity, String webhookId) {
    if (ngTriggerEntity.getTriggerStatus().getWebhookInfo() == null) {
      if (isEmpty(webhookId)) {
        webhookId = ngTriggerEntity.getWebhookId();
        log.info("WebhookId manually added by the user : {}", webhookId);
      }
      ngTriggerEntity.getTriggerStatus().setWebhookInfo(WebhookInfo.builder().webhookId(webhookId).build());
    }
  }
}
