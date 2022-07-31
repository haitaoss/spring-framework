
## 本地编译配置
1. 删掉代码检查规范
2. 添加仓库
3. 注释掉插件
4. 配置环境变量，我配置的是 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.16.jdk/Contents/Home`
5. 编译代码：`./gradlew build`

## 编写测试类测试
- 需要在idea中配置 gradle 的编译信息

![img.png](.README_imgs/img.png)

- 配置项目的编译器信息

![image-20220727215837291](.README_imgs/image-20220727215837291.png)

- 使用 idea 的 gradle 插件构建失败，可以使用命令行进行构建 `./gradlew build`


## Spring 整合 Mybatis

Mybatis 官网：https://mybatis.org/mybatis-3/getting-started.html