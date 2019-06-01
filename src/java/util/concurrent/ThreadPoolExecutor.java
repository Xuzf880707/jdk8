/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute}, and fewer
 * than corePoolSize threads are running, a new thread is created to
 * handle the request, even if other worker threads are idle.  If
 * there are more than corePoolSize but less than maximumPoolSize
 * threads running, a new thread will be created only if the queue is
 * full.  By setting corePoolSize and maximumPoolSize the same, you
 * create a fixed-size thread pool. By setting maximumPoolSize to an
 * essentially unbounded value such as {@code Integer.MAX_VALUE}, you
 * allow the pool to accommodate an arbitrary number of concurrent
 * tasks. Most typically, core and maximum pool sizes are set only
 * upon construction, but they may also be changed dynamically using
 * {@link #setCorePoolSize} and {@link #setMaximumPoolSize}. </dd>
 *
 * <dt>On-demand construction</dt>
 *
 * <dd> By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime}). This
 * provides a means of reducing resource consumption when the pool is
 * not being actively used. If the pool becomes more active later, new
 * threads will be constructed. This parameter can also be changed
 * dynamically using method {@link #setKeepAliveTime}. Using a value
 * of {@code Long.MAX_VALUE} {@link TimeUnit#NANOSECONDS} effectively
 * disables idle threads from ever terminating prior to shut down. By
 * default, the keep-alive policy applies only when there are more
 * than corePoolSizeThreads. But method {@link
 * #allowCoreThreadTimeOut(boolean)} can be used to apply this
 * time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * <li> If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * <li> If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * </ul>
 *
 * There are three general strategies for queuing:
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.  </li>
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.  </li>
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.  </li>
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd> New tasks submitted in method {@link #execute} will be
 * <em>rejected</em> when the Executor has been shut down, and also
 * when the Executor uses finite bounds for both maximum threads and
 * work queue capacity, and is saturated.  In either case, the {@code
 * execute} method invokes the {@link
 * RejectedExecutionHandler#rejectedExecution} method of its {@link
 * RejectedExecutionHandler}.  Four predefined handler policies are
 * provided:
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>
 *
 * <dd>This class provides {@code protected} overridable {@link
 * #beforeExecute} and {@link #afterExecute} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log
 * entries. Additionally, method {@link #terminated} can be overridden
 * to perform any special processing that needs to be done once the
 * Executor has fully terminated.
 *
 * <p>If hook or callback methods throw exceptions, internal worker
 * threads may in turn fail and abruptly terminate.</dd>
 *
 * <dt>Queue maintenance</dt>
 *
 * <dd> Method {@link #getQueue} allows access to the work queue for
 * purposes of monitoring and debugging.  Use of this method for any
 * other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove} and {@link #purge} are available to assist in
 * storage reclamation when large numbers of queued tasks become
 * cancelled.</dd>
 *
 * <dt>Finalization</dt>
 *
 * <dd> A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * </dl>
 *
 * <p> <b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     *   workerCount, indicating the effective number of threads
     *   runState,    indicating whether running, shutting down etc
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * The runState provides the main lifecyle control, taking on values:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *   TIDYING:  All tasks have terminated, workerCount is zero,
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *   TERMINATED: terminated() has completed
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */
    //所有的状态都是用一个32bit的值来表示
    // 0-2位：前3位表示线程池的状态，第0位是1表示线程池是running获得状态
    //3-32位：表示线程的容量
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    //Integer共有32位，最右边29位表示工作线程数，最左边3位表示线程池状态
    //注：简单的说，3个二进制可以表示从0至7的8个不同的数值
    //Integer.SIZE=32,COUNT_BITS=32-3=29
    private static final int COUNT_BITS = Integer.SIZE - 3;
    // 0000-0000-0000-0000-0000-0000-0000-0001 -> 0010-0000-0000-0000-0000-0000-0000-0000
    // (0010-0000-0000-0000-0000-0000-0000-0000) - 1
    //  =
    //0001-1111-1111-1111-1111-1111-1111-1111 持有线程最大的容量
    //类似于子网掩码，用于位的与运算，得到左边3位，还是右边29位
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    //1111-1111-1111-1111-1111-1111-1111-1111
    //      ->
    //1110-0000-0000-0000-0000-0000-0000-0000
    //用左边3位，实现5中线程状态，此状态表示线程池能接受任务
    private static final int RUNNING    = -1 << COUNT_BITS;
    //0000-0000-0000-0000-0000-0000-0000-0000
    //此状态表示线程池不再接受任务，但是可以继续执行队列中的任务
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    //0010-0000-0000-0000-0000-0000-0000-0000
    //此状态全面拒绝，并中断正在处理的任务
    private static final int STOP       =  1 << COUNT_BITS;
    //0000-0000-0000-0000-0000-0000-0000-0010
    //  ->
    //0100-0000-0000-0000-0000-0000-0000-0000
    //此状态表示所有任务已经被终止
    private static final int TIDYING    =  2 << COUNT_BITS;
    // //0000-0000-0000-0000-0000-0000-0000-0011
    //  ->
    //0110-0000-0000-0000-0000-0000-0000-0000
    //此状态表示已清理完线程
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    //~CAPACITY =1110-0000-0000-0000-0000-0000-0000-0000
    //c & ~CAPACITY可以获得线程池的状态
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    //CAPACITY =0001-1111-1111-1111-1111-1111-1111-1111
    //c & CAPACITY 可以得到工作线程worker的数量
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    //rs就是状态值，wc就是工作数，这两个进行或操作，就能得到ctl的值（32bit的值）,ctl的前3位代表线程池状态，后29代表线程的数量
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    /***
     * 判断线程状态，比较：c>=s
     * @param c
     * @param s
     * @return
     */
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }
    //running状态时，32bit的首位是1，所以值肯定是负数，而SHUTDOWN=0
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempt to CAS-increment the workerCount field of ctl.
     */
    /***
     * 利用cas来添加一个工作线程worker
     * @param expect
     * @return
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempt to CAS-decrement the workerCount field of ctl.
     * 利用cas来减少一个工作线程worker
     *
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     * 当需要访问工作线程集合和相关的记录的时候用来枷锁。虽然我们可以用使用某种类型的并发集合，
     * 但是在震慑力使用lock通常来说更好。原因之一是对于这些系列化的interruptIdleWorkers
     * 可以不必要的中断，特别是在线程池进行shutdown的时候。否则退出的线程会并发的中断还没有中断的线程。
     * 也会简化相关统计，例如线程池最大容量记录。我们也可以在shutdown和shutdownNow的时候持有锁，
     * 这样可以确保工作线程集合中的工作线程在线程是否可以被中断的检查的稳定性
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread#start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    /***
     * 用于创建线程的工厂
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    /***
     * 订阅拒绝策略
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    /**
     * 允许核心线程超时事件
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    /***
     * 核心线程最大数
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     */
    /***
     * 最大的线程数
     */
    private volatile int maximumPoolSize;

    /**
     * 默认的拒绝策略
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =
            new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
            new RuntimePermission("modifyThread");

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    /***
     * worker是一个实现Runnable的工作线程
     * 1、worker初次启动时
     *      firstTask！=null:则直接调用firstTask.run()
     */
    private final class Worker
            extends AbstractQueuedSynchronizer
            implements Runnable
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** 每个线程完成的任务数计数 */
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         *   根据给定的第一个任务，并通过ThreadFactory创建一个工程线程worker
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    /***
     * 更新线程池状态，如果线程池的状态大于targetState，则不用更新
     *      runStateAtLeast(c, targetState)：比较当前线程池的状态是否大于目标状态targetState
     *      ctlOf(targetState, workerCountOf(c))：获得线程池的值
     *      ctl.compareAndSet：cas来更新线程池状态
     * @param targetState
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    /***
     * 尝试终结线程池。
     *      每个线程worker在关闭退出的时候都要做一次尝试，如果线程池调用了shutdown，且队列为空，且它又是线程池中的最后一个线程，则它就要负责终结线程池。
     *  1、检查线程池状态和队列的情况
     *      如果线程池是runing状态，又或者线程池是TERMINATED或TIDYING，那么就不需要调用终结线程池的动作，直接返回
     *      如果线程池是SHUTDOWN，且队列不为空，则也不应该调用终结线程池的动作，直接返回
     *  2、中断空闲的线程(比如空闲的核心线程)
     *  3、终结线程池
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            //中断一个空闲的线程
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {//中断线程
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    /***
     * 向所有非线程发出中断信息(包括空闲和非空闲的线程)
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    /***
     * 中断线程，这里只会中断的是空闲的线程
     *      如果一个worker线程在运行非空闲的话，则w.tryLock()==false,这就是为什么线程在运行后的时候要lock
     *  总结 ：
     *      当onlyOne=true时，这里可能不会中断任何线程
     *      当onlyOne=false时，这里只会中断所有空闲的线程，非空闲的线程不会被中断
     * @param onlyOne 是否只尝试中断一个空闲线程
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    /***
     * 移除队列中的等待任务，返回被移除的任务列表
     * @return
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        List<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread#start), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    /***
     *根据线程任务添加工作线程worker
     *      检查线程池的状态、当前worker是用来干嘛，是新增的core线程还是在关闭线程池时用来处理剩余的队列的任务等等
     *
     * @param firstTask ：外部启动线程池时需要构造的第一个线程，它是线程的母体
     * @param core：false表示非核心线程(比如核心线程满了 队列也满了)  true表示创建核心线程
     * @return
     * @deprecated
     *      1、检查线程池状态，确认是否可创建工作线程worker，以下三种情况返回false，创建失败
     *          a:线程池状态rs>shutdown,表示线程池已经关闭了，就没必要创建新的工作线程worker
     *          b:线程池状态rs=shutdown&&firstTask！=null，表示线程池已经是shutdown状态，这个时候线程池应该等待已有任务全部执行完后，然后关闭线程池，所以不应该为新的任务firstTask分配新的线程
     *          c:线程池状态rs=shutdown&&firstTask==null&&workQueue.isEmpty(),在线程池被调用shutdown，如果队列已经是空了，就没必要创建新的工作线程worker来处理队列中的任务
     *      2、通过在for循环中用cas操作来创建worker，可保证在高并发的情况下能成功添加统计worker,也能保证worker不会超过配置的参数限制
     *          (1)、如果线程池中的worker数已经超出了容量，则添加worker失败，直接返回false
     *          (2)、如果创建的是核心线程，而线程池中的worker已经超出了核心线程数，则添加worker失败，直接返回false
     *          (3)、如果创建的是非核心线程，而线程池中的worker已经超出了最大线程数，则添加worker失败，直接返回false
     *          (4)、并发CAS的添加worker：
     *              添加失败,则从判断线程池状态的变化，如果发生了改变，则从步骤1重新开始；如果线程池状态未变化，则从步骤2重新开始
     *              添加成功：则可以执行步骤3
     *      3、创建一个worker，并为worker.thread分配一个Thread线程对象,并再次检查线程池的状态变化(高并发的情况下，线程池的状态可能发生改变)
     *          线程池状态rs=running,则将worker添加到集合workers，workerAdded = true
     *          线程池状态rs=shutdown&&fastTask==null，则表示是新建worker用来处理队列中等待的队列，则将worker添加到集合workers，workerAdded = true
     *          其它情况则worker添加失败，并返回workerAdded = false
     *      4、判断workerAdded，如果workerAdded = true则表示worker成功新增成功,则启动worker中的thread线程并开始执行任务，在以下情况下workerStarted=true,其它情况下workerStarted=false
     *          a:worker添加成功，并成功启动worker中的线程thread,worker本身并不启动。这样保证一个worker对应一个线程
     *      5、检查workerStarted
     *          workerStarted=false,表示线程启动失败，则要把worker从workers中移除，且对于worker的统计的值要减一
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        /***
         *
         */
        retry:
        for (;;) {
            int c = ctl.get();//获得线程池的值(包含线程数和状态)
            int rs = runStateOf(c);//线程状态

            // Check if queue empty only if necessary.
            /***
             *  1、如果线程池已经关闭了，状态大于shutdown，则直接返回false，不应该创建新的worker线程。
             *  2、如果线程是shutdown：
             *      如果请求线程为了新任务，则直接拒绝创建线程。
             *      如果请求创建线程是为了协助shutdown指令，处理队列中未完成的任务，则可创建
             */
            if(rs>SHUTDOWN){//当调用shutdownnow的时候，rs=STOP>SHUTDOWN
                return false
            }else if(rs==SHUTDOWN){//可能调用了shutdown，这时候会协助执行完对垒中等待的任务，但是同时也不能接收新的任务
                if(firstTask!=null){//表示添加新任务，则立马拒绝
                    return false
                }else if(workQueue.isEmpty()){//如果队列是空的话，就没必要添加信的线程协助处理队列中的task
                    return false;
                }
            }
//            if (rs >= SHUTDOWN &&
//                ! (rs == SHUTDOWN &&
//                   firstTask == null &&
//                   ! workQueue.isEmpty()))
//                return false;

            for (;;) {
                int wc = workerCountOf(c);//获得当前工作线程数worker
                /***
                 * 如果当前工作线程worker>=CAPACITY 超过线程池最大容量，那肯定不能再创建了
                 * 如果工作线程worker大于核心线程数了，则不能在创建核心线程数了
                 */
                if(wc >= CAPACITY){//如果当前工作线程worker>=CAPACITY 超过线程池最大容量，那肯定不能再创建了
                    return false;
                }
                //用core来表示是否创建核心线程数
                if( wc >= (core ? corePoolSize : maximumPoolSize)){//如果工作线程worker大于核心线程数了，则不能再创建核心线程数了
                    return false;
                }
//                if (wc >= CAPACITY ||
//                    wc >= (core ? corePoolSize : maximumPoolSize))
//                    return false;
                if (compareAndIncrementWorkerCount(c))//增加一个worker,这里用了cas解决高并发的问题，如果添加失败，则继续不断循环
                    break retry;
                c = ctl.get();  // 再次读取线程池的值
                //判断线程池的状态是否发声变化，如果没变，则不断的重复尝试添加一个worker，直到以下三种情况，则中断for循环
                // a:如果是创建核心线程，则超过核心线程，则不允许再添加worker，返回false，
                // b:如果不是创建核心线程，则当达到最大线程了则不允许再添加worker，返回false，
                // c:成功添加worker，则跳出retry循环，继续开始创建新加的worker

                //如果线程池状态发生变化，表示可能在你创建线程的时候，线程池状态被别的线程修改了(比如shutdownnow等)，所以要从头检查线程池状态
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }
        //  开始创建工作线程
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            final ReentrantLock mainLock = this.mainLock;
            //根据当前task，新建一个Worker,并分配一个thread线程
            //worker中有一个属性thread指向一个新建的线程thread
            //worker自己本身也是一个runnable
            w = new Worker(firstTask);
            //注意这是Worker中的属性对象thread
            final Thread t = w.thread;
            if (t != null) {
                //在进行ThreadPollExecutor的敏感操作时，都需要持有主锁，避免在添加和启动线程时被干扰
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int c = ctl.get();//获得当前线程的值
                    int rs = runStateOf(c);//获得当前线程状态
                    /****
                     *  判断线程池状态
                     *  在以下集中情况下，将worker加入到对列中
                     *      a: rs=running 线程池的状态是正常
                     *      b:rs == SHUTDOWN && firstTask == null,比如线程池正常关闭了，这个时候会添加worker来处理队列中待处理的task任务
                     *
                     */
                    //当前线程池状态为RUNNING或SHUTDOWN，且firstTask初始线程为空时
                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        workers.add(w);//将worker添加到workers
                        int s = workers.size();
                        //整个线程池在运行期间最大并发任务个数
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;//添加worker成功
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    //如果worker添加成功，则启动worker中的thread线程,这个thread在start的时候，会调用worker的run方法
                    // 实际上：Thread t = new Thread(Worker);
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            //如果添加失败(比如已经添加到workers中但是启动失败)，则要将worker从workers移除，且计数减1
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     *   如果worker启动失败，则要把worker从workers中移除，并将计数减1，同时要检查线程池是否可关闭
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)//一般是workers添加成功，但是启动worker.thread失败，这个时候就要移除这个worker
                workers.remove(w);
            decrementWorkerCount();//将worker计数恢复
            tryTerminate();//判断线程池状态，决定是否将线程池状态修改为Terminate，因为这个worker有可能是最后一个
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    /***
     * 线程退出(当队列为空，线程数大于核心线程的时候，该线程都会退出销毁)
     * 1、非正常导致退出销毁线程，则线程池中的线程数要减1
     * 2、从workers移除该worker(因为这个线程退出有可能是因为启动线程的时候失败导致线程退出)
     * 3、检查尝试是否终结线程池(如果它是最后一个线程)
     * 4、如果线程池是running或stop的正常状态：
     *      如果是非中断而导致线程退出：
     *          保持最少核心线程数，如果线程池线程数少于最少核心线程数，则要补上一个没有firstTask任务的线程，用于弥补被丢弃的线程
     *
     * @param w
     * @param completedAbruptly
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //如果是被中断退出，则计数要减1
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //汇总线程池完成的总任务
            completedTaskCount += w.completedTasks;
            //从workers移除该worker(因为这个线程退出有可能是因为启动线程的时候失败导致线程退出)
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        //线程每次退出的时候，都会检查线程池状态，并判断自己是不是最后一个待关闭的线程，看是否需要关闭改变线程池的状态，比如从SHUTDOWN到TERMINATED
        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {//如果线程池是running或shutdown
            if (!completedAbruptly) {//如果是非中断而导致线程退出，
                //如果对核心线程设置了空闲时间，则核心线程允许最少为0，不然核心线程最少是corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                //如果当前线程池的总线程数大于最少核心线程数，则不填补被丢弃的线程
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            //保持最少核心线程数，如果线程池线程数少于最少核心线程数，则要补上一个没有firstTask任务的线程，用于弥补被丢弃的线程
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait.
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    /***
     * 尝试从队列中获取的等待的任务
     * 1、如果线程池状态为STOP、TIDYING、TERMINATED，或者说队列为空且线程池状态为shutdown， 则释放返回null(接下来worker会被释放掉)
     * 2、从队列中拉去task任务(这边是控制线程的核心线程数的主要逻辑)
     *      a:如果核心线程设置了空闲时间或者当前线程总数超过核心线程，则表示该线程在获取task的时候，不会一直阻塞下去，会在等待一段时间后返回结果：workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS)，不会一直不阻塞等待
     *          没拿到则返回null
     *      b:如果核心线程未设置空闲时间且当前线程总数小于等于核心线程 workQueue.take()阻塞等待
     * 3、检查拉取的task是否为null
     *      task=null,则表示该worker可以移除，它会返回null给上一层(并在之后被销毁)，并且会更新线程池的值
     * @return
     */
    private Runnable getTask() {
        //timedOut=true的话表示，该线程从队列中没拿到任务并被直接返回(只有线程池中的线程超过核心线程总数或者核心线程设置了超时)
        boolean timedOut = false; // Did the last poll() time out?

        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // 如果线程池状态大于等于SHUTDOWN，表示非正常运行状态，也就是SHUTDOWN、STOP、TIDYING、TERMINATED
            //STOP、TIDYING、TERMINATED||队列为空 则释放该空闲的worker并返回nuLL
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            boolean timed;      // Are workers subject to culling?
            //
            for (;;) {
                //获得线程池中当前的线程总数
                int wc = workerCountOf(c);
                //判断是否允许核心线程数超时销毁，默认是false，或者池中的线程数是否超过核心线程数
                timed = allowCoreThreadTimeOut || wc > corePoolSize;//
                //如果当前线程数少于最大线程数
                if (wc <= maximumPoolSize && ! (timedOut && timed))
                    break;
                if (compareAndDecrementWorkerCount(c))
                    return null;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }

            try {
                //timed==true，表示不阻塞获取(获取不到可销毁)
                //poll():不阻塞等待
                //take()阻塞等待
                //如果当前线程数超过核心线程数，或者核心线程设置了超时时间，则采用poll从队列中获取task任务，否则采用take方法来获取任务
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                if (r != null)
                    return r;
                //如果队列中不存在task了，表示该线程可销毁
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     * worker线程会重复的从队列中拿task任务并执行它们
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     * 我们可以通过启动一个初始task，这样我们就不需要获取第一个task。否则，我们就
     * 需要通过getTask方法取获取一个任务，主要这个线程池始running状态。如果通过getTask
     * 方法返回null,那么worker会由于线程池状态的变化而退出，在一些额外的异常的情况下，worker也会退出。
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and
     * clearInterruptsForTaskRun called to ensure that unless pool is
     * stopping, this thread does not have its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to
     * afterExecute. We separately handle RuntimeException, Error
     * (both of which the specs guarantee that we trap) and arbitrary
     * Throwables.  Because we cannot rethrow Throwables within
     * Runnable.run, we wrap them within Errors on the way out (to the
     * thread's UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * @param w the worker
     */
    /**
     * 开始执行worker工作任务
     * 1、获取待执行的task任务，会先检查firstWork是否null（如果该worker是为了用来处理等待队列中剩余的任务，则它是没有firstTask的）
     *      firstWork==null,则会从等待队列中获取线程
     *      firstWork!=null,则先执行这个firstWork，执行完后继续从对垒中获取任务
     * 2、检查线程池的状态
     *      线程池状态rs>=STOP,中断线程,也就是更新线程状态的中断位
     *      线程池状态rs<STOP,则清除当前线程的中断状态
     * 3、调用beforeExecute(wt, task);这个可以自己扩展，可以在线程执行前做一些准备工作
     * 4、调用task.run()，执行任务的run方法，这里直接调用run方法，避免再启一个线程
     * 5、调用afterExecute(wt, task);这个可以自己扩展，可以做一些善后工作，比如关闭资源
     * 6、执行完所有的task：
     *      如果当前所有的线程数大于core线程数，则getTask()直接返回null，那么会调用processWorkerExit(w, completedAbruptly)正常关闭关闭worker
     *      如果当前所有的线程数小于core线程数，则getTask()会一直阻塞不返回(从而控制核心线程数)
     * @param w
     */
    final void runWorker(Worker w) {
        //获取当前线程，
        Thread wt = Thread.currentThread();
        //获得worker的fristTask线程，可以为null(当线程池关闭的时候，会创建worker用于处理队列中剩下的任务，这个时候fristTask=null)
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // 释放worker的锁，允许被interrupt中断
        boolean completedAbruptly = true;
        try {
            /***
             * 如果worker的firstTask为空，那么就应该通过getTask去队列中拿取任务
             */
            while (task != null || (task = getTask()) != null) {//如果有可被worker执行的task任务
                w.lock();//这边加锁是为了搞死线程池，我现在不是非空闲的，这样在shutdown()时就不会被中断
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                /***
                 * 判断线程状态：
                 *      （线程池状态为TIDYING/STOP/TERMINATED || (线程中断状态为true && 线程池状态为TIDYING/STOP/TERMINATED ) )
                 *          && worker还未被中断
                 线程池状态rs>=STOP,中断线程
                 *          线程池状态rs<STOP,则清除当前线程的中断状态
                 */
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    //这个是扩展方法，可以自己实现，在线程执行前执行
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();//启动线程任务，这里直接调用run方法，避免再启一个线程
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    //清除这个任务，协助gc回收
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }//正常执行完所有的task，并不阻塞返回，则表示该worker可清除，则completedAbruptly=false
            completedAbruptly = false;
        } finally {
            //退出并关闭worker
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argumenWo
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.-
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,ThS
                                      TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    /***
     * 1、检查工作线程worker是否超过核心线程数配置
     * 2、检查线程池状态，看是否需要将线程加入等待队列(只有线程池是running情况下才会被加入队列)
     * 3、如果队列满了，则创建非核心线程
     * 4、非核心线程创建失败，则拒绝该task任务
     *
     * @param command
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        int c = ctl.get();//获得当前线程池的状态值
        //判断线程池中的线程是否小于核心线程数，则创建线程任务并执行
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            //到这里，表示创建失败，为了防止外部已经在线程池中加入新任务，所以重新获取一下当前线程池的状态
            c = ctl.get();
        }//执行到这里，说明add worker失败了，这个时候可能线程池已经关闭，也有可能是核心线程已经满了
        //如果是核心线程已经满了，则将线程加入到等待队列中，这个offer如果队列满了，则直接返回false
        if (isRunning(c) && workQueue.offer(command)) {//只有线程池处于running状态，才会执行后半句：置入队列
            int recheck = ctl.get();//获得线程池值
            //如果线程池不是running状态，则不应该允许添加任务，所以要将之前添加的任务移除
            if (! isRunning(recheck) && remove(command))
                reject(command);//根据拒绝策略处理这个被拒绝的任务
            else if (workerCountOf(recheck) == 0)//如果worker数量是0，就是线程
                addWorker(null, false);//添加非核心线程，且该线程没有fastTask
        }
        else if (!addWorker(command, false))//如果添加非核心线程失败，则拒绝这个任务
            //发生拒绝的理由两种：（1）线程池状态为非RUNNING状态 （2）等待队列已满
            reject(command);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException {@inheritDoc}
     */
    /***
     * 1、检查关闭权限
     * 2、更新线程池状态为SHUTDOWN，线程池状态只能降，不能升
     * 3、向所有的空闲的线程worker发送中断请求（它只中断空闲线程）
     * 4、如果有定时任务，取消定时任务(ScheduledThreadPoolExecutor会重写onShutdown)
     * 5、尝试终止线程池
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    /***
     * 1、检查关闭权限
     * 2、更新线程池状态为STOP，线程池状态只能降，不能升
     * 3、直接向所有的工作线程worker发出中断请求(包括空闲和非空闲的)
     * 4、移除线程池中所有的队列
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return true if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        shutdown();
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
                addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p> This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return true if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                        "Shutting down"));
        return super.toString() +
                "[" + rs +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always.
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " +
                    e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}

