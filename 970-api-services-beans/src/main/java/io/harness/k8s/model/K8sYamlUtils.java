/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.yaml.BooleanPatchedRepresenter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.InputStream;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sYamlUtils {
  private final org.yaml.snakeyaml.Yaml mapper;
  public static int CUSTOM_LOAD_SIZE = 100 * 1024 * 1024;
  /**
   * Instantiates a new K8s yaml utils.
   */
  public K8sYamlUtils() {
    mapper = createYamlWithCustomConstructor();
  }
  /**
   * Read.
   *
   * @param <T>  the generic type
   * @param yaml the yaml
   * @param cls  the cls
   * @return the t
   * @throws JsonParseException   the json parse exception
   * @throws JsonMappingException the json mapping exception
   * @throws IOException          Signals that an I/O exception has occurred.
   */
  public <T> T read(String yaml, Class<T> cls) throws JsonParseException, JsonMappingException, IOException {
    return mapper.loadAs(yaml, cls);
  }

  public <T> T read(InputStream io, Class<T> cls) throws JsonParseException, JsonMappingException, IOException {
    return mapper.loadAs(io, cls);
  }

  public static <T> T yamlLoadAs(String spec, Class<T> clazz) {
    org.yaml.snakeyaml.Yaml yaml = createYamlWithCustomConstructor();
    return yaml.loadAs(spec, clazz);
  }

  public static org.yaml.snakeyaml.Yaml createYamlWithCustomConstructor() {
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setCodePointLimit(customLoadSize());
    BooleanPatchedRepresenter booleanPatchedRepresenter = new BooleanPatchedRepresenter();
    PropertyUtils propertyUtils = booleanPatchedRepresenter.getPropertyUtils();
    propertyUtils.setSkipMissingProperties(true);
    return new org.yaml.snakeyaml.Yaml(new Yaml.CustomConstructor(Object.class, loaderOptions),
        booleanPatchedRepresenter, initDumperOptions(booleanPatchedRepresenter), loaderOptions);
  }

  private static DumperOptions initDumperOptions(Representer representer) {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(representer.getDefaultFlowStyle());
    dumperOptions.setDefaultScalarStyle(representer.getDefaultScalarStyle());
    dumperOptions.setAllowReadOnlyProperties(representer.getPropertyUtils().isAllowReadOnlyProperties());
    dumperOptions.setTimeZone(representer.getTimeZone());
    return dumperOptions;
  }

  public static int customLoadSize() {
    return CUSTOM_LOAD_SIZE;
  }
}
