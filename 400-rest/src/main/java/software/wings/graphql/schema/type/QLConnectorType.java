/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type;

/**
 * @author rktummala on 07/18/19
 */
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public enum QLConnectorType implements QLEnum {
  SMTP,
  JENKINS,
  BAMBOO,
  SPLUNK,
  ELK,
  LOGZ,
  SUMO,
  APP_DYNAMICS,
  NEW_RELIC,
  DYNA_TRACE,
  BUG_SNAG,
  DATA_DOG,
  APM_VERIFICATION,
  PROMETHEUS,
  ELB,
  SLACK,
  DOCKER,
  ECR,
  GCR,
  NEXUS,
  ARTIFACTORY,
  AMAZON_S3,
  GCS,
  GIT,
  SMB,
  JIRA,
  SFTP,
  SERVICENOW,
  HTTP_HELM_REPO,
  AMAZON_S3_HELM_REPO,
  GCS_HELM_REPO,
  OCI_HELM_REPO;

  @Override
  public String getStringValue() {
    return this.name();
  }
}
