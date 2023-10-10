/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretusage;

import io.harness.beans.IdentifierRef;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import java.util.Set;

public interface SecretRuntimeUsageService {
  /**
   * Produce Secret runtime usage event
   *
   * @param secretIdentifierRef secret that is being referenced
   * @param referredByEntity entity that references the secret
   * @param usageDetail usage details. It consists of -
   *                    usageType - usage type which belongs to EntityUsageTypes
   *                    usageData - This is an abstract class that can be extended to capture usage specific data
   */
  void createSecretRuntimeUsage(
      IdentifierRef secretIdentifierRef, EntityDetailProtoDTO referredByEntity, EntityUsageDetailProto usageDetail);

  /**
   * Produce Secret runtime usage event
   *
   * @param accountIdentifier account id
   * @param secretDTOV2 secret that is being referenced
   * @param referredByEntity entity that references the secret
   * @param usageDetail usage details. It consists of -
   *                    usageType - usage type which belongs to EntityUsageTypes
   *                    usageData - This is an abstract class that can be extended to capture usage specific data
   */
  void createSecretRuntimeUsage(String accountIdentifier, SecretDTOV2 secretDTOV2,
      EntityDetailProtoDTO referredByEntity, EntityUsageDetailProto usageDetail);

  /**
   * Produce Secret runtime usage event
   *
   * @param secretReferences a set of secrets being referenced
   * @param usageDetail usage details. It consists of -
   *                    usageType - usage type which belongs to EntityUsageTypes
   *                    usageData - This is an abstract class that can be extended to capture usage specific data
   */
  void createSecretRuntimeUsage(Set<VisitedSecretReference> secretReferences, EntityUsageDetailProto usageDetail);
}
