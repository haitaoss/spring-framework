package cn.haitaoss;

import cn.haitaoss.service.AService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-27 21:10
 */
public class Test {
    /*
    1、 Spring.是如何创建一个Bean对象的？


    UserService -> 推断构造方法 -> 普通对象 -> 依赖注入 -> 初始化前(@PostConstruct) -> 初始化(InitializingBean) -> 初始化后(AOP) -> 代理对象 -> 放入Map单例池(SingletonObject) -> bean对象


    依赖注入可以细化：
    1. 注入 BService 属性
    2. 从Map单例池中获取 BService 对象
    3. creatingSet 判断该属性是否正在创建中
        (比如：AService 中有一个属性 BService，而 BService 中有属性 AService；初始化 AService 的时候会对 BService 进行属性注入，此时 creatingSet 里面放的就是 AService)
    4. 出现了 循环依赖
    4. 从 二级缓存(earlySingletonObjects) 获取 BService 对象
    5. 从 三级缓存(singletonFactories) 获取 Lambda，执行 Lambda 得到 AOP 后的代理对象
    6. 将 代理对象 存入 二级缓存(earlySingletonObjects)

     */
    /*
     2、什么是单例池？作用是什么？


     什么是单例池：单例池就是一个 Map，将一个普通对象经过 Spring 一些列操作后存放到 Map中。
     作用：保证一个 Bean对象 只会被创建一次

     */
    /*
    3、Bean对象和普通对象之间的区别是什么？

    Bean对象 是由普通对象经过 Spring 的 属性注入、初始化前、初始化、初始化后 加工而成的对象

     */
    /*
    4、依赖注入是怎么实现的？

   通过反射实现的，Spring 通过推断构造方法创建了一个对象之后，通过反射获取 对象里面的字段，然后判断字段是否包含了特定的注解（@Autowired、@Resource、@Value）
   如果存在，就设置字段的值

   伪代码如下：
    Object obj = new Object();
    for (Field field : obj.getClass().getFields()) {
        if (field.isAnnotationPresent(Autowired.class)) {
            field.setAccessible(true);
            field.set(obj, null);
        }
    }

     */
    /*
     5、@PostConstruct注解是如何工作的？

     Spring 通过构造器创建完 普通对象之后，反射执行 标注了 @PostConstruct 的方法
     */
    // 6、Bean的初始化是如何工作的？
    /*
    7、Bean的初始化和实例化区别么？

    实例化只是通过调用构造器创建了对象，而初始化是Spring的普通对象的 加工环节比如有：属性注入、调用初始化方法、AOP
     */
    /*
     8、推断构造方法是什么意思？

     如果我们的类里面有多个构造器，Spring 会根据这个顺序调用构造器

     1. 标注了 @Autowired 注解的构造器
     2. 使用无参构造器

     */
    // 9、单例Bean和单例模式之间有什么关系
    /*
    10、什么是先 byType 再 byName?

    使用 @Autowired 进行属性注入的时候，先通过属性的类型从容器中获取 beanObject
    如果获取多个，就通过属性的名字从上面的 List<beanObject> 从获取一个
     */
    /*
    11、 Spring A0P底层是怎么工作的？

    首先 Spring 初始化一个普通对象，有一个环节是 初始化后，这个环节 Spring 会遍历 容器中所有 BeanPostProcessor 接口的实例，对 普通对象进行加工
    其中 有一个 AnnotationAwareAspectJAutoProxyCreator 这个实现了 BeanPostProcessor，其中里面的 before 方法就是 创建出一个代理对象，
    然后 Spring 就会把 代理对象存入到 单例池中，
    所以从 Spring容器中 获取的 beanObject 就是代理对象，执行的方法自然就是代理对象的方法，从而可以执行 AOP 需要增强的功能
     */
    // 12、 Spring事务底层是怎么工作的？
    /*
    13、同类方法调用为什么会事务失效？

    因为同类方法调用是 通过 this.method() 执行的，而不是通过 代理对象.method()
     */
    // 14、 @Configuration注解的作用是什么？
    /*
    15、 Spring.为什么要用三级缓存来解决循环依赖？


    三级缓存分别是：singletonObject、earlySingletonObjects、singletonFactories

    singletonObject：存储最终的 beanObject
    earlySingletonObjects：存储经过 AOP 处理过的 beanObject
    singletonFactories：存储 beanObject 对应的 AOP Lambda，通过执行 Lambda 就会执行 AOP 创建出最终的 beanObject

     */
    public static void main(String[] args) {


        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

		/*UserService userService = context.getBean(UserService.class);
		userService.test();*/


        // AppConfig appConfig = context.getBean(AppConfig.class);


        AService aService = (AService) context.getBean("AService");
        aService.test();


    }

}
