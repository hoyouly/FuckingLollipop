/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a thread-local storage, that is, a variable for which each thread
 * has its own value. All threads share the same {@code ThreadLocal} object,
 * but each sees a different value when accessing it, and changes made by one
 * thread do not affect the other threads. The implementation supports
 * {@code null} values.
 *
 * @author Bob Lee
 * @see java.lang.Thread
 */
public class ThreadLocal<T> {

    /* Thanks to Josh Bloch and Doug Lea for code reviews and impl advice. */

	/**
	 * Creates a new thread-local variable.
	 */
	public ThreadLocal() {
	}

	/**
	 * Returns the value of this variable for the current thread. If an entry
	 * doesn't yet exist for this variable on this thread, this method will
	 * create an entry, populating the value with the result of
	 * {@link #initialValue()}.
	 *
	 * @return the current value of the variable for the calling thread.
	 */
	@SuppressWarnings("unchecked")
	public T get() {
		// Optimized for the fast path.
		//获取当前线程
		Thread currentThread = Thread.currentThread();
		//查找当前线程的本地储存区
		//value 是从线程对象 currentThread 成员属性取出来的中,所以不同线程有不同value, 就是有不同的副本.
		Values values = values(currentThread);
		if (values != null) {
			//得到Values中的数组对象
			Object[] table = values.table;
			//获取索引
			int index = hash & values.mask;
			// 获取值，得到ThreadLocal的reference的坐标
			// 尝试从第一次计算hash 得到索引取值,如果key 等于 将执行getAfterMiss 方法.一般都存在第一次计算得到索引的地方
			if (this.reference == table[index]) {
				//根据上面可知，ThreadLocal的值在table中存储的位置总是TheadLocal中reference的下一位，这样就得到了TheadLocal的值
				//返回当前线程储存区中的数据
				return (T) table[index + 1];
			}
		} else {
			//创建Values对象
			values = initializeValues(currentThread);
		}
		//从目标线程存储区没有查询是则返回null
		return (T) values.getAfterMiss(this);
	}

	/**
	 * Provides the initial value of this variable for the current thread.
	 * The default implementation returns {@code null}.
	 *
	 * @return the initial value of the variable.
	 */
	protected T initialValue() {
		return null;
	}

	/**
	 * Sets the value of this variable for the current thread. If set to
	 * {@code null}, the value will be set to null and the underlying entry will
	 * still be present.
	 *
	 * @param value the new value of the variable for the caller thread.
	 */
	public void set(T value) {
		//获取当前线程
		Thread currentThread = Thread.currentThread();
		//查找当前线程的本地储存区
		Values values = values(currentThread);
		if (values == null) {
			//当线程本地存储区，尚未存储该线程相关信息时，则创建Values对象
			values = initializeValues(currentThread);
		}
		//保存数据value到当前线程this
		values.put(this, value);
	}

	/**
	 * Removes the entry for this variable in the current thread. If this call
	 * is followed by a {@link #get()} before a {@link #set},
	 * {@code #get()} will call {@link #initialValue()} and create a new
	 * entry with the resulting value.
	 *
	 * @since 1.5
	 */
	public void remove() {
		Thread currentThread = Thread.currentThread();
		Values values = values(currentThread);
		if (values != null) {
			values.remove(this);
		}
	}

	/**
	 * Creates Values instance for this thread and variable type.
	 */
	Values initializeValues(Thread current) {
		return current.localValues = new Values();
	}

	/**
	 * Gets Values instance for this thread and variable type.
	 */
	Values values(Thread current) {
		return current.localValues;
	}

	/**
	 * Weak reference to this thread local instance.
	 * 这个线程本地实例的弱引用。
	 * 弱引用持有它,有利于回收,防止内存泄漏.
	 * 如果他为null时它所在的存放数组索引的地方将被设置为TOMBSTONE 对象,value所在的地方设置为null,不再持有它对象也有利于回收.
	 * 下一个ThreadLocal 对象set时.如果找到数组存放的索引,而且在这个索引数组里面的对象为TOMBSTONE将会被替换成这个.从而达到内存复用.
	 */
	private final Reference<ThreadLocal<T>> reference = new WeakReference<ThreadLocal<T>>(this);

	/**
	 * Hash counter.
	 */
	private static AtomicInteger hashCounter = new AtomicInteger(0);

