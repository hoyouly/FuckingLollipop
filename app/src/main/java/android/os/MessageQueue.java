/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

import android.util.Log;
import android.util.Printer;

import java.util.ArrayList;

/**
 * Low-level class holding the list of messages to be dispatched by a
 * {@link Looper}.  Messages are not added directly to a MessageQueue,
 * but rather through {@link Handler} objects associated with the Looper.
 * <p>
 * <p>You can retrieve the MessageQueue for the current thread with
 * {@link Looper#myQueue() Looper.myQueue()}.
 */
public final class MessageQueue {
	// True if the message queue can be quit.  如果消息队列可以退出，则为真。
	private final boolean mQuitAllowed;

	@SuppressWarnings("unused")
	private long mPtr; // used by native code

	Message mMessages;
	private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
	private IdleHandler[] mPendingIdleHandlers;
	private boolean mQuitting;

	// Indicates whether next() is blocked waiting in pollOnce() with a non-zero timeout.
	//指示next（）是否被阻塞，并在pollOnce（）中等待一个非零超时。
	private boolean mBlocked;

	// The next barrier token.
	// Barriers are indicated by messages with a null target whose arg1 field carries the token.
	private int mNextBarrierToken;

	private native static long nativeInit();

	private native static void nativeDestroy(long ptr);

	private native static void nativePollOnce(long ptr, int timeoutMillis);

	private native static void nativeWake(long ptr);

	private native static boolean nativeIsIdling(long ptr);

	/**
	 * Callback interface for discovering when a thread is going to block
	 * waiting for more messages.
	 * 回调接口，用于发现线程何时阻塞等待更多消息。
	 */
	public static interface IdleHandler {
		/**
		 * Called when the message queue has run out of messages and will now
		 * wait for more.  Return true to keep your idle handler active, false
		 * to have it removed.  This may be called if there are still messages
		 * pending in the queue, but they are all scheduled to be dispatched
		 * after the current time.
		 */
		// 返回true表示保持在MessageQueue的mIdleHandlers中
		boolean queueIdle();
	}

	/**
	 * Add a new {@link IdleHandler} to this message queue.  This may be
	 * removed automatically for you by returning false from
	 * {@link IdleHandler#queueIdle IdleHandler.queueIdle()} when it is
	 * invoked, or explicitly removing it with {@link #removeIdleHandler}.
	 * <p>
	 * <p>This method is safe to call from any thread.
	 *
	 * @param handler The IdleHandler to be added.
	 */
	public void addIdleHandler(IdleHandler handler) {
		if (handler == null) {
			throw new NullPointerException("Can't add a null IdleHandler");
		}
		synchronized (this) {
			mIdleHandlers.add(handler);
		}
	}

	/**
	 * Remove an {@link IdleHandler} from the queue that was previously added
	 * with {@link #addIdleHandler}.  If the given object is not currently
	 * in the idle list, nothing is done.
	 *
	 * @param handler The IdleHandler to be removed.
	 */
	public void removeIdleHandler(IdleHandler handler) {
		synchronized (this) {
			mIdleHandlers.remove(handler);
		}
	}

