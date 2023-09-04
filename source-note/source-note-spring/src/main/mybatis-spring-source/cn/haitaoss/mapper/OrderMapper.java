package cn.haitaoss.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 17:33
 *
 */
public interface OrderMapper {
    @Select("SELECT  'order' ")
    String getOrderName();
}