	/**
	 * Internal hash. We deliberately don't bother with #hashCode().
	 * Hashes must be even. This ensures that the result of
	 * (hash & (table.length - 1)) points to a key and not a value.
	 * <p>
	 * We increment by Doug Lea's Magic Number(TM) (*2 since keys are in
	 * every other bucket) to help prevent clustering.
	 * 为啥使用0x61c88647  防止集中,为啥*2 因为key  所在的索引为偶数.
	 * 第一次计算时hash 为零.也就是第一次创建ThreadLocal key 必然在0索引.然后系统已经使用了n次(hashCounter 为静态...).
	 */
	private final int hash = hashCounter.getAndAdd(0x61c88647 * 2);

	/**
	 * Per-thread map of ThreadLocal instances to values.
	 */
	static class Values {

		/**
		 * Size must always be a power of 2.
		 * 数组初始值大小，必须是2的N次方
		 */
		private static final int INITIAL_SIZE = 16;

		/**
		 * Placeholder for deleted entries.
		 * 被删除的数据
		 */
		private static final Object TOMBSTONE = new Object();

		/**
		 * Map entries. Contains alternating keys (ThreadLocal) and values.
		 * The length is always a power of 2.
		 * 存放数据的数组，使用key/value映射大小总是2的N次方
		 */
		private Object[] table;

		/**
		 * Used to turn hashes into indices.
		 * 和key的hash值进行与运算，获取数组中的索引
		 */
		private int mask;

		/**
		 * Number of live entries.
		 * 当前有效的Key的数量
		 */
		private int size;

		/**
		 * Number of tombstones.
		 * 已经失效的key的数量
		 */
		private int tombstones;

		/**
		 * Maximum number of live entries and tombstones.
		 * key的总和
		 */
		private int maximumLoad;

		/**
		 * Points to the next cell to clean up.
		 * 下一次查找失效key的起始位置
		 */
		private int clean;

		/**
		 * Constructs a new, empty instance.
		 *
		 */
		Values() {
			initializeTable(INITIAL_SIZE);
			this.size = 0;
			this.tombstones = 0;
		}

		/**
		 * Used for InheritableThreadLocals.
		 * 使用外部Values拷贝的构造函数
		 */
		Values(Values fromParent) {
			this.table = fromParent.table.clone();
			this.mask = fromParent.mask;
			this.size = fromParent.size;
			this.tombstones = fromParent.tombstones;
			this.maximumLoad = fromParent.maximumLoad;
			this.clean = fromParent.clean;
			inheritValues(fromParent);
		}

		/**
		 * Inherits values from a parent thread.
		 *
		 */
		@SuppressWarnings({"unchecked"})
		private void inheritValues(Values fromParent) {
			// Transfer values from parent to child thread.
			Object[] table = this.table;
			for (int i = table.length - 2; i >= 0; i -= 2) {
				Object k = table[i];

				if (k == null || k == TOMBSTONE) {
					// Skip this entry.
					continue;
				}

				// The table can only contain null, tombstones and references.
				Reference<InheritableThreadLocal<?>> reference = (Reference<InheritableThreadLocal<?>>) k;
				// Raw type enables us to pass in an Object below.
				InheritableThreadLocal key = reference.get();
				if (key != null) {
					// Replace value with filtered value.
					// We should just let exceptions bubble out and tank
					// the thread creation
					table[i + 1] = key.childValue(fromParent.table[i + 1]);
				} else {
					// The key was reclaimed.
					table[i] = TOMBSTONE;
					table[i + 1] = null;
					fromParent.table[i] = TOMBSTONE;
					fromParent.table[i + 1] = null;

					tombstones++;
					fromParent.tombstones++;

					size--;
					fromParent.size--;
				}
			}
		}

		/**
		 * Creates a new, empty table with the given capacity.
		 * 初始化数组大小
		 */
		private void initializeTable(int capacity) {
			//存储数据的table数组大小默认为32
			this.table = new Object[capacity * 2];
			//mask的默认大小为table的长度减1，即table数组中最后一个元素的索引
			this.mask = table.length - 1;
			this.clean = 0;
			//默认存储的最大值为数组长度的1/3
			this.maximumLoad = capacity * 2 / 3; // 2/3
		}

