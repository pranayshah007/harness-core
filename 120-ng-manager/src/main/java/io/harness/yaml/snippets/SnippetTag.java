/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.snippets;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.snippets.bean.YamlSnippetTags;

@OwnedBy(DX)
public enum SnippetTag implements YamlSnippetTags {
  k8s,
  git,
  docker,
  connector,
  secretmanager,
  secret,
  secretText,
  secretFile,
  sshKey,
  service,
  infra,
  steps,
  pipeline,
  http,
  splunk,
  appdynamics,
  vault,
  azurekeyvault,
  local,
  gcpkms,
  gcp,
  aws,
  awskms,
  awssecretmanager,
  artifactory,
  jira,
  nexus,
  github,
  gitlab,
  bitbucket,
  ceaws,
  ceazure,
  cek8s,
  codecommit,
  httphelmrepo,
  newrelic,
  gcpcloudcost,
  prometheus,
  datadog,
  sumologic,
  dynatrace,
  pagerduty,
  customhealth,
  servicenow,
  errortracking,
  azure,
  pdc,
  azurerepo,
  ocihelmrepo
}
