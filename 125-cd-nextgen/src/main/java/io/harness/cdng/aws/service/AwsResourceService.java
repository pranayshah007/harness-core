/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;

import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(CDP)
public interface AwsResourceService {
  /**
   * Get the list of all the Capabilities in cloudformation
   *
   * @return the list of capabilities
   */
  List<String> getCapabilities();

  /**
   * Get the list of all the cloudformation states
   *
   * @return the list of all the cloudformation states
   */
  Set<String> getCFStates();

  /**
   * Get the list of available regions from the aws.yaml resource file
   *
   * @return the list of available regions
   */
  Map<String, String> getRegions();

  /**
   * Get all the rolesARNs associated with the given computeProviderId and deployment type
   *
   * @param awsConnectorRef the IdentifierRef of the aws connector
   * @param orgIdentifier the org identifier
   * @param projectIdentifier the project identifier
   *
   * @return the list of rolesARNs
   */
  Map<String, String> getRolesARNs(IdentifierRef awsConnectorRef, String orgIdentifier, String projectIdentifier);

  /**
   * Get all parameter keys for a cloudformation template
   *
   * @param type Where the template is stored (GIT, S3, or inline)
   * @param region AWS region
   * @param isBranch For GIT, the fetchType, (branch or commit)
   * @param branch The branch reference for GIT
   * @param filePath The file path for the template
   * @param commitId The commit id for GIT
   * @param awsConnectorRef the IdentifierRef of the aws connector
   * @param data the template data if inline is selected
   * @param connectorDTO the IdentifierRef of the git connector
   *
   * @return the list of Cloudformation param keys
   */
  List<AwsCFTemplateParamsData> getCFparametersKeys(String type, String region, boolean isBranch, String branch,
      String filePath, String commitId, IdentifierRef awsConnectorRef, String data, String connectorDTO);
}
