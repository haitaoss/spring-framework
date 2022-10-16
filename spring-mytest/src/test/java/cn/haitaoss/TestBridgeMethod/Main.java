package cn.haitaoss.TestBridgeMethod;

import java.lang.reflect.Method;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:37
 *
 */
public class Main {
    public static interface A<T> {
        void a(T t);
    }

    public static class B implements A<String> {

        @Override
        public void a(String s) {

        }
    }

    public static void main(String[] args) {
        for (Method declaredMethod : B.class.getMethods()) {
            System.out.println(declaredMethod.getName() + "--->bridge: " + declaredMethod.isBridge());
        }
    }
}
