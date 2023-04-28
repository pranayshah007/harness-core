package io.harness.ccm.msp.service.impl;

import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.AmountDetails;
import io.harness.ccm.msp.entities.ManagedAccountDetails;
import io.harness.ccm.msp.entities.ManagedAccountsOverview;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MarginDetailsServiceImpl implements MarginDetailsService {
  @Inject MarginDetailsDao marginDetailsDao;

  @Override
  public String save(MarginDetails marginDetails) {
    return marginDetailsDao.save(marginDetails);
  }

  @Override
  public String addManagedAccount(String mspAccountId, String managedAccountId, String managedAccountName) {
    return marginDetailsDao.save(MarginDetails.builder()
                                     .accountId(managedAccountId)
                                     .accountName(managedAccountName)
                                     .mspAccountId(mspAccountId)
                                     .build());
  }

  @Override
  public MarginDetails update(MarginDetails marginDetails) {
    return marginDetailsDao.update(marginDetails);
  }

  @Override
  public MarginDetails unsetMargins(String uuid, String accountId) {
    return marginDetailsDao.unsetMarginRules(uuid, accountId);
  }

  @Override
  public MarginDetails get(String uuid) {
    return marginDetailsDao.get(uuid);
  }

  @Override
  public List<MarginDetails> list(String mspAccountId) {
    return marginDetailsDao.list(mspAccountId);
  }

  @Override
  public List<ManagedAccountDetails> listManagedAccountDetails(String mspAccountId) {
    List<MarginDetails> marginDetailsList = marginDetailsDao.list(mspAccountId);
    List<ManagedAccountDetails> managedAccountDetails = new ArrayList<>();
    marginDetailsList.forEach(marginDetails
        -> managedAccountDetails.add(ManagedAccountDetails.builder()
                                         .accountId(marginDetails.getAccountId())
                                         .accountName(marginDetails.getAccountName())
                                         .build()));
    return managedAccountDetails;
  }

  @Override
  public ManagedAccountsOverview getTotalMarkupAndSpend(String mspAccountId) {
    List<MarginDetails> marginDetailsList = list(mspAccountId);
    return ManagedAccountsOverview.builder()
        .totalMarkupAmount(getTotalMarkupAmountDetails(marginDetailsList))
        .totalSpend(getTotalSpendDetails(marginDetailsList))
        .build();
  }

  private AmountDetails getTotalMarkupAmountDetails(List<MarginDetails> marginDetailsList) {
    return AmountDetails.builder()
        .currentMonth(marginDetailsList.stream()
                          .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                          .map(marginDetails -> marginDetails.getMarkupAmountDetails().getCurrentMonth())
                          .collect(Collectors.toList())
                          .stream()
                          .mapToDouble(Double::doubleValue)
                          .sum())
        .lastMonth(marginDetailsList.stream()
                       .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                       .map(marginDetails -> marginDetails.getMarkupAmountDetails().getLastMonth())
                       .collect(Collectors.toList())
                       .stream()
                       .mapToDouble(Double::doubleValue)
                       .sum())
        .currentQuarter(marginDetailsList.stream()
                            .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                            .map(marginDetails -> marginDetails.getMarkupAmountDetails().getCurrentQuarter())
                            .collect(Collectors.toList())
                            .stream()
                            .mapToDouble(Double::doubleValue)
                            .sum())
        .lastQuarter(marginDetailsList.stream()
                         .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                         .map(marginDetails -> marginDetails.getMarkupAmountDetails().getLastQuarter())
                         .collect(Collectors.toList())
                         .stream()
                         .mapToDouble(Double::doubleValue)
                         .sum())
        .build();
  }

  private AmountDetails getTotalSpendDetails(List<MarginDetails> marginDetailsList) {
    return AmountDetails.builder()
        .currentMonth(marginDetailsList.stream()
                          .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                          .map(marginDetails -> marginDetails.getTotalSpendDetails().getCurrentMonth())
                          .collect(Collectors.toList())
                          .stream()
                          .mapToDouble(Double::doubleValue)
                          .sum())
        .lastMonth(marginDetailsList.stream()
                       .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                       .map(marginDetails -> marginDetails.getTotalSpendDetails().getLastMonth())
                       .collect(Collectors.toList())
                       .stream()
                       .mapToDouble(Double::doubleValue)
                       .sum())
        .currentQuarter(marginDetailsList.stream()
                            .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                            .map(marginDetails -> marginDetails.getTotalSpendDetails().getCurrentQuarter())
                            .collect(Collectors.toList())
                            .stream()
                            .mapToDouble(Double::doubleValue)
                            .sum())
        .lastQuarter(marginDetailsList.stream()
                         .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                         .map(marginDetails -> marginDetails.getTotalSpendDetails().getLastQuarter())
                         .collect(Collectors.toList())
                         .stream()
                         .mapToDouble(Double::doubleValue)
                         .sum())
        .build();
  }
}
