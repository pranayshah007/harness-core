/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.reflection;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.packages.HarnessPackages;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import org.reflections.Reflections;
import org.reflections.serializers.JsonSerializer;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class HarnessReflections {
  public static final String CLASSPATH_METADATA_FILE_NAME = "classpath_metadata.json";
  private static final File CLASSPATH_METADATA_FILE = new File(CLASSPATH_METADATA_FILE_NAME);
  private static final AtomicReference<Reflections> reflections = new AtomicReference<>();

  public Reflections get() {
    if (reflections.get() == null) {
      if (doHarnessReflectionsFileExist()) {
        ConfigurationBuilder config = new ConfigurationBuilder();
        config.setSerializer(new JsonSerializer());

        reflections.set(new Reflections(config).collect(CLASSPATH_METADATA_FILE));
      } else {
        FilterBuilder filter = new FilterBuilder();
        filter.includePackage(HarnessPackages.IO_HARNESS);
        filter.includePackage(HarnessPackages.SOFTWARE_WINGS);

        ConfigurationBuilder builder = new ConfigurationBuilder()
                                           .useParallelExecutor()
                                           .addUrls(ClasspathHelper.forPackage(HarnessPackages.IO_HARNESS))
                                           .addUrls(ClasspathHelper.forPackage(HarnessPackages.SOFTWARE_WINGS))
                                           .filterInputsBy(filter);
        reflections.set(new Reflections(builder));
      }
    }

    return reflections.get();
  }

  private static boolean doHarnessReflectionsFileExist() {
    return Files.exists(CLASSPATH_METADATA_FILE.toPath());
  }
}
