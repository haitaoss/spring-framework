package cn.haitaoss.spi;

import java.util.ServiceLoader;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-16 11:25
 *
 */
public class Test_SPI {
    public static void main(String[] args) {
        /**
         * https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html
         * */
        // 通过 SPI 机制，拿到接口的实现类
        ServiceLoader<IA> load = ServiceLoader.load(IA.class);
        load.forEach(System.out::println);
    }
}