/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateHeartbeatResponse;
import io.harness.beans.DelegateTaskEventsResponse;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.ChecksumType;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.logging.common.AccessTokenBean;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.packages.HarnessPackages;
import io.harness.reflection.ReflectionUtils;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.google.common.hash.Hashing;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.ProtocolMessageEnum;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import org.reflections.Reflections;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(HarnessTeam.CDP)
class MicroserviceInterfaceTool {
  static void log(String message) {
    System.out.println(message);
  }

  // There is some communication that happens neither over Kryo / Proto
  // We have a static Set of classes for the same for now.
  // Eventually the plan is to move this communication to Kryo also.
  private static Map<String, String> computeJsonHashes() throws Exception {
    Set<Class> jsonClasses = new HashSet<>(
        Arrays.asList(DelegateRegisterResponse.class, DelegateParams.class, DelegateConnectionHeartbeat.class,
            DelegateProfileParams.class, FileBucket.class, DelegateFile.class, ChecksumType.class,
            DelegateScripts.class, AccessTokenBean.class, DelegateTaskEvent.class, DelegateTaskAbortEvent.class,
            ConnectorHeartbeatDelegateResponse.class, ConnectorValidationResult.class, ConnectivityStatus.class,
            ErrorDetail.class, DelegateHeartbeatResponse.class, DelegateTaskEventsResponse.class));
    Map<String, String> jsonHashes = new HashMap<>();
    for (Class jsonClass : jsonClasses) {
      jsonHashes.put(jsonClass.getCanonicalName(), calculateStringHash(jsonClass));
    }
    return jsonHashes;
  }

