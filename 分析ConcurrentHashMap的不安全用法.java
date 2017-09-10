HashMap + syncronized 实现线程安全
public class TestClass {
	private HashMap<String, Integer> record = new HashMap<>();
	public syncronized void add(String key) {
		Integer value = record.get(key);
		if (value == null) { //新建对象，设置数量为 1
			record.put(key, 1);
		} else { //更新已存在对象的数量
			record.put(key, value + 1);
		}
	}
}

ConcurrentHashMap 的"陷阱式"用法，无法保证线程安全
public class TestClass {
	private ConcurrentHashMap<String, Integer> record = new ConcurrentHashMap<>();
	public void add(String key) {
		Integer value = record.get(key);
		if (value == null) {
			record.put(key, 1);
		} else {
			record.put(key, value + 1);
		}
	}
}

ConcurrentHashMap 的 Node 类
class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    volatile V value;
    volatile Node<K,V> next;
}

不安全原因分析：类比"单例"模式下"创建对象"  <==>  "ConcurrentHashMap的" put() "新建Node对象"
本质：put(key, value) 可能执行 {创建新对象, 更新已有对象} 其中的1项操作
实例分析：线程 A, B 并发执行 add("example") 方法，此时 record 中暂无其记录 
1. 线程 A 执行 record.get("example")，获得的 value == null (即引用变量 value 未指向任何对象)
2. 线程 B 得到了CPU， 依次执行 record.get("example")；if (value == null)；record.put(key, 1)
	详细分析 record.put(key, 1)的操作流程
	2.1 根据hash(key.hashCode())，定位到Segment[i]，执行 Segment[i].lock() "获取锁"
	2.2 根据hash(key.hashCode()), 定位到Segment[i]下的 Node[j]，即"链表头"
	2.3 新建Node对象，此时 Node 对象的 value 属性已经指向了 "常量池" 中的真实对象
	2.4 将新建的Node 对象插入 Node[i] 所在的链表的 "头部"
	2.5 Segment[i].unlock() "释放锁"
	2.6 释放 CPU
3. 线程 A 得到了CPU，虽然此时线程B 已经在 ConcurrentHashMap 中为"example"创建了对象，但是 value 只是个普通的 Integer 类型引用变量，
	它的取值一直未 null，即从未指向过真实对象，因此目前线程 A 的value变量 还不具有 "volatile" 关键字的特征，
	自然也就无法反应线程 B 的最新修改结果
4. 线程 A 执行 if (value == null)，发现 value 对象确实为 null, 于是执行 record.put(key, 1) 将 "example" 对应的记录"更新为 1"
	"但是" 它作为"计数器"应该做的事情是 record.put(key, value + 1)！！！！！