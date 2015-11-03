package com.weezlabs.rx_giphy.Network;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Admin on 17.10.2015.
 */
public class BaseResponse<T> {
	@SerializedName("data")
	private T data_;

	@SerializedName("pagination")
	private PaginationInfo paginationInfo_;


	public T getData() {
		return data_;
	}

	public PaginationInfo getPaginationInfo() {
		return paginationInfo_;
	}

	public static class PaginationInfo{
		@SerializedName("total_count")
		private int total_;

		@SerializedName("count")
		private int count_;

		@SerializedName("offset")
		private int offset_;

		public int getTotalCount() {
			return total_;
		}

		public int getCount() {
			return count_;
		}

		public int getOffset() {
			return offset_;
		}
	}
}