  private static Map<String, String> computeProtoHashes(Set<String> protoDependencies) throws Exception {
    Set<Class> protoClasses = new HashSet<>();
    Reflections reflections =
        new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS, "io.serializer");
    protoClasses.addAll(reflections.getSubTypesOf(GeneratedMessageV3.class));
    protoClasses.addAll(reflections.getSubTypesOf(ProtocolMessageEnum.class));
    Map<String, String> classToHash = new HashMap<>();
    for (Class protoClass : protoClasses) {
      if (EmptyPredicate.isEmpty(protoDependencies) || protoDependencies.contains(protoClass.getCanonicalName())) {
        classToHash.put(protoClass.getCanonicalName(), calculateStringHash(protoClass));
      }
    }
    return classToHash;
  }

  static Map<String, String> computeKryoHashes(Set<String> kryoDependencies) throws Exception {
    Kryo kryo = new Kryo();
    log("Loading all implementers of Kryo Registrars");
    Set<Class<? extends KryoRegistrar>> registrars = getAllImplementingClasses();
    for (Class<? extends KryoRegistrar> registrar : registrars) {
      if (EmptyPredicate.isEmpty(kryoDependencies)
          || kryoDependencies.stream().anyMatch(dependency -> registrar.getSimpleName().equals(dependency))) {
        log(registrar.getSimpleName());
        registrar.newInstance().register(kryo);
      }
    }

    DefaultClassResolver classResolver = (DefaultClassResolver) kryo.getClassResolver();
    Field field = classResolver.getClass().getDeclaredField("classToRegistration");
    field.setAccessible(true);
    ObjectMap<Class, Registration> map = (ObjectMap<Class, Registration>) field.get(classResolver);

    ObjectMap.Keys<Class> keys = map.keys();
    Map<String, String> classToHash = new HashMap<>();
    while (keys.hasNext) {
      Class next = keys.next();
      String canonicalName = next.getCanonicalName();
      classToHash.put(canonicalName, calculateStringHash(next));
    }
    return classToHash;
  }

  private static void parseMicroserviceDependencies(
      Set<String> kryoDependencies, Set<String> protoDependencies, String[] args) throws Exception {
    for (String arg : args) {
      if (arg.startsWith("kryo-file")) {
        List<String> collect =
            Files.lines(Paths.get(arg.substring(10)), Charset.defaultCharset()).collect(Collectors.toList());
        kryoDependencies.addAll(collect.stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toList()));
      } else if (arg.startsWith("proto-file")) {
        List<String> collect =
            Files.lines(Paths.get(arg.substring(11)), Charset.defaultCharset()).collect(Collectors.toList());
        protoDependencies.addAll(collect.stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toList()));
      } else if (!arg.equals("ignore-json")) {
        log("Un recognized option: " + arg);
      }
    }
  }

  private static Map<String, String> computeBEInternalApis() throws Exception {
    Reflections reflections =
        new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS, "io.serializer");
    Set<Class<?>> resourceClasses = reflections.getTypesAnnotatedWith(Path.class);
    Map<String, String> classToHash = new HashMap<>();
    for (Class resourceClass : resourceClasses) {
      Method[] methods = resourceClass.getMethods();
      if (EmptyPredicate.isEmpty(methods)) {
        continue;
      }
      for (Method method : methods) {
        Annotation[] declaredAnnotations = method.getDeclaredAnnotations();
        if (EmptyPredicate.isEmpty(declaredAnnotations)) {
          continue;
        }
        for (Annotation declaredAnnotation : declaredAnnotations) {
          if (declaredAnnotation instanceof InternalApi) {
            /*
             * The return type information is tricky to find as the types are either
             * io.harness.rest.RestResponse<T> OR io.harness.ng.core.dto.ResponseDTO<T>
             * We need to find the type of Type Parameter.
             * This information is not directly available via reflection,
             * Hence we use the "toGenericString" and try to extract the information from there
             */
            Class<?> returnType = method.getReturnType();
            String methodString = method.toGenericString();
            String[] split = methodString.split(" ");
            String paramClassName = "";
            if (split.length == 3) {
              if (RestResponse.class.equals(returnType)) {
                // Example: public io.harness.rest.RestResponse<io.harness.beans.FeatureFlag>
                // software.wings.resources.FeatureFlagResource.getFeatureFlag(java.lang.String)
                paramClassName = split[1].substring(29, split[1].length() - 1);
              } else if (ResponseDTO.class.equals(returnType)) {
                // Example: public io.harness.ng.core.dto.ResponseDTO<io.harness.ng.core.dto.TokenDTO>
                // io.harness.ng.core.remote.TokenResource.getToken(java.lang.String)
                paramClassName = split[1].substring(35, split[1].length() - 1);
              }
            }
            if (EmptyPredicate.isNotEmpty(paramClassName)) {
              /*
               * The String could be a collection also. And in general could be pretty
               * complicated to parse. Hence we do not fail, if we are not able to parse.
               */
              if (paramClassName.startsWith("java.util.List<") && paramClassName.endsWith(">")) {
                // We return only Lists right now. So adding special handling for the List case.
                paramClassName = paramClassName.substring(15, paramClassName.length() - 1);
              }
              try {
                Class<?> paramClass = Class.forName(paramClassName);
                classToHash.put(paramClass.getCanonicalName(), calculateStringHash(paramClass));
              } catch (Exception ex) {
                log(ex.getMessage());
              }
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (EmptyPredicate.isNotEmpty(parameterTypes)) {
              for (Class parameterType : parameterTypes) {
                classToHash.put(parameterType.getCanonicalName(), calculateStringHash(parameterType));
              }
            }
          }
        }
      }
    }
    return classToHash;
  }

  public static void main(String[] args) {
    try {
      Set<String> kryoDependencies = new HashSet<>();
      Set<String> protoDependencies = new HashSet<>();
      parseMicroserviceDependencies(kryoDependencies, protoDependencies, args);
      Map<String, String> classToHash = computeKryoHashes(kryoDependencies);
      classToHash.putAll(computeProtoHashes(protoDependencies));
      if (!Arrays.asList(args).contains("ignore-json")) {
        classToHash.putAll(computeJsonHashes());
      }
      classToHash.putAll(computeBEInternalApis());
      List<String> sortedClasses = classToHash.keySet().stream().sorted(String::compareTo).collect(Collectors.toList());
      List<String> sortedHashes = sortedClasses.stream().map(classToHash::get).collect(Collectors.toList());
      String concatenatedHashes = HarnessStringUtils.join(",", sortedHashes);
      String codebaseHash = Hashing.sha256().hashString(concatenatedHashes, StandardCharsets.UTF_8).toString();
      String message = String.format("Codebase Hash:%s", codebaseHash);
      log(message);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  // This method computes the hash of a class by generating a String representation of the
  // fields of that class. We can continue improving it further.
  static String calculateStringHash(Class specialClass) throws Exception {
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(specialClass);
    List<String> collect = fields.stream()
                               .map(field -> field.getType().getCanonicalName() + ":" + field.getName())
                               .sorted(String::compareTo)
                               .collect(Collectors.toList());
    String result = HarnessStringUtils.join(";", collect);
    String hash = Hashing.sha256().hashString(result, StandardCharsets.UTF_8).toString();
    return hash;
  }

  private static Set<Class<? extends KryoRegistrar>> getAllImplementingClasses() {
    Set<Class<? extends KryoRegistrar>> retval = new HashSet<>();
    Reflections reflections =
        new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS, "io.serializer");
    retval.addAll(reflections.getSubTypesOf(KryoRegistrar.class));
    return retval;
  }
}
