package cn.haitaoss.javaconfig.SpringUtils;

import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 *
 */
public class Spring_API_PropertyPlaceholderHelper {

    public static void main(String[] args) {
        /**
         * 使用 PropertyPlaceholderHelper 解析占位符
         *
         * Spring默认的占位符的前缀是${ ，后缀是 }，默认值分隔符是 :
         * 比如使用@Value("${name:xx}") 表示获取属性的意思，没有找到name属性就使用:后面的值作为默认值
         * */
        PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("${",
                "}",
                ":",
                true);

        PropertyPlaceholderHelper.PlaceholderResolver placeholderResolver = new PropertyPlaceholderHelper.PlaceholderResolver() {
            Map<String, String> map = new HashMap();

            {
                map.put("name1", "real_name1");
                map.put("name2", "${name3}");
                map.put("name3", "real_name3");
            }

            @Override
            public String resolvePlaceholder(String placeholderName) {
                return map.get(placeholderName);
                /**
                 *  方法的实现使用 {@link PropertySourcesPropertyResolver#getPropertyAsRawString(String)}
                 *  就能对接上Spring的存储环境变量的工具类了
                 * */
                // return context.getEnvironment().getProperty(placeholderName);
            }
        };
        String s = placeholderHelper.replacePlaceholders("${name1}--->${name2}--->${not_value:default_value}", placeholderResolver);
        System.out.println("s = " + s);
    }
}
