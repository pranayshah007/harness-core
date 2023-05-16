/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.handlers;

import io.harness.ModuleType;
import io.harness.cd.CDLicenseType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.services.LicenseService;
import io.harness.repositories.SubscriptionDetailRepository;
import io.harness.subscription.entities.SubscriptionDetail;
import io.harness.subscription.enums.SubscriptionStatus;
import io.harness.subscription.helpers.StripeHelper;
import io.harness.subscription.params.StripeSubscriptionRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.PaymentIntent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InvoicePaymentSucceedHandler implements StripeEventHandler {
  private final LicenseService licenseService;
  private final SubscriptionDetailRepository subscriptionDetailRepository;
  private final StripeHelper stripeHelper;

  private static final String DEVELOPERS_TYPE = "DEVELOPERS";
  private static final String MAU_TYPE = "MAU";
  private static final String MAU_SUPPORT_TYPE = "MAU_SUPPORT";
  private static final String DEVELOPERS_SUPPORT_TYPE = "DEVELOPERS_SUPPORT";
  private static final String STRIPE_QUANTITY_KEY = "max";
  private static final String STRIPE_MODULE_TYPE_KEY = "module";

  @Inject
  public InvoicePaymentSucceedHandler(LicenseService licenseService,
      SubscriptionDetailRepository subscriptionDetailRepository, StripeHelper stripeHelper) {
    this.licenseService = licenseService;
    this.subscriptionDetailRepository = subscriptionDetailRepository;
    this.stripeHelper = stripeHelper;
  }

  @Override
  public void handleEvent(Event event) {
    Invoice invoice = StripeEventUtils.convertEvent(event, Invoice.class);

    syncLicense(invoice);
    updatePaymentIntentForFirstPayment(invoice);
  }

  private void syncLicense(Invoice invoice) {
    String id = invoice.getSubscription();
    SubscriptionDetail subscriptionDetail = subscriptionDetailRepository.findBySubscriptionId(id);
    String accountIdentifier = subscriptionDetail.getAccountIdentifier();

    Set<ModuleType> moduleTypes = getModuleTypes(invoice);

    moduleTypes.forEach((ModuleType moduleType) -> {
      log.info(
          "synchronizing invoice {} under subscription {}, going to update license under account {} and moduleType {}",
          invoice.getId(), id, accountIdentifier, moduleType);

      ModuleLicense existingLicense =
          licenseService.getCurrentLicense(subscriptionDetail.getAccountIdentifier(), moduleType);

      if (existingLicense == null) {
        ModuleLicense newLicense = generateLicense(invoice, moduleType, accountIdentifier);
        licenseService.createModuleLicense(newLicense);
      } else {
        log.info("Updating existing license {} via strip sync", existingLicense.getId());
        ModuleLicense updateLicense = generateLicense(invoice, moduleType, accountIdentifier);
        updateLicense.setId(existingLicense.getId());
        licenseService.updateModuleLicense(updateLicense);
      }
    });

    subscriptionDetail.setStatus(SubscriptionStatus.ACTIVE.toString());
    subscriptionDetailRepository.save(subscriptionDetail);
  }
  private String getModuleType(InvoiceLineItem invoiceLineItem) {
    return invoiceLineItem.getPrice().getMetadata().get(STRIPE_MODULE_TYPE_KEY);
  }

  private Set<ModuleType> getModuleTypes(Invoice invoice) {
    Set<ModuleType> moduleTypes = new HashSet<>();
    invoice.getLines().getData().stream().forEach((InvoiceLineItem invoiceLineItem) -> {
      String moduleType = getModuleType(invoiceLineItem);
      if (moduleType != null && !moduleTypes.contains(moduleType)) {
        moduleTypes.add(ModuleType.fromString(moduleType));
      }
    });
    return moduleTypes;
  }

  private void updatePaymentIntentForFirstPayment(Invoice invoice) {
    if (invoice.getBillingReason().equals("subscription_create")) {
      String subscriptionId = invoice.getSubscription();
      String paymentIntentId = invoice.getPaymentIntent();

      try {
        // Retrieve the payment intent used to pay the subscription
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        stripeHelper.updateSubscriptionDefaultPayment(StripeSubscriptionRequest.builder()
                                                          .subscriptionId(subscriptionId)
                                                          .paymentMethodId(paymentIntent.getPaymentMethod())
                                                          .build());
      } catch (StripeException e) {
        log.error("Unable to set default payment on subscription " + subscriptionId, e);
      }
    }
  }

  private ModuleLicense generateLicense(Invoice invoice, ModuleType moduleType, String accountIdentifier) {
    List<InvoiceLineItem> items = invoice.getLines().getData();

    switch (moduleType) {
      case CD:
        CDModuleLicense cdModuleLicense =
            CDModuleLicense.builder().workloads(0).cdLicenseType(CDLicenseType.SERVICES).build();
        syncCDLicense(cdModuleLicense, items);
        setBasicInfo(accountIdentifier, moduleType, cdModuleLicense);
        return cdModuleLicense;
      case CV:
      case SRM:
        throw new IllegalStateException("CV license is not supported yet");
      case CF:
        CFModuleLicense cfModuleLicense = CFModuleLicense.builder().build();
        syncCFLicense(cfModuleLicense, items);
        setBasicInfo(accountIdentifier, moduleType, cfModuleLicense);
        return cfModuleLicense;
      case CE:
        CEModuleLicense ceModuleLicense = CEModuleLicense.builder().spendLimit(0L).build();
        syncCELicense(ceModuleLicense, items);
        setBasicInfo(accountIdentifier, moduleType, ceModuleLicense);
        return ceModuleLicense;
      case CI:
        CIModuleLicense ciModuleLicense = CIModuleLicense.builder().numberOfCommitters(0).build();
        syncCILicense(ciModuleLicense, items);
        setBasicInfo(accountIdentifier, moduleType, ciModuleLicense);
        return ciModuleLicense;
      default:
        throw new IllegalStateException("Unsupported license type");
    }
  }

  private void syncCDLicense(CDModuleLicense cdModuleLicense, List<InvoiceLineItem> items) {
    items.forEach(item -> {
      if (isPaymentConsequence(item)) {
        if (isItem("SERVICES", item)) {
          cdModuleLicense.setWorkloads(cdModuleLicense.getWorkloads() + item.getQuantity().intValue());
          setLicenseProperty(item, cdModuleLicense);
        }
        if (isItem("SUPPORT", item)) {
          cdModuleLicense.setPremiumSupport(true);
        }
      }
    });
  }

  private void syncCFLicense(CFModuleLicense cfModuleLicense, List<InvoiceLineItem> items) {
    items.forEach(item -> {
      setLicenseProperty(item, cfModuleLicense);
      if (isPaymentConsequence(item)) {
        if (isItem(DEVELOPERS_TYPE, item)) {
          cfModuleLicense.setNumberOfUsers(item.getQuantity().intValue());
        }
        if (isItem(MAU_TYPE, item)) {
          cfModuleLicense.setNumberOfClientMAUs(getQuantity(item));
        }
        if (isItem(MAU_SUPPORT_TYPE, item) || isItem(DEVELOPERS_SUPPORT_TYPE, item)) {
          cfModuleLicense.setPremiumSupport(true);
        }
      }
    });
  }

  private void syncCILicense(CIModuleLicense ciModuleLicense, List<InvoiceLineItem> items) {
    items.forEach(item -> {
      if (isPaymentConsequence(item)) {
        if (isItem("DEVELOPERS", item)) {
          ciModuleLicense.setNumberOfCommitters(
              ciModuleLicense.getNumberOfCommitters() + item.getQuantity().intValue());
          setLicenseProperty(item, ciModuleLicense);
        }
        if (isItem(DEVELOPERS_SUPPORT_TYPE, item)) {
          ciModuleLicense.setPremiumSupport(true);
        }
      }
    });
  }

  private void syncCELicense(CEModuleLicense ceModuleLicense, List<InvoiceLineItem> items) {
    items.forEach(item -> {
      if (isPaymentConsequence(item)) {
        if (isItem("CLOUD_SPEND", item)) {
          ceModuleLicense.setSpendLimit(ceModuleLicense.getSpendLimit() + item.getQuantity());
          setLicenseProperty(item, ceModuleLicense);
        }
        if (isItem("SUPPORT", item)) {
          ceModuleLicense.setPremiumSupport(true);
        }
      }
    });
  }

  private boolean isItem(String itemName, InvoiceLineItem invoiceLineItem) {
    return itemName.equalsIgnoreCase(invoiceLineItem.getPrice().getMetadata().get("type"));
  }

  private boolean isEnterprise(InvoiceLineItem invoiceLineItem) {
    return "ENTERPRISE".equalsIgnoreCase(invoiceLineItem.getPrice().getMetadata().get("edition"));
  }

  private boolean isTeam(InvoiceLineItem invoiceLineItem) {
    return "TEAM".equalsIgnoreCase(invoiceLineItem.getPrice().getMetadata().get("edition"));
  }

  private boolean isPaymentConsequence(InvoiceLineItem invoiceLineItem) {
    return !invoiceLineItem.getProration() || (invoiceLineItem.getProration() && invoiceLineItem.getAmount() > 0);
  }

  private long getQuantity(InvoiceLineItem invoiceLineItem) {
    return Long.parseLong(invoiceLineItem.getPrice().getMetadata().get(STRIPE_QUANTITY_KEY));
  }

  private void setLicenseProperty(InvoiceLineItem item, ModuleLicense moduleLicense) {
    setEdition(item, moduleLicense);
    setExpiry(item, moduleLicense);
  }

  private void setEdition(InvoiceLineItem item, ModuleLicense moduleLicense) {
    if (isEnterprise(item)) {
      moduleLicense.setEdition(Edition.ENTERPRISE);
    } else if (isTeam(item)) {
      moduleLicense.setEdition(Edition.TEAM);
    }
  }

  private void setExpiry(InvoiceLineItem item, ModuleLicense moduleLicense) {
    moduleLicense.setExpiryTime(item.getPeriod().getEnd() * 1000);
  }

  private void setBasicInfo(String accountIdentifier, ModuleType moduleType, ModuleLicense moduleLicense) {
    moduleLicense.setAccountIdentifier(accountIdentifier);
    moduleLicense.setModuleType(moduleType);
    moduleLicense.setSelfService(true);
    moduleLicense.setLicenseType(LicenseType.PAID);
    moduleLicense.setStatus(LicenseStatus.ACTIVE);
  }
}
