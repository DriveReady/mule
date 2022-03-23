/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.classloader;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mule.maven.client.api.MavenClient;
import org.mule.maven.client.api.MavenClientProvider;
import org.mule.maven.client.api.model.BundleDependency;
import org.mule.maven.client.api.model.BundleDescriptor;
import org.mule.maven.client.api.model.MavenConfiguration;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;
import org.mule.runtime.module.artifact.api.classloader.MuleArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactDescriptor;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static org.apache.commons.io.FileUtils.toFile;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mule.maven.client.api.MavenClientProvider.discoverProvider;
import static org.mule.maven.client.api.model.MavenConfiguration.newMavenConfigurationBuilder;
import static org.mule.runtime.core.api.util.ClassUtils.getField;
import static org.mule.runtime.module.artifact.api.classloader.ChildFirstLookupStrategy.CHILD_FIRST;
import static org.mule.test.allure.AllureConstants.LeakPrevention.LEAK_PREVENTION;
import static org.mule.test.allure.AllureConstants.LeakPrevention.LeakPreventionMetaspace.METASPACE_LEAK_PREVENTION_ON_REDEPLOY;

@Feature(LEAK_PREVENTION)
@RunWith(Parameterized.class)
@Story(METASPACE_LEAK_PREVENTION_ON_REDEPLOY)
public class IBMMQResourceReleaserPropertiesTriggerTestCase {

  static final String KNOWN_DRIVER_CLASS_NAME = "com.ibm.mq.jms.MQConnectionFactory";
  private final static String IBM_MQ_TRACE_CLASS = "com.ibm.msg.client.commonservices.trace.Trace";
  private final static String DRIVER_GROUP_ID = "com.ibm.mq";
  private final static String DRIVER_ARTIFACT_ID = "com.ibm.mq.allclient";
  private final static String IBM_MQ_MBEAN_DOMAIN = "IBM MQ";

  String driverVersion;
  private ClassLoaderLookupPolicy testLookupPolicy;
  MuleArtifactClassLoader artifactClassLoader = null;
  BundleDependency dependency = null;

  // Parameterized
  public IBMMQResourceReleaserPropertiesTriggerTestCase(String driverVersion) {
    this.driverVersion = driverVersion;
    this.testLookupPolicy = new ClassLoaderLookupPolicy() {

      @Override
      public LookupStrategy getClassLookupStrategy(String className) {
        return CHILD_FIRST;
      }

      @Override
      public LookupStrategy getPackageLookupStrategy(String packageName) {
        return null;
      }

      @Override
      public ClassLoaderLookupPolicy extend(Map<String, LookupStrategy> lookupStrategies) {
        return null;
      }

      @Override
      public ClassLoaderLookupPolicy extend(Map<String, LookupStrategy> lookupStrategies, boolean overwrite) {
        return null;
      }
    };
  }


  @Parameterized.Parameters(name = "Testing Driver {0}")
  public static String[] data() throws NoSuchFieldException, IllegalAccessException {
    return new String[] {
        "9.2.5.0",
        "9.2.4.0",
        "9.2.3.0",
        "9.2.2.0",
        "9.1.1.0"
    };
  }

  @Before
  public void setup() throws Exception {
    URL settingsUrl = getClass().getClassLoader().getResource("custom-settings.xml");
    final MavenClientProvider mavenClientProvider = discoverProvider(this.getClass().getClassLoader());

    final Supplier<File> localMavenRepository =
        mavenClientProvider.getLocalRepositorySuppliers().environmentMavenRepositorySupplier();

    final MavenConfiguration.MavenConfigurationBuilder mavenConfigurationBuilder =
        newMavenConfigurationBuilder().globalSettingsLocation(toFile(settingsUrl));

    MavenClient mavenClient = mavenClientProvider
        .createMavenClient(mavenConfigurationBuilder.localMavenRepositoryLocation(localMavenRepository.get()).build());

    BundleDescriptor bundleDescriptor = new BundleDescriptor.Builder().setGroupId(DRIVER_GROUP_ID)
        .setArtifactId(DRIVER_ARTIFACT_ID).setVersion(driverVersion).build();

    dependency = mavenClient.resolveBundleDescriptor(bundleDescriptor);

    artifactClassLoader = new MuleArtifactClassLoader("test", mock(ArtifactDescriptor.class),
                                                      new URL[] {dependency.getBundleUri().toURL()},
                                                      currentThread().getContextClassLoader(), testLookupPolicy);


  }

  @Test
  @Description("When redeploying an application which contains the IBM MQ Driver, the proper cleanup should be performed " +
      "on redeployment but, if the property avoid.ibm.mq.cleanup.mbeans=true, the mbeans clean should be skiped.")
  public void releaserMBeansPropertyTrueTriggerTest() throws Exception {
    Properties props = System.getProperties();
    props.setProperty("avoid.ibm.mq.cleanup.mbeans", "true");
    Field shouldReleaseIbmMQResourcesField = getField(MuleArtifactClassLoader.class, "shouldReleaseIbmMQResources", false);
    shouldReleaseIbmMQResourcesField.setAccessible(true);
    assertThat(shouldReleaseIbmMQResourcesField.get(artifactClassLoader), is(false));
    // Force to load a Driver class so the resource releaser is flagged to run on dispose
    Class<?> connectionFactoryClass = Class.forName(KNOWN_DRIVER_CLASS_NAME, true, artifactClassLoader);
    Object connectionFactory = connectionFactoryClass.newInstance();
    artifactClassLoader.dispose();
    assertThat(countMBeans(artifactClassLoader), is(2));
    props.setProperty("avoid.ibm.mq.cleanup.mbeans", "false");
  }

  private int countMBeans(MuleArtifactClassLoader artifactClassLoader) throws MalformedObjectNameException {
    MBeanServer mBeanServer = getPlatformMBeanServer();
    final Hashtable<String, String> keys = new Hashtable<>();
    keys.put("type", "CommonServices");
    keys.put("name", "*");
    return mBeanServer.queryMBeans(new ObjectName(IBM_MQ_MBEAN_DOMAIN, keys), null).size();
  }
}
