package org.mountcloud.graphql.service;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface GraphqlAPIService {
	@Headers({"Content-Type: application/json"})
	@POST("graphql")
	Observable<String> graphql(@Header ("x-auth-userid") String authorization, @Body String text);
}
