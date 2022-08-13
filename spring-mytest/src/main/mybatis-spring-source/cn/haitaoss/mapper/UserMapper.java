package cn.haitaoss.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:52
 *
 */
public interface UserMapper {
    @Select("SELECT 'user' ")
    String getUsername();
}
