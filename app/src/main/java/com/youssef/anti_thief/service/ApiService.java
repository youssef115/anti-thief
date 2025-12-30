package com.youssef.anti_thief.service;

import com.youssef.anti_thief.DTO.EncryptedPayload;
import com.youssef.anti_thief.DTO.LocationPayload;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    

    @POST("api/location")
    Call<ResponseBody> sendLocationBatch(@Body List<LocationPayload> payloads);
    

    @POST("api/secure/location")
    Call<ResponseBody> sendEncryptedLocation(@Body EncryptedPayload payload);
}
