package com.immortal.util.objectutil;

import com.immortal.util.objectutil.filed.compare.FiledCompare;
import com.immortal.util.objectutil.filed.compare.SimpleFiledCompare;
import com.immortal.util.objectutil.filed.converter.ValueConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本类提供把一个对象转换成另外一个对象,转换的原则是通过原来对象的get方法获<p>
 * 取原来对象属性的值,再通过目标对象的set方法把原来对象的值赋值给目标对象
 * <blockquote><pre>
 * class Src {
 * private String name;
 * private int age;
 * private int score;
 *  ... 各个属性的get set方法
 * }
 * class Target {
 * private String name;
 * private int age;
 * private int value;
 *  ... 各个属性的get set方法
 * }
 *  Src src = new Src("src", 1, 2);
 *  Target target = ObjectUtil.object2Object(src, Target.class);
 *  调用后:
 *      target的name是src
 *      target的age是1
 *      target的value是默认值
 * 如果想把src对象的score也赋值给target的value,只需要加入map映射就行了
 * Src src = new Src("src", 1, 2);
 * // 定义映射
 * Map<String, String> map = new HashMap<String, String>();
 * map.put("score", "value");
 * Target target = ObjectUtil.object2Object(src, Target.class, map);
 * 调用后结果和上述一样只是target的value是值也变成2了
 * </pre></blockquote>
 * <p>
 * 本类为了方便扩展,把两个对象判断字段是否相等定义成接口,只要FiledCompare.compare返回true
 * <p>就认为这2个对象对象的字段要进行赋值,为了适应一个对象的中字段的类型和另一个类的字段类型
 * <p>不一致也能进行转换,定义了ValueConvert接口,在转换的时候通过ValueConvert.convert来
 * <p>转换值得类型,比如一个类中的time是Date类型,而要转换到的类的time是String类型,这样直接,
 * <p>转换会失败,就需要以下转换器
 * <blockquote><pre>
 * public class Date2StringConvert implements ValueConverter<Date, String> {
 * public String converter(Date date) {
 *      return new SimpleDateFormat("yyyyMMdd").format(date);
 * }
 * }
 * </pre></blockquote>
 * <p>
 * what's new 2.0
 * 加入FiledCompare接口<p>
 * 加入ValueConverter接口<p>
 * 实现自定义,转换对象更加灵活<p>
 *
 * @author Immortal
 * @version V2.0
 * @since 2016-03-30
 */
public abstract class ObjectUtil {

    private static final Logger log = LoggerFactory.getLogger(ObjectUtil.class);
    /**
     * java基本类型对象的包装类型
     */
    private static final Map<Class<?>, Class<?>> javaTypeMap = new HashMap<Class<?>, Class<?>>(8);

    /**
     * 字段比较
     */
    private static List<FiledCompare> filedCompares = new ArrayList<FiledCompare>();

    /**
     * 值转换
     */
    private static Map<Class<?>/*原类型,转换后的类型*/, Map<Class<?>, ValueConverter<?, ?>/*对应的转换对象*/>> valueConverts =
            new HashMap<Class<?>, Map<Class<?>, ValueConverter<?, ?>>>(10);

    static {
        javaTypeMap.put(boolean.class, Boolean.class);
        javaTypeMap.put(byte.class, Byte.class);
        javaTypeMap.put(char.class, Character.class);
        javaTypeMap.put(short.class, Short.class);
        javaTypeMap.put(int.class, Integer.class);
        javaTypeMap.put(long.class, Long.class);
        javaTypeMap.put(float.class, Float.class);
        javaTypeMap.put(double.class, Double.class);

        // 添加字段比较
        addFiledCompare(new SimpleFiledCompare());

        // 添加值转换
        // addValueConvert(new Int2StringConvert());
    }

    public static void addFiledCompare(FiledCompare filedCompare) {
        filedCompares.add(filedCompare);
    }

    /**
     * ValueConvert可以是lamdba表达式,必须用此方法添加
     *
     * @param valueConvert ValueConverter
     * @param srcClass     源对象对象
     * @param targetClass  转换到新对象
     */
    public static void addValueConvert(ValueConverter<?, ?> valueConvert, Class<?> srcClass, Class<?> targetClass) {
        Map<Class<?>, ValueConverter<?, ?>> value = null;
        if ((value = valueConverts.get(srcClass)) == null) {
            value = new HashMap<Class<?>, ValueConverter<?, ?>>(1);
        }
        value.put(targetClass, valueConvert);
        valueConverts.put(srcClass, value);
        if (log.isDebugEnabled()) {
            log.debug("add ValueConverter " + srcClass.getName() + " -> " + targetClass.getName());
        }
    }

