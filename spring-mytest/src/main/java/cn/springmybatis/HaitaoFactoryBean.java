package cn.springmybatis;


import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:56
 *  FactoryBean 会创建出2个对象，名字分别是：haitaoFactoryBean、&haitaoFactoryBean
 *      - haitaoFactoryBean : 这个就是 createInstance 方法返回的对象
 *      - &haitaoFactoryBean ：这个就是 HaitaoFactoryBean 的实例
 */
public class HaitaoFactoryBean implements FactoryBean<Object> {

    private SqlSession sqlSession;

    private Class<?> mapperClass;

    public HaitaoFactoryBean(Class<?> mapperClass) {
        this.mapperClass = mapperClass;
    }

    @Autowired
    public void setSqlSession(SqlSessionFactory sqlSessionFactory) {
        sqlSessionFactory.getConfiguration()
                .addMapper(mapperClass);
        this.sqlSession = sqlSessionFactory.openSession();
    }

    @Override
    public Object getObject() throws Exception {
        return sqlSession.getMapper(mapperClass);
    }

    @Override
    public Class<?> getObjectType() {
        return mapperClass;
    }
}
