## 本地编译配置

1. 删掉代码检查规范
2. 添加仓库
3. 注释掉插件
4. 配置环境变量，我配置的是 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.16.jdk/Contents/Home`
5. 编译代码：`./gradlew build`

## 编写测试类测试

- 需要在idea中配置 gradle 的编译信息

![img.png](.README_imgs/img.png)

- 配置项目的编译器信息

![image-20220727215837291](.README_imgs/image-20220727215837291.png)

- 使用 idea 的 gradle 插件构建失败，可以使用命令行进行构建 `./gradlew build`

## Spring 整合 Mybatis

Mybatis 官网：https://mybatis.org/mybatis-3/getting-started.html

## Spring 扫描源码

### 源码分析

关键的几个方法：

- org.springframework.context.annotation.ConfigurationClassPostProcessor
    - org.springframework.context.annotation.ConfigurationClassPostProcessor.postProcessBeanDefinitionRegistry
        - org.springframework.context.annotation.ConfigurationClassParser.doProcessConfigurationClass
            - org.springframework.context.annotation.ClassPathBeanDefinitionScanner.doScan

核心步骤：

1. @ComponentScan 注解
2. 构造扫描器 ClassPathBeanDefinitionScanner
3. 根据 @ComponentScan 注解的属性配置扫描器
4. 扫描: 两种扫描方式
    - 扫描指定的类：工具目录配置了 `resources/META-INF/spring.components` 内容，就只会扫描里面定义的类。这是Spring扫描的优化机制
    - 扫描指定包下的所有类：获得扫描路径下所有的class文件（Resource对象）
5. 利用 ASM 技术读取class文件信息
6. 进行filter+条件注解的判断
7. 进行独立类、接口、抽样类 @Lookup的判断
8. 判断生成的BeanDefinition是否重复
9. 添加到Spring容器中

### [ASM 技术](https://asm.ow2.io/)

> 简单来说，ASM是一个操作Java字节码的类库。
> #### 第一个问题，ASM的操作对象是什么呢？
> ASM所操作的对象是字节码（ByteCode）数据
> #### 第二个问题，ASM是如何处理字节码（ByteCode）数据的？
> - ASM处理字节码（ByteCode）的方式是“拆分－修改－合并”
> - SM处理字节码（ByteCode）数据的思路是这样的：第一步，将.class文件拆分成多个部分；第二步，对某一个部分的信息进行修改；第三步，将多个部分重新组织成一个新的.class文件。
> - 说白了就是文件解析，并不会把这个 .class 文件加载到 JVM 中，就是文件的解析工具

为什么要使用 ASM ?

- 扫描到所有的 class 资源后，要判断该 class 是否作为一个 bean对象（比如标注了@Component 注解），
  如果我们通过反射来判断，那么在 Spring 启动阶段就会加载很多的bean，这势必会浪费系统资源和耗时（因为可能很多的工具类，是不需要Spring进行管理的）。

### Lookup 注解

这个注解标注在方法上，如果一个bean对象中的方法标注了 Lookup注解，那么会生成代理对象放入 bean容器中，
当标注了 Lookup 注解的方法时，会直接返回 Lookup 需要查找的bean，并不会执行方法，
所以说 Lookup 的value值要指定，否则没有意义。

使用说明：如果Lookup注解的value没有指定，那么会根据方法的返回值类型查找bean，如果指定了value 那就根据name查找bean

使用场景：A 依赖多例bean B，可以使用Lookup 注解在A中定义一个方法，该方法每次都会从容器中获取一个bean，因为B 是多例的，所以每次都是返回新的对象

```java

@Component
public class LookupService {
    @Autowired
    private Demo demo;

    @Lookup("demo")
    public Demo getDemo() {
        return null;
    }

    public void test1() {
        System.out.println(demo); // 单例的，不符合 Demo 这个bean的作用域
    }

    public void test2() {
        System.out.println(getDemo()); // 多例的
    }

}

@Component
@Scope("prototype")
class Demo {

}
```
## Conditional 注解的作用

```java
@ComponentScan(value = "cn.haitaoss.javaconfig.beanfactorypostprocessor", excludeFilters = {}, includeFilters = {})
```

执行完 excludeFilters 、includeFilters 会进行 @Conditional 条件的判断
org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider.isCandidateComponent(
org.springframework.core.type.classreading.MetadataReader)

```java

