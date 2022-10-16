package cn.haitaoss.testIsSynthetic;

public class Main {

    private static class Inner {
    }

    static void checkSynthetic(String name) {
        try {
            System.out.println(name + " : " + Class.forName(name).isSynthetic());
        } catch (ClassNotFoundException exc) {
            exc.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) throws Exception {
        new Inner(); // 编译器会生成 Main$1 这个class文件
        checkSynthetic("cn.haitaoss.testIsSynthetic.Main"); // false
        checkSynthetic("cn.haitaoss.testIsSynthetic.Main$Inner"); // false
        checkSynthetic("cn.haitaoss.testIsSynthetic.Main$1"); // true
        System.out.println(Inner.class.isSynthetic());// false

    }
}