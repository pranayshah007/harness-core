/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.marketplace.gcp.procurement;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.handler.impl.segment.SegmentHandler.Keys;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.exception.GcpMarketplaceException;
import io.harness.marketplace.gcp.procurement.pubsub.ProcurementPubsubMessage;
import io.harness.marketplace.gcp.procurement.pubsub.ProcurementPubsubMessage.AccountMessage;

import software.wings.beans.account.AccountStatus;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer.GCPMarketplaceCustomerKeys;
import software.wings.beans.marketplace.gcp.GCPMarketplaceProduct;
import software.wings.beans.marketplace.gcp.GCPMarketplaceProduct.GCPMarketplaceProductBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloudcommerceprocurement.v1.model.Account;
import com.google.cloudcommerceprocurement.v1.model.Entitlement;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Slf4j
@Singleton
public class GcpMarketplaceMessageReceiver implements MessageReceiver {
  private static final String PRODUCT_NAME_FIELD = "productExternalName";
  private static String SYSTEM = "system";
  private static String GCP_ACCOUNT_APPROVAL_REQUEST_RECEIVED = "Gcp Account Approval Request received";
  private static String GCP_ACCOUNT_APPROVAL_REQUEST_APPROVED = "Gcp Account approved";
  private static String GCP_ACCOUNT_APPROVAL_REQUEST_REJECTED = "Gcp Account rejected";
  private static String GCP_ENTITLEMENT_APPROVAL_REQUEST_RECEIVED = "Gcp Entitlement Approval Request received";
  private static String GCP_ENTITLEMENT_APPROVAL_REQUEST_APPROVED = "Gcp Entitlement approved";
  private static String GCP_ENTITLEMENT_CHANGE_APPROVAL_REQUEST_APPROVED = "Gcp Entitlement change approved";
  private static String GCP_ENTITLEMENT_CANCELLATION_APPROVED = "Gcp Entitlement cancellation approved";
  /*
   This dummy account id has to be added to the GCPMarketplaceCustomer to support billing only flows.
   */
  private static String DUMMY_HARNESS_ACCOUNT_ID = "HarnessGcpDummyAccount";

  private final WingsPersistence wingsPersistence;
  private final AccountService accountService;
  private final GcpProcurementService gcpProcurementService;
  private final GcpProductsRegistry gcpProductsRegistry;
  private final SegmentHelper segmentHelper;
  private final Gson gson = new Gson();

  public GcpMarketplaceMessageReceiver(GcpProcurementService gcpProcurementService, WingsPersistence wingsPersistence,
      AccountService accountService, GcpProductsRegistry gcpProductsRegistry, SegmentHelper segmentHelper) {
    this.gcpProcurementService = gcpProcurementService;
    this.wingsPersistence = wingsPersistence;
    this.accountService = accountService;
    this.gcpProductsRegistry = gcpProductsRegistry;
    this.segmentHelper = segmentHelper;
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
    String data = message.getData().toStringUtf8();
    log.info("Received GCP marketplace message: {}", data);

    boolean ack;
    try {
      ack = processPubsubMessage(parseMessage(data));
    } catch (IOException e) {
      throw new GcpMarketplaceException(
          String.format("Failed to handle GCP marketplace message %s.", message.getData().toStringUtf8()), e);
    }

    if (ack) {
      consumer.ack();
      log.info("Acknowledged GCP marketplace message: {}", message.getData().toStringUtf8());
    } else {
      consumer.nack();
      log.info("Not acknowledged GCP marketplace message: {}", message.getData().toStringUtf8());
    }
  }

  private ProcurementPubsubMessage parseMessage(String data) {
    if (StringUtils.isNotBlank(data)) {
      return gson.fromJson(data, ProcurementPubsubMessage.class);
    }
    return null;
  }

  public boolean processPubsubMessage(ProcurementPubsubMessage message) throws IOException {
    if (message == null) {
      return true;
    }

    if (message.getAccount() != null && StringUtils.isNotBlank(message.getAccount().getId())) {
      return processAccount(message.getAccount(), message.getEventType());
    }

    if (message.getEntitlement() != null && StringUtils.isNotBlank(message.getEntitlement().getId())) {
      return processEntitlement(message.getEntitlement(), message.getEventType());
    }

    return false;
  }

