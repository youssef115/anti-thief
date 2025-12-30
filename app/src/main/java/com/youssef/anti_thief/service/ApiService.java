package com.youssef.anti_thief.service;

import com.youssef.anti_thief.DTO.LocationPayload;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    
    @POST("api/location")
    Call<Void> sendLocation(@Body LocationPayload payload);
}
