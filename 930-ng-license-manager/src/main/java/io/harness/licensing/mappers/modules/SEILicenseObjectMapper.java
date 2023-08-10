package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;
import io.harness.licensing.entities.modules.SEIModuleLicense;
import io.harness.licensing.mappers.LicenseObjectMapper;
import com.google.inject.Singleton;
@OwnedBy(HarnessTeam.SEI)
@Singleton
public class SEILicenseObjectMapper implements LicenseObjectMapper<SEIModuleLicense, SEIModuleLicenseDTO> {
    @Override
    public SEIModuleLicenseDTO toDTO(SEIModuleLicense moduleLicense) {
        return SEIModuleLicenseDTO.builder()
                .numberOfContributors(moduleLicense.getNumberOfContributors())
               /* .startTime(moduleLicense.getStartTime())
                .expiryTime(moduleLicense.getExpiryTime())
                .premiumSupport(moduleLicense.isPremiumSupport())
                .selfService(moduleLicense.isSelfService())*/
                .build();
    }

    @Override
    public SEIModuleLicense toEntity(SEIModuleLicenseDTO moduleLicenseDTO) {
        return SEIModuleLicense.builder()
                .numberOfContributors(moduleLicenseDTO.getNumberOfContributors())
               // .startTime(moduleLicenseDTO.getStartTime())
                //.expiryTime(moduleLicenseDTO.getExpiryTime())
               // .premiumSupport(moduleLicenseDTO.isPremiumSupport())
              //  .selfService(moduleLicenseDTO.isSelfService())
                .build();
    }
}
