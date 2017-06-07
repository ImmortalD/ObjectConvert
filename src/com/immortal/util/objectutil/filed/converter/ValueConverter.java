package com.immortal.util.objectutil.filed.converter;

/**
 * 把S对象转换到T对象
 *
 * @author Immortal
 * @version V1.0
 * @since 2017-05-08
 */
public interface ValueConverter<S, T> {
    /**
     * 转换值,把S对象转换到T对象
     *
     * @param s 要转换的对象
     * @return 返回转换后的对象
     */
    T convert(S s);
}
