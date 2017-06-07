package com.immortal.util.objectutil.filed.compare;

/**
 * 比较类的两个字段是否相等,不管这个两个字段是否
 * 真的相等,只要本方法返回true,就认为相等
 *
 * @author Immortal
 * @version V1.0
 * @since 2017-05-08
 */
public interface FiledCompare {
    /**
     * 比较类的两个字段是否相等,不管这个两个字段是否
     * 真的相等,只要本方法返回true,就认为相等
     *
     * @param srcFiledName    要比较的一个字段
     * @param targetFiledName 要比较的另一个字段
     * @return 返回比较结果true or false
     */
    boolean compare(String srcFiledName, String targetFiledName);
}
