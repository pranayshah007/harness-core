package io.harness.delegate.beans.ldap;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Data;
import software.wings.beans.dto.LdapSettings;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.PL;

@Data
@Builder
@OwnedBy(PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class LdapSettingsWithEncryptedDataDetail {

    @NotNull @Valid
    LdapSettings ldapSettings;
    @NotNull EncryptedDataDetail encryptedDataDetail;
}