@FunctionalInterface
public interface Condition {
    /**
     *
     * @param context 这里面可以拿到，beanFactory 已经注册的beanDefinition 和 单例bean。所以Condition的判断只能是判断当前环境的
     * @param metadata
     * @return
     */
    boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
}
```

## 类型转换

### bean的生命周期中时候会使用 TypeConverter?

```java
/**
 * 创建bean -> 填充bean -> AutowiredAnnotationBeanPostProcessor#postProcessProperties -> resolveFieldValue -> getTypeConverter
 * @see AbstractAutowireCapableBeanFactory#doCreateBean(String, RootBeanDefinition, Object[])
 * @see AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)
 * @see AutowiredAnnotationBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)
 * @see AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#resolveFieldValue(Field, Object, String)
 * @see AbstractBeanFactory#getTypeConverter()
 * 注：类型转换的功能是通过 ConversionService 实现的
 * */
```

### 什么时候往 TypeConverter 中设置 conversionService? IOC 容器refresh环节

```java
/**
 * 会在这里从容器中获取一个name 是 conversionService 的bean 进行注入
 * @see org.springframework.context.support.AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)
 */
```

### 有哪些 ConversionService ？

其中 DefaultFormattingConversionService 功能强大： 类型转换 + 格式化
![img.png](img.png)

```java

@Component
public class TestConversionService {
    @Value("2022-08-11")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date = new Date();

    @Value("101.11")
    @NumberFormat(pattern = "#")
    private Integer money;

    @Value("code,play")
    private String[] jobs;

    @Override
    public String toString() {
        return "TestConversionService{" + "date=" + date + ", money=" + money + ", jobs=" + Arrays.toString(jobs) + '}';
    }
}
```

### 如何自定义类型装换？

```java

@Component
public class TestConversionService {
    @Value("haitaoss")
    private Person person;
}
```

```java
public class AppConfig {
    public AppConfig() {
        System.out.println("AppConfig 构造器");
    }

    @Bean
    public FormattingConversionService conversionService() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new String2PersonConverter());
        return conversionService;
    }
}

class String2PersonConverter implements ConditionalGenericConverter {
    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return String.class.isAssignableFrom(sourceType.getType())
               && Person.class.isAssignableFrom(targetType.getType());
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(String.class, Person.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        Person person = new Person();
        person.setName(source + "--->");
        return person;
    }
}
```

## bean 创建的生命周期

```java
/**
 * org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String, java.lang.Class, java.lang.Object...)
 * org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean(java.lang.String, java.lang.Class, java.lang.Object[], boolean)
 *   循环依赖核心代码：如果bean正在创建 -> 二级缓存获取 -> 三级缓存 对正在创建的bean 进行提前AOP 然后返回
 *      org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String)
 * org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 * org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBeanInstance(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, org.springframework.beans.BeanWrapper)
 *  org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#initializeBean(java.lang.String, java.lang.Object, org.springframework.beans.factory.support.RootBeanDefinition)
 * */
```

1. 所有的bean 都是通过 `getBean()` 来创建的
```java
/** 
 * 
 * @see AbstractApplicationContext#refresh()
 * @see AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)
 * @see DefaultListableBeanFactory#preInstantiateSingletons()
 *  - 遍历 beanNames 
 *  - 不是抽象的 && 是单例的 && 不是懒加载的
 *  - 是不是 FactoryBean :
 *      - 是：
 *          - 创建 FactoryBean 实例
 *          - 判断 是否立即初始化 FactoryBean#getObject 返回的bean
 *      - 不是： 
 *          @see org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String)
 *      注：创建bean 都是执行 getBean
 * */
```

2. `getBean()` 流程
```java
/**
 * @see AbstractBeanFactory#doGetBean(String, Class, Object[], boolean)
 * 根据beanName 从单例池获取bean
 *  是否存在
 *      存在：
 *          - beanName 是 &开头的 直接返回
 *          - 不是 &开头，获取的 bean 不是 FactoryBean 的实例 直接返回
 *          - 不是 &开头，获取的 bean 是 FactoryBean 的实例，那就是要返回 FactoryBean#getObject 返回的bean
 *              1. 从 factoryBeanObjectCache 中获取
 *              2. 缓存中不存在，执行 `FactoryBean#getObject` 存储缓存，然后返回
 *      不存在：
 *          - 当前beanFactory 中不存在 该beanName 的 definition，判断父 beanFactory 是否存在，存在就执行  org.springframework.beans.factory.BeanFactory#getBean(java.lang.String)
 *          - 获取该bean 所有的 dependsOn 的值，遍历执行 org.springframework.beans.factory.support.AbstractBeanFactory#getBean(java.lang.String)
 *          - 是单例bean
 *              @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String, org.springframework.beans.factory.ObjectFactory)
 *          - 是多例bean
 *              @see org.springframework.beans.factory.support.AbstractBeanFactory#createBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 *          - 其他bean（web应用的：request域、session域、application域）
 *              @see org.springframework.beans.factory.config.Scope#get(java.lang.String, org.springframework.beans.factory.ObjectFactory)
 * */
