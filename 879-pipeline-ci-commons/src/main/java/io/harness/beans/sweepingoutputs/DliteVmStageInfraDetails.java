/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("dliteVmStageInfraDetails")
@JsonTypeName("dliteVmStageInfraDetails")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails")
public class DliteVmStageInfraDetails implements StageInfraDetails {
  String poolId;
  String workDir;
  Map<String, String> volToMountPathMap; // host volume name to mount path mapping
  String harnessImageConnectorRef;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @Builder.Default @NotNull private Type type = Type.DLITE_VM;
  //@Getter private InfraInfo infraInfo;
  @Getter private CIInitializeTaskParams.Type infraInfo;

  @Override
  public StageInfraDetails.Type getType() {
    return type;
  }
}
