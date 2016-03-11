package com.bbbbiu.biu.httpd;


import android.util.Log;

import com.bbbbiu.biu.httpd.servlet.StaticServlet;
import com.bbbbiu.biu.httpd.util.ContentType;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP服务器。
 * <p/>
 * RequestListener 监听TCP端口，等待请求到来
 * RequestManager 管理请求，每个请求开一个线程处理
 * RequestHandler 处理请求，并返回
 */
public class HttpDaemon {
    public static final String TAG = HttpDaemon.class.getSimpleName();


    public static final int SOCKET_READ_TIMEOUT = 10000;
    private volatile ServerSocket mServerSocket;

    private final int mPort;
    private Thread mListenThread;
    private RequestManager mRequestManager;


    private boolean isDownload;
    private boolean isUpload;

    public void setIsUpload() {
        this.isUpload = true;
        this.isDownload = false;
    }

    public void setIsDownload() {
        this.isDownload = true;
        this.isUpload = false;
    }


    /**
     * 关联URL与其对应的{@link HttpServlet}
     */
    private static HashMap<String, HttpServlet> servletMap;
    public static final String STATIC_FILE_REG = "/static/.*";

    public int getPort() {
        return mPort;
    }

    /**
     * 指定监听端口
     *
     * @param port 端口
     */
    public HttpDaemon(int port) {
        mPort = port;
        mRequestManager = new RequestManager();

        servletMap = new HashMap<>();
    }

    /**
     * 注册 Servlet
     *
     * @param urlPattern servlet处理的URL（正则表达式）
     * @param servlet    servlet实例
     */
    public static void regServlet(String urlPattern, HttpServlet servlet) {
        servletMap.put(urlPattern, servlet);
    }


