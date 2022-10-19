package software.wings.service.impl;

import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.service.intfc.account.AccountLicenseObserver;

public class ArtifactCollectionDisablementListener implements AccountLicenseObserver {
  @Override
  public boolean onLicenseChange(Account account) {
    if (account.getLicenseInfo() != null && account.getLicenseInfo().getAccountStatus().equals(AccountStatus.ACTIVE)) {
      disableArtifactCollection();
    }
    return false;
  }

  private void disableArtifactCollection() {
    // implement
  }
}
