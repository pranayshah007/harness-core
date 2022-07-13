/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.agent.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * A representation of a single agent mTLS endpoint.
 * Any endpoint added to this collection will be picked up by the agent gateway and configured automatically.
 *
 * Note:
 *    As of now, the design is to only have one endpoint per account (same endpoint for cg + ng).
 *    Keep accountId as a unique index to avoid racing conditions creating multiple endpoints per one account.
 *    In case the design gets changed later, the index will have to be dropped and recreated as non-unique.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldNameConstants(innerTypeName = "AgentMtlsEndpointKeys")
@Entity(value = "agentMtlsEndpoint", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.DEL)
public class AgentMtlsEndpoint implements PersistentEntity {
  /**
   * The unique identifier of the mTLS endpoint.
   */
  @Id @NotEmpty private String uuid;

  /**
   * The id of the owning account of the mTLS endpoint.
   */
  @FdUniqueIndex @NotEmpty private String accountId;

  /**
   * The FQDN that is used by an agent to connect to the mTLS endpoint.
   */
  @FdUniqueIndex @NotEmpty private String fqdn;

  /**
   * PEM encoded list of CA certificates used by the agent-gateway to verify client certificates.
   */
  @NotEmpty private String caCertificates;

  /**
   * The mode of the mTLS endpoint.
   */
  private AgentMtlsMode mode;
}
