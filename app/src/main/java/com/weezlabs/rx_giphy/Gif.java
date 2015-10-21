package com.weezlabs.rx_giphy;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;

/**
 * Created by Admin on 17.10.2015.
 */
public class Gif {
	@SerializedName("id")
	private String id_;

	@Expose
	private String url_;

	public String getId() {
		return id_;
	}

	public String getUrl() {
		return url_;
	}

	public static class GifDeserializer implements JsonDeserializer<Gif> {

		@Override
		public Gif deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			Gif gif = new Gson().fromJson(json, typeOfT);
			String url = json.getAsJsonObject().get("images").getAsJsonObject().get("fixed_height").getAsJsonObject().get("url").getAsString();
			gif.setUrl(url);
			return gif;
		}
	}

	public void setId(String id) {
		this.id_ = id;
	}

	public void setUrl(String url) {
		this.url_ = url;
	}
}
