package cn.haitaoss;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.context.support.DefaultTestContextBootstrapper;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;


@ContextConfiguration(
		classes = {
				MainTest.class
		},
		initializers = {},
		loader = AnnotationConfigContextLoader.class // 默认是 DelegatingSmartContextLoader
)
//@ContextHierarchy(@ContextConfiguration)
//@TestExecutionListeners
// @WebAppConfiguration
@BootstrapWith(DefaultTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@Slf4j
public class MainTest {
	/**
	 * 1. 声明了 `@ExtendWith(SpringExtension.class)`
	 * 2. 当执行 junit（执行 @Test 方法）时会回调 {@link SpringExtension#postProcessTestInstance(Object, ExtensionContext)}
	 */
	/**
	 * 加工 TestInstance
	 * {@link SpringExtension#postProcessTestInstance}
	 *
	 * 1. new TestContextManager(TestContextManager.class)
	 * {@link TestContextManager#TestContextManager(Class)}
	 * TestContextManager testContextManager = new TestContextManager(TestContextManager.class)
	 * <p>
	 * DefaultBootstrapContext
	 * 从 testClass 上找到 @BootstrapWith 注解，然后实例化 @BootstrapWith.value() 得到 TestContextBootstrapper
	 * <p>
	 * 2. 使用 TestContextManager 加工 TestInstance
	 * testContextManager.prepareTestInstance(testInstance);
	 */
	/**
	 * SpringExtension
	 * DefaultTestContextBootstrapper
	 * TestContextManager
	 * TestContextBootstrapper
	 * CacheAwareContextLoaderDelegate
	 * TestExecutionListener
	 * <p>
	 * {@link SpringExtension#postProcessTestInstance(Object, ExtensionContext)}
	 * {@link ServletTestExecutionListener#prepareTestInstance(TestContext)}
	 * {@link DependencyInjectionTestExecutionListener#prepareTestInstance(TestContext)}
	 */

	/*
	spring-test/src/main/resources/META-INF/spring.factories
	# Default TestExecutionListeners for the Spring TestContext Framework
#
org.springframework.test.context.TestExecutionListener = \
	org.springframework.test.context.web.ServletTestExecutionListener,\
	org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener,\
	org.springframework.test.context.event.ApplicationEventsTestExecutionListener,\
	org.springframework.test.context.support.DependencyInjectionTestExecutionListener,\
	org.springframework.test.context.support.DirtiesContextTestExecutionListener,\
	org.springframework.test.context.transaction.TransactionalTestExecutionListener,\
	org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener,\
	org.springframework.test.context.event.EventPublishingTestExecutionListener

# Default ContextCustomizerFactory implementations for the Spring TestContext Framework
#
org.springframework.test.context.ContextCustomizerFactory = \
	org.springframework.test.context.web.socket.MockServerContainerContextCustomizerFactory,\
	org.springframework.test.context.support.DynamicPropertiesContextCustomizerFactory

	* */
	@Autowired
	ApplicationContext applicationContext;

	@Test
	void testLog() {
		log.info(applicationContext.getId());
		log.info(Arrays.toString(applicationContext.getBeanDefinitionNames()));
	}
}
