package cn.haitaoss.javaconfig.Lazy;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-25 19:29
 *
 */
@Component
@Data
public class Test {
    @Autowired
    @Lazy
    private X x;

    @Autowired
    private X x2;
}

@Component
@Lazy
class X {

}
