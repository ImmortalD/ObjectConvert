package com.immortal.test;

import com.immortal.util.objectutil.ObjectUtil;
import com.immortal.util.objectutil.filed.converter.ValueConverter;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Immortal
 * @version V1.0
 * @since 2016-3-30
 */
interface X {

}

class XXP implements X {

}

class XX extends XXP {

}

class Src {
	private String name;
	private int age;
	private int score;
	private Date time;
	private String sub = "SrcStr";
	private XX xx = new XX();

	public String getSub() {
		return sub;
	}

	public void setSub(String sub) {
		this.sub = sub;
	}

	public XX getXx() {
		return xx;
	}

	public void setXx(XX xx) {
		this.xx = xx;
	}

	public Src(String name, int age, int score, Date time) {
		this.name = name;
		this.age = age;
		this.score = score;
		this.time = time;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("Src{");
		sb.append("name='").append(name).append('\'');
		sb.append(", age=").append(age);
		sb.append(", score=").append(score);
		sb.append(", time=").append(time);
		sb.append('}');
		return sb.toString();
	}
}

class SubCls {
	private String sub = "subStr";

	public String getSub() {
		return sub;
	}

	public void setSub(String sub) {
		this.sub = sub;
	}
}

class Target extends SubCls implements Serializable {

	private static final long serialVersionUID = 4479647807495714111L;
	private String name;
	private int age;
	private String value;
	private String time;
	transient String oooo = "--";
	private X xx = new XX();

	public X getXx() {
		return xx;
	}

	public void setXx(X xx) {
		this.xx = xx;
	}

	public Target() {
	}

	public Target(String name, int age, String value, String time) {
		this.name = name;
		this.age = age;
		this.value = value;
		this.time = time;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("Target{");
		sb.append("name='").append(name).append('\'');
		sb.append(", age=").append(age);
		sb.append(", value='").append(value).append('\'');
		sb.append(", time='").append(time).append('\'');
		sb.append('}');
		return sb.toString();
	}
}

class Int2String implements ValueConverter<Integer, String> {
	@Override
	public String convert(Integer integer) {
		return String.valueOf(integer);
	}
}

public class TestObjectUtil {

	public static void main(String[] args) {
		XX x = new XX();
		System.out.println(x);
		Src src = new Src("src", 1, 2, new Date());
		Target target = new Target();
		target.setTime("----------");
		target = ObjectUtil.object2Object(src, target);
		System.out.println("简单的转换");
		System.out.println(src.toString());
		System.out.println(target.toString());

		// 定义映射,把score的值设置到value上
		Map<String, String> map = new HashMap<String, String>();
		map.put("score", "value");
		// 由于score与value的类型不同,score是int,value是字符串,想要转换要加ValueConvert,
		// 上面测试的转换time的类型不同,转换后taeget的time依然是null,

		// int -> String的值转换
	   /* ObjectUtil.addValueConvert(new ValueConverter<Integer, String>() {
			@Override
            public String convert(Integer integer) {
                return String.valueOf(integer);
            }
        });
*/
		ObjectUtil.addValueConvert(new Int2String());
		// Date -> String的值转换  lamdba必须调用3个参数的addValueConvert来添加
		ObjectUtil.addValueConvert(date -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").
										   format(date)
				, Date.class, String.class
		);


		target = ObjectUtil.object2Object(src, Target.class, map);
		System.out.println("自定义字段的转换");
		System.out.println(src.toString());
		System.out.println(target.toString());
		System.out.println(ObjectUtil.object2Map(target));

		Map<String, Object> convertMap = new HashMap<String, Object>();
		convertMap.put("name", 11);
		convertMap.put("sub", "map_sub");
		convertMap.put("age", 110);

		Map<String, String> fieldMap = new HashMap<String, String>();
		fieldMap.put("name1", "age");
		List<String> list = new ArrayList<>();
		list.add("name");
		target = ObjectUtil.map2Object(convertMap, Target.class, fieldMap, null, list);
		System.out.println(target);
	}

}
