/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("k8StageInfraDetails")
@JsonTypeName("k8StageInfraDetails")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.sweepingoutputs.K8StageInfraDetails")
public class K8StageInfraDetails implements StageInfraDetails {
  List<String> containerNames;
  Infrastructure infrastructure;
  String podName;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @Builder.Default @NotNull private Type type = Type.K8;

  @Override
  public StageInfraDetails.Type getType() {
    return type;
  }
}