```

3. `DefaultSingletonBeanRegistry#getSingleton` 流程
```java
/**
 * DefaultSingletonBeanRegistry#getSingleton
 * 1. 从单例缓存池中获取不到 bean `this.singletonObjects.get(beanName);`
 *  - 单例池获取 -> 二级缓存获取 -> 三级缓存获取，执行缓存里的ObjectFactory 进行提前AOP
 *  - 提前AOP，【第一次】执行beanPostProcessor
 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 * 2. 标记当前bean 正在创建（用来解决循环依赖）`this.singletonsCurrentlyInCreation.add(beanName)`
 * 3. 创建bean 
 *  @see AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])
 * 4. 删除标记 `this.singletonsCurrentlyInCreation.remove(beanName)`
 * 5. 将bean 放入单例池
 *  @see DefaultSingletonBeanRegistry#addSingleton(String, Object)
 *  1. 加入到单例缓存池中
 *  2. 从三级缓存中移除
 *  3. 从二级缓存中移除
 * */
```

4. `AbstractAutowireCapableBeanFactory#createBean` 可以通过后置处理器，快速返回，不要执行bean创建的生命周期
```java
/**
 * @see AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])
 * 1. 【第二次】执行beanPostProcessor 可以实现 不执行后面 bean的构造器、属性注入、初始化流程（简而言之可以拦截bean的创建过程）
 *  - 返回值是否为null:
 *      为null：继续走 bean 的生命周期流程
 *      不为null：执行beanPostProcessor，执行bean的初始化后动作（AOP和注解事务是在 `postProcessAfterInitialization` 实现的）
 *          @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 * 2. 真正开始创建bean了，返回创建结果(这里才是bean的核心生命周期流程) 
 * @see org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Object[])
 * */
```

5. `AbstractAutowireCapableBeanFactory#doCreateBean` bean创建的生命周期
```java
/**
 * 1. 构造器初始化
 *  @see AbstractAutowireCapableBeanFactory#createBeanInstance(String, RootBeanDefinition, Object[])
 *  【第三次】执行beanPostProcessor，返回构造器
 *      @see SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors(Class, String)
 * 2. 【第四次】执行beanPostProcessor, 对 @Autowired @Value的注解的预解析
 *  @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition(RootBeanDefinition, Class, String)
 *  3. 满足（是单例bean && 允许循环引用 && 当前bean在真正创建bean集合中）往三级缓存中，记录 当前实例化的bean 的 提前AOP 操作
 * @see DefaultSingletonBeanRegistry#addSingletonFactory(String, ObjectFactory)
 *      - 参数 ObjectFactory
 *          @see AbstractAutowireCapableBeanFactory#getEarlyBeanReference(String, RootBeanDefinition, Object)
 *          - `getEarlyBeanReference` 里面其实是提前AOP的操作，说白了就是执行beanPostProcessor
 *              @see SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(Object, String)
 *  4. 属性注入
 * @see AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)
 *      1. 【第五次】执行beanPostProcessor, 停止属性注入
 *          @see InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation(Object, String)
 *      2. 对属性的值进行解析(会使用 TypeConverter )，存到 `PropertyValues`（注意这里还没有把值设置到bean对象中，只是存到 `PropertyValues`）
 *          @see AutowireCapableBeanFactory#resolveDependency(DependencyDescriptor, String, Set, TypeConverter)
 *      3. 【第六次】执行beanPostProcessor, 也是解析配置的属性值 记录在 `PropertyValues`
 *          @see InstantiationAwareBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)
 *      4. 【第七次】执行beanPostProcessor，也是解析配置的属性值 记录在 `PropertyValues`
 *          @see InstantiationAwareBeanPostProcessor#postProcessPropertyValues(PropertyValues, PropertyDescriptor[], Object, String)
 *      5. 如果上面解析的 PropertyValues 不为null，就把 `PropertyValues` 注入到 bean实例中，完成属性注入
 *          @see AbstractAutowireCapableBeanFactory#applyPropertyValues(String, BeanDefinition, BeanWrapper, PropertyValues)
 *  5. 初始化操作
 * @see AbstractAutowireCapableBeanFactory#initializeBean(String, Object, RootBeanDefinition)
 *      1. 完成对 XxxAware 接口的方法回调
 *          @see AbstractAutowireCapableBeanFactory#invokeAwareMethods(String, Object)
 *      2. 【第八次】执行beanPostProcessor, 比如 执行 @PostConstruct 标注的方法
 *          @see BeanPostProcessor#postProcessBeforeInitialization(Object, String)
 *      3. 执行初始化方法, 执行 org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
 *          @see AbstractAutowireCapableBeanFactory#invokeInitMethods(String, Object, RootBeanDefinition)
 *      4. 【第九次】执行beanPostProcessor, @EnableAspectJAutoProxy 、@EnableTransactionManagement 都是在这里完成代理对象的创建的
 *          @see BeanPostProcessor#postProcessAfterInitialization(Object, String)
 *  6. 销毁bean
 *      【第十次】执行beanPostProcessor
 *          @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor.requiresDestruction
 * */
```

