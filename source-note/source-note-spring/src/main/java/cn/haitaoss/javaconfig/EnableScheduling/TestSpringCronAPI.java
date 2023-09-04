package cn.haitaoss.javaconfig.EnableScheduling;

import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 09:29
 */
public class TestSpringCronAPI {

    public static void main(String[] args) {
        String expression = "*/1 5 12 * * ?";
        CronExpression parse = CronExpression.parse(expression);

        ZonedDateTime dateTime = ZonedDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault());
        ZonedDateTime next = parse.next(dateTime);
        // 下一次执行时间
        System.out.println(next.format(DateTimeFormatter.ISO_DATE_TIME));

    }
}