    /**
     * 添加一个ValueConvert,ValueConvert不能是lamdba表达式
     *
     * @param valueConvert ValueConverter
     */
    public static void addValueConvert(ValueConverter<?, ?> valueConvert) {
        if (valueConvert.getClass().getName().contains("$$Lambda$1")) {
            log.error("this ValueConverter implements by Lamdba,should " +
                    "invoke addValueConvert(ValueConverter<?, ?> valueConvert, Class<?> srcClass, Class<?> targetClass) " +
                    "add ValueConverter");
            return;
        }
        Type genType = valueConvert.getClass().getGenericInterfaces()[0];
        Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
        if (params.length == 2) {
            addValueConvert(valueConvert, (Class<?>) params[0], (Class<?>) params[1]);
        }
    }

    // -------------------------------------------
    //             一个对象转换到另一个对象
    // -------------------------------------------

    /**
     * 把一种对象的转换到另一种对象,转换原则按照相同的字段进行转换如果两个,对象有不同字段也要
     * 相互转换,可以通过map这个参数来配置.本方法还可以忽略字段,对应参数是skipSrcFiled和
     * skipTargetFiled,其处理优先map的处理
     *
     * @param srcObj          源对象对象
     * @param targetObj       转换到新对象
     * @param map             把不同字段的属性进行自定义映射
     * @param <T>             转换后对象的类型
     * @param <K>             原来对象的类型
     * @param skipSrcFiled    忽略源对象的字段
     * @param skipTargetFiled 忽略目标对象的字段
     * @param <T>
     * @param <K>
     * @return
     * @return<T> 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, final T targetObj,
                                         final Map<String, String> map,
                                         List<String> skipSrcFiled,
                                         List<String> skipTargetFiled) {
        if (srcObj == null || targetObj == null)
            return null;

        Method[] targetObjSetMethods = getMethodsStartWith(targetObj.getClass(), "set");
        Method[] srcObjGetMethods = getMethodsStartWith(srcObj.getClass(), "get");

        if (targetObjSetMethods == null || srcObjGetMethods == null)
            return targetObj;

        for (Method srcObjGetMethod : srcObjGetMethods) {
            String srcFiledName = getFiledNameBySetOrGetMethod(srcObjGetMethod);
            Method invokeTargetObjMethod = getInvokeTargetMethod(srcFiledName, targetObjSetMethods);

            if (skipSrcFiled != null && skipSrcFiled.contains(srcFiledName)) {
                continue;
            }

            if (skipTargetFiled != null
                    && skipTargetFiled.contains(getFiledNameBySetOrGetMethod(invokeTargetObjMethod))) {
                continue;
            }

            // 如果上面没有匹配,则在map映射中找
            String newSrcFiledName = null;
            if (invokeTargetObjMethod == null && map != null && (newSrcFiledName = map.get(srcFiledName)) != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Custom field convert [" + srcFiledName + "] -> [" + map.get(srcFiledName) + "]");
                }
                invokeTargetObjMethod = getInvokeTargetMethod(newSrcFiledName, targetObjSetMethods);
            }

            // copy值
            if (invokeTargetObjMethod != null) {
                copyValue(srcObjGetMethod, invokeTargetObjMethod, srcObj, targetObj);
            }
        }
        return targetObj;
    }

    /**
     * 把一种对象的转换到另一种对象,转换原则按照相同的字段进行转换如果两个,对象有不同字段也要
     * 相互转换,可以通过map这个参数来配置.
     *
     * @param srcObj    源对象对象
     * @param targetObj 转换到新对象
     * @param map       把不同字段的属性进行自定义映射
     * @param <T>       转换后对象的类型
     * @param <K>       原来对象的类型
     * @return<T> 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, final T targetObj, final Map<String, String> map) {
        return object2Object(srcObj, targetObj, map, null, null);
    }

    /**
     * 把一种对象的转换到另一种对象,转换原则按照相同的字段进行转换如果两个,对象有不同字段也要
     * 相互转换,可以通过map这个参数来配置.
     *
     * @param srcObj          源对象对象
     * @param targetClassType 目标对象的class
     * @param map             把不同字段的属性进行自定义映射
     * @param <T>             转换后对象的类型
     * @param <K>             原来对象的类型
     * @return<T> 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, final Class<T> targetClassType, final Map<String, String> map) {
        return object2Object(srcObj, newObject(targetClassType), map);
    }

    /**
     * 把一种对象的转换到另一种对象
     *
     * @param srcObj    源对象对象
     * @param targetObj 目标对象
     * @param <T>       转换后对象的类型
     * @param <K>       原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, T targetObj) {
        return object2Object(srcObj, targetObj, (Map<String, String>) null);
    }

    /**
     * 把一种对象的转换到另一种对象
     *
     * @param srcObj          源对象对象
     * @param targetClassType 目标到对象的class
     * @param <T>             转换后对象的类型
     * @param <K>             原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, final Class<T> targetClassType) {
        return object2Object(srcObj, newObject(targetClassType), (Map<String, String>) null);
    }

    /**
     * 把一种对象的转换到另一种对象
     *
     * @param srcObj    源对象对象
     * @param targetObj 目标对象
     * @param namePairs 对象有不同字段也要相互转换,可以通过namePairs这个参数来配置.
     * @param <T>       转换后对象的类型
     * @param <K>       原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, T targetObj, List<NamePair> namePairs) {
        return object2Object(srcObj, targetObj, nameParis2Map(namePairs));
    }

    /**
     * 把一种对象的转换到另一种对象
     *
     * @param srcObj          源对象对象
     * @param targetClassType 目标到对象的class
     * @param <T>             转换后对象的类型
     * @param <K>             原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> T object2Object(final K srcObj, final Class<T> targetClassType, List<NamePair> namePairs) {
        return object2Object(srcObj, newObject(targetClassType), nameParis2Map(namePairs));
    }


    // -------------------------------------------
    //             map转到一个对象
    // -------------------------------------------

    /**
     * 把map转换成对象
     *
     * @param map       要转换的map
     * @param targetObj 转换的目标对象
     * @param <T>
     * @return 转换后的目标对象
     */
    public static <T> T map2Object(final Map<String, Object> map, final T targetObj) {
        if (map == null || map.size() == 0 || targetObj == null)
            return targetObj;

        Method[] targetObjSetMethods = getMethodsStartWith(targetObj.getClass(), "set");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Method targetObjMethod = getInvokeTargetMethod(entry.getKey(), targetObjSetMethods);
            copyValue(targetObjMethod, entry.getValue(), targetObj);
        }
        return targetObj;
    }

    /**
     * 把一个map集合转换到想要的具体对象
     *
     * @param map             map对象
     * @param targetClassType 目标对象的Class
     * @param <T>
     * @return 转换后的目标对象
     */
    public static <T> T map2Object(final Map<String, Object> map, final Class<T> targetClassType) {
        return map2Object(map, newObject(targetClassType));
    }

    // -------------------------------------------
    //             一个对象转到map
    // -------------------------------------------

    /**
     * 把一个对象转换到map,map的key是对象的属性,map的value是对象的值
     *
     * @param o   转换的对象
     * @param <T>
     * @return
     * @throws NullPointerException
     */
    public static <T> Map<String, Object> object2Map(T o) {
        Field[] fields = o.getClass().getDeclaredFields();
        Map<String, Object> map = new HashMap<String, Object>(fields.length);

        for (Field field : fields) {
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            try {
                map.put(field.getName(), field.get(o));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return map;
    }

    // -------------------------------------------
    //             一个list转换到另一个list
    // -------------------------------------------

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList    源对象list
     * @param targetList 转换后的list
     * @param map        把不同字段的属性进行自定义映射
     * @param <T>        转换后对象的class
     * @param <K>        list中原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final List<T> targetList, Map<String, String> map) {
        if (srcList != null && srcList.size() > 0) {
            for (int i = 0; i < srcList.size() && i < targetList.size(); i++)
                object2Object(srcList.get(i), targetList.get(i), map);
        }
        return targetList;
    }

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList         源对象list
     * @param targetClassType 转换后的list的中元素的类型
     * @param map             把不同字段的属性进行自定义映射
     * @param <T>
     * @param <K>
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final Class<T> targetClassType, final Map<String, String> map) {
        return list2List(srcList, new ArrayList<T>(srcList.size()), map);
    }

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList         源对象list
     * @param targetClassType 转换后的list的中元素的类型
     * @param <T>             转换后对象的class
     * @param <K>             list中原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final Class<T> targetClassType) {
        return list2List(srcList, new ArrayList<T>(srcList.size()), (Map<String, String>) null);
    }

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList    源对象list
     * @param targetList 转换后的list
     * @param namePairs  把不同字段的属性进行自定义映射
     * @param <T>        转换后对象的class
     * @param <K>        list中原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final List<T> targetList, final List<NamePair> namePairs) {
        return list2List(srcList, targetList, nameParis2Map(namePairs));

    }

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList         源对象list
     * @param targetClassType 转换后的list的中元素的类型
     * @param namePairs       把不同字段的属性进行自定义映射
     * @param <T>             转换后对象的class
     * @param <K>             list中原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final Class<T> targetClassType, List<NamePair> namePairs) {
        return list2List(srcList, new ArrayList<T>(srcList.size()), nameParis2Map(namePairs));
    }

    /**
     * 把list从中源对象类型转换到另外一种类型,注意长度以targetList和srcList中较
     * 小的长度为准,转换的顺序与List索引一一对应,因此对应顺序需要调用者自己保证
     *
     * @param srcList    源对象list
     * @param targetList 转换后的list
     * @param <T>        转换后对象的class
     * @param <K>        list中原来对象的类型
     * @return 转换后的目标对象
     */
    public static <T, K> List<T> list2List(final List<K> srcList, final List<T> targetList) {
        return list2List(srcList, new ArrayList<T>(srcList.size()), (Map<String, String>) null);
    }


    // ---------------------------------------------------
    //               private methods
    // ---------------------------------------------------

    /**
     * 根据两个类型查找值转换器,本方法有待改进,这里没有考虑类的继承情况<p>
     * 只是简单的较比两个类的类型是否一致,这已经能满足绝大部分的要求
     *
     * @param srcClass    原类型
     * @param targetClass 目标类型
     * @return 返回对象的值转换器, 没有找到返回null
     */
    @SuppressWarnings("unchecked")
    private static ValueConverter<Object, Object> getValueConvert(Class<?> srcClass, Class<?> targetClass) {
        Class<?> newSrcClass = javaTypeMap.get(srcClass);
        if (newSrcClass == null) {
            newSrcClass = srcClass;
        }
        Class<?> newTargetClass = javaTypeMap.get(targetClass);
        if (newTargetClass == null) {
            newTargetClass = targetClass;
        }

        Map<Class<?>, ValueConverter<?, ?>> targetValueConverts = null;
        ValueConverter<?, ?> valueConverter = null;
        if (valueConverts == null
                || (targetValueConverts = valueConverts.get(newSrcClass)) == null
                || (valueConverter = targetValueConverts.get(newTargetClass)) == null) {

            log.debug("[" + srcClass.getName() + "] -> [" + targetClass.getName() + "] isn't find ValueConverter");
            return null;
        }

        log.debug("[" + srcClass.getName() + "] -> [" + targetClass.getName() + "] find ValueConverter:" + valueConverter.getClass().getName());
        return (ValueConverter<Object, Object>) valueConverter;
    }

    /**
     * 把一个对象的某个字段的值赋值给员外一个对象的某个字段
     *
     * @param srcObjMethod    原对象的get方法
     * @param targetObjMethod 目标对象的get方法
     * @param srcObj          原对象
     * @param targetObj       目标对象
     */
    private static void copyValue(Method srcObjMethod, Method targetObjMethod, Object srcObj, Object targetObj) {
        setExecutableAccessible(srcObjMethod);
        Object obj = null;
        try {
            obj = srcObjMethod.invoke(srcObj);
        } catch (IllegalAccessException e) {
            log.error("get source object value fail", e.getMessage());
            return;
        } catch (InvocationTargetException e) {
            log.error("get source object value fail", e.getMessage());
            return;
        }
        copyValue(targetObjMethod, obj, targetObj);
    }

    /**
     * 将调用targetObjMethod方法把value设置到targetObj对象中
     *
     * @param targetObjMethod 要调用的set方法
     * @param value           值
     * @param targetObj       目标对象
     */
    private static void copyValue(Method targetObjMethod, Object value, Object targetObj) {
        if (targetObjMethod == null) {
            return;
        }
        setExecutableAccessible(targetObjMethod);
        // 根据原来get方法的返回值类型和set方法的参数类型获取转换器
        ValueConverter<Object, Object> valueConvert = getValueConvert(value.getClass(), targetObjMethod.getParameterTypes()[0]);

        // 转换值
        if (valueConvert != null) {
            value = valueConvert.convert(value);
        }

        // 给目标对象的目标字段复制
        try {
            if (log.isDebugEnabled()) {
                log.debug("[" + targetObjMethod.getDeclaringClass().getName() + "."
                        + getFiledNameBySetOrGetMethod(targetObjMethod) + "] set value [" + value + "]");
            }
            targetObjMethod.invoke(targetObj, value);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }

    /**
     * 根据set或get方法获取对象的字段名称
     *
     * @param method get或set方法
     * @return 对象的字段名称
     */
    private static String getFiledNameBySetOrGetMethod(Method method) {
        return method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
    }

    /**
     * 比较这个两个字段是否一致,只要FiledCompare的compare方法返回true,就认为这2个字段一致,进行转换
     *
     * @param srcFiledName    原字段名称
     * @param targetFiledName 目标字段名称
     * @return 比较的结果
     */
    private static boolean isInvokeTargetMethod(String srcFiledName, String targetFiledName) {
        for (FiledCompare filedCompare : filedCompares) {
            if (filedCompare.compare(srcFiledName, targetFiledName)) {
                if (log.isDebugEnabled()) {
                    log.debug("[" + srcFiledName + "] -> [" + targetFiledName + "],by FiledCompare [" + filedCompare.getClass().getName() + "]");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 根据原字段名称获取目标对象的set方法
     *
     * @param srcFiledName     原字段名称
     * @param targetObjMethods 目标对象的set方法
     * @return 返回对象set方法
     */
    private static Method getInvokeTargetMethod(String srcFiledName, Method[] targetObjMethods) {
        for (Method targetObjMethod : targetObjMethods) {
            if (targetObjMethod.getName().startsWith("set")) { // targetObj的set方法
                String targetFiledName = getFiledNameBySetOrGetMethod(targetObjMethod);
                if (isInvokeTargetMethod(srcFiledName, targetFiledName)) {
                    if (log.isDebugEnabled()) {
                        log.debug("source filed [" + srcFiledName + "] find target method [" +
                                targetObjMethod.getDeclaringClass().getName() + "." + targetObjMethod.getName() + "]");
                    }
                    return targetObjMethod;
                }
            }
        } // end for
        log.debug("source filed [" + srcFiledName + "] not find target method");
        return null;
    }

    /**
     * 获取class文件中以特定字符串开头的方法
     *
     * @param classType 类的class对象
     * @param startStr  开始的字符串
     * @return 返回查找方法的数组
     */
    private static Method[] getMethodsStartWith(Class<?> classType, String startStr) {
        Method[] methods = classType.getDeclaredMethods();
        List<Method> methodList = new ArrayList<Method>();

        if (methods != null)
            for (Method method : methods) {
                if (method.getName().startsWith(startStr)) {
                    methodList.add(method);
                }
            }

        Method[] resMethods = new Method[methodList.size()];
        for (int i = 0; i < methodList.size(); i++) {
            resMethods[i] = methodList.get(i);
        }
        return resMethods;
    }

    /**
     * 根据字节码文件创建Java对象,必须有无参构造方法,否则抛出异常
     *
     * @param targetClassType 要创建对象的字节码文件
     * @param <T>
     * @return 创建的对象, 失败抛出异常
     */
    private static <T> T newObject(final Class<T> targetClassType) {
        if (targetClassType == null)
            return null;
        T targetObj = null;

        Constructor<T> constructor = null;
        try {
            constructor = targetClassType.getConstructor();
            setExecutableAccessible(constructor);
            targetObj = constructor.newInstance();
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
        return targetObj;
    }

    /**
     * 把转换成Map对象
     * List<NamePair>
     *
     * @param namePairs 要转换的List<NamePair>对象
     * @return 返回转换后的Map
     */
    private static Map<String, String> nameParis2Map(List<NamePair> namePairs) {
        Map<String, String> map = null;
        if (namePairs != null && namePairs.size() > 0) {
            map = new HashMap<String, String>(namePairs.size());
            for (NamePair t : namePairs) {
                map.put(t.getOldName(), t.getNewName());
            }
        }
        return map;
    }

    private static void setExecutableAccessible(Executable executable) {
        if (!Modifier.isPublic(executable.getDeclaringClass().getModifiers())) {
            executable.setAccessible(true);
        }
    }

}
