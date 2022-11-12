package cn.haitaoss.javaconfig.EnableCaching;

import lombok.Data;
import org.springframework.cache.annotation.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-07 20:18
 *
 */
@CacheConfig(cacheNames = "emp")
@Import(SpringCacheConfig.class)
public class EnableCachingTest {
    @Data
    public static class Emp {
        static int count;
        private String eId;
        private String eName;

        public void setEName(String eName) {
            this.eName = eName + count++;
        }
    }

   /* @Caching(cacheable = {@Cacheable(cacheNames = {"a", "b"}),
            @Cacheable(condition = "#root.methodName.startsWith('x')"),},
            evict = {@CacheEvict(beforeInvocation = true, allEntries = true,condition = "@enableCachingTest != null ")},
            put = {@CachePut})*/
    @CacheEvict(beforeInvocation = true, allEntries = true,condition = "@enableCachingTest != null ")
    public void test() {
        System.out.println("hello spring cache");
    }

    @Caching(put = {
            @CachePut(key = "#emp.EId", unless = "#result == null"),
            @CachePut(cacheNames = "emp", key = "#emp.EName", unless = "#result == null")
    })
    public Emp edit(Emp emp) {
        System.out.println("cache option ---> 更新缓存的值");
        return emp;
    }

    @CacheEvict(cacheNames = "emp", key = "#eId")
    public void delete(String eId) {
        System.out.println("cache option ---> 清空缓存");
    }

    @Cacheable(cacheNames = "emp", key = "#eId")
    public Emp get(String eId) {
        System.out.println("cache option ---> 查询数据库");
        Emp emp = new Emp();
        emp.setEId(eId);
        emp.setEName("haitao");
        return emp;
    }

    @Cacheable(cacheNames = "emp", key = "#eName")
    public Emp getEName(String eName) {
        return new Emp();
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableCachingTest.class);
        EnableCachingTest bean = context.getBean(EnableCachingTest.class);
        System.out.println("---->查询<----");
        System.out.println(bean.get("1"));
        System.out.println(bean.get("1"));

        Emp emp = new Emp();
        emp.setEId("1");
        emp.setEName("haitao");
        bean.edit(emp);

        System.out.println("---->查询<----");
        System.out.println(bean.get("1"));
        System.out.println(bean.get("1"));

        bean.delete("1");

        System.out.println("---->查询<----");
        System.out.println(bean.get("1"));
        System.out.println(bean.get("1"));

        System.out.println("======");
        bean.test();
    }

}

