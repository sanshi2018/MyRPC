package rpc;


import basic.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

public class Worker implements Executor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int id;

    /**
     * callId的序列
     */
    private static int nextCall = 1;

    private volatile boolean running;

    private LocalServer localServer;

    private Thread thread;

    WorkerMsgHandle msgHandler;

    private static ThreadLocal<Worker> threadLocal = new ThreadLocal<>();

    private BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    /**
     * 管理所有的服务，key:服务ID value:服务
     */
    private final Map<Object, Service> allServices = new HashMap<>();


    public Worker(int id, LocalServer localServer) {
        this.id = id;
        this.localServer = localServer;
        this.msgHandler = new WorkerMsgHandle(this);
    }

    protected void start() {
        thread = new Thread(this::run);
        thread.setName("Worker-" + id);
        thread.start();
        execute(() -> { allServices.values().forEach(this::initService); });
    }

    protected void stop() {
        execute(() -> {
            allServices.values().forEach(this::destroyService);
            running = false;
        });
    }

    protected void run() {
        // 提交task放到线程池中去执行
        threadLocal.set(this);
        running = true;
        while (running) {
            try {
                taskQueue.take().run();
            } catch (InterruptedException e) {
                logger.error("执行任务失败", e);
            }
        }

        taskQueue.clear();
        threadLocal.set(null);
        thread = null;
    }

    protected void update() {
        return;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "提交的【task】不能为空");
        try {
            taskQueue.put(task);
        } catch (InterruptedException e) {
            logger.error("提交任务失败", e);
        }
    }

    // region ServiceHandler
    protected void doAddService(Service service) {
        service.worker = this;
        allServices.put(service.getId(), service);
        if (running) {
            initService(service);
        }

    }

    private void initService(Service service) {
        try {
            service.init();
        } catch (Exception e) {
            logger.error("初始化服务失败", e);
        }
    }

    protected void doRemoveService(Service service) {
        Object serviceId = service.getId();
        if (running) {
            destroyService(service);
        }
        service.worker = null;
        allServices.remove(serviceId);
        if (running) {
            destroyService(service);
        }
    }

    private void destroyService(Service service) {
        try {
            service.destroy();
        } catch (Exception e) {
            logger.error("销毁[{}]服务失败",service.getId() ,e);
        }
    }
    // endregion


}