    /**
     * 安全地关闭流
     *
     * @param closable 可关闭对象
     */
    public static void safeClose(Object closable) {
        try {
            if (closable != null) {
                if (closable instanceof Closeable) {
                    ((Closeable) closable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close", e);
        }
    }

    /**
     * 启动服务器
     *
     * @throws IOException 端口已被使用
     */
    public void start() throws IOException {
        mServerSocket = new ServerSocket();
        mServerSocket.setReuseAddress(true);

        RequestListener requestListener = new RequestListener(SOCKET_READ_TIMEOUT);
        mListenThread = new Thread(requestListener);
        mListenThread.setDaemon(true);
        mListenThread.setName("Httpd Main Listener");
        mListenThread.start();

        // 等待另一个线程中的 ServerSocket 绑定端口成功
        while ((!requestListener.wasBinned()) && requestListener.getBindException() == null) {
            try {
                Thread.sleep(10L);
            } catch (Throwable ignored) {
            }
        }

        // 端口已被占用
        if (requestListener.getBindException() != null) {
            Log.i(TAG, "Port in use");
            throw requestListener.bindException;
        }
    }

    /**
     * 关闭服务器，所有后台线程
     */
    public void stop() {
        try {
            safeClose(mServerSocket);
            mRequestManager.closeAll();
            if (mListenThread != null) {
                mListenThread.join();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not stop all connections ", e);
        }
    }

    /**
     * 是否Alive
     */
    public final boolean isAlive() {
        return wasStarted() && !this.mServerSocket.isClosed() && this.mListenThread.isAlive();
    }

    /**
     * 是否启动了
     */
    public final boolean wasStarted() {
        return this.mServerSocket != null && this.mListenThread != null;
    }

    /**
     * 服务器主程序，监听请求
     */
    private class RequestListener implements Runnable {

        private final int timeout;


        public IOException bindException;


        public boolean hasBinned = false;


        public boolean wasBinned() {
            return hasBinned;
        }

        public IOException getBindException() {
            return bindException;
        }


        public RequestListener(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public void run() {
            try {
                mServerSocket.bind(new InetSocketAddress(mPort)); //监听端口
                hasBinned = true;
            } catch (IOException e) {
                this.bindException = e;
                return;
            }
            do {
                try {
                    final Socket finalAccept = mServerSocket.accept(); //等待请求
                    if (timeout > 0) {
                        finalAccept.setSoTimeout(timeout);
                    }
                    final InputStream inputStream = finalAccept.getInputStream(); //获取HTTP Request输入流

                    mRequestManager.handleRequest(inputStream, finalAccept); // 开线程，处理请求

                } catch (IOException e) {
                    Log.w(TAG, "Communication with the client broken");
                }
            } while (!mServerSocket.isClosed()); //不断等待请求
        }
    }


    /**
     * 分配，管理请求
     */
    private class RequestManager {
        private final String TAG = RequestManager.class.getSimpleName();
        private long requestCount;

        private final List<RequestHandler> running = Collections.synchronizedList(new ArrayList<RequestHandler>());

        public void handleRequest(InputStream inputStream, Socket acceptSocket) {
            RequestHandler requestHandler = new RequestHandler(inputStream, acceptSocket);
            running.add(requestHandler);
            requestCount++;

            Log.i(TAG, String.format("Request #%s come from %s",
                    requestCount, acceptSocket.getInetAddress().getHostAddress()));

            Thread thread = new Thread(requestHandler);
            thread.setDaemon(true);
            thread.setName("[RequestHandler #" + this.requestCount + "] ");
            thread.start();
        }


        public void close(RequestHandler requestHandler) {
            running.remove(requestHandler);
        }

        public void closeAll() {
            // copy of the list for concurrency
            for (RequestHandler requestHandler : new ArrayList<>(running)) {
                requestHandler.close();
            }
        }
    }


    /**
     * 处理请求
     */
    private class RequestHandler implements Runnable {
        private final String TAG = RequestHandler.class.getSimpleName();

        private InputStream inputStream;
        private OutputStream outputStream;
        private Socket acceptSocket;

        public RequestHandler(InputStream inputStream, Socket acceptSocket) {
            this.inputStream = inputStream;
            this.acceptSocket = acceptSocket;
            outputStream = null;
        }

        public void close() {
            if (outputStream != null) {
                safeClose(outputStream);
            }
            safeClose(inputStream);
            safeClose(acceptSocket);
        }


        @Override
        public void run() {
            try {
                outputStream = acceptSocket.getOutputStream();

                HttpRequest request = HttpRequest.parseRequest(inputStream, acceptSocket.getInetAddress());
                HttpResponse response;


                if (request == null) {
                    response = HttpResponse.newResponse(HttpResponse.Status.BAD_REQUEST, ContentType.MIME_PLAINTEXT,
                            HttpResponse.Status.BAD_REQUEST.getDescription());
                    response.send(outputStream);
                    return;
                }


                String acceptEncoding = request.getHeaders().get("accept-encoding");
                boolean keepAlive = request.isKeepAlive();

                response = handleRequest(request);


                // 返回用GZip压缩？
                boolean useGzip = response.getMimeType() != null && response.getMimeType().toLowerCase().contains("text/");
                response.setGzipEncoding(useGzip && acceptEncoding != null && acceptEncoding.contains("gzip"));
                response.setKeepAlive(keepAlive);

                response.send(outputStream);

                Log.i(TAG,
                        String.format("%s %s %s %s",
                                request.getMethod().toString(), request.getUri(),
                                response.getStatus().getDescription(),
                                request.getHeaders().get("user-agent")
                        )
                );

            } catch (IOException e) {
                Log.w(TAG, e.toString());
            } finally {
                safeClose(inputStream);
                safeClose(outputStream);
                safeClose(acceptSocket);
                mRequestManager.close(this);
            }
        }

        /**
         * 根据URL，分发请求
         */
        private HttpResponse handleRequest(HttpRequest request) {
            String uri = request.getUri();
            HttpRequest.Method method = request.getMethod();

            HttpServlet servlet = null;

            if (uri.equals("/")) {
                Log.d(TAG, String.valueOf(isDownload) + ":up:" + String.valueOf(isUpload));
                if (isDownload) {
                    return HttpResponse.newRedirectResponse("/download");
                } else if (isUpload) {
                    return HttpResponse.newRedirectResponse("/upload");
                }
            }

            for (Map.Entry<String, HttpServlet> entry : servletMap.entrySet()) {
                if (uri.matches(entry.getKey())) {
                    servlet = entry.getValue();
                    break;
                }
            }
            if (servlet != null) {
                if (method == HttpRequest.Method.GET) {
                    return servlet.doGet(request);
                } else {
                    return servlet.doPost(request);
                }
            } else {
                return HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND,
                        ContentType.MIME_PLAINTEXT, "Not Found");
            }
        }
    }
}