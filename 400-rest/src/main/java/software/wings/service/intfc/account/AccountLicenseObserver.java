package software.wings.service.intfc.account;

import software.wings.beans.Account;

public interface AccountLicenseObserver {
  boolean onLicenseChange(Account account);
  boolean onLicenseChange(String accountId);
}