  private HashMap<String, String> getPropertiesMap(String gcpAccountId) {
    return new HashMap<String, String>() {
      { put("gcpAccountId", gcpAccountId); }
    };
  }

  private Map<String, Boolean> getIntegrations() {
    return new HashMap<String, Boolean>() {
      { put(Keys.SALESFORCE, Boolean.TRUE); }
    };
  }

  public boolean processAccount(AccountMessage accountMessage, ProcurementEventType eventType) throws IOException {
    String gcpAccountId = accountMessage.getId();
    GCPMarketplaceCustomer gcpMarketplaceCustomer = getCustomer(gcpAccountId);
    Account account = gcpProcurementService.getAccount(gcpAccountId);
    if (account == null) {
      // GCP account doesn't exist anymore so we should delete Harness account mapped to this GCP account
      if (gcpMarketplaceCustomer != null) {
        log.info("Deleting Account provisioned through GCP Marketplace with accountId: {} and GCP AccountId: {}.",
            gcpMarketplaceCustomer.getHarnessAccountId(), gcpAccountId);
        if (!DUMMY_HARNESS_ACCOUNT_ID.equals(gcpMarketplaceCustomer.getHarnessAccountId())) {
          accountService.delete(gcpMarketplaceCustomer.getHarnessAccountId());
        }
        wingsPersistence.delete(GCPMarketplaceCustomer.class, gcpMarketplaceCustomer.getUuid());
        return true;
      } else {
        return false;
      }
    }
    return true;
  }

  public boolean processEntitlement(ProcurementPubsubMessage.EntitlementMessage entitlementMessage,
      ProcurementEventType eventType) throws IOException {
    String entitlementId = entitlementMessage.getId();

    Entitlement entitlement = gcpProcurementService.getEntitlement(entitlementId);

    if (entitlement == null) {
      log.warn(
          "Received '{}' event from GCP marketplace for entitlement that doesn't exists anymore, entitlementId: {}",
          eventType, entitlementId);
      return true;
    }

    String gcpAccountId = GcpProcurementService.getAccountId(entitlement.getAccount());
    GCPMarketplaceCustomer customer = getCustomer(gcpAccountId);

    Map<String, String> properties = getPropertiesMap(gcpAccountId);
    Map<String, Boolean> integrations = getIntegrations();

    if (customer == null) {
      wingsPersistence.save(GCPMarketplaceCustomer.builder()
                                .gcpAccountId(gcpAccountId)
                                .harnessAccountId(DUMMY_HARNESS_ACCOUNT_ID)
                                .build());
      gcpProcurementService.approveAccount(gcpAccountId);
      log.info("Approved Gcp Account: {} successfully from Entitlement flow", gcpAccountId);
      segmentHelper.reportTrackEvent(SYSTEM, GCP_ACCOUNT_APPROVAL_REQUEST_APPROVED, properties, integrations);
    }

    switch (eventType) {
      case ENTITLEMENT_CREATION_REQUESTED:
        log.info("Gcp Entitlement approval request received for gcp account: {}", gcpAccountId);
        if (entitlement.getState().equals("ENTITLEMENT_ACTIVATION_REQUESTED")) {
          segmentHelper.reportTrackEvent(SYSTEM, GCP_ENTITLEMENT_APPROVAL_REQUEST_RECEIVED, properties, integrations);
          gcpProcurementService.approveEntitlement(entitlementId);
          segmentHelper.reportTrackEvent(SYSTEM, GCP_ENTITLEMENT_APPROVAL_REQUEST_APPROVED, properties, integrations);
          return true;
        }
        break;
      case ENTITLEMENT_ACTIVE:
      case ENTITLEMENT_PLAN_CHANGED:
        if (entitlement.getState().equals("ENTITLEMENT_ACTIVE")) {
          updateCustomer(customer, entitlement);
          return true;
        }
        break;
      case ENTITLEMENT_PLAN_CHANGE_REQUESTED:
        if (entitlement.getState().equals("ENTITLEMENT_PENDING_PLAN_CHANGE_APPROVAL")) {
          gcpProcurementService.approveEntitlementPlanChange(entitlementId, entitlement.getNewPendingPlan());
          segmentHelper.reportTrackEvent(
              SYSTEM, GCP_ENTITLEMENT_CHANGE_APPROVAL_REQUEST_APPROVED, properties, integrations);
          return true;
        }
        break;
      case ENTITLEMENT_CANCELLED:
        if (entitlement.getState().equals("ENTITLEMENT_CANCELLED")) {
          cancelCustomer(customer, entitlement);
          segmentHelper.reportTrackEvent(SYSTEM, GCP_ENTITLEMENT_CANCELLATION_APPROVED, properties, integrations);
          return true;
        }
        break;
      case ENTITLEMENT_PLAN_CHANGE_CANCELLED:
      case ENTITLEMENT_PENDING_CANCELLATION:
      case ENTITLEMENT_CANCELLATION_REVERTED:
      case ENTITLEMENT_CANCELLING:
      case ENTITLEMENT_DELETED:
        segmentHelper.reportTrackEvent(SYSTEM, eventType.name(), properties, integrations);
        return true;
      default:
        throw new IllegalStateException("Unexpected value for GCP marketplace eventType: " + eventType);
    }

    return false;
  }

