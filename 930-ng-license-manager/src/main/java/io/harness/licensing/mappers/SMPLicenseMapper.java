package io.harness.licensing.mappers;

import io.harness.licensing.beans.modules.SMPDecLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.smp.license.models.LicenseMeta;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.models.SMPLicenseEnc;

public class SMPLicenseMapper {

    public SMPEncLicenseDTO toSMPEncLicenseDTO(SMPLicenseEnc smpLicenseEnc) {
        return SMPEncLicenseDTO.builder()
                .encryptedLicense(smpLicenseEnc.getEncryptedSMPLicense())
                .build();
    }

    public SMPLicenseEnc toSMPLicenseEnc(SMPEncLicenseDTO smpEncLicenseDTO) {
        return SMPLicenseEnc.builder()
                .encryptedSMPLicense(smpEncLicenseDTO.getEncryptedLicense())
                .build();
    }

}