## 如何实现bean创建的优先级
```java
/**
 * 如何实现bean创建的优先级：
 * 1. 实现 BeanFactoryPostProcessor（缺点：破坏了bean的生命周期）
 * 2. 重写 cn.haitaoss.javaconfig.ordercreatebean.MySmartInstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 *  这个是 BeanPostProcessor 中最先执行的回调方法，其他的BeanPostProcessor 也可以
 * 3. 重写 onRefresh 方法，通过发布并消费早期事件
 *      cn.haitaoss.javaconfig.applicationlistener.MyAnnotationConfigApplicationContext#onRefresh()
 * 4. 使用 @DependsOn("b")
 */
```

## 单例bean循环依赖导致的错误
```java
/**
 * spring 为了解决单例bean循环依赖问题，是才用 提请 AOP 的方式 来解决的，
 * 提前AOP是执行
 *      @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 * 然后在bean的生命周期的最后阶段会执行 org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * 也可能会返回代理对象。所以就可能出现 postProcessAfterInitialization 创建的代理对象和一开始提前AOP注入给其他bean的不一样
 * 所以只能报错了。
 *
 * 解决方式：
 * 1. 将 postProcessAfterInitialization 的代理逻辑放到 SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference 实现
 * 2. 使用 @Lazy 注解，不要在初始化的时间就从容器中获取bean，而是直接返回一个代理对象
 * 3. 使用 @Lookup
 */
```

## BeanFactoryPostProcessor
特点：只会在 IOC 生命周期中 执行一次。就是一个bean工厂执行一次

有两个接口可用：
- BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
  - 可用来修改和注册 BeanDefinition（JavaConfig
    就是通过 ConfigurationClassPostProcessor 来注册beanDefinition的）
- BeanFactoryPostProcessor#postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory) 
  - 此时 beanDefinition 都加载完了,可以在这里创建bean 来实现提前创建的目的(但是会破坏bean的生命周期)
  - 此时的参数就是beanFactory，可以往里面注册默认的单例bean等操作

## BeanPostProcessor
特点：每个bean的生命周期中都会执行一次。实例化前、构造器初始化、实例化后、属性填充前、初始化前、初始化后

```java
/** 
 * 核心的接口类型：
 *  1. SmartInstantiationAwareBeanPostProcessor
 *  2. MergedBeanDefinitionPostProcessor
 *  3. InstantiationAwareBeanPostProcessor
 *  4. BeanPostProcessor
 *  5. DestructionAwareBeanPostProcessor
 *  
 * 四处地方可以将对象加工厂代理对象：
 *  1. SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference
 *      - 在这里代理不会出现循环依赖问题
 *  2. InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
 *      - 在这里代理不会出现循环依赖问题
 *  3. BeanPostProcessor#postProcessBeforeInitialization
 *  4. BeanPostProcessor#postProcessAfterInitialization
 *  
 *  每个 BeanPostProcessor 回调方法的作用：
 *     提前AOP
 *     org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 *     
 *     实例化前。如果该方法返回值不为null，先执行初始化后，然后直接返回该对象。不在执行bean生命周期的构造器初始化、属性填充、初始化操作）
 *     org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 *     
 *     构造器初始化。如果返回值不为null，就会使用返回的构造器进行实例化
 *     org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors(java.lang.Class, java.lang.String)
 *     
 *     合并beanDefinition。这里可以拿到BeanDefinition
 *     org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition(org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Class, java.lang.String)
 *     
 *     实例化后。可以拿到构造器初始化后的对象
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation(java.lang.Object, java.lang.String)
 *     
 *     属性注入前。可以拿到解析注解或者xml中设置的属性值
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessProperties(org.springframework.beans.PropertyValues, java.lang.Object, java.lang.String)
 *     
 *     属性注入前。可以拿到解析注解或者xml中设置的属性值（过时方法）
 *     org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessPropertyValues(org.springframework.beans.PropertyValues, java.beans.PropertyDescriptor[], java.lang.Object, java.lang.String)
 *     
 *     初始化前。此时的bean已经完成了属性注入、Wrapper注入，还未执行初始化方法(org.springframework.beans.factory.InitializingBean#afterPropertiesSet())
 *     org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization(java.lang.Object, java.lang.String)
 *     
 *     初始化后。这是bean生命周期的最后一个环节了
 *     org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * 
 *     销毁bean的回调
 *     org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#requiresDestruction(java.lang.Object)
 * */
```