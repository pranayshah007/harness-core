/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_CREATED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_FINISHED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_NUMBER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_BUILD_STARTED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_AVATAR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_EMAIL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_AUTHOR_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.CI_REPO_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_ACTION;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_CREATED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_FINISHED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_NUMBER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_STARTED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_TRIGGER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_MAJOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_MAJOR_MINOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_MICRO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_MINOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_MODIFIER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_CALVER_SHORT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AFTER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_AVATAR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_EMAIL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BEFORE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_GIT_HTTP_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_GIT_SSH_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_NAMESPACE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_OWNER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_PRIVATE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_SCM;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REPO_VISIBILITY;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_BUILD;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_ERROR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_MAJOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_MINOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_PATCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_PRERELEASE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SEMVER_SHORT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SOURCE_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_FINISHED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_STARTED;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TARGET_BRANCH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CustomExecutionSource;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ExecutionSource.Type;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class BuildEnvironmentUtils {
  private static final String REPO_SCM = "git";
  private static final int MAX_ENV_VAR_LEN = 8191;

  public static Map<String, String> getBuildEnvironmentVariables(CIExecutionArgs ciExecutionArgs) {
    Map<String, String> envVarMap = new HashMap<>();
    if (ciExecutionArgs == null) {
      return envVarMap;
    }

    envVarMap.putAll(getDroneTimestampVars());
    try {
      envVarMap.put(DRONE_BUILD_NUMBER, ciExecutionArgs.getRunSequence());
      envVarMap.put(CI_BUILD_NUMBER, ciExecutionArgs.getRunSequence());

    } catch (Exception ex) {
      log.error("Failed to put build number env var", ex);
    }
    if (ciExecutionArgs.getExecutionSource() == null) {
      return envVarMap;
    }

    // TODO we should still set base env vars if build is manually kicked off... and repo aswell
    // tag webhooks are being treated as manually executed builds... the code doesn't seem to have been written
    if (ciExecutionArgs.getExecutionSource().getType() == ExecutionSource.Type.WEBHOOK) {
      WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) ciExecutionArgs.getExecutionSource();
      if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
        BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
        envVarMap.putAll(getBaseEnvVars(branchWebhookEvent.getBaseAttributes()));
        envVarMap.putAll(getBuildRepoEnvvars(branchWebhookEvent.getRepository()));
        if (branchWebhookEvent.getBranchName().startsWith("refs/tags/")) {
          String droneTag = branchWebhookEvent.getBranchName().replaceFirst("refs/tags/", "");
          envVarMap.put(DRONE_TAG, droneTag);
          envVarMap.putAll(getSemverEnvVars(droneTag));
          envVarMap.putAll(getCalverEnvVars(droneTag));
        }
        envVarMap.put(DRONE_BUILD_EVENT, "push");
        envVarMap.put(CI_BUILD_EVENT, "push");
      }
      if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
        PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
        envVarMap.putAll(getBaseEnvVars(prWebhookEvent.getBaseAttributes()));
        envVarMap.putAll(getBuildRepoEnvvars(prWebhookEvent.getRepository()));

        setBitbucketCloudCommitRef(prWebhookEvent, envVarMap);

        envVarMap.put(DRONE_BUILD_EVENT, "pull_request");
        envVarMap.put(CI_BUILD_EVENT, "pull_request");

        if (!isEmpty(prWebhookEvent.getPullRequestId().toString())) {
          envVarMap.put(DRONE_PULL_REQUEST, prWebhookEvent.getPullRequestId().toString());
        }
      }
      if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.RELEASE) {
        ReleaseWebhookEvent releaseWebhookEvent = (ReleaseWebhookEvent) webhookExecutionSource.getWebhookEvent();
        envVarMap.putAll(getBaseEnvVars(releaseWebhookEvent.getBaseAttributes()));
        envVarMap.putAll(getBuildRepoEnvvars(releaseWebhookEvent.getRepository()));
        envVarMap.put(DRONE_TAG, releaseWebhookEvent.getReleaseTag());
        envVarMap.putAll(getSemverEnvVars(releaseWebhookEvent.getReleaseTag()));
        envVarMap.putAll(getCalverEnvVars(releaseWebhookEvent.getReleaseTag()));
        envVarMap.put(DRONE_BUILD_EVENT, "release");
        envVarMap.put(CI_BUILD_EVENT, "release");
      }
    } else if (ciExecutionArgs.getExecutionSource().getType() == ExecutionSource.Type.MANUAL) {
      ManualExecutionSource manualExecutionSource = (ManualExecutionSource) ciExecutionArgs.getExecutionSource();

      if (!isEmpty(manualExecutionSource.getBranch())) {
        envVarMap.put(DRONE_COMMIT_BRANCH, manualExecutionSource.getBranch());
        envVarMap.put(CI_COMMIT_BRANCH, manualExecutionSource.getBranch());
      }

      if (!isEmpty(manualExecutionSource.getTag())) {
        envVarMap.put(DRONE_TAG, manualExecutionSource.getTag());
        envVarMap.putAll(getSemverEnvVars(manualExecutionSource.getTag()));
        envVarMap.putAll(getCalverEnvVars(manualExecutionSource.getTag()));
        envVarMap.put(DRONE_BUILD_EVENT, "tag");
        envVarMap.put(CI_BUILD_EVENT, "tag");
      }
      if (!isEmpty(manualExecutionSource.getCommitSha())) {
        envVarMap.put(DRONE_COMMIT_SHA, manualExecutionSource.getCommitSha());
        envVarMap.put(CI_COMMIT_SHA, manualExecutionSource.getCommitSha());
      }
      if (!isEmpty(manualExecutionSource.getPrNumber())) {
        envVarMap.put(DRONE_PULL_REQUEST, manualExecutionSource.getPrNumber());
      }

    } else if (ciExecutionArgs.getExecutionSource().getType() == Type.CUSTOM) {
      CustomExecutionSource customExecutionSource = (CustomExecutionSource) ciExecutionArgs.getExecutionSource();
      if (!isEmpty(customExecutionSource.getBranch())) {
        envVarMap.put(DRONE_COMMIT_BRANCH, customExecutionSource.getBranch());
        envVarMap.put(CI_COMMIT_BRANCH, customExecutionSource.getBranch());
      }
      if (!isEmpty(customExecutionSource.getTag())) {
        envVarMap.put(DRONE_TAG, customExecutionSource.getTag());
        envVarMap.putAll(getSemverEnvVars(customExecutionSource.getTag()));
        envVarMap.putAll(getCalverEnvVars(customExecutionSource.getTag()));
        envVarMap.put(DRONE_BUILD_EVENT, "tag");
        envVarMap.put(CI_BUILD_EVENT, "tag");
      }
    }
    return envVarMap;
  }

  private static Map<String, String> getBuildRepoEnvvars(Repository repository) {
    Map<String, String> envVarMap = new HashMap<>();
    setEnvironmentVariable(envVarMap, DRONE_REPO, repository.getSlug());
    setEnvironmentVariable(envVarMap, CI_REPO, repository.getSlug());
    setEnvironmentVariable(envVarMap, DRONE_REPO_SCM, REPO_SCM);
    setEnvironmentVariable(envVarMap, DRONE_REPO_OWNER, repository.getNamespace());
    setEnvironmentVariable(envVarMap, DRONE_REPO_NAMESPACE, repository.getNamespace());
    setEnvironmentVariable(envVarMap, DRONE_REPO_NAME, repository.getName());
    setEnvironmentVariable(envVarMap, CI_REPO_NAME, repository.getName());

    setEnvironmentVariable(envVarMap, DRONE_REPO_LINK, repository.getLink());
    setEnvironmentVariable(envVarMap, CI_REPO_LINK, repository.getLink());

    setEnvironmentVariable(envVarMap, DRONE_REPO_BRANCH, repository.getBranch());
    setEnvironmentVariable(envVarMap, DRONE_GIT_HTTP_URL, repository.getHttpURL());
    setEnvironmentVariable(envVarMap, DRONE_GIT_SSH_URL, repository.getSshURL());
    setEnvironmentVariable(envVarMap, DRONE_REPO_PRIVATE, String.valueOf(repository.isPrivate()));
    setEnvironmentVariable(envVarMap, DRONE_REPO_VISIBILITY, repository.isPrivate() ? "private" : "public");
    return envVarMap;
  }

  private static Map<String, String> getBaseEnvVars(WebhookBaseAttributes baseAttributes) {
    Map<String, String> envVarMap = new HashMap<>();
    setEnvironmentVariable(envVarMap, DRONE_BRANCH, baseAttributes.getTarget());
    setEnvironmentVariable(envVarMap, DRONE_SOURCE_BRANCH, baseAttributes.getSource());
    setEnvironmentVariable(envVarMap, DRONE_TARGET_BRANCH, baseAttributes.getTarget());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT, baseAttributes.getAfter());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_SHA, baseAttributes.getAfter());
    setEnvironmentVariable(envVarMap, CI_COMMIT_SHA, baseAttributes.getAfter());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_BEFORE, baseAttributes.getBefore());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_AFTER, baseAttributes.getAfter());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_REF, baseAttributes.getRef());
    setEnvironmentVariable(envVarMap, CI_COMMIT_REF, baseAttributes.getRef());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_BRANCH, baseAttributes.getTarget());
    setEnvironmentVariable(envVarMap, CI_COMMIT_BRANCH, baseAttributes.getTarget());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_LINK, baseAttributes.getLink());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_MESSAGE, trimEnvVar(baseAttributes.getMessage()));
    setEnvironmentVariable(envVarMap, CI_COMMIT_MESSAGE, trimEnvVar(baseAttributes.getMessage()));
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_AUTHOR, baseAttributes.getAuthorLogin());
    setEnvironmentVariable(envVarMap, CI_COMMIT_AUTHOR, baseAttributes.getAuthorLogin());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_AUTHOR_EMAIL, baseAttributes.getAuthorEmail());
    setEnvironmentVariable(envVarMap, CI_COMMIT_AUTHOR_EMAIL, baseAttributes.getAuthorEmail());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_AUTHOR_AVATAR, baseAttributes.getAuthorAvatar());
    setEnvironmentVariable(envVarMap, CI_COMMIT_AUTHOR_AVATAR, baseAttributes.getAuthorAvatar());
    setEnvironmentVariable(envVarMap, DRONE_COMMIT_AUTHOR_NAME, baseAttributes.getAuthorName());
    setEnvironmentVariable(envVarMap, CI_COMMIT_AUTHOR_NAME, baseAttributes.getAuthorName());
    setEnvironmentVariable(envVarMap, DRONE_BUILD_TRIGGER, baseAttributes.getAuthorName());
    if (isNotEmpty(baseAttributes.getAction())) {
      envVarMap.put(DRONE_BUILD_ACTION, baseAttributes.getAction());
    }
    return envVarMap;
  }

  public static Map<String, String> getDroneTimestampVars() {
    // even drone can't tell the finished timestamp when running so sets it to the start time
    String unixTimestamp = String.valueOf(Instant.now().getEpochSecond());
    Map<String, String> envVarMap = new HashMap<>();
    setEnvironmentVariable(envVarMap, DRONE_BUILD_CREATED, unixTimestamp);
    setEnvironmentVariable(envVarMap, DRONE_BUILD_STARTED, unixTimestamp);
    setEnvironmentVariable(envVarMap, DRONE_BUILD_FINISHED, unixTimestamp);
    setEnvironmentVariable(envVarMap, DRONE_STAGE_STARTED, unixTimestamp);
    setEnvironmentVariable(envVarMap, DRONE_STAGE_FINISHED, unixTimestamp);
    setEnvironmentVariable(envVarMap, CI_BUILD_STARTED, unixTimestamp);
    setEnvironmentVariable(envVarMap, CI_BUILD_CREATED, unixTimestamp);
    setEnvironmentVariable(envVarMap, CI_BUILD_FINISHED, unixTimestamp);

    return envVarMap;
  }

  private static Map<String, String> getCalverEnvVars(String droneTag) {
    Map<String, String> envVarMap = new HashMap<>();

    droneTag = droneTag.startsWith("v") ? droneTag.substring(1) : droneTag;
    String[] p = droneTag.split("\\.", 3);
    if (p.length < 2) {
      return envVarMap;
    }

    String major = p[0];
    String minor = p[1];
    String micro = p.length > 2 ? p[2] : "";
    String modifier = "";

    if (micro.contains("-")) {
      String[] parts = micro.split("-", 2);
      micro = parts[0];
      modifier = parts[1];
    }

    try {
      Integer.parseInt(major);
      Integer.parseInt(minor);
    } catch (NumberFormatException e) {
      return envVarMap;
    }

    int year = Integer.parseInt(major);
    boolean isValidYear = (year > 18 && year < 100) || (year > 2018 && year < 9999);
    if (!isValidYear) {
      return envVarMap;
    }

    Boolean microIsEmpty = micro.isEmpty();
    String droneCalver =
        major + "." + minor + (microIsEmpty ? "" : "." + micro) + (modifier.isEmpty() ? "" : "-" + modifier);
    setEnvironmentVariable(envVarMap, DRONE_CALVER, droneCalver);
    setEnvironmentVariable(envVarMap, DRONE_CALVER_MAJOR_MINOR, major + "." + minor);
    setEnvironmentVariable(envVarMap, DRONE_CALVER_MAJOR, major);
    setEnvironmentVariable(envVarMap, DRONE_CALVER_MINOR, minor);
    setEnvironmentVariable(envVarMap, DRONE_CALVER_MICRO, micro);
    if (!modifier.isEmpty()) {
      setEnvironmentVariable(envVarMap, DRONE_CALVER_MODIFIER, modifier);
    }

    String droneCalverShort = major + "." + minor + (microIsEmpty ? "" : "." + micro);
    setEnvironmentVariable(envVarMap, DRONE_CALVER_SHORT, droneCalverShort);

    return envVarMap;
  }

  private static Map<String, String> getSemverEnvVars(String droneTag) {
    Map<String, String> envVarMap = new HashMap<>();

    droneTag = droneTag.startsWith("v") ? droneTag.substring(1) : droneTag;
    Pattern pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([\\w.]+))?(?:\\+([\\w.]+))?$");
    Matcher matcher = pattern.matcher(droneTag);

    if (!matcher.matches()) {
      setEnvironmentVariable(envVarMap, DRONE_SEMVER_ERROR, "Invalid semver format");
      return envVarMap;
    }

    String major = matcher.group(1);
    String minor = matcher.group(2);
    String patch = matcher.group(3);
    String prerelease = matcher.group(4) != null ? matcher.group(4) : "";
    String metadata = matcher.group(5) != null ? matcher.group(5) : "";

    setEnvironmentVariable(envVarMap, DRONE_SEMVER, droneTag);
    setEnvironmentVariable(envVarMap, DRONE_SEMVER_MAJOR, major);
    setEnvironmentVariable(envVarMap, DRONE_SEMVER_MINOR, minor);
    setEnvironmentVariable(envVarMap, DRONE_SEMVER_PATCH, patch);

    if (!prerelease.isEmpty()) {
      setEnvironmentVariable(envVarMap, DRONE_SEMVER_PRERELEASE, prerelease);
    }

    if (!metadata.isEmpty()) {
      setEnvironmentVariable(envVarMap, DRONE_SEMVER_BUILD, metadata);
    }

    String shortVersion = major + "." + minor + "." + patch;
    setEnvironmentVariable(envVarMap, DRONE_SEMVER_SHORT, shortVersion);

    return envVarMap;
  }

  // Max length of environment variable allowed is 8191 characters in windows and 32768 in linux
  private static String trimEnvVar(String value) {
    if (isEmpty(value)) {
      return "";
    }
    if (value.length() < MAX_ENV_VAR_LEN) {
      return value;
    }

    return abbreviate(value, MAX_ENV_VAR_LEN);
  }

  private static void setBitbucketCloudCommitRef(PRWebhookEvent prWebhookEvent, Map<String, String> envVarMap) {
    // Set this field only for bitbucket cloud.
    String link = prWebhookEvent.getRepository().getLink();
    if (isNotEmpty(link) && link.contains("bitbucket.org")) {
      String commitRef = format("+refs/heads/%s", prWebhookEvent.getBaseAttributes().getSource());
      setEnvironmentVariable(envVarMap, DRONE_COMMIT_REF, commitRef);
      setEnvironmentVariable(envVarMap, CI_COMMIT_REF, commitRef);
    }
  }

  public static void setEnvironmentVariable(Map<String, String> envVarMap, String var, String value) {
    if (value == null) {
      return;
    }
    envVarMap.put(var, value);
  }
}
