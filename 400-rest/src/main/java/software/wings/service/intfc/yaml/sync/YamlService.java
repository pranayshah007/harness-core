/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml.sync;

import io.harness.exception.YamlException;
import io.harness.rest.RestResponse;
import io.harness.yaml.BaseYaml;

import software.wings.beans.Base;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.EntityInformation;
import software.wings.beans.yaml.YamlType;
import software.wings.exception.YamlProcessingException;
import software.wings.yaml.FileOperationStatus;
import software.wings.yaml.YamlOperationResponse;
import software.wings.yaml.YamlPayload;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author rktummala on 10/17/17
 */
public interface YamlService<Y extends BaseYaml, B extends Base> {
  /**
   * Process all the yaml changes, convert to beans and save the changes in mongodb.
   * @param changeList yaml change list
   * @throws YamlProcessingException if yaml is not well formed or if any error in processing
   */
  List<ChangeContext> processChangeSet(List<Change> changeList) throws YamlProcessingException;

  List<ChangeContext> processChangeSet(List<Change> changeList, boolean isGitSyncPath) throws YamlProcessingException;

  /**
   * @param yamlPayload
   * @param accountId
   * @param entityId
   */
  RestResponse<B> update(YamlPayload yamlPayload, String accountId, String entityId);

  RestResponse processYamlFilesAsZip(String accountId, InputStream fileInputStream, String yamlPath) throws IOException;

  void syncYamlTemplate(String accountId);

  void sortByProcessingOrder(List<Change> changeList);

  int findOrdinal(String yamlFilePath, String accountId);

  YamlType findYamlType(String yamlFilePath) throws YamlException;

  BaseYaml getYamlForFilePath(String accountId, String yamlFilePath, String yamlSubType, String applicationId);

  String obtainAppIdFromGitFileChange(String accountId, String yamlFilePath);

  YamlOperationResponse upsertYAMLFilesAsZip(String accountId, InputStream fileInputStream) throws IOException;

  YamlOperationResponse deleteYAMLByPaths(String accountId, List<String> filePaths);

  YamlOperationResponse deleteYAMLByPathsV2(String accountId, List<EntityInformation> entityInformations);

  FileOperationStatus upsertYAMLFile(String accountId, String yamlFilePath, String yamlContent);
}
