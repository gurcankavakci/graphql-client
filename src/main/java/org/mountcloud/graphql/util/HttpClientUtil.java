package org.mountcloud.graphql.util;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.mountcloud.graphql.service.GraphqlAPIService;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;


/**
 * 2018/2/10. http util
 * @author zhanghaishan
 * @version V1.0
 */
public class HttpClientUtil {
    private static Logger log = LogManager.getLogger(HttpClientUtil.class);

    public String doGet(String url, Map<String, String> param) throws IOException, URISyntaxException {

        // 创建Httpclient对象
        CloseableHttpClient httpclient = HttpClients.createDefault();

        String resultString = "";
        CloseableHttpResponse response = null;
        try {
            // 创建uri
            URIBuilder builder = new URIBuilder(url);
            if (param != null) {
                for (String key : param.keySet()) {
                    builder.addParameter(key, param.get(key));
                }
            }
            URI uri = builder.build();

            // 创建http GET请求
            HttpGet httpGet = new HttpGet(uri);

            // 执行请求
            response = httpclient.execute(httpGet);
            // 判断返回状态是否为200
            if (response.getStatusLine().getStatusCode() == 200) {
                resultString = EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        } finally {
            if (response != null) {
                response.close();
            }
            httpclient.close();
        }
        return resultString;
    }

    public String doGet(String url) throws IOException, URISyntaxException {
        return doGet(url, null);
    }

    public String doPost(String url, Map<String, String> param,Map<String,String> headers) throws IOException {
        // 创建Httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String resultString = "";
        try {
            // 创建Http Post请求
            HttpPost httpPost = new HttpPost(url);
            // 创建参数列表
            if (param != null) {
                List<NameValuePair> paramList = new ArrayList<>();
                for (String key : param.keySet()) {
                    paramList.add(new BasicNameValuePair(key, param.get(key)));
                }
                // 模拟表单
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList,"utf-8");
                httpPost.setEntity(entity);
            }
            // 设置请求头
            if (headers != null) {
                for (String key : headers.keySet()) {
                    httpPost.addHeader(key,headers.get(key));
                }
            }
            // 执行http请求
            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "utf-8");
        }finally {
            response.close();
        }

        return resultString;
    }

    public String doPost(String url,Map<String, String> param) throws IOException {
        return doPost(url, param,null);
    }

    public String doPost(String url) throws IOException {
        return doPost(url, null,null);
    }

    public String doPostJson(String url,String json) throws IOException {
        return doPostJson(url,json,null);
    }

    public String doPostJson(String url, String json,Map<String,String> headers) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new LoggingInterceptor())
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();


        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(url)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
                .build();

        GraphqlAPIService graphqlAPIService = retrofit.create(GraphqlAPIService.class);

        AtomicReference<String> result = new AtomicReference<>();
        graphqlAPIService.graphql("admin", json)
                .doOnError(err -> log.error(err.getLocalizedMessage(), err))
                .retryWhen(this::retryOnNetworkError)
                .retryWhen(this::retryOnHTTPError)
                .blockingSubscribe(s -> {
                    result.set(s);
                });

        return result.get();
    }

    protected Observable retryOnNetworkError(Observable<Throwable> f) {
        return f.flatMap(
                err -> {
                    if (
                            err instanceof SocketTimeoutException
                                    || err instanceof NoRouteToHostException
                                    || err instanceof UnknownHostException
                                    || err instanceof BindException
                                    || err instanceof ClosedChannelException
                                    || err instanceof ConnectException
                                    || err instanceof InterruptedIOException
                                    || err instanceof ProtocolException
                                    || err instanceof SocketException
                                    || err instanceof UnknownServiceException
                    ) {
                        log.error("A network exception occured on request, will retry after 5 seconds.", err);
                        return Observable.timer(5, TimeUnit.SECONDS);
                    }

                    log.info("Propagating error from retryOnNetworkError", err);
                    return Observable.error(err);
                }
        );
    }

    protected Observable retryOnHTTPError(Observable<Throwable> f) {
        return f.flatMap(
                err -> {
                    if (err instanceof HttpException) {
                        HttpException httpException = (HttpException) err;
                        switch (httpException.code()) {
                            case 400:
                                log.error("The request had bad syntax or was inherently impossible to be satisfied",
                                        httpException);
                                return Observable.error(err);
                            case 401:
                                log.error("Authorization required.", httpException);
                                return Observable.error(err);
                            case 402:
                                log.error("Payment required.", httpException);
                                return Observable.error(err);
                            case 403:
                                log.error("The request is for something forbidden.", httpException);
                                return Observable.error(err);
                            case 404:
                                log.error(
                                        "The server has not found anything matching the URI given.", httpException);
                                return Observable.error(err);
                            case 500:
                                log.error(
                                        "The server encountered an unexpected condition which prevented it from "
                                                + "fulfilling the request.",
                                        httpException);
                                return Observable.timer(30, TimeUnit.SECONDS);
                            case 501:
                                log.error("The server does not support the facility required.", httpException);
                                return Observable.error(err);
                            case 502:
                                log.error(
                                        "The server cannot process the request due to a high load "
                                                + "(whether HTTP servicing or other requests.)",
                                        httpException);
                                return Observable.timer(30, TimeUnit.SECONDS);
                            case 503:
                                log.error(
                                        "This is equivalent to Internal Error 500, but in the case of a server "
                                                + "which is in turn accessing some other service, this indicates that the respose "
                                                + "from the other service did not return within a time that the gateway was "
                                                + "prepared to wait. As from the point of view of the clientand the "
                                                + "HTTP transaction the other service is hidden within the server, this maybe "
                                                + "treated identically to Internal error 500, but has more diagnostic value.",
                                        httpException);
                                return Observable.timer(30, TimeUnit.SECONDS);
                            default:
                                log.error("Undefined http exception code.");
                                return Observable.error(err);
                        }
                    }

                    log.info("Propagating error from retryOnHTTPError", err);
                    return Observable.error(err);
                }
        );
    }

}
