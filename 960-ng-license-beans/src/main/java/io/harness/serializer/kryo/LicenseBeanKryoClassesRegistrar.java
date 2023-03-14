/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO;
import io.harness.licensing.beans.modules.IACMModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SRMModuleLicenseDTO;
import io.harness.licensing.beans.modules.STOModuleLicenseDTO;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class LicenseBeanKryoClassesRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(CDModuleLicenseDTO.class, 930001);
    kryo.register(CEModuleLicenseDTO.class, 930002);
    kryo.register(CFModuleLicenseDTO.class, 930003);
    kryo.register(CIModuleLicenseDTO.class, 930004);
    kryo.register(SRMModuleLicenseDTO.class, 930005);
    kryo.register(STOModuleLicenseDTO.class, 930011);
    kryo.register(ModuleLicenseDTO.class, 930006);
    kryo.register(Edition.class, 930007);
    kryo.register(LicenseType.class, 930008);
    kryo.register(LicenseStatus.class, 930009);
    kryo.register(ChaosModuleLicenseDTO.class, 9800002);
    kryo.register(IACMModuleLicenseDTO.class, 930012);
  }
}
