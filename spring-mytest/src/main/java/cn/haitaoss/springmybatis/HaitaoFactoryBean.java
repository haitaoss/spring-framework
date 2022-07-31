package cn.haitaoss.springmybatis;


import cn.haitaoss.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:56
 *  FactoryBean 会创建出2个对象，名字分别是：haitaoFactoryBean、&haitaoFactoryBean
 *      - haitaoFactoryBean : 这个就是 createInstance 方法返回的对象
 *      - &haitaoFactoryBean ：这个就是 HaitaoFactoryBean 的实例
 */
@Component
public class HaitaoFactoryBean implements FactoryBean<Object> {

    private SqlSession sqlSession;

    @Autowired
    public void setSqlSession(SqlSessionFactory sqlSessionFactory) {
        sqlSessionFactory.getConfiguration().addMapper(UserMapper.class);
        this.sqlSession = sqlSessionFactory.openSession();
    }

    @Override
    public Object getObject() throws Exception {
       /* return Proxy.newProxyInstance(HaitaoFactoryBean.class.getClassLoader(), new Class<?>[]{UserMapper.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                System.out.println("method = " + method);
                return null;
            }
        });*/
        UserMapper mapper = sqlSession.getMapper(UserMapper.class);
        return mapper;
    }

    @Override
    public Class<?> getObjectType() {
        return UserMapper.class;
    }
}
