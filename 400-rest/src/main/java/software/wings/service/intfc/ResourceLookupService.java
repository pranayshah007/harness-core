/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.audit.EntityAuditRecord;
import software.wings.beans.EntityType;
import software.wings.beans.ResourceLookup;

import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
public interface ResourceLookupService {
  /**
   * Creates the.
   *
   * @param header the header
   * @return the audit header
   */
  ResourceLookup create(ResourceLookup header);

  /**
   * List.
   *
   * @param req the req
   * @return the page response
   */
  PageResponse<ResourceLookup> list(PageRequest<ResourceLookup> req);

  void updateResourceName(ResourceLookup resourceLookup);

  void delete(ResourceLookup resourceLookup);

  List<String> listApplicationLevelResourceTypes();

  List<String> listAccountLevelResourceTypes();

  void deleteResourceLookupRecordIfNeeded(EntityAuditRecord record, String accountId);

  <T> void updateResourceLookupRecordIfNeeded(EntityAuditRecord record, String accountId, T newEntity, T oldEntity);

  void saveResourceLookupRecordIfNeeded(EntityAuditRecord record, String accountId);

  ResourceLookup getWithResourceId(String accountId, String resourceId);

  void updateResourceLookupRecordWithTags(
      String accountId, String entityId, String tagKey, String tagValue, boolean addTag);

  PageResponse<ResourceLookup> listResourceLookupRecordsWithTags(
      String accountId, String filter, String limit, String offset);

  <T> PageResponse<T> listWithTagFilters(PageRequest<T> request, String filter, EntityType entityType, boolean withTags,
      boolean hitSecondary, boolean addAccountFilterAutomatically);

  Map<String, ResourceLookup> getResourceLookupMapWithResourceIds(String accountId, Set<String> resourceIds);
}
