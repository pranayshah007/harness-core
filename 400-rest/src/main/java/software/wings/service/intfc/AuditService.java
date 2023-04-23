/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.RequestType;
import software.wings.audit.AuditHeaderYamlResponse;
import software.wings.audit.AuditRecord;
import software.wings.audit.EntityAuditRecord;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.service.intfc.entitycrud.EntityCrudOperationObserver;

import java.io.InputStream;
import java.util.List;
import org.omg.CORBA.INVALID_ACTIVITY;

/**
 * HttpAuditService.
 *
 * @author Rishi
 */
public interface AuditService extends EntityCrudOperationObserver {
  /**
   * Creates the.
   *
   * @param header the header
   * @return the audit header
   */
  AuditHeader create(AuditHeader header);

  /**
   * Create string.
   *
   * @param header      the header
   * @param requestType the request type
   * @param inputStream the input stream
   * @return the string
   */
  String create(AuditHeader header, RequestType requestType, InputStream inputStream);
  /**
   * Finalize.
   *
   * @param header  the header
   * @param payload the payload
   */
  void finalize(AuditHeader header, byte[] payload);

  /**
   * List.
   *
   * @param req the req
   * @return the page response
   */
  PageResponse<AuditHeader> list(PageRequest<AuditHeader> req);

  // ---- Following 3 methods are for AuditRecord collection.
  AuditRecord fetchMostRecentAuditRecord(String id);

  List<AuditRecord> fetchLimitedEntityAuditRecordsOlderThanGivenTime(String auditHeaderId, long timestamp, int limit);

  boolean deleteTempAuditRecords(List<String> ids);
  // ----

  void addEntityAuditRecordsToSet(List<EntityAuditRecord> entityAuditRecords, String accountId, String auditHeaderId);

  /**
   * Update user.
   *
   * @param header the header
   * @param user   the user
   */
  void updateUser(AuditHeader header, User user);

  /**
   * Read.
   *
   * @param appId         the app id
   * @param auditHeaderId the audit header id
   * @return the audit header
   */
  AuditHeader read(String appId, String auditHeaderId);

  /**
   * Deletes the old audit records
   * @param retentionMillis
   */
  void deleteAuditRecords(long retentionMillis);

  AuditHeaderYamlResponse fetchAuditEntityYamls(String headerId, String entityId, String accountId);

  <T> void registerAuditActions(String accountId, T oldEntity, T newEntity, Type type);

  PageResponse<AuditHeader> listUsingFilter(String accountId, String filter, String limit, String offset);

  String getAuditHeaderIdFromGlobalContext() throws INVALID_ACTIVITY;
}
