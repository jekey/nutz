package org.nutz.castor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nutz.castor.castor.Array2Array;
import org.nutz.castor.castor.Object2Object;
import org.nutz.lang.Lang;
import org.nutz.lang.Mirror;
import org.nutz.lang.Streams;
import org.nutz.lang.TypeExtractor;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.resource.Scans;

/**
 * 一个创建 Castor 的工厂类。它的使用方式是：
 * 
 * <pre>
 * Castors.me().cast(obj, fromType, toType);
 * </pre>
 * 
 * 
 * @author zozoh(zozohtnt@gmail.com)
 * @author Wendal(wendal1985@gmail.com)
 */
public class Castors {

	private static final Log log = Logs.get();

	private static Castors one = new Castors();

	/**
	 * @return 单例
	 */
	public static Castors me() {
		return one;
	}

	/**
	 * @return 一个新的 Castors 实例
	 */
	public static Castors create() {
		return new Castors();
	}

	/**
	 * 如何抽取对象的类型级别
	 */
	private TypeExtractor extractor;
	/**
	 * Castor 的搜索路径
	 */
	private List<Class<?>> paths;
	/**
	 * Castor 的配置
	 */
	private Object setting;

	/**
	 * 设置转换的配置
	 * <p>
	 * 配置对象所有的共有方法都会被遍历。只要这个方法有一个且只有一个参数，并且该参数 是一个 org.nutz.castor.Castor
	 * 接口的实现类。那么就会被认为是对该种 Castor 的一个 配置。
	 * <p>
	 * 当初始化这个 Castor 之前，会用这个方法来设置一下你的 Castor （如果你的 Castor 需要配置的话）
	 * 
	 * @param obj
	 *            配置对象。可以是任意的 Java 对象。
	 */
	public synchronized Castors setSetting(Object obj) {
		if (obj != null) {
			setting = obj;
			this.reload();
		}
		return this;
	}

	/**
	 * 你的的 Castor 可能存在在不同包下，你可以把每个包下的随便一个 Castor 作为例子放到一个列表里。 这样， Nutz.Castor
	 * 就能找到同一包下其他的 Castor 了。
	 * <p>
	 * 你的 Castor 存放在 CLASSPAH 下或者 Jar 包里都是没有问题的
	 * 
	 * @param paths
	 *            Castor 例子列表
	 */
	public synchronized Castors setPaths(List<Class<?>> paths) {
		if (paths != null) {
			this.paths = paths;
			reload();
		}
		return this;
	}

	/**
	 * 将 Castor 的寻找路径恢复成默认值。
	 */
	public synchronized Castors resetPaths() {
		paths = new ArrayList<Class<?>>();
		paths.add(Array2Array.class);
		reload();
		return this;
	}

	/**
	 * 增加 Castor 的寻找路径。
	 * 
	 * @param paths
	 *            示例 Castor
	 */
	public synchronized Castors addPaths(Class<?>... paths) {
		if (null != paths) {
			for (Class<?> path : paths)
				if (path != null)
					this.paths.add(path);
			reload();
		}
		return this;
	}

	/**
	 * 设置自定义的对象类型提取器逻辑
	 * 
	 * @param te
	 *            类型提取器
	 */
	public synchronized Castors setTypeExtractor(TypeExtractor te) {
		extractor = te;
		return this;
	}

	private Castors() {
		setting = new DefaultCastorSetting();
		resetPaths();
	}

	private void reload() {
		if (paths == null || paths.size() == 0) {
			resetPaths();
			return;
		}
		HashMap<Class<?>, Method> settingMap = new HashMap<Class<?>, Method>();
		for (Method m1 : setting.getClass().getMethods()) {
			Class<?>[] pts = m1.getParameterTypes();
			if (pts.length == 1 && Castor.class.isAssignableFrom(pts[0]))
				settingMap.put(pts[0], m1);
		}
		// build castors
//		this.map = new HashMap<String, Map<String, Castor<?, ?>>>();

		this.map = new HashMap<Integer, Castor<?,?>>();
		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
		for (Iterator<Class<?>> it = paths.iterator(); it.hasNext();) {
			Class<?> baseClass = it.next();
			if (baseClass == null)
				continue;
			List<Class<?>> list = Scans.me().scanPackage(baseClass);
			if (null != list && list.size() > 0)
				classes.addAll(list);
		}
		// 一个类都找不到,好吧,加载默认的,从文件读取列表
		if (classes.size() == 0) {
			if (log.isWarnEnabled())
				log.warn("!!No castor found!!!!!!!!! Load default castor list");

			String str = Streams.readAndClose(Streams.fileInr("/org/nutz/castor/default-castors.txt"));
			String[] classNames = str.split("\\n");
			for (String className : classNames)
				try {
					classes.add(Class.forName("org.nutz.castor.castor." + className.trim()));
				}
				catch (Throwable e) {}
		}
		for (Class<?> klass : classes) {
			try {
				if (Modifier.isAbstract(klass.getModifiers()))
					continue;
				if (!Castor.class.isAssignableFrom(klass))
					continue;
				fillMap(klass, settingMap);
			}
			catch (Throwable e) {
				if (log.isWarnEnabled())
					log.warnf("Fail to create castor [%s] because: %s", klass, e.getMessage());
			}
		}
		if (log.isDebugEnabled())
			log.debugf("Using %s castor for Castors", map.size());
	}
	
