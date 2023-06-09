package com.singularity.ee.service.limitAlerting;

import com.appdynamics.apm.appagent.api.IMetricAndEventReporter;
import com.singularity.ee.agent.appagent.kernel.config.AgentDisableManager;
import com.singularity.ee.agent.appagent.kernel.controller.metrics.MetricHandler;
import com.singularity.ee.agent.appagent.services.ThreadLocalManager;
import com.singularity.ee.agent.appagent.services.transactionmonitor.common.ReaperBasedCPMLimiterRegistry;
import com.singularity.ee.agent.appagent.services.transactionmonitor.common.ResetableCallsLimiter;
import com.singularity.ee.agent.appagent.services.transactionmonitor.nonboot.spi.IBTContext;
import com.singularity.ee.agent.commonservices.metricgeneration.MetricGenerationService;
import com.singularity.ee.agent.commonservices.metricgeneration.MetricReporter;
import com.singularity.ee.agent.util.ThreadMapContainer;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.constants.AgentType;
import com.singularity.ee.util.javaspecific.collections.ADConcurrentHashMap;
import com.singularity.ee.util.system.SystemUtilsTranslateable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class ReflectionHelper {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ReflectionHelper");

    public static void setMaxEvents( Object eventServiceObject, int newMaxEvents ) {
        try {
            modifyPrivateFinalInt(eventServiceObject, "maxEventSize", newMaxEvents);
        } catch (Exception e) {
            logger.warn(String.format("Can not set the maxEventSize to %d, Exception: %s",newMaxEvents,e.getMessage()),e);
        }
    }

    public static int getMaxEvents( Object eventServiceObject ) {
        try {
            return getPrivateFinalInt(eventServiceObject, "maxEventSize");
        } catch (Exception e) {
            logger.warn(String.format("Can not get the maxEventSize from object sending from system property, Exception: %s",e.getMessage()),e);
            return SystemUtilsTranslateable.getIntProperty("appdynamics.agent.maxEvents", 100);
        }
    }

    public static void modifyPrivateFinalInt(Object object, String fieldName, int newValue) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(object, "com.singularity.ee.agent.commonservices.eventgeneration");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }

        // Update the field value
        field.setInt(object, newValue);
    }

    private static void removeFinalModifierJava8(Field field) throws NoSuchFieldException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private static void removeFinalModifierJava9Plus(Field field) throws NoSuchFieldException, IllegalAccessException {
        field.getClass()
                .getDeclaredField("modifiers")
                .setAccessible(true);
        field.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private static void updateModuleAccessJava9Plus(Object myObject, String addOpensWhat ) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        Class<?> moduleClass = Class.forName("java.lang.Module");
        Method getModuleMethod = Class.class.getMethod("getModule");
        Object declaringModule = getModuleMethod.invoke(myObject.getClass());
        Object currentModule = getModuleMethod.invoke(ReflectionHelper.class);

        Method addOpensMethod = currentModule.getClass().getMethod("addOpens", String.class, moduleClass);
        addOpensMethod.invoke(currentModule, addOpensWhat, declaringModule);

        /*
        Class<?> reflectionHelperClass = ReflectionHelper.class;
        Object declaringModule = declaringClass.getClassLoader()
                .loadClass("java.lang.Module")
                .getMethod("getModule")
                .invoke(declaringClass);
        Object reflectionExampleModule = reflectionHelperClass.getClassLoader()
                .loadClass("java.lang.Module")
                .getMethod("getModule")
                .invoke(reflectionHelperClass);

        Method addOpensMethod = reflectionExampleModule.getClass().getMethod("addOpens", String.class, reflectionExampleModule.getClass());
        addOpensMethod.invoke(reflectionExampleModule, addOpensWhat, declaringModule);

         */
    }

    private static boolean isJava9OrAbove() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = 8;
        if( javaVersion.contains(".") ) {
            majorVersion = Integer.parseInt(javaVersion.split("\\.")[1]);
        } else {
            majorVersion = Integer.parseInt(javaVersion);
        }
        return majorVersion > 8;
    }

    public static int getPrivateFinalInt(Object object, String fieldName) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException {
        Field field = object.getClass().getDeclaredField(fieldName);
        if ( isJava9OrAbove() )
            updateModuleAccessJava9Plus(object, "com.singularity.ee.agent.commonservices.eventgeneration");
        field.setAccessible(true);
        // Retrieve the field value
        return field.getInt(object);
    }

    public static void addReportMetric( Object metricAggregator, long value ) {

        Method reportMethod = null;
        try {
            reportMethod = metricAggregator.getClass().getDeclaredMethod("_report", long.class);
            if (isJava9OrAbove()) {
                updateModuleAccessJava9Plus( metricAggregator, "com.singularity.ee.agent.commonservices.metricgeneration.aggregation");
            }
            reportMethod.setAccessible(true);
            reportMethod.invoke(metricAggregator, value);
        } catch (Exception e) {
            logger.warn(String.format("Can not call %s._report(%d), Exception: %s",metricAggregator.getClass().getName(), value, e.getMessage()), e);
        }

    }

    public static MetricGenerationService getMetricGenerationService( Object metricReporter ) throws IllegalAccessException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException {
        Field field = metricReporter.getClass().getDeclaredField("mgs");
        if ( isJava9OrAbove() )
            updateModuleAccessJava9Plus(metricReporter, "com.singularity.ee.agent.commonservices.metricgeneration");
        field.setAccessible(true);
        return (MetricGenerationService) field.get(metricReporter);
    }

    public static AgentType getAgentType( Object metricGenerationService ) throws NoSuchFieldException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Field field = metricGenerationService.getClass().getDeclaredField("agentType");
        if ( isJava9OrAbove() )
            updateModuleAccessJava9Plus(metricGenerationService, "com.singularity.ee.agent.commonservices.metricgeneration");
        field.setAccessible(true);
        return (AgentType) field.get(metricGenerationService);
    }

    public static ADConcurrentHashMap<String, ResetableCallsLimiter> getCpmLimitersByIdentifierMap(ReaperBasedCPMLimiterRegistry reaperBasedCPMLimiterRegistry) throws Exception{
        Field field = reaperBasedCPMLimiterRegistry.getClass().getDeclaredField("cpmLimitersByIdentifier");

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(reaperBasedCPMLimiterRegistry, "com.singularity.ee.agent.appagent.services.transactionmonitor.common");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }
        return (ADConcurrentHashMap<String, ResetableCallsLimiter>) field.get(reaperBasedCPMLimiterRegistry);
    }

    public static ThreadMapContainer<IBTContext> getBTContextMap(ThreadLocalManager threadLocalManager) throws Exception{
        Field field = threadLocalManager.getClass().getDeclaredField("btContextMap");

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(threadLocalManager, "com.singularity.ee.agent.appagent.services");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }
        return (ThreadMapContainer<IBTContext>) field.get(threadLocalManager);
    }

    public static Map<Object,Object> geIBTContexttMapObjects(ThreadMapContainer<IBTContext> threadMapContainer) throws Exception {
        Field field = threadMapContainer.getClass().getDeclaredField("map");

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(threadMapContainer, "com.singularity.ee.agent.appagent.services");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }
        return (Map<Object,Object>) field.get(threadMapContainer);
    }

    /*
    public static void setMetricReporter( Object metricGenerationService, String fieldName, StatMetricReporter statMetricReporter ) throws NoSuchFieldException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Field field = metricGenerationService.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(metricGenerationService, "com.singularity.ee.agent.commonservices.metricgeneration");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }

        // Update the field value
        field.set(metricGenerationService, (MetricReporter) statMetricReporter);
    }

    public static void setMetricHandler(IMetricAndEventReporter metricAndEventPublisher, String fieldName, StatMetricHandler statMetricHandler) throws NoSuchFieldException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Field field = metricAndEventPublisher.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(metricAndEventPublisher, "com.singularity.ee.agent.commonservices.metricgeneration");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }

        // Update the field value
        field.set(metricAndEventPublisher, (MetricHandler) statMetricHandler);
    }

    public static AgentDisableManager getAgentDisableManager( Object object, String fieldName ) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);

        // Check the Java version at runtime
        if (isJava9OrAbove()) {
            // Update the module to allow access to the field
            updateModuleAccessJava9Plus(object, "com.singularity.ee.agent.appagent.kernel.config.xml");

            // Remove the final modifier using Java 9+ specific logic
            removeFinalModifierJava9Plus(field);
        } else {
            // Remove the final modifier using Java 8 specific logic
            removeFinalModifierJava8(field);
        }
        return (AgentDisableManager) field.get(object);
    }

     */
}
