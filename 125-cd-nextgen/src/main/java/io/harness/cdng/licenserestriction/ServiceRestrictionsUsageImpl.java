package io.harness.cdng.licenserestriction;

import static io.harness.licensing.beans.modules.types.CDLicenseType.SERVICES;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.usage.beans.ServiceUsageDTO;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;

import com.google.inject.Inject;
import java.util.Date;

@OwnedBy(HarnessTeam.CDP)
public class ServiceRestrictionsUsageImpl implements RestrictionUsageInterface<RateLimitRestrictionMetadataDTO> {
  @Inject private LicenseUsageInterface licenseUsageInterface;

  @Override
  public long getCurrentValue(String accountIdentifier, RateLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    long ans = 0;
    ServiceUsageDTO serviceUsageDTO = (ServiceUsageDTO) licenseUsageInterface.getLicenseUsage(accountIdentifier,
        ModuleType.CD, new Date().getTime(), CDUsageRequestParams.builder().cdLicenseType(SERVICES).build());
    if (serviceUsageDTO != null && serviceUsageDTO.getServiceLicenses() != null) {
      ans = serviceUsageDTO.getServiceLicenses().getCount();
    }

    return ans;
  }
}