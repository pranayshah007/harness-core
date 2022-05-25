/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kryo;

import static io.harness.rule.OwnerRule.JOHANNES;

import static java.lang.Integer.parseInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.DelegateRegistrars;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.ObjectMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KryoRegistrationTest extends CategoryTest {
  private static final String KRYO_REGISTRATION_FILE = "kryo-registrations.txt";
  /**
   * Data types that are excluded from failing the test in case the same type is registered with the same id in two
   * different registrars. This is required as Kryo automatically registers these types for every registrar.
   */
  private static final HashSet<String> EXACT_DUPLICATE_EXCLUSIONS =
      new HashSet<>(Arrays.asList("int", "java.lang.Integer", "float", "java.lang.Float", "boolean",
          "java.lang.Boolean", "byte", "java.lang.Byte", "char", "java.lang.Character", "short", "java.lang.Short",
          "long", "java.lang.Long", "double", "java.lang.Double", "void", "java.lang.Void", "java.lang.String"));

  private static void log(String message) {
    System.out.println(message);
  }

  /**
   * Checks whether there is any class for which more than one id has been registered.
   * Note:
   *    This check won't find duplicates within the same class as kryo ignores it silently.
   */
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testForDuplicateClassesBetweenKryoRegistrars()
      throws InstantiationException, IllegalAccessException, NoSuchFieldException {
    Map<Class, ImmutablePair<String, Integer>> processedRegistrations = new HashMap<>();
    for (Class<? extends KryoRegistrar> registrarClass : getAllKryoRegistrars()) {
      log(String.format("checking registrar '%s'.", registrarClass.getName()));

      Kryo kryo = new Kryo();
      registrarClass.newInstance().register(kryo);
      ObjectMap<Class, Registration> registrations = extractClassRegistrationsFromKryo(kryo);

      for (ObjectMap.Entry<Class, Registration> registration : registrations.entries()) {
        if (processedRegistrations.containsKey(registration.key)) {
          ImmutablePair<String, Integer> processedRegistration = processedRegistrations.get(registration.key);

          // ignore exact duplicates if they are explicitly excluded (no need to register, already registered)
          if (registration.value.getId() == processedRegistration.right
              && EXACT_DUPLICATE_EXCLUSIONS.contains(registration.key.getName())) {
            continue;
          }

          fail(String.format("Found duplicate kryo registrations for class '%s':\n"
                  + ">%s\n"
                  + "   %d:%s\n"
                  + ">%s\n"
                  + "   %d:%s",
              registration.key.getName(), registrarClass.getSimpleName(), registration.value.getId(),
              registration.key.getName(), processedRegistration.left, processedRegistration.right,
              registration.key.getName()));
        }

        processedRegistrations.put(
            registration.key, new ImmutablePair<>(registrarClass.getSimpleName(), registration.value.getId()));
      }
    }
  }

  /**
   * Checks whether there is any id for which more than one class has been registered.
   * Note:
   *    This check won't find duplicates within the same class as kryo overwrites it silently.
   */
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testForDuplicateIdsBetweenKryoRegistrars()
      throws InstantiationException, IllegalAccessException, NoSuchFieldException {
    IntMap<ImmutablePair<String, String>> processedRegistrations = new IntMap<>();
    for (Class<? extends KryoRegistrar> registrarClass : getAllKryoRegistrars()) {
      log(String.format("checking registrar '%s'.", registrarClass.getName()));

      Kryo kryo = new Kryo();
      registrarClass.newInstance().register(kryo);
      IntMap<Registration> registrations = extractIdRegistrationsFromKryo(kryo);

      for (IntMap.Entry<Registration> registration : registrations.entries()) {
        if (processedRegistrations.containsKey(registration.key)) {
          ImmutablePair<String, String> processedRegistration = processedRegistrations.get(registration.key);

          // ignore exact duplicates if they are explicitly excluded (no need to register, already registered)
          if (registration.value.getType().getName() == processedRegistration.right
              && EXACT_DUPLICATE_EXCLUSIONS.contains(registration.value.getType().getName())) {
            continue;
          }

          fail(String.format("Found duplicate kryo registrations for id '%d':\n"
                  + ">%s\n"
                  + "   %d:%s\n"
                  + ">%s\n"
                  + "   %d:%s",
              registration.key, registrarClass.getSimpleName(), registration.key,
              registration.value.getType().getName(), processedRegistration.left, registration.key,
              processedRegistration.right));
        }

        processedRegistrations.put(registration.key,
            new ImmutablePair<>(registrarClass.getSimpleName(), registration.value.getType().getName()));
      }
    }
  }

  /**
   * Compares all registered classes found during runtime with the expected registrations defined in
   * kryo-registrations.txt. This test was added to ensure that when moving classes between packages, one does not
   * remove it from the source registrar and forget to add it to the destination registrar.
   */
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testKryoRegistrarForUnexpectedChanges()
      throws IOException, InstantiationException, IllegalAccessException, NoSuchFieldException {
    log("Get all classes registered with Kryo.");
    SortedMap<Integer, String> registeredClasses = getAllClassesRegisteredWithKryo();

    log(String.format("Load expected kryo registrations from resources file '%s'.", KRYO_REGISTRATION_FILE));
    Map<Integer, String> expectedRegisteredClasses = loadAllExpectedKryoRegistrations();

    // ensure all registered classes match the expected registrations
    for (Map.Entry<Integer, String> registeredEntry : registeredClasses.entrySet()) {
      assertThat(expectedRegisteredClasses.containsKey(registeredEntry.getKey()))
          .withFailMessage(
              "Found new registration with id '%d' that doesn't exist in list of expected registrations (class: '%s')",
              registeredEntry.getKey(), registeredEntry.getValue())
          .isEqualTo(true);
      assertThat(expectedRegisteredClasses.get(registeredEntry.getKey()))
          .withFailMessage("Found registration with id '%d' for class '%s' which doesn't match expected class '%s'.",
              registeredEntry.getKey(), registeredEntry.getValue(),
              expectedRegisteredClasses.get(registeredEntry.getKey()))
          .isEqualTo(registeredEntry.getValue());
    }

    // ensure all entries in registration file are registered
    for (Map.Entry<Integer, String> expectedRegisteredEntry : expectedRegisteredClasses.entrySet()) {
      assertThat(registeredClasses.containsKey(expectedRegisteredEntry.getKey()))
          .withFailMessage("Expected registration with id '%d' wasn't found (class: '%s').",
              expectedRegisteredEntry.getKey(), expectedRegisteredEntry.getValue())
          .isEqualTo(true);
    }
  }

  private static SortedMap<Integer, String> getAllClassesRegisteredWithKryo()
      throws InstantiationException, IllegalAccessException, NoSuchFieldException {
    Kryo kryo = new Kryo();
    SortedMap<Integer, String> registeredClasses = new TreeMap<>();

    log("Load all registrar classes.");

    Set<Class<? extends KryoRegistrar>> registrarClasses = getAllKryoRegistrars();
    for (Class<? extends KryoRegistrar> registrarClass : registrarClasses) {
      log(String.format("Loading registrar '%s'.", registrarClass.getName()));
      registrarClass.newInstance().register(kryo);
    }

    log("Extract all registered classes from kryo.");
    IntMap<Registration> idToRegistration = extractIdRegistrationsFromKryo(kryo);

    IntMap.Keys registeredKyroIds = idToRegistration.keys();
    while (registeredKyroIds.hasNext) {
      int registrationId = registeredKyroIds.next();
      Registration registration = kryo.getRegistration(registrationId);
      registeredClasses.put(registration.getId(), registration.getType().getName());
    }

    return registeredClasses;
  }

  private static IntMap<Registration> extractIdRegistrationsFromKryo(Kryo kryo)
      throws NoSuchFieldException, IllegalAccessException {
    // We need to access a private field from the kryo resolver in order to get all registrations.
    DefaultClassResolver classResolver = (DefaultClassResolver) kryo.getClassResolver();
    Field idToRegistrationField = classResolver.getClass().getDeclaredField("idToRegistration");
    idToRegistrationField.setAccessible(true);
    return (IntMap<Registration>) idToRegistrationField.get(classResolver);
  }

  private static ObjectMap<Class, Registration> extractClassRegistrationsFromKryo(Kryo kryo)
      throws NoSuchFieldException, IllegalAccessException {
    // We need to access a private field from the kryo resolver in order to get all registrations.
    DefaultClassResolver classResolver = (DefaultClassResolver) kryo.getClassResolver();
    Field classToRegistrationField = classResolver.getClass().getDeclaredField("classToRegistration");
    classToRegistrationField.setAccessible(true);
    return (ObjectMap<Class, Registration>) classToRegistrationField.get(classResolver);
  }

  private static Set<Class<? extends KryoRegistrar>> getAllKryoRegistrars() {
    return DelegateRegistrars.kryoRegistrars;
  }

  private static Map<Integer, String> loadAllExpectedKryoRegistrations() throws IOException {
    Map<Integer, String> expectedRegisteredClasses = new HashMap<>();
    List<String> lines =
        IOUtils.readLines(KryoRegistrationTest.class.getClassLoader().getResourceAsStream(KRYO_REGISTRATION_FILE),
            StandardCharsets.UTF_8);
    int previousId = -1;
    for (String line : lines) {
      int firstIdx = line.indexOf(':');
      int lastIdx = line.lastIndexOf(':');

      // ignore lines without entries or multiple colons
      if (firstIdx == -1 || firstIdx != lastIdx) {
        continue;
      }

      Integer id = parseInt(line.substring(0, line.indexOf(':')));
      String name = line.substring(line.indexOf(':') + 1).trim();

      assertThat(id)
          .withFailMessage("Found entry with id %d after entry with id %d."
                  + "Please ensure the entries in '%s' are in ascending order and there are no duplicates.",
              id, previousId, KRYO_REGISTRATION_FILE)
          .isGreaterThan(previousId);

      expectedRegisteredClasses.put(id, name);

      previousId = id;
    }

    return expectedRegisteredClasses;
  }
}
