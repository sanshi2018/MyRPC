package rpc;

import basic.service.Service;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rpc.connect.Connector;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地服务器
 */
public class LocalServer {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 本地服务器ID
     */
    private final int id;

    /**
     * 刷帧间隔，单位毫秒
     */
    private int updateInterval = 50;

    /**
     * 调用超时时间，单位秒
     */
    private int callTtl = 10;

    /**
     * 维护的链接
     */
    private Set<Connector> connectors = new HashSet<>();

    /**
     * 管理所有的工作线程，key:工作线程ID value:工作线程
     */
    private Map<Integer, Worker> workers = new HashMap<>();

    /**
     * 管理所有的服务，key:服务ID value:服务
     */
    private final Map<Object, Service> services = new HashMap<>();

    /**
     * 是否运行中
     */
    private boolean running;

    /**
     * 定时任务执行器
     */
    private ScheduledExecutorService executor;

    /**
     * 协议处理器
     */
    public ProtocolHandle protocolHandle;

    public LocalServer(int id, int workerNum, Connector... connectors) {
        Validate.isTrue(id > 0, "服务器ID必须是正整数");
        this.id = id;
        this.initConnectors(connectors);
        this.initWorkers(workerNum);
        protocolHandle = new ProtocolHandle(this);
    }

    private void initConnectors(Connector[] connectors) {
        if (connectors == null || connectors.length == 0) {
            this.connectors = Collections.emptySet();
            return;
        }
        for (Connector connector : connectors) {
            this.connectors.add(connector);
        }
    }

    private void initWorkers(int workerNum) {
        workerNum = Math.max(workerNum, 1);
        for (int i = 0; i < workerNum; i++) {
            Worker worker = new Worker(i,this);
            workers.put(i, worker);
        }
        workers = Collections.unmodifiableMap(workers);
    }

    public void start() {
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::update, 0, updateInterval, TimeUnit.MILLISECONDS);
        workers.values().forEach(Worker::start);
        connectors.forEach(Connector::start);
        running = true;
    }

    protected void update() {
        if (!running) {
            return;
        }
        try {
            connectors.forEach(Connector::update);
            workers.values().forEach(Worker::update);
        }catch (Exception e){
            logger.error("服务器刷帧异常", e);
        }
    }

    // get id
    public int getId() {
        return id;
    }
    public Connector getConnectorByRemoteSrvId(int remoteId) {
        for (Connector connector : connectors) {
            if (connector.getRemoteId() == remoteId) {
                return connector;
            }
        }
        return null;
    }
    // get worker by id
    public Worker getWorkerById(int workerId) {
        return workers.get(workerId);
    }

    // region Service 相关

    // get service by id
    public Service getService(Object serviceId) {
        return services.get(serviceId);
    }

    public void addService(Service service) {
        addService(getWorker(), service);
    }
    public void addService(Worker worker, Service service) {
        Object serviceId = Objects.requireNonNull(service.getId(), "服务ID不能为空");
        if (services.putIfAbsent(serviceId, service) == null) {
            worker.execute(() -> worker.doAddService(service));
        }else {
            logger.error("服务ID重复，serviceId={}", serviceId);
        }
    }

    public void removeService(Object serviceId) {
        Service service = services.remove(serviceId);
        if (service == null) {
            logger.error("服务不存在，serviceId={}", serviceId);
            return;
        }
        Worker worker = service.getWorker();
        worker.execute(() -> worker.doRemoveService(service));
    }

    // endregion




}
