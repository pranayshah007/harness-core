/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers;

import io.harness.licensing.beans.modules.SMPDecLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.smp.license.models.*;

import java.util.UUID;

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

    public SMPLicense toSMPLicense(SMPDecLicenseDTO decLicenseDTO) {
        LicenseMeta licenseMeta = new LicenseMeta();
        licenseMeta.setLicenseVersion(Integer.parseInt(decLicenseDTO.getLicenseVersion()));
        licenseMeta.setLibraryVersion(LibraryVersion.V1);
        licenseMeta.setAccountDTO(AccountInfo.builder()
                .companyName(decLicenseDTO.getCompanyName())
                .identifier(decLicenseDTO.getAccountIdentifier())
                .name(decLicenseDTO.getAccountName())
                .build());
        return SMPLicense.builder()
                .licenseMeta(licenseMeta)
                .moduleLicenses(decLicenseDTO.getModuleLicenses())
                .build();
    }

}