  private GCPMarketplaceCustomer getCustomer(String gcpAccountId) {
    return wingsPersistence.createQuery(GCPMarketplaceCustomer.class)
        .filter(GCPMarketplaceCustomerKeys.gcpAccountId, gcpAccountId)
        .get();
  }

  private void updateCustomer(GCPMarketplaceCustomer customer, Entitlement entitlement) {
    log.info("Updating customer (accountId: {}), with data from GCP entitlement object: {}",
        customer.getHarnessAccountId(), entitlement);
    GCPMarketplaceProductBuilder product = GCPMarketplaceProduct.builder();
    String productName = (String) entitlement.get(PRODUCT_NAME_FIELD);
    product.product(productName);
    product.plan(entitlement.getPlan());
    product.quoteId((String) entitlement.get("quoteExternalName"));
    product.startTime(Instant.parse(entitlement.getCreateTime()));
    String usageReportingId = entitlement.getUsageReportingId();
    if (StringUtils.isNotBlank(usageReportingId)) {
      product.usageReportingId(usageReportingId);
    }

    Optional<GCPMarketplaceProduct> gcpMarketplaceProduct = getProduct(customer, productName);
    if (gcpMarketplaceProduct.isPresent()) {
      getGcpProductHandler(productName).handlePlanChange(customer.getHarnessAccountId(), entitlement.getPlan());
      customer.getProducts().remove(gcpMarketplaceProduct.get());
    } else {
      getGcpProductHandler(productName).handleNewSubscription(customer.getHarnessAccountId(), entitlement.getPlan());
    }

    if (null == customer.getProducts()) {
      customer.setProducts(new ArrayList<>());
    }
    customer.getProducts().add(product.build());
    wingsPersistence.save(customer);
  }

  private GcpProductHandler getGcpProductHandler(String productName) {
    return gcpProductsRegistry.getGcpProductHandler(productName);
  }

  private Optional<GCPMarketplaceProduct> getProduct(GCPMarketplaceCustomer customer, String productName) {
    if (CollectionUtils.isEmpty(customer.getProducts())) {
      return Optional.empty();
    }
    for (GCPMarketplaceProduct subscribedProduct : customer.getProducts()) {
      if (subscribedProduct.getProduct().equals(productName)) {
        return Optional.of(subscribedProduct);
      }
    }
    return Optional.empty();
  }

  private void cancelCustomer(GCPMarketplaceCustomer customer, Entitlement entitlement) {
    String productName = (String) entitlement.get(PRODUCT_NAME_FIELD);
    getGcpProductHandler(productName).handleSubscriptionCancellation(customer.getHarnessAccountId());

    Optional<GCPMarketplaceProduct> productToCancel = getProduct(customer, productName);
    productToCancel.ifPresent(gcpMarketplaceProduct -> customer.getProducts().remove(gcpMarketplaceProduct));

    if (CollectionUtils.isEmpty(customer.getProducts())) {
      // deactivate Harness account if there is no active products left
      accountService.updateAccountStatus(customer.getHarnessAccountId(), AccountStatus.INACTIVE);
    }
    wingsPersistence.save(customer);
  }
}