	MessageQueue(boolean quitAllowed) {
		mQuitAllowed = quitAllowed;
		//通过native方法初始化消息队列，其中mPtr是供native代码使用
		mPtr = nativeInit();
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			dispose();
		} finally {
			super.finalize();
		}
	}

	// Disposes of the underlying message queue.
	// Must only be called on the looper thread or the finalizer.
	private void dispose() {
		if (mPtr != 0) {
			nativeDestroy(mPtr);
			mPtr = 0;
		}
	}

	Message next() {
		// Return here if the message loop has already quit and been disposed.
		// This can happen if the application tries to restart a looper after quit
		// which is not supported.
		//如果消息循环已经退出并被处理，返回这里。如果应用程序试图在不支持退出后重新启动looper，就会发生这种情况。
		final long ptr = mPtr;
		//当消息循环已经退出，则直接返回
		if (ptr == 0) {
			return null;
		}

		int pendingIdleHandlerCount = -1; // -1 only during first iteration  // 循环迭代的首次为-1
		int nextPollTimeoutMillis = 0;//代表下一个消息到来前，还需要等待的时长；当nextPollTimeoutMillis = -1时，表示消息队列中无消息，会一直等待下去。
		//无限循环，如果队列中没有消息，那么next()方法就会一直阻塞在这里，当新消息到来的时候，next方法就会返回这条消息并且将其从链表中移除
		for (; ; ) {
			if (nextPollTimeoutMillis != 0) {
				Binder.flushPendingCommands();
			}
			//在主线程的MessageQueue没有消息时，便阻塞在loop的queue.next()中的nativePollOnce()方法里
			//此时主线程会释放CPU资源进入休眠状态，直到下个消息到达或者有事务发生，通过往pipe管道写端写入数据来唤醒主线程工作。
			//阻塞操作，当等待nextPollTimeoutMillis时长，或者消息队列被唤醒，都会返回
			nativePollOnce(ptr, nextPollTimeoutMillis);

			synchronized (this) {
				// Try to retrieve the next message.  Return if found.
				final long now = SystemClock.uptimeMillis();
				Message prevMsg = null;
				Message msg = mMessages;
				if (msg != null && msg.target == null) {
					//msg.target为空是一类特殊消息（栅栏消息），用于阻塞所有同步消息，但是对异步消息没有影响，
                    //在这个前提下，当头部是特殊消息时需要往后找是否有异步消息
					// Stalled by a barrier.  Find the next asynchronous message in the queue.
					//当消息Handler为空时，查询MessageQueue中的下一条异步消息msg，则退出循环。
					do {
						prevMsg = msg;
						msg = msg.next;
					} while (msg != null && !msg.isAsynchronous());
				}

				// 走到这一步, 有两种可能,
				// 一种是遍历到队尾没有发现异步消息,
				// 另一种是找到queue中的第一个异步消息

				if (msg != null) { // 找到queue中的第一个异步消息
					if (now < msg.when) { // 没有到消息的执行时间
						// Next message is not ready.  Set a timeout to wake up when it is ready.
						//当异步消息触发时间大于当前时间，则设置下一次轮询的超时时长
						nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
					} else {// 当前消息到达可以执行的时间, 直接返回这个msg
						// Got a message.
						// 获取一条消息，并返回
						mBlocked = false;
						if (prevMsg != null) {
							prevMsg.next = msg.next;
						} else {
							mMessages = msg.next;
						}
						msg.next = null;
						if (false) Log.v("MessageQueue", "Returning message: " + msg);
						return msg;
					}
				} else {
					// No more messages.                //没有消息
					nextPollTimeoutMillis = -1;
				}

				// Process the quit message now that all pending messages have been handled.
				//消息正在退出，返回null
				if (mQuitting) {
					dispose();
					return null;
				}

				// If first time idle, then get the number of idlers to run.
				// Idle handles only run if the queue is empty or if the first message
				// in the queue (possibly a barrier) is due to be handled in the future.
				// 如果queue中没有msg, 或者msg没到可执行的时间,那么现在线程就处于空闲时间了, 可以执行IdleHandler了
				if (pendingIdleHandlerCount < 0 && (mMessages == null || now < mMessages.when)) {
					// pendingIdleHandlerCount在进入for循环之前是被初始化为-1的  并且没有更多地消息要进行处理
					pendingIdleHandlerCount = mIdleHandlers.size();
				}
				if (pendingIdleHandlerCount <= 0) {
					//没有idle handlers 需要运行，则循环并等待。
					mBlocked = true;
					continue;
				}

				if (mPendingIdleHandlers == null) {
					mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
				}
				mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
			}

			// Run the idle handlers.
			// We only ever reach this code block during the first iteration.
			//只有第一次循环时，会运行idle handlers，执行完成后，重置pendingIdleHandlerCount为0.
			for (int i = 0; i < pendingIdleHandlerCount; i++) {
				final IdleHandler idler = mPendingIdleHandlers[i];
				//只有第一次循环时，会运行idle handlers，执行完成后，重置pendingIdleHandlerCount为0.
				mPendingIdleHandlers[i] = null; // release the reference to the handler

				boolean keep = false;
				try {
					keep = idler.queueIdle();//idle时执行的方法
				} catch (Throwable t) {
					Log.wtf("MessageQueue", "IdleHandler threw exception", t);
				}

				if (!keep) {
					synchronized (this) {
						// 如果之前addIdleHandler中返回为false,
						// 就在执行完这个IdleHandler的callback之后, 将这个idler移除掉
						mIdleHandlers.remove(idler);
					}
				}
			}

			// Reset the idle handler count to 0 so we do not run them again.
			//重置idle handler个数为0，以保证不会再次重复运行
			pendingIdleHandlerCount = 0;

			// While calling an idle handler, a new message could have been delivered
			// so go back and look again for a pending message without waiting.
			//当调用一个空闲handler时，一个新message能够被分发，因此无需等待可以直接查询pending message.
			nextPollTimeoutMillis = 0;
		}
	}

	void quit(boolean safe) {
		// 当mQuitAllowed为false，表示不运行退出，强行调用quit()会抛出异常
		if (!mQuitAllowed) {
			throw new IllegalStateException("Main thread not allowed to quit.");
		}

		synchronized (this) {
			if (mQuitting) {//防止多次执行退出操作
				return;
			}
			mQuitting = true;

			if (safe) {
				//移除尚未触发的所有消息
				removeAllFutureMessagesLocked();
			} else {//移除所有的消息
				removeAllMessagesLocked();
			}

			// We can assume mPtr != 0 because mQuitting was previously false.
			//mQuitting=false，那么认定为 mPtr != 0
			nativeWake(mPtr);
		}
	}

	int enqueueSyncBarrier(long when) {//Barrier 拦截器 在这个拦截器后面的消息都暂时无法执行，直到这个拦截器被移除了，
		// Enqueue a new sync barrier token.
		// We don't need to wake the queue because the purpose of a barrier is to stall it.
		//创建一个target为空的特殊消息，并根据when插入MessageQueue中合适的位置
		// 无需唤醒因为栅栏消息的目的在于阻塞消息的执行
		synchronized (this) {
			final int token = mNextBarrierToken++;
			final Message msg = Message.obtain();
			msg.markInUse();
			msg.when = when;
			msg.arg1 = token;

			Message prev = null;
			Message p = mMessages;
			if (when != 0) {
				while (p != null && p.when <= when) {
					prev = p;
					p = p.next;
				}
			}
			if (prev != null) { // invariant: p == prev.next
				msg.next = p;
				prev.next = msg;
			} else {
				msg.next = p;
				mMessages = msg;
			}
			return token;
		}
	}

	void removeSyncBarrier(int token) {
		// Remove a sync barrier token from the queue.
		// If the queue is no longer stalled by a barrier then wake it.
		// 移除token对应的栅栏消息，并在必要的时候进行唤醒
		synchronized (this) {
			Message prev = null;
			Message p = mMessages;
			while (p != null && (p.target != null || p.arg1 != token)) {
				prev = p;
				p = p.next;
			}
			if (p == null) {
				throw new IllegalStateException("The specified message queue synchronization " + " barrier token has not been posted or has already been removed.");
			}
			final boolean needWake;
			if (prev != null) {
				prev.next = p.next;
				needWake = false;
			} else {
				mMessages = p.next;
				needWake = mMessages == null || mMessages.target != null;
			}
			p.recycleUnchecked();

			// If the loop is quitting then it is already awake.
			// We can assume mPtr != 0 when mQuitting is false.
			if (needWake && !mQuitting) {
				//用于唤醒功能
				nativeWake(mPtr);
			}
		}
	}

	boolean enqueueMessage(Message msg, long when) {
		// 每一个普通Message必须有一个target
		if (msg.target == null) {
			throw new IllegalArgumentException("Message must have a target.");
		}
		if (msg.isInUse()) {
			throw new IllegalStateException(msg + " This message is already in use.");
		}

		synchronized (this) {
			if (mQuitting) {//正在退出时
				IllegalStateException e = new IllegalStateException(msg.target + " sending message to a Handler on a dead thread");
				Log.w("MessageQueue", e.getMessage(), e);
				msg.recycle();//回收msg，加入到消息池
				return false;
			}

			msg.markInUse();
			msg.when = when;
			Message p = mMessages;
			boolean needWake;
			if (p == null || when == 0 || when < p.when) {
				// New head, wake up the event queue if blocked. 如果阻塞，唤醒事件队列。
				//p为null(代表MessageQueue没有消息） 或者msg的触发时间是队列中最早的， 则进入该该分支
				msg.next = p;
				mMessages = msg;
				needWake = mBlocked;//当阻塞时需要唤醒
			} else {
				// Inserted within the middle of the queue.  Usually we don't have to wake
				// up the event queue unless there is a barrier at the head of the queue
				// and the message is the earliest asynchronous message in the queue.
				//插入队列中间。 通常，我们不必唤醒事件队列，除非队列头部存在障碍，并且消息是队列中最早的异步消息。
				needWake = mBlocked && p.target == null && msg.isAsynchronous();
				Message prev;
				for (; ; ) {
					prev = p;
					p = p.next;
					if (p == null || when < p.when) {
						break;
					}
					if (needWake && p.isAsynchronous()) {
						needWake = false;
					}
				}
				msg.next = p; // invariant: p == prev.next
				prev.next = msg;
			}

			// We can assume mPtr != 0 because mQuitting is false.
			//消息没有退出，我们认为此时mPtr != 0
			if (needWake) {
				//用于唤醒功能
				nativeWake(mPtr);
			}
		}
		return true;
	}

	boolean hasMessages(Handler h, int what, Object object) {
		if (h == null) {
			return false;
		}

		synchronized (this) {
			Message p = mMessages;
			while (p != null) {
				if (p.target == h && p.what == what && (object == null || p.obj == object)) {
					return true;
				}
				p = p.next;
			}
			return false;
		}
	}

	boolean hasMessages(Handler h, Runnable r, Object object) {
		if (h == null) {
			return false;
		}

		synchronized (this) {
			Message p = mMessages;
			while (p != null) {
				if (p.target == h && p.callback == r && (object == null || p.obj == object)) {
					return true;
				}
				p = p.next;
			}
			return false;
		}
	}

	boolean isIdling() {
		synchronized (this) {
			return isIdlingLocked();
		}
	}

	private boolean isIdlingLocked() {
		// If the loop is quitting then it must not be idling.
		// We can assume mPtr != 0 when mQuitting is false.
		return !mQuitting && nativeIsIdling(mPtr);
	}

	void removeMessages(Handler h, int what, Object object) {
		if (h == null) {
			return;
		}

		synchronized (this) {
			Message p = mMessages;
			//从消息队列的头部开始，移除所有符合条件的消息
			// Remove all messages at front.
			while (p != null && p.target == h && p.what == what && (object == null || p.obj == object)) {
				Message n = p.next;
				mMessages = n;
				p.recycleUnchecked();
				p = n;
			}
			//移除剩余的符合要求的消息
			// Remove all messages after front.
			while (p != null) {
				Message n = p.next;
				if (n != null) {
					if (n.target == h && n.what == what && (object == null || n.obj == object)) {
						Message nn = n.next;
						n.recycleUnchecked();
						p.next = nn;
						continue;
					}
				}
				p = n;
			}
		}
	}

	void removeMessages(Handler h, Runnable r, Object object) {
		if (h == null || r == null) {
			return;
		}

		synchronized (this) {
			Message p = mMessages;

			// Remove all messages at front.
			while (p != null && p.target == h && p.callback == r && (object == null || p.obj == object)) {
				Message n = p.next;
				mMessages = n;
				p.recycleUnchecked();
				p = n;
			}

			// Remove all messages after front.
			while (p != null) {
				Message n = p.next;
				if (n != null) {
					if (n.target == h && n.callback == r && (object == null || n.obj == object)) {
						Message nn = n.next;
						n.recycleUnchecked();
						p.next = nn;
						continue;
					}
				}
				p = n;
			}
		}
	}

	void removeCallbacksAndMessages(Handler h, Object object) {
		if (h == null) {
			return;
		}

		synchronized (this) {
			Message p = mMessages;

			// Remove all messages at front.
			while (p != null && p.target == h && (object == null || p.obj == object)) {
				Message n = p.next;
				mMessages = n;
				p.recycleUnchecked();
				p = n;
			}

			// Remove all messages after front.
			while (p != null) {
				Message n = p.next;
				if (n != null) {
					if (n.target == h && (object == null || n.obj == object)) {
						Message nn = n.next;
						n.recycleUnchecked();
						p.next = nn;
						continue;
					}
				}
				p = n;
			}
		}
	}

	private void removeAllMessagesLocked() {
		Message p = mMessages;
		while (p != null) {
			Message n = p.next;
			p.recycleUnchecked();
			p = n;
		}
		mMessages = null;
	}

	private void removeAllFutureMessagesLocked() {
		final long now = SystemClock.uptimeMillis();
		Message p = mMessages;
		if (p != null) {
			if (p.when > now) {
				removeAllMessagesLocked();
			} else {
				Message n;
				for (; ; ) {
					n = p.next;
					if (n == null) {
						return;
					}
					if (n.when > now) {
						break;
					}
					p = n;
				}
				p.next = null;
				do {
					p = n;
					n = p.next;
					p.recycleUnchecked();
				} while (n != null);
			}
		}
	}

	void dump(Printer pw, String prefix) {
		synchronized (this) {
			long now = SystemClock.uptimeMillis();
			int n = 0;
			for (Message msg = mMessages; msg != null; msg = msg.next) {
				pw.println(prefix + "Message " + n + ": " + msg.toString(now));
				n++;
			}
			pw.println(prefix + "(Total messages: " + n + ", idling=" + isIdlingLocked() + ", quitting=" + mQuitting + ")");
		}
	}
}