		/**
		 * Cleans up after garbage-collected thread locals.
		 */
		private void cleanUp() {
			//如果需要扩容，则直接返回，扩容的过程中对失效的地方进行了标记
			if (rehash()) {
				// If we rehashed, we needn't clean up (clean up happens as
				// a side effect).
				return;
			}

			//没有值的话，什么也不做
			if (size == 0) {
				// No live entries == nothing to clean.
				return;
			}

			// Clean log(table.length) entries picking up where we left off
			// last time.
			//默认是0，标记每次clean的位置
			int index = clean;
			Object[] table = this.table;
			for (int counter = table.length; counter > 0; counter >>= 1, index = next(index)) {
				Object k = table[index];
				//如果key是失效的或者为null，则不做处理
				if (k == TOMBSTONE || k == null) {
					continue; // on to next entry
				}

				// The table can only contain null, tombstones and references.
				//table只能存储null、tombstone、和references
				@SuppressWarnings("unchecked") Reference<ThreadLocal<?>> reference = (Reference<ThreadLocal<?>>) k;
				//检查key是否失效，失效的话进行标记并且释放它的值
				if (reference.get() == null) {
					// This thread local was reclaimed by the garbage collector.
					table[index] = TOMBSTONE;
					table[index + 1] = null;
					tombstones++;
					size--;
				}
			}

			// Point cursor to next index.
			//标记下次开始clean的位置
			clean = index;
		}

		/**
		 * Rehashes the table, expanding or contracting it as necessary.
		 * Gets rid of tombstones. Returns true if a rehash occurred.
		 * We must rehash every time we fill a null slot; we depend on the
		 * presence of null slots to end searches (otherwise, we'll infinitely
		 * loop).
		 * 对数组进行扩容
		 */
		private boolean rehash() {
			//不需要扩容
			if (tombstones + size < maximumLoad) {
				return false;
			}

			int capacity = table.length >> 1;

			// Default to the same capacity. This will create a table of the
			// same size and move over the live entries, analogous to a
			// garbage collection. This should only happen if you churn a
			// bunch of thread local garbage (removing and reinserting
			// the same thread locals over and over will overwrite tombstones
			// and not fill up the table).
			int newCapacity = capacity;

			if (size > (capacity >> 1)) {
				// More than 1/2 filled w/ live entries.
				// Double size.
				//双倍扩容
				newCapacity = capacity * 2;
			}
			//标记数组
			Object[] oldTable = this.table;

			// Allocate new table.
			//重新初始化数组大小
			initializeTable(newCapacity);

			// We won't have any tombstones after this.
			// 重置失效的key
			this.tombstones = 0;

			// If we have no live entries, we can quit here.
			//没有有效的key
			if (size == 0) {
				return true;
			}

			// Move over entries.
			//数组扩容
			for (int i = oldTable.length - 2; i >= 0; i -= 2) {
				Object k = oldTable[i];
				//丢弃失效的key
				if (k == null || k == TOMBSTONE) {
					// Skip this entry.
					continue;
				}

				// The table can only contain null, tombstones and references.
				@SuppressWarnings("unchecked") Reference<ThreadLocal<?>> reference = (Reference<ThreadLocal<?>>) k;
				ThreadLocal<?> key = reference.get();
				if (key != null) {
					// Entry is still live. Move it over.
					//添加有效的key和value
					add(key, oldTable[i + 1]);
				} else {
					// The key was reclaimed.
					size--;
				}
			}

			return true;
		}

		/**
		 * Adds an entry during rehashing. Compared to put(), this method
		 * doesn't have to clean up, check for existing entries, account for
		 * tombstones, etc.
		 */
		void add(ThreadLocal<?> key, Object value) {
			//根据key的hash和mask进行&运算，获取index
			for (int index = key.hash & mask; ; index = next(index)) {
				Object k = table[index];
				if (k == null) {
					//将key存储到数组的index位置
					table[index] = key.reference;
					//将value存储到数组的index+1位置
					table[index + 1] = value;
					return;
				}
			}
		}

