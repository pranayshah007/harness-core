/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.subscription.entities.SubscriptionDetail;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@HarnessRepo
@Transactional
public interface SubscriptionDetailRepository extends CrudRepository<SubscriptionDetail, String> {
  List<SubscriptionDetail> findByAccountIdentifier(String accountIdentifier);
  List<SubscriptionDetail> findByAccountIdentifierAndPaymentFrequency(
      String accountIdentifier, String paymentFrequency);
  SubscriptionDetail findBySubscriptionId(String subscriptionId);
  long deleteBySubscriptionId(String subscriptionId);
}