/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.github.reinert.jjschema.Attributes;
import lombok.Data;

@Data
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Label {
  @Attributes(title = "Name") private String name;
  @Attributes(title = "Value") private String value;

  public static final class Builder {
    private String name;
    private String value;

    private Builder() {}

    public static Builder aLabel() {
      return new Builder();
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withValue(String value) {
      this.value = value;
      return this;
    }

    public Label build() {
      Label label = new Label();
      label.setName(name);
      label.setValue(value);
      return label;
    }
  }
}