	/**
	 * 填充 map .<br>
	 * 在map中使用hash值来做为key来进行存储
	 * @param klass
	 * @param settingMap
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	private void fillMap(Class<?> klass, HashMap<Class<?>, Method> settingMap) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
	    Castor<?, ?> castor = (Castor<?, ?>) klass.newInstance();
	    if(!map.containsKey(castor.hashCode())){
	        map.put(castor.hashCode(), castor);
	    }
	    Method m = settingMap.get(castor.getClass());
        if (null == m) {
            for (Entry<Class<?>, Method> entry : settingMap.entrySet()) {
                Class<?> cc = entry.getKey();
                if (cc.isAssignableFrom(klass)) {
                    m = settingMap.get(cc);
                    break;
                }
            }
        }
        if (null != m)
            m.invoke(setting, castor);
	}

	/**
	 * First index is "from" (source) The second index is "to" (target)
	 */
//	private Map<String, Map<String, Castor<?, ?>>> map;
	private Map<Integer, Castor<?,?>> map;

	/**
	 * 转换一个 POJO 从一个指定的类型到另外的类型
	 * 
	 * @param src
	 *            源对象
	 * @param fromType
	 *            源对象类型
	 * @param toType
	 *            目标类型
	 * @param args
	 *            转换时参数。有些 Castor 可能需要这个参数，比如 Array2Map
	 * @return 目标对象
	 * @throws FailToCastObjectException
	 *             如果没有找到转换器，或者转换失败
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <F, T> T cast(Object src, Class<F> fromType, Class<T> toType, String... args)
			throws FailToCastObjectException {
		if (null == src) {
			// 原生数据的默认值
			if (toType.isPrimitive()) {
				if (toType == int.class)
					return (T) Integer.valueOf(0);
				else if (toType == long.class)
					return (T) Long.valueOf(0L);
				else if (toType == byte.class)
					return (T) Byte.valueOf((byte) 0);
				else if (toType == short.class)
					return (T) Short.valueOf((short) 0);
				else if (toType == float.class)
					return (T) Float.valueOf(.0f);
				else if (toType == double.class)
					return (T) Double.valueOf(.0);
				else if (toType == boolean.class)
					return (T) Boolean.FALSE;
				else if (toType == char.class)
					return (T) Character.valueOf(' ');
				throw Lang.impossible();
			}
			// 是对象，直接返回 null
			return null;
		}
		if (fromType == toType || toType == null || fromType == null)
			return (T) src;
		if (fromType.getName().equals(toType.getName()))
			return (T) src;
		if (toType.isAssignableFrom(fromType))
			return (T) src;
		Mirror<?> from = Mirror.me(fromType, extractor);
		if (from.canCastToDirectly(toType)) // Use language built-in cases
			return (T) src;
		Castor c = find(from, toType);
		if (null == c)
			throw new FailToCastObjectException(String.format(	"Can not find castor for '%s'=>'%s' in (%d) because:\n%s",
																fromType.getName(),
																toType.getName(),
																map.size(),
																"Fail to find matched castor"));
		try {
			return (T) c.cast(src, toType, args);
		}
		catch (FailToCastObjectException e) {
			throw e;
		}
		catch (Exception e) {
			throw new FailToCastObjectException(String.format(	"Fail to cast from <%s> to <%s> for {%s} because:\n%s:%s",
																fromType.getName(),
																toType.getName(),
																src,
																e.getClass().getSimpleName(),
																e.getMessage()),
												e);
		}
	}

	/**
	 * 获取一个转换器
	 * 
	 * @param from
	 *            源类型
	 * @param to
	 *            目标类型
	 * @return 转换器
	 */
	public <F, T> Castor<F, T> find(Class<F> from, Class<T> to) {
		return find(Mirror.me(from), to);
	}

	@SuppressWarnings("unchecked")
	private <F, T> Castor<F, T> find(Mirror<F> from, Class<T> toType) {
		Mirror<T> to = Mirror.me(toType, extractor);
		Class<?>[] fets = from.extractTypes();
		Class<?>[] tets = to.extractTypes();
		for(Class<?> ft : fets){
		    for(Class<?> tt : tets){
		        if(map.containsKey(Castor.fetchHash(ft, tt))){
		            return (Castor<F, T>) map.get(Castor.fetchHash(ft, tt));
		        }
		    }
		}
		return null;
	}

	/**
	 * 转换一个 POJO 到另外的类型
	 * 
	 * @param src
	 *            源对象
	 * @param toType
	 *            目标类型
	 * @return 目标对象
	 * @throws FailToCastObjectException
	 *             如果没有找到转换器，或者转换失败
	 */
	public <T> T castTo(Object src, Class<T> toType) throws FailToCastObjectException {
		return cast(src, null == src ? null : src.getClass(), toType);
	}

	/**
	 * 判断一个类型是否可以被转换成另外一个类型
	 * <p>
	 * 判断的依据就是看是否可以直接被转型，以及能不能找到一个专有的转换器
	 * 
	 * @param fromType
	 *            起始类型
	 * @param toType
	 *            目标类型
	 * @return 是否可以转换
	 */
	public boolean canCast(Class<?> fromType, Class<?> toType) {
		if (Mirror.me(fromType).canCastToDirectly(toType))
			return true;
		Castor<?, ?> castor = this.find(fromType, toType);
		return !(castor instanceof Object2Object);
	}

	/**
	 * 将一个 POJO 转换成字符串
	 * 
	 * @param src
	 *            源对象
	 * @return 字符串
	 */
	public String castToString(Object src) {
		try {
			return castTo(src, String.class);
		}
		catch (FailToCastObjectException e) {
			return String.valueOf(src);
		}
	}
}
