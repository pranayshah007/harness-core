/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.spotinst;

import io.harness.logging.LogCallback;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;

import java.util.List;
import java.util.Optional;

public interface SpotInstHelperServiceDelegate {
  List<ElastiGroup> listAllElstiGroups(String spotInstToken, String spotInstAccountId) throws Exception;
  List<ElastiGroup> listAllElastiGroups(String spotInstToken, String spotInstAccountId, String elastiGroupNamePrefix)
      throws Exception;
  String getElastigroupJson(String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception;
  Optional<ElastiGroup> getElastiGroupByName(String spotInstToken, String spotInstAccountId, String elastiGroupName)
      throws Exception;
  Optional<ElastiGroup> getElastiGroupById(String spotInstToken, String spotInstAccountId, String elastiGroupId)
      throws Exception;
  ElastiGroup createElastiGroup(String spotInstToken, String spotInstAccountId, String jsonPayload) throws Exception;
  ElastiGroup createElastiGroup(String spotInstToken, String spotInstAccountId, String jsonPayload,
      LogCallback deployLogCallback) throws Exception;
  void updateElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, String jsonPayload)
      throws Exception;
  void updateElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, Object group)
      throws Exception;
  void updateElastiGroupCapacity(
      String spotInstToken, String spotInstAccountId, String elastiGroupId, ElastiGroup group) throws Exception;
  void deleteElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception;
  void scaleUpElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
      throws Exception;
  void scaleDownElastiGroup(String spotInstToken, String spotInstAccountId, String elastiGroupId, int adjustment)
      throws Exception;
  List<ElastiGroupInstanceHealth> listElastiGroupInstancesHealth(
      String spotInstToken, String spotInstAccountId, String elastiGroupId) throws Exception;
}