		/**
		 * Sets entry for given ThreadLocal to given value, creating an
		 * entry if necessary.
		 */
		void put(ThreadLocal<?> key, Object value) {
			//将失效的key进行标记，释放它的值
			cleanUp();

			// Keep track of first tombstone. That's where we want to go back
			// and add an entry if necessary.
			//标记第一个失效数据的索引
			int firstTombstone = -1;
			//使用key的hash和mask进行&运算，获取当前key在数组中的index
			for (int index = key.hash & mask; ; index = next(index)) {
				//直接获取key
				Object k = table[index];
				//如果key相同，则直接更新value，key对应的索引为index，则value的索引为index+1
				if (k == key.reference) {
					// Replace existing entry.
					table[index + 1] = value;
					return;
				}
				//如果key不存在
				if (k == null) {
					//当前不存在失效key
					if (firstTombstone == -1) {
						//ThreadLocal的值在table中存储的位置总是TheadLocal中reference的下一位
						table[index] = key.reference;
						table[index + 1] = value;
						size++;
						return;
					}

					//ThreadLocal的值在table中存储的位置总是TheadLocal中reference的下一位
					//如果存在失效的key，则将需要存储的值保存到失效的key所在的位置
					table[firstTombstone] = key.reference;
					table[firstTombstone + 1] = value;
					tombstones--;
					size++;
					return;
				}

				// Remember first tombstone.
				//对失效的key进行标记
				if (firstTombstone == -1 && k == TOMBSTONE) {
					firstTombstone = index;
				}
			}
		}

		/**
		 * Gets value for given ThreadLocal after not finding it in the first
		 * slot.
		 * 获取给定ThreadLocal的值，因为在第一个slot 中没有找到它。
		 * 之前没有set过值，调用get方法时会调用到此方法
		 */
		Object getAfterMiss(ThreadLocal<?> key) {
			Object[] table = this.table;
			//获取索引
			int index = key.hash & mask;

			// If the first slot is empty, the search is over.
			//如果key不存在，那么直接返回ThreadLocal方法返回值，默认为null
			if (table[index] == null) {
				Object value = key.initialValue();

				// If the table is still the same and the slot is still empty...
				//如果是同一个数组且key不存在，（疑问为什么会不相等，方法开始赋的值）
				if (this.table == table && table[index] == null) {
					table[index] = key.reference;
					table[index + 1] = value;
					size++;

					cleanUp();
					return value;
				}

				// The table changed during initialValue().
				// 添加到table数组
				put(key, value);
				return value;
			}

			// Keep track of first tombstone. That's where we want to go back
			// and add an entry if necessary.
			//key存在时的处理
			int firstTombstone = -1;

			// Continue search.
			for (index = next(index); ; index = next(index)) {
				Object reference = table[index];
				if (reference == key.reference) {
					//根据index查到且相等直接返回
					return table[index + 1];
				}

				// If no entry was found...
				//如果没有查到，继续返回默认的value
				if (reference == null) {
					Object value = key.initialValue();

					// If the table is still the same...
					if (this.table == table) {
						// If we passed a tombstone and that slot still
						// contains a tombstone...
						if (firstTombstone > -1 && table[firstTombstone] == TOMBSTONE) {
							table[firstTombstone] = key.reference;
							table[firstTombstone + 1] = value;
							tombstones--;
							size++;

							// No need to clean up here. We aren't filling
							// in a null slot.
							return value;
						}

						// If this slot is still empty...
						if (table[index] == null) {
							table[index] = key.reference;
							table[index + 1] = value;
							size++;

							cleanUp();
							return value;
						}
					}

					// The table changed during initialValue().
					put(key, value);
					return value;
				}
				//标记无效的key
				if (firstTombstone == -1 && reference == TOMBSTONE) {
					// Keep track of this tombstone so we can overwrite it.
					firstTombstone = index;
				}
			}
		}

		/**
		 * Removes entry for the given ThreadLocal.
		 */
		void remove(ThreadLocal<?> key) {
			cleanUp();

			for (int index = key.hash & mask; ; index = next(index)) {
				Object reference = table[index];

				if (reference == key.reference) {
					// Success!
					table[index] = TOMBSTONE;
					table[index + 1] = null;
					tombstones++;
					size--;
					return;
				}

				if (reference == null) {
					// No entry found.
					return;
				}
			}
		}

		/**
		 * Gets the next index. If we're at the end of the table, we wrap back
		 * around to 0.
		 */
		private int next(int index) {
			return (index + 2) & mask;
		}
	}
}
