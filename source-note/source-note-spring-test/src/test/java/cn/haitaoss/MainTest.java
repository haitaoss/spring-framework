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
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DefaultTestContextBootstrapper;


@ContextConfiguration(classes = {
		MainTest.class
})
//@ContextHierarchy(@ContextConfiguration)
//@TestExecutionListeners
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
	@Autowired
	ApplicationContext applicationContext;

	@Test
	void testLog() {
		log.info(applicationContext.getId());
		log.info(Arrays.toString(applicationContext.getBeanDefinitionNames()));
	}
}
