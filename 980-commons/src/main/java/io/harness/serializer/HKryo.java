/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.context.GlobalContext;
import io.harness.context.GlobalContextData;
import io.harness.context.MdcGlobalContextData;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExplanationException;
import io.harness.exception.FailureType;
import io.harness.exception.FunctorException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.reflection.CodeUtils;
import io.harness.rest.RestResponse;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.util.DefaultStreamFactory;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import de.javakaffee.kryoserializers.ArraysAsListSerializer;
import de.javakaffee.kryoserializers.GregorianCalendarSerializer;
import de.javakaffee.kryoserializers.JdkProxySerializer;
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import de.javakaffee.kryoserializers.cglib.CGLibProxySerializer;
import de.javakaffee.kryoserializers.guava.ArrayListMultimapSerializer;
import de.javakaffee.kryoserializers.guava.HashMultimapSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableListSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableMapSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableMultimapSerializer;
import de.javakaffee.kryoserializers.guava.ImmutableSetSerializer;
import de.javakaffee.kryoserializers.guava.LinkedHashMultimapSerializer;
import de.javakaffee.kryoserializers.guava.LinkedListMultimapSerializer;
import de.javakaffee.kryoserializers.guava.ReverseListSerializer;
import de.javakaffee.kryoserializers.guava.TreeMultimapSerializer;
import de.javakaffee.kryoserializers.guava.UnmodifiableNavigableSetSerializer;
import java.lang.reflect.InvocationHandler;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.objenesis.strategy.StdInstantiatorStrategy;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class HKryo extends Kryo {
  @Setter private String currentLocation;
  private final boolean skipHarnessClassOriginRegistrarCheck;

  public HKryo(ClassResolver classResolver) {
    this(classResolver, false);
  }

  /**
   * Creates a new HKryo instance.
   * @param classResolver the class resolver.
   * @param skipHarnessClassOriginRegistrarCheck if true, classes can be registered by registrars from other sources -
   *     only meant for UTs.
   */
  public HKryo(ClassResolver classResolver, boolean skipHarnessClassOriginRegistrarCheck) {
    super(classResolver, new MapReferenceResolver(), new DefaultStreamFactory());

    this.skipHarnessClassOriginRegistrarCheck = skipHarnessClassOriginRegistrarCheck;

    setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    setDefaultSerializer(CompatibleFieldSerializer.class);
    getFieldSerializerConfig().setCachedFieldNameStrategy(FieldSerializer.CachedFieldNameStrategy.EXTENDED);
    getFieldSerializerConfig().setCopyTransient(false);
    setRegistrationRequired(true);

    register(byte[].class, 10);
    register(char[].class, 11);
    register(short[].class, 12);
    register(int[].class, 13);
    register(long[].class, 14);
    register(float[].class, 15);
    register(double[].class, 16);
    register(boolean[].class, 17);
    register(String[].class, 18);
    register(Object[].class, 19);
    register(BigInteger.class, 20);
    register(BigDecimal.class, 21);
    register(Class.class, 22);
    register(Date.class, 23);
    // kryo.register(Enum.class, 24);
    register(EnumSet.class, 25);
    register(Currency.class, 26);
    register(StringBuffer.class, 27);
    register(StringBuilder.class, 28);
    register(Collections.EMPTY_LIST.getClass(), 29);
    register(Collections.EMPTY_MAP.getClass(), 30);
    register(Collections.EMPTY_SET.getClass(), 31);
    register(Collections.singletonList(null).getClass(), 32);
    register(Collections.singletonMap(null, null).getClass(), 33);
    register(Collections.singleton(null).getClass(), 34);
    register(ArrayList.class, 35);
    register(HashMap.class, 36);
    register(TreeSet.class, 37);
    register(Collection.class, 38);
    register(TreeMap.class, 39);
    register(Map.class, 40);
    register(TimeZone.class, 41);
    register(Calendar.class, 42);
    register(Locale.class, 43);
    register(Charset.class, 44);
    register(URL.class, 45);
    register(Optional.class, 46);
    register(asList("").getClass(), new ArraysAsListSerializer(), 47);
    register(java.util.Vector.class, 48);
    register(java.util.HashSet.class, 49);
    register(java.util.LinkedHashMap.class, 50);

    // Guava ArrayListMultimap, HashMultimap, LinkedHashMultimap, LinkedListMultimap, TreeMultimap
    register(ArrayListMultimap.class, new ArrayListMultimapSerializer(), 51);
    register(HashMultimap.class, new HashMultimapSerializer(), 52);
    register(LinkedHashMultimap.class, new LinkedHashMultimapSerializer(), 53);
    register(LinkedListMultimap.class, new LinkedListMultimapSerializer(), 54);
    register(TreeMultimap.class, new TreeMultimapSerializer(), 55);
    register(InterruptedException.class, 56);
    register(Sets.unmodifiableNavigableSet(new TreeSet<>()).getClass(), new UnmodifiableNavigableSetSerializer(), 57);
    register(Lists.reverse(Lists.newLinkedList()).getClass(), ReverseListSerializer.forReverseList(), 58);
    register(Lists.reverse(Lists.newArrayList()).getClass(), ReverseListSerializer.forRandomAccessReverseList(), 59);
    register(InvocationHandler.class, new JdkProxySerializer(), 61);
    register(GregorianCalendar.class, new GregorianCalendarSerializer(), 62);

    // register CGLibProxySerializer, works in combination with the appropriate action in
    // handleUnregisteredClass (see below)
    register(CGLibProxySerializer.CGLibProxyMarker.class, new CGLibProxySerializer(), 63);

    register(RuntimeException.class, 64);
    register(NullPointerException.class, 65);
    register(IllegalStateException.class, 66);
    register(java.io.IOException.class, 67);
    register(IllegalArgumentException.class, 68);
    register(java.net.SocketTimeoutException.class, 69);
    register(ExceptionInInitializerError.class, 70);
    register(java.net.UnknownHostException.class, 71);
    register(NoSuchMethodException.class, 72);
    register(NoClassDefFoundError.class, 73);
    register(javax.net.ssl.SSLHandshakeException.class, 74);
    register(java.util.concurrent.atomic.AtomicInteger.class, 75);
    register(java.net.ConnectException.class, 76);
    register(StringIndexOutOfBoundsException.class, 77);
    register(java.util.LinkedList.class, 78);
    register(ArrayListMultimap.class, new ArrayListMultimapSerializer(), 51);
    register(HashMultimap.class, new HashMultimapSerializer(), 52);
    register(Instant.class, 7235);

    // External Serializers
    UnmodifiableCollectionsSerializer.registerSerializers(this);
    SynchronizedCollectionsSerializer.registerSerializers(this);

    // guava ImmutableList, ImmutableSet, ImmutableMap, ImmutableMultimap, ReverseList, UnmodifiableNavigableSet
    ImmutableListSerializer.registerSerializers(this);
    ImmutableSetSerializer.registerSerializers(this);
    ImmutableMapSerializer.registerSerializers(this);
    ImmutableMultimapSerializer.registerSerializers(this);

    register(ErrorCode.class, 5233);
    register(Level.class, 5590);
    register(ResponseMessage.class, 5316);
    register(RestResponse.class, 5224);

    register(ExplanationException.class, 5324);
    register(FunctorException.class, 5589);
    register(HintException.class, 5325);
    register(InvalidArgumentsException.class, 5326);
    register(InvalidRequestException.class, 5327);
    register(UnauthorizedException.class, 5329);
    register(UnexpectedException.class, 5330);
    register(WingsException.ReportTarget.class, 5348);
    register(WingsException.class, 5174);

    register(JSONArray.class, 5583);
    register(JSONObject.class, 5584);

    register(FileData.class, 1201);
    register(GlobalContext.class, 1202);
    register(GlobalContextData.class, 1203);
    register(SocketException.class, 1204);
    register(FailureType.class, 1205);
    register(MdcGlobalContextData.class, 1206);
    try {
      register(Class.forName("java.util.HashMap$KeySet"), 1207);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private Registration check(Registration registration, int id) {
    if (CodeUtils.isHarnessClass(registration.getType()) && !this.skipHarnessClassOriginRegistrarCheck) {
      final String location = CodeUtils.location(registration.getType());
      if (currentLocation != null && !currentLocation.equals(location)) {
        throw new IllegalStateException(format("The class %s in %s is registered from registrar from module %s",
            registration.getType().getCanonicalName(), location, currentLocation));
      }
    }

    if (registration.getId() != id) {
      throw new IllegalStateException(
          format("The class %s was already registered with id %d, do not double register it with %d",
              registration.getType().getCanonicalName(), registration.getId(), id));
    }
    return registration;
  }

  @Override
  public Registration register(Class type) {
    throw new IllegalStateException("Do not use this method");
  }

  @Override
  public Registration register(Class type, int id) {
    if (getRegistration(id) != null) {
      throw new IllegalStateException("The id " + id + " is already used by " + getRegistration(id).getType());
    }
    return check(super.register(type, id), id);
  }

  private static IntMap<Class> allowed;
  static {
    allowed = new IntMap<>();
    allowed.put(0, int.class);
    allowed.put(1, String.class);
    allowed.put(2, float.class);
    allowed.put(3, boolean.class);
    allowed.put(4, byte.class);
    allowed.put(5, char.class);
    allowed.put(6, short.class);
    allowed.put(7, long.class);
    allowed.put(8, double.class);
    allowed.put(9, void.class);
  }

  private static IntMap<String> allowedNames;
  static {
    allowedNames = new IntMap<>();
    allowedNames.put(24, "java.util.Collections.UnmodifiableCollection");
    allowedNames.put(60, "java.util.Collections.UnmodifiableRandomAccessList");
    allowedNames.put(79, "java.util.Collections.UnmodifiableList");
    allowedNames.put(80, "java.util.Collections.UnmodifiableSet");
    allowedNames.put(81, "java.util.Collections.UnmodifiableSortedSet");
    allowedNames.put(82, "java.util.Collections.UnmodifiableMap");
    allowedNames.put(83, "java.util.Collections.UnmodifiableSortedMap");
    allowedNames.put(84, "java.util.Collections.SynchronizedCollection");
    allowedNames.put(85, "java.util.Collections.SynchronizedRandomAccessList");
    allowedNames.put(86, "java.util.Collections.SynchronizedList");
    allowedNames.put(87, "java.util.Collections.SynchronizedSet");
    allowedNames.put(88, "java.util.Collections.SynchronizedSortedSet");
    allowedNames.put(89, "java.util.Collections.SynchronizedMap");
    allowedNames.put(90, "java.util.Collections.SynchronizedSortedMap");
    allowedNames.put(91, "com.google.common.collect.ImmutableList");
    allowedNames.put(92, "com.google.common.collect.RegularImmutableList");
    allowedNames.put(93, "com.google.common.collect.SingletonImmutableList");
    allowedNames.put(94, "com.google.common.collect.Lists.StringAsImmutableList");
    allowedNames.put(95, "com.google.common.collect.RegularImmutableTable.Values");
    allowedNames.put(96, "com.google.common.collect.ImmutableSet");
    allowedNames.put(97, "com.google.common.collect.RegularImmutableSet");
    allowedNames.put(98, "com.google.common.collect.SingletonImmutableSet");
    allowedNames.put(97, "com.google.common.collect.RegularImmutableSet");
    allowedNames.put(99, "com.google.common.collect.ImmutableEnumSet");
    allowedNames.put(100, "com.google.common.collect.ImmutableMap");
    allowedNames.put(101, "com.google.common.collect.RegularImmutableMap");
    allowedNames.put(102, "com.google.common.collect.SingletonImmutableBiMap");
    allowedNames.put(103, "com.google.common.collect.ImmutableEnumMap");
    allowedNames.put(104, "com.google.common.collect.ImmutableMultimap");
    allowedNames.put(105, "com.google.common.collect.EmptyImmutableListMultimap");
    allowedNames.put(106, "com.google.common.collect.ImmutableListMultimap");
    allowedNames.put(107, "com.google.common.collect.EmptyImmutableSetMultimap");
    allowedNames.put(108, "com.google.common.collect.ImmutableSetMultimap");
  }

  @Override
  public Registration register(Class type, Serializer serializer) {
    final Registration registration = super.register(type, serializer);
    if (allowed.get(registration.getId()) == registration.getType()) {
      return registration;
    }
    if (registration.getType().getCanonicalName().equals(allowedNames.get(registration.getId()))) {
      return registration;
    }
    throw new IllegalStateException("Do not use this method");
  }

  @Override
  public Registration register(Class type, Serializer serializer, int id) {
    return check(super.register(type, serializer, id), id);
  }

  @Override
  public Registration register(Registration registration) {
    return check(super.register(registration), registration.getId());
  }
}
