package cn.haitaoss.service;

import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:52
 */
@Component
// 当一个类 不满足 排除规则 且 满足 包含规则是，在判断这个类有没有 Conditional 注解，如果有使用这个规则再次判断，只有返回true 才能暂且算是一个bean（还需要判断是否是独立类）
// @Conditional(HaitaoCondition.class)
public class UserService {
    public void test() {
        System.out.println("userService");
    }

    // 虽然满足了种种规则，但是成员内部类不是独立类
    // org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider.isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition)
    @Component
    public class MemberService1 {
        public MemberService1() {
            System.out.println("初始化了");
        }
    }

    @Component
    public static class MemberService2 {

    }
}
