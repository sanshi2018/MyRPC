package rpc;

import basic.protocol.Protocol;
import basic.protocol.Request;
import basic.protocol.Response;
import basic.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rpc.connect.Connector;

public class ProtocolHandle {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    LocalServer localServer;
    public ProtocolHandle(LocalServer localServer) {
        this.localServer = localServer;
    }

    /**
     * 发送远端服务器
     * @param targetServerId 目标服务器ID
     * @param protocol 协议
     */
    protected void sendProtocol(int targetServerId, Protocol protocol) {
        Connector connector = localServer.getConnectorByRemoteSrvId(targetServerId);
        if (connector != null) {
            connector.sendProtocol(targetServerId, protocol);
        } else {
            throw new IllegalArgumentException(String.format("远程服务器[%s]不存在", targetServerId));
        }
    }

    /**
     * 发送RPC请求
     */
    protected void sendRequest(int targetServerId, Request request, int securityModifier) {
        if (targetServerId == localServer.getId() || targetServerId == 0) {
            //本地服务器直接处理
            handleRequest(request, securityModifier);
        } else {
            sendProtocol(targetServerId, request);
        }
    }

    /**
     * 处理RPC请求
     */
    protected void handleRequest(Request request, int securityModifier) {
        Service service = localServer.getService(request.getServiceId());
        if (service == null) {
            logger.error("处理RPC请求，服务[" + request.getServiceId() + "]不存在");
        } else {
            Worker worker = service.getWorker();
            worker.execute(() -> worker.handleRequest(request, securityModifier));
        }
    }

    /**
     * 发送RPC响应
     */
    protected void sendResponse(int targetServerId, Response response) {
        if (targetServerId == localServer.getId()) {
            //本地服务器直接处理
            handleResponse(response);
        } else {
            sendProtocol(targetServerId, response);
        }
    }

    /**
     * 处理RPC响应
     */
    protected void handleResponse(Response response) {
        int workerId = (int) (response.getCallId() >> 32);
        Worker worker = localServer.getWorkerById(workerId);
        if (worker != null) {
            worker.execute(() -> worker.handleResponse(response));
        } else {
            logger.error("处理RPC响应，worker线程[{}}]不存在, originServerId:{},callId:{}",workerId, response.getServerId(), response.getCallId());
        }

    }


}
