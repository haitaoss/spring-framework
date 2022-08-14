package cn.haitaoss.service;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 15:29
 */
public class Order {
    private String desc;

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "Order{" + "desc='" + desc + '\'' + '}';
    }
}
