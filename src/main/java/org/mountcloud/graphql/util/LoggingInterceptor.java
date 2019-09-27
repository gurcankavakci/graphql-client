package org.mountcloud.graphql.util;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class LoggingInterceptor implements Interceptor {
	private static Logger logger = LogManager.getLogger(LoggingInterceptor.class);

	@Override
	public Response intercept(Chain chain) throws IOException {
		Request request = chain.request();

		long t1 = System.nanoTime();
		logger.info(String.format("Sending request %s on %s",
				request.url(), chain.connection()));

		Response response = chain.proceed(request);

		long t2 = System.nanoTime();
		logger.info(String.format("Received response for %s in %.1fms",
				response.request().url(), (t2 - t1) / 1e6d));

		return response;
	}
}
