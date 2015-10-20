package com.weezlabs.rx_giphy;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Admin on 17.10.2015.
 */
public class BaseResponse<T> {
	@SerializedName("data")
	private T data_;

	public T getData() {
		return data_;
	}
}
